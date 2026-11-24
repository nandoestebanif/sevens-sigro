package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.dominio.ErrorSigro

/**
 * Qué hacer con un registro de la cola cuando el servidor responde con error (HU-38).
 * Es la misma decisión para salidas y para la cola general: estaba dentro de RepositorioSalidasImpl
 * y se sacó aquí sin cambiarla, para que la asistencia siga exactamente la misma regla.
 */
internal sealed interface DecisionEnvio {
    /** Queda PENDIENTE con su nota. `cortar`: falla por señal, sesión o servidor; fallarán todas. */
    data class Reintentar(val nota: String, val cortar: Boolean) : DecisionEnvio
    /** El servidor ya lo tenía (regla 3). */
    data object YaRecibido : DecisionEnvio
    /** Regla de negocio: queda RECHAZADO con su motivo y no se pierde. */
    data class Rechazar(val motivo: String) : DecisionEnvio
}

/**
 * `que` completa la nota del 404: «El servidor aún no recibe salidas: …».
 * Un 404 SIN `codigo` es la ruta que no existe (servidor anterior): se espera. Un 404 CON `codigo` es una
 * regla de negocio (`almacen_no_existe`, `compra_no_existe`): se rechaza con el `mensaje` del servidor,
 * igual que un 422, para que el almacenero lo vea en «Mis registros».
 */
internal fun decidirEnvio(e: ErrorSigro, que: String): DecisionEnvio = when (e) {
    // `when` exhaustivo sobre la jerarquía sellada: si mañana se agrega un tipo de error, esto no compila
    // hasta decidir qué hacer con él.
    is ErrorSigro.SinConexion, is ErrorSigro.Servidor ->
        DecisionEnvio.Reintentar("Sin señal o el servidor está ocupado: se enviará solo.", cortar = true)
    is ErrorSigro.SesionVencida -> DecisionEnvio.Reintentar("Tu sesión venció: vuelve a ingresar y se envía.", cortar = true)
    is ErrorSigro.YaRegistrado -> DecisionEnvio.YaRecibido // el servidor ya la tenía (regla 3)
    is ErrorSigro.Negocio ->
        if (e.estadoHttp == 404 && e.codigo == "http_404") {
            DecisionEnvio.Reintentar("El servidor aún no recibe $que: queda guardada en el celular.", cortar = true)
        } else {
            DecisionEnvio.Rechazar(e.message) // stock insuficiente, dispositivo no autorizado…
        }
    is ErrorSigro.SinPermiso, is ErrorSigro.Validacion -> DecisionEnvio.Rechazar(e.message)
}
