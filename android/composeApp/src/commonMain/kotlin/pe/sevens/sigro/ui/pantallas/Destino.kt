package pe.sevens.sigro.ui.pantallas

import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.dominio.SalidaRegistrada

/** Pantallas dentro de una obra. Las que necesitan un dato lo llevan consigo. */
sealed interface Destino {
    data object Inicio : Destino
    data object Salida : Destino
    /** Una salida rechazada, para registrarla de nuevo con sus datos ya puestos (HU-38). */
    data class SalidaDe(val base: SalidaRegistrada) : Destino
    data object Pendientes : Destino
    /** «Mis registros» abierto solo con lo rechazado (al tocar «Rechazadas» en el inicio). */
    data object Rechazados : Destino
    data object Dispositivo : Destino
    /** Perfil: quién eres, en qué obra, tu celular, el catálogo del celular y cerrar sesión. */
    data object Perfil : Destino
    // Catálogo (HU-08, HU-10, HU-17)
    data object Catalogo : Destino
    data class Ficha(val insumo: FichaInsumo) : Destino
    data object InsumoNuevo : Destino
    // Mano de obra (HU-62, HU-63)
    data object Trabajadores : Destino
    data object TrabajadorNuevo : Destino
    data object Asistencia : Destino
    // Almacén: se guarda en el celular y se envía cuando exista el servicio almacén
    data object Entrada : Destino
    data object IngresoSinCompra : Destino
    data object Traslado : Destino
    data object Conteo : Destino
    data object Cierre : Destino
    data object Stock : Destino
}
