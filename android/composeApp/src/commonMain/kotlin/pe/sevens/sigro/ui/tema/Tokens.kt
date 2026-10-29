package pe.sevens.sigro.ui.tema

import androidx.compose.ui.graphics.Color

/*
 * Fichas técnicas del diseño aprobado (android/diseno/LEEME.md, «estilo B claro moderno + vidrio»).
 * Un solo lugar para los valores: si el lienzo cambia, se cambia aquí.
 */
object Paleta {
    // Texto
    val Tinta = Color(0xFF111827)          // principal y botón negro
    val Texto2 = Color(0xFF374151)         // secundario fuerte
    val Texto3 = Color(0xFF4B5563)         // secundario
    val Pizarra = Color(0xFF475569)        // trazos de los planos
    val Gris = Color(0xFF9CA3AF)           // botón deshabilitado, chevrones

    // Acento
    val Naranja = Color(0xFFC2410C)
    val NaranjaClaro = Color(0xFFFFEDD5)
    val NaranjaOscuro = Color(0xFF9A3412)
    val NaranjaVivo = Color(0xFFFB923C)    // íconos sobre negro, manchas del fondo
    val Ambar = Color(0xFFB45309)          // cifra «por enviar»
    val Rojo = Color(0xFFB91C1C)           // cifra «rechazada»

    // Estados (siempre con ícono + palabra)
    val PendienteTexto = Color(0xFF92400E); val PendienteFondo = Color(0xFFFEF3C7)
    val EnviadoTexto = Color(0xFF166534); val EnviadoFondo = Color(0xFFDCFCE7)
    val RechazadoTexto = Color(0xFF991B1B); val RechazadoFondo = Color(0xFFFEE2E2)
    val ErrorTexto = Color(0xFF7F1D1D)

    // Fondo base y vidrio
    val FondoBase = Color(0xFFEDF1F6)
    val Noche = Color(0xFF1E293B)          // franja de la ilustración en Entrar y Elegir obra
    val Vidrio = Color.White.copy(alpha = 0.58f)
    val VidrioFuerte = Color.White.copy(alpha = 0.78f)
    val BordeVidrio = Color.White.copy(alpha = 0.75f)
    val BordeVidrioFuerte = Color.White.copy(alpha = 0.85f)
    val BordeCampo = Tinta.copy(alpha = 0.14f)
    val Separador = Tinta.copy(alpha = 0.07f)
}
