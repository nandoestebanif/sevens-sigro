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

/** Resultado de guardar o abrir un secreto con la huella (la contraseña cifrada para entrar después). */
sealed interface ResultadoSecreto {
    data class Ok(val valor: String) : ResultadoSecreto
    data object Cancelada : ResultadoSecreto
    data class Fallo(val mensaje: String) : ResultadoSecreto
    /** La llave se invalidó porque se agregó una huella al celular: el secreto ya no se puede abrir. */
    data object HuellaNueva : ResultadoSecreto
    /** Este celular no puede atar un secreto a la huella (Android 10 o menos): solo desbloquea la app. */
    data object NoSoportado : ResultadoSecreto
}

/** La huella del celular (expect/actual en plataforma: BiometricPrompt en Android). */
interface Biometria {
    fun disponibilidad(): DisponibilidadHuella
    suspend fun verificar(titulo: String, detalle: String): ResultadoHuella
    /** Borra la llave del Keystore del candado (al desactivar o al cerrar sesión). */
    fun olvidar() {}

    /** Si puede guardar la contraseña cifrada con una llave que solo abre la huella (Android 11+). */
    fun guardaClaves(): Boolean = false
    /** Cifra [secreto] tras la huella. En [ResultadoSecreto.Ok] viene el «sobre» cifrado (texto) para guardar. */
    suspend fun cifrar(titulo: String, detalle: String, secreto: String): ResultadoSecreto = ResultadoSecreto.NoSoportado
    /** Abre el [sobre] tras la huella. En [ResultadoSecreto.Ok] viene el secreto. */
    suspend fun descifrar(titulo: String, detalle: String, sobre: String): ResultadoSecreto = ResultadoSecreto.NoSoportado
    /** Borra la llave de la contraseña guardada («no soy yo», huella apagada o contraseña cambiada). */
    fun olvidarClave() {}
}

/**
 * La cuenta que entra con huella en ESTE celular (una sola: la última que la activó). [sobre] es la contraseña
 * cifrada con una llave del Keystore que solo se abre con la huella: sin la huella no sirve, ni copiando la base.
 * Cerrar sesión NO la borra (es justo para volver a entrar con la huella); «no soy yo» sí.
 */
data class CuentaConHuella(val usuarioId: Int, val usuario: String, val nombre: String, val servidor: String, val sobre: String) {
    /** El primer nombre, para «Entrar como Adiel». */
    val primerNombre: String get() = nombre.trim().substringBefore(' ').ifEmpty { usuario }
}

/**
 * La contraseña recién escrita, solo en memoria y por poco tiempo: es lo que se cifra si la persona activa la huella
 * justo al entrar. Se entrega una sola vez y vence a los [vigenciaMs] (no queda en disco ni en el estado de pantalla).
 */
class ClaveReciente(private val reloj: () -> Long, private val vigenciaMs: Long = 10 * 60_000L) {
    private var guardada: Triple<String, String, Long>? = null

    fun guardar(usuario: String, clave: String) { guardada = Triple(usuario.trim().lowercase(), clave, reloj()) }

    /** ¿Hay una contraseña vigente de [usuario]? (sin entregarla) */
    fun hay(usuario: String): Boolean = vigente(usuario) != null

    /** La entrega y la olvida. `null` si no hay, si es de otro usuario o si ya venció. */
    fun tomar(usuario: String): String? = vigente(usuario).also { guardada = null }

    fun olvidar() { guardada = null }

    private var conContrasena = false

    /** Desde el candado eligió «entrar con mi contraseña»: la pantalla de entrar abre el formulario, sin pedir huella. */
    fun preferirContrasena() { conContrasena = true }

    /** ¿La próxima entrada va con contraseña? Se responde una sola vez. */
    fun tomarPreferirContrasena(): Boolean = conContrasena.also { conContrasena = false }

    private fun vigente(usuario: String): String? {
        val (u, c, t) = guardada ?: return null
        val ahora = reloj()
        return c.takeIf { u == usuario.trim().lowercase() && ahora >= t && ahora - t <= vigenciaMs }
    }
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
    /** La cuenta que entra con huella en este celular (sobrevive a «cerrar sesión»). */
    suspend fun cuenta(): CuentaConHuella?
    suspend fun guardarCuenta(c: CuentaConHuella)
    suspend fun olvidarCuenta()
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

    /** La cuenta con huella ↔ texto (una línea por dato; ningún dato lleva salto de línea). Dañado → null. */
    fun codificarCuenta(c: CuentaConHuella): String =
        listOf(c.usuarioId.toString(), c.usuario, c.nombre, c.servidor, c.sobre).joinToString("\n") { it.replace('\n', ' ') }

    fun decodificarCuenta(texto: String?): CuentaConHuella? {
        val p = texto?.split('\n') ?: return null
        if (p.size != 5 || p.any { it.isBlank() }) return null
        return CuentaConHuella(p[0].toIntOrNull() ?: return null, p[1], p[2], p[3], p[4])
    }

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
