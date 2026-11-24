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
    /** A1: lo que dijo el servidor al recibirla (nombre de quien registró y hora del servidor). */
    val registradoPor: String? = null,
    val fechaServidor: String? = null,
)

/** Datos que el almacenero llena en la pantalla de salida. */
data class NuevaSalida(
    val obraId: Int,
    val insumo: Insumo,
    val cantidad: String,
    val motivo: String,
    val quienRetira: String,
    val frente: String?,
    /** M6: almacén de donde sale. Nulo solo si no se pudo saber (sin señal la primera vez): el servidor lo
     *  resuelve si la obra tiene un único almacén activo, y si no, rechaza con su mensaje. */
    val almacenId: Int? = null,
)

/**
 * Estado del celular frente a la obra (HU-15).
 * `visible = false`: el usuario no puede listar los dispositivos (403, solo el administrador puede), así que
 * NO se sabe si ya lo pidió: no se dice «Sin solicitar» (solo con un servidor sin `GET …/dispositivos/mios`).
 * `otroCelularActivo`: la persona ya tiene OTRO celular autorizado en la obra (p. ej. reinstaló la app y cambió el
 * código). Regla 5: uno por responsable; el administrador revoca el anterior y autoriza este.
 */
data class EstadoDispositivo(
    val identificador: String,
    val estado: String?, // null: aún no se pidió autorización desde este celular (si `visible`)
    val visible: Boolean = true,
    val otroCelularActivo: Boolean = false,
) {
    val estadoLegible: String
        get() = when {
            !visible && estado == null -> "Estado no disponible para tu usuario"
            estado == null -> "Sin solicitar"
            estado == "pendiente" -> "Pendiente de autorizar"
            estado == AUTORIZADO -> "Autorizado"
            estado == "revocado" -> "Revocado"
            estado == YA_SOLICITADO -> "Ya solicitado (pendiente o autorizado)"
            else -> estado
        }

    companion object {
        /** El servidor respondió 409 al pedir: este celular ya estaba registrado en la obra. */
        const val YA_SOLICITADO = "ya_solicitado"
        const val AUTORIZADO = "autorizado"
    }
}

/**
 * Error de la API traducido a una jerarquía SELLADA: la pantalla y la política de envío deciden con un `when`
 * exhaustivo (el compilador avisa si aparece un tipo nuevo y alguien no lo maneja).
 * Todos conservan {codigo, mensaje} del contrato del servidor y el estado HTTP cuando lo hubo.
 *
 * Se crea siempre con la función [ErrorSigro] de abajo, que clasifica: así quien lanza el error no tiene que
 * saber a qué subtipo corresponde.
 */
sealed class ErrorSigro(
    val codigo: String,
    override val message: String,
    val estadoHttp: Int?,
    @Suppress("UNUSED_PARAMETER") sellado: Sellado,   // distingue el constructor de la función ErrorSigro(…)
) : Exception(message) {
    /** Marca interna: solo los subtipos de aquí pueden construir un ErrorSigro. */
    object Sellado

    /** Sin señal o el servidor no respondió (no hubo respuesta HTTP útil). */
    class SinConexion(codigo: String, mensaje: String, estadoHttp: Int? = null) : ErrorSigro(codigo, mensaje, estadoHttp, Sellado)
    /** 5xx, 408 o 429: el servidor está caído u ocupado; el mismo pedido se acepta más tarde. */
    class Servidor(codigo: String, mensaje: String, estadoHttp: Int) : ErrorSigro(codigo, mensaje, estadoHttp, Sellado)
    /** 401 o sin sesión guardada: hay que volver a ingresar. */
    class SesionVencida(codigo: String, mensaje: String, estadoHttp: Int?) : ErrorSigro(codigo, mensaje, estadoHttp, Sellado)
    /** 403: el rol no tiene ese privilegio en esta obra. La sesión NO se cierra. */
    class SinPermiso(codigo: String, mensaje: String) : ErrorSigro(codigo, mensaje, 403, Sellado)
    /** 409 por uuid_cliente repetido: el servidor ya tenía ese registro (regla 3). */
    class YaRegistrado(codigo: String, mensaje: String) : ErrorSigro(codigo, mensaje, 409, Sellado)
    /** 400/422 o validación local: un dato está mal escrito. */
    class Validacion(codigo: String, mensaje: String, estadoHttp: Int?) : ErrorSigro(codigo, mensaje, estadoHttp, Sellado)
    /** Regla de negocio (stock insuficiente, celular no autorizado, 404 con código…). */
    class Negocio(codigo: String, mensaje: String, estadoHttp: Int?) : ErrorSigro(codigo, mensaje, estadoHttp, Sellado)

    /**
     * Sin señal, servidor caído u ocupado: se reintenta, no se rechaza.
     * 408 (tiempo agotado) y 429 (demasiadas peticiones) no son una regla de negocio: el mismo registro
     * se acepta más tarde. Sin esto, un registro válido quedaba RECHAZADO para siempre por un tope de la nube.
     */
    val esTransitorio: Boolean get() = this is SinConexion || this is Servidor
}

private val SIN_CONEXION = setOf("sin_conexion", "servidor_no_responde")
private val SIN_SESION = setOf("sin_sesion", "credenciales")

/** Traduce {codigo, mensaje, estado HTTP} al subtipo que corresponde. Es la única puerta para crear un error. */
fun ErrorSigro(codigo: String, message: String, estadoHttp: Int? = null): ErrorSigro = when {
    codigo in SIN_CONEXION -> ErrorSigro.SinConexion(codigo, message, estadoHttp)
    estadoHttp != null && (estadoHttp >= 500 || estadoHttp == 408 || estadoHttp == 429) -> ErrorSigro.Servidor(codigo, message, estadoHttp)
    estadoHttp == 401 || codigo in SIN_SESION -> ErrorSigro.SesionVencida(codigo, message, estadoHttp)
    estadoHttp == 403 -> ErrorSigro.SinPermiso(codigo, message)
    estadoHttp == 409 && codigo.contains("uuid") -> ErrorSigro.YaRegistrado(codigo, message)
    estadoHttp == 400 || estadoHttp == 422 || (estadoHttp == null && codigo !in SIN_CONEXION) -> ErrorSigro.Validacion(codigo, message, estadoHttp)
    else -> ErrorSigro.Negocio(codigo, message, estadoHttp)
}
