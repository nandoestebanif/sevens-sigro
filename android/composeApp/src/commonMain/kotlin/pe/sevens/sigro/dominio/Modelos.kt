package pe.sevens.sigro.dominio

/*
 * Modelos del dominio de la app del almacenero.
 * No dependen de Ktor, de Room ni de Compose: son la capa que no cambia si cambia la tecnología.
 */

/** Sesión abierta contra la API (HU-12). El token vive en el celular, nunca en el código. */
data class Sesion(
    val servidor: String,
    val token: String,
    val usuarioId: Int,
    val nombre: String,
    val usuario: String,
    val obras: List<ObraAsignada>,
)

/** Obra donde la persona está asignada, con su rol en ESA obra (regla 9: el alcance es la obra). */
data class ObraAsignada(val id: Int, val nombre: String, val rol: String) {
    val rolLegible: String
        get() = when (rol) {
            "almacenero" -> "Almacenero"
            "ssoma" -> "Ingeniero SSOMA"
            "residente" -> "Residente"
            "administrador" -> "Administrador"
            "gerente" -> "Gerente"
            else -> rol.replaceFirstChar { it.uppercase() }
        }
}

/** Insumo del catálogo único (regla 8). Se guarda copia local para buscar sin señal. */
data class Insumo(
    val id: Int,
    val codigo: String,
    val descripcion: String,
    val unidad: String,
)

/** Estado de un movimiento registrado en el celular (HU-37 / HU-38). */
enum class EstadoEnvio {
    /** Guardado en el celular, esperando señal o un reintento. */
    PENDIENTE,
    /** El servidor lo recibió (o ya lo tenía: reenvío idempotente, regla 3). */
    ENVIADO,
    /** El servidor lo rechazó por una regla de negocio: queda con su motivo y no se pierde. */
    RECHAZADO,
}

/**
 * Salida de material registrada en el celular (HU-40), antes y después de enviarse.
 * La cantidad viaja como texto decimal para no perder precisión (la API también usa Decimal).
 * El saldo NO está aquí: lo calcula el servidor (regla 2).
 */
data class SalidaRegistrada(
    val uuidCliente: String,
    val obraId: Int,
    val insumo: Insumo,
    val cantidad: String,
    val motivo: String,
    val quienRetira: String,
    val frente: String?,
    val horaDispositivoMs: Long,
    val estado: EstadoEnvio,
    val motivoRechazo: String?,
    val nota: String?,
    val intentos: Int,
)

/** Datos que el almacenero llena en la pantalla de salida. */
data class NuevaSalida(
    val obraId: Int,
    val insumo: Insumo,
    val cantidad: String,
    val motivo: String,
    val quienRetira: String,
    val frente: String?,
)

/** Estado del celular frente a la obra (HU-15). */
data class EstadoDispositivo(
    val identificador: String,
    val estado: String?, // null: aún no se pidió autorización desde este celular
) {
    val estadoLegible: String
        get() = when (estado) {
            null -> "Sin solicitar"
            "pendiente" -> "Pendiente de autorizar"
            "autorizado" -> "Autorizado"
            "revocado" -> "Revocado"
            else -> estado
        }
}

/** Error de la API con el mismo contrato que usa la web: {codigo, mensaje}. */
class ErrorSigro(
    val codigo: String,
    override val message: String,
    val estadoHttp: Int? = null,
) : Exception(message) {
    /** Sin señal o servidor caído: se reintenta, no se rechaza. */
    val esTransitorio: Boolean
        get() = codigo == "sin_conexion" || (estadoHttp != null && estadoHttp >= 500)
}
