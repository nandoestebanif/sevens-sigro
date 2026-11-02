package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable

/** Lo poco que cambia entre Android e iOS. Todo lo demás es código común. */
expect object Plataforma {
    /** «android» o «ios»: prefijo del identificador del dispositivo (HU-15). */
    val nombre: String

    /** Servidor por defecto: el ambiente de pruebas (HT-01) en el celular, la PC local en escritorio. */
    val servidorPorDefecto: String

    /** Hora del dispositivo en milisegundos (HU-37 la guarda; la que vale para auditar es la del servidor). */
    fun ahoraMs(): Long

    /** «28/10 14:05» en la zona del celular. */
    fun formatearHora(ms: Long): String
}

/** Botón «atrás» del sistema (Android). En iOS el gesto lo maneja la barra superior. */
@Composable
expect fun ManejarAtras(activo: Boolean, alVolver: () -> Unit)

/** «Reducir movimiento» del sistema: con él encendido, las animaciones del diseño se apagan. */
@Composable
expect fun reducirMovimiento(): Boolean

/**
 * El vidrio va sin desenfoque (blanco 82–90 %, el respaldo que el diseño ya permite): en celulares de
 * gama baja (Android «low RAM») y con «reducir movimiento». Desenfocar cada tarjeta en cada cuadro es
 * lo más caro de la interfaz (docs/rendimiento-app.md).
 */
@Composable
expect fun vidrioSinDesenfoque(): Boolean
