package pe.sevens.sigro.dominio

/*
 * Capacidad nativa: la cámara, para fotografiar la guía de remisión al recibir una compra (HU-35).
 * El dominio solo conoce esta interfaz; cada plataforma la implementa (androidMain: permiso CAMERA +
 * intent de cámara del sistema; iOS y escritorio: «no disponible»). Ninguna clase de Android entra aquí.
 */

/** Los estados del permiso que la pantalla resuelve, cada uno con su propio mensaje. */
enum class PermisoCamara {
    /** Concedido: se puede tomar la foto. */
    CONCEDIDO,
    /** Nunca se preguntó: el primer toque abre el diálogo del sistema. */
    SIN_PREGUNTAR,
    /** Se negó una vez: se explica para qué sirve y se puede volver a pedir. */
    DENEGADO,
    /** «No volver a preguntar»: el sistema ya no muestra el diálogo; solo se activa desde Ajustes. */
    DENEGADO_PARA_SIEMPRE,
    /** La plataforma no tiene cámara integrada en esta versión (iOS, escritorio). */
    NO_DISPONIBLE,
}

/** Resultado de intentar tomar una foto. */
sealed interface ResultadoFoto {
    /** Foto guardada en el almacenamiento privado de la app. */
    data class Tomada(val ruta: String) : ResultadoFoto
    /** El usuario cerró la cámara sin tomarla. */
    data object Cancelada : ResultadoFoto
    /** No hay permiso: la pantalla decide qué mostrar según el estado. */
    data class SinPermiso(val permiso: PermisoCamara) : ResultadoFoto
}

interface Camara {
    /** Estado actual del permiso (se vuelve a leer cada vez: el usuario puede cambiarlo en Ajustes). */
    fun permiso(): PermisoCamara
    /** Pide el permiso al sistema (si corresponde) y devuelve el estado en que quedó. */
    suspend fun pedirPermiso(): PermisoCamara
    /** Abre la cámara del sistema y guarda la foto con ese nombre. */
    suspend fun tomarFoto(nombre: String): ResultadoFoto
    /** Abre los ajustes de la app (único camino tras «no volver a preguntar»). */
    fun abrirAjustes()
}

/**
 * La regla de los estados, sin Android: se prueba en commonTest.
 * `yaSePregunto`: la app pidió el permiso alguna vez. `sistemaPermiteExplicar`: Android devuelve
 * shouldShowRequestPermissionRationale = true cuando el usuario negó pero todavía se le puede volver a preguntar.
 */
fun estadoPermiso(concedido: Boolean, yaSePregunto: Boolean, sistemaPermiteExplicar: Boolean): PermisoCamara = when {
    concedido -> PermisoCamara.CONCEDIDO
    !yaSePregunto -> PermisoCamara.SIN_PREGUNTAR
    sistemaPermiteExplicar -> PermisoCamara.DENEGADO
    else -> PermisoCamara.DENEGADO_PARA_SIEMPRE
}

/** Implementación para plataformas sin cámara integrada todavía (iOS y escritorio). */
object CamaraNoDisponible : Camara {
    override fun permiso() = PermisoCamara.NO_DISPONIBLE
    override suspend fun pedirPermiso() = PermisoCamara.NO_DISPONIBLE
    override suspend fun tomarFoto(nombre: String): ResultadoFoto = ResultadoFoto.SinPermiso(PermisoCamara.NO_DISPONIBLE)
    override fun abrirAjustes() {}
}
