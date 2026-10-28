package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable

/** Lo poco que cambia entre Android e iOS. Todo lo demás es código común. */
expect object Plataforma {
    /** «android» o «ios»: prefijo del identificador del dispositivo (HU-15). */
    val nombre: String

    /** Servidor por defecto en desarrollo: el emulador de Android ve la PC como 10.0.2.2. */
    val servidorPorDefecto: String

    /** Hora del dispositivo en milisegundos (HU-37 la guarda; la que vale para auditar es la del servidor). */
    fun ahoraMs(): Long

    /** «28/10 14:05» en la zona del celular. */
    fun formatearHora(ms: Long): String
}

/** Botón «atrás» del sistema (Android). En iOS el gesto lo maneja la barra superior. */
@Composable
expect fun ManejarAtras(activo: Boolean, alVolver: () -> Unit)
