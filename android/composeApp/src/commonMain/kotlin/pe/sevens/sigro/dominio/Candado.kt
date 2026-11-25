package pe.sevens.sigro.dominio

/*
 * Entrar con huella (rediseño v4, 25/11). La app se bloquea tras un rato sin usarla y se abre con la huella o
 * con el PIN/patrón del celular (BiometricPrompt). La huella nunca sale del celular: Android solo responde
 * «sí, es el dueño». Refuerza la regla 5 (un celular autorizado por responsable): nadie registra a tu nombre
 * con tu celular desbloqueado. Todo lo de este archivo es Kotlin puro y se prueba sin el celular.
 */

/** Minutos sin usar la app que se pueden elegir en Perfil. */
val MINUTOS_BLOQUEO = listOf(5, 15, 30)
const val MINUTOS_POR_DEFECTO = 15

/** Ajuste de cada usuario en ESTE celular. [ofrecida]: ya se le preguntó una vez si la quería (no se insiste). */
data class AjusteHuella(val activa: Boolean = false, val minutos: Int = MINUTOS_POR_DEFECTO, val ofrecida: Boolean = false)

/** Si el celular puede pedir huella (o PIN/patrón). */
enum class DisponibilidadHuella {
    DISPONIBLE,
    /** Tiene sensor pero no hay huella ni bloqueo de pantalla configurado. */
    SIN_REGISTRAR,
    NO_DISPONIBLE,
}

sealed interface ResultadoHuella {
    data object Ok : ResultadoHuella
    /** La persona cerró el diálogo: no es un error. */
    data object Cancelada : ResultadoHuella
    data class Fallo(val mensaje: String) : ResultadoHuella
    /** Se agregó una huella nueva al celular desde la última vez: ya no se confía y se pide la contraseña. */
    data object HuellaNueva : ResultadoHuella
}

/** La huella del celular (expect/actual en plataforma: BiometricPrompt en Android). */
interface Biometria {
    fun disponibilidad(): DisponibilidadHuella
    suspend fun verificar(titulo: String, detalle: String): ResultadoHuella
    /** Borra la llave del Keystore (al desactivar o al cerrar sesión). */
    fun olvidar() {}
}

/** Sin huella (escritorio, iOS por ahora, o un celular sin sensor ni bloqueo). */
object SinBiometria : Biometria {
    override fun disponibilidad() = DisponibilidadHuella.NO_DISPONIBLE
    override suspend fun verificar(titulo: String, detalle: String): ResultadoHuella = ResultadoHuella.Fallo("Este equipo no tiene huella.")
}

/** Dónde se guarda el ajuste (por usuario) y la última vez que se usó la app. */
interface RepositorioCandado {
    suspend fun ajuste(usuarioId: Int): AjusteHuella
    suspend fun guardar(usuarioId: Int, ajuste: AjusteHuella)
    suspend fun ultimoUso(): Long?
    suspend fun guardarUltimoUso(ms: Long)
}

object ReglaCandado {
    /**
     * ¿Toca bloquear? Solo con la huella activa, y si pasó el tiempo elegido desde el último uso. Sin registro
     * del último uso (primera vez tras instalar) o con el reloj del celular atrasado, se bloquea: ante la duda,
     * el candado.
     */
    fun vencido(ajuste: AjusteHuella, ultimoUsoMs: Long?, ahoraMs: Long): Boolean {
        if (!ajuste.activa) return false
        if (ultimoUsoMs == null || ahoraMs < ultimoUsoMs) return true
        return ahoraMs - ultimoUsoMs >= ajuste.minutos * 60_000L
    }

    /** Se ofrece una sola vez, si el celular puede y la persona aún no la tiene. */
    fun ofrecer(ajuste: AjusteHuella, disponible: DisponibilidadHuella): Boolean =
        !ajuste.activa && !ajuste.ofrecida && disponible == DisponibilidadHuella.DISPONIBLE

    /** Minutos válidos: si llega otro valor (ajuste viejo o editado), se usa el de siempre. */
    fun minutosValidos(m: Int): Int = if (m in MINUTOS_BLOQUEO) m else MINUTOS_POR_DEFECTO

    /** «activa=1;min=15;ofrecida=0» ↔ ajuste. Un texto dañado da el ajuste por defecto (huella apagada). */
    fun codificar(a: AjusteHuella): String = "activa=${if (a.activa) 1 else 0};min=${a.minutos};ofrecida=${if (a.ofrecida) 1 else 0}"

    fun decodificar(texto: String?): AjusteHuella {
        if (texto.isNullOrBlank()) return AjusteHuella()
        val m = texto.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        return AjusteHuella(
            activa = m["activa"] == "1",
            minutos = minutosValidos(m["min"]?.toIntOrNull() ?: MINUTOS_POR_DEFECTO),
            ofrecida = m["ofrecida"] == "1",
        )
    }
}
