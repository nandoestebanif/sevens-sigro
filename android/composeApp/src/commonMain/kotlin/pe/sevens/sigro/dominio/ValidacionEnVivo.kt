package pe.sevens.sigro.dominio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lo que el formulario muestra: el error de cada campo y los campos ya revisados que están bien (✓). */
data class Revision<K>(val errores: Map<K, String> = emptyMap(), val correctos: Set<K> = emptySet())

/**
 * Validación mientras se escribe, con la regla «premiar pronto, avisar con calma»:
 *
 * - lo que el almacenero **corrige** deja de marcarse al instante (no espera a que termine de escribir);
 * - un error **nuevo** aparece tras una pausa corta al escribir ([PAUSA_MS]), para no regañar a mitad de
 *   un número («0» antes de «0,5»), y al momento si sale del campo o elige de una lista;
 * - un campo que todavía no tocó no se marca, salvo al intentar guardar (entonces se revisa todo).
 *
 * Solo decide qué se muestra y cuándo: las reglas son las de siempre ([validar]) y el servidor vuelve a
 * verificar todo. Es Kotlin puro (corre sin la base ni la pantalla).
 */
class ValidacionEnVivo<K>(
    private val alcance: CoroutineScope,
    private val validar: () -> Map<K, String>,
    private val publicar: (Revision<K>) -> Unit,
    private val pausaMs: Long = PAUSA_MS,
) {
    private val tocados = mutableSetOf<K>()
    private var todoTocado = false
    private var visible = Revision<K>()
    private var espera: Job? = null

    /** El usuario escribió en [campo]: lo corregido se quita ya; lo nuevo que esté mal se muestra tras la pausa. */
    fun cambio(campo: K) {
        tocados += campo
        val todos = validar()
        mostrar(Revision(soloLoQueSigue(visible.errores, todos), correctos(todos)))
        espera?.cancel()
        espera = alcance.launch {
            delay(pausaMs)
            mostrar(revisar())
        }
    }

    /** Salió de [campo] o eligió una opción de una lista: se revisa al momento, sin pausa. */
    fun salir(campo: K) {
        tocados += campo
        espera?.cancel()
        mostrar(revisar())
    }

    /** Cambió algo que afecta a otros campos ya tocados (p. ej. el origen del ingreso): se revisa al momento. */
    fun revisarYa() {
        espera?.cancel()
        mostrar(revisar())
    }

    /** Al guardar se revisan todos los campos. Devuelve los errores: vacío = se puede guardar. */
    fun alGuardar(): Map<K, String> {
        espera?.cancel()
        todoTocado = true
        val todos = validar()
        mostrar(Revision(todos, correctos(todos)))
        return todos
    }

    /** Formulario nuevo («registrar otra»): nada tocado, nada marcado. */
    fun reiniciar() {
        espera?.cancel()
        tocados.clear()
        todoTocado = false
        mostrar(Revision())
    }

    private fun revisar(): Revision<K> {
        val todos = validar()
        return Revision(todos.filterKeys(::tocado), correctos(todos))
    }

    private fun tocado(campo: K) = todoTocado || campo in tocados
    private fun correctos(todos: Map<K, String>) = tocados.filterTo(mutableSetOf()) { it !in todos }

    private fun mostrar(r: Revision<K>) {
        visible = r
        publicar(r)
    }

    companion object {
        /** Pausa al escribir antes de mostrar un error nuevo: lo que tarda una persona entre tecla y tecla, y algo más. */
        const val PAUSA_MS = 600L

        /** De los errores que ya se ven, quedan solo los que siguen mal, con su mensaje de ahora. */
        fun <K> soloLoQueSigue(visibles: Map<K, String>, todos: Map<K, String>): Map<K, String> =
            visibles.keys.filter { it in todos }.associateWith { todos.getValue(it) }
    }
}
