package pe.sevens.sigro.ui.pantallas

import pe.sevens.sigro.dominio.FichaInsumo

/** Pantallas dentro de una obra. Las que necesitan un dato lo llevan consigo. */
sealed interface Destino {
    data object Inicio : Destino
    data object Salida : Destino
    data object Pendientes : Destino
    data object Dispositivo : Destino
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
