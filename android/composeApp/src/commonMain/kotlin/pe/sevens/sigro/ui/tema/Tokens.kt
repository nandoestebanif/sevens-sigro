package pe.sevens.sigro.ui.tema

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Claro, oscuro o como el celular. `oscuro` es estado de Compose: cambiarlo vuelve a pintar todo lo que leyó
 * un color de [Paleta]. Lo fija `App()` según la preferencia guardada y el tema del sistema.
 */
object Apariencia {
    var modo by mutableStateOf(ModoTema.SISTEMA)
    var oscuro by mutableStateOf(false)
}

enum class ModoTema(val texto: String) { SISTEMA("Como el celular"), CLARO("Claro"), OSCURO("Oscuro") }

/*
 * Fichas técnicas del diseño aprobado (android/diseno/LEEME.md, «estilo B claro moderno + vidrio»).
 * Un solo lugar para los valores: si el lienzo cambia, se cambia aquí. Cada ficha tiene su valor claro y su
 * valor oscuro; en oscuro el texto y los acentos se aclaran para seguir pasando AA sobre el fondo pizarra.
 */
object Paleta {
    private inline fun c(claro: Long, oscuro: Long) = Color(if (Apariencia.oscuro) oscuro else claro)

    // Texto
    val Tinta get() = c(0xFF111827, 0xFFF1F5F9)          // principal y botón principal
    val Texto2 get() = c(0xFF374151, 0xFFD1D5DB)         // secundario fuerte
    val Texto3 get() = c(0xFF4B5563, 0xFFAEB7C4)         // secundario
    val Pizarra get() = c(0xFF475569, 0xFF94A3B8)        // trazos de los planos
    val Gris get() = c(0xFF9CA3AF, 0xFF4B5563)           // botón deshabilitado, chevrones
    /** Lo que va encima de un relleno [Tinta] (texto del botón principal, opción elegida). */
    val SobreTinta get() = c(0xFFFFFFFF, 0xFF0F172A)

    // Acento
    val Naranja get() = c(0xFFC2410C, 0xFFFB923C)
    val NaranjaClaro get() = c(0xFFFFEDD5, 0xFF431407)
    val NaranjaOscuro get() = c(0xFF9A3412, 0xFFFDBA74)
    val NaranjaTexto get() = c(0xFF7C2D12, 0xFFFED7AA)   // texto sobre NaranjaClaro
    val NaranjaVivo get() = c(0xFFFB923C, 0xFFFB923C)    // íconos sobre el botón principal, manchas del fondo
    /** Lo que va encima de un relleno [Naranja] o [Ambar]. */
    val SobreAcento get() = c(0xFFFFFFFF, 0xFF1C0A02)
    val Ambar get() = c(0xFFB45309, 0xFFFCD34D)          // cifra «por enviar»
    val Rojo get() = c(0xFFB91C1C, 0xFFFCA5A5)           // cifra «rechazada»

    // Estados (siempre con ícono + palabra)
    val PendienteTexto get() = c(0xFF92400E, 0xFFFCD34D); val PendienteFondo get() = c(0xFFFEF3C7, 0xFF451A03)
    val EnviadoTexto get() = c(0xFF166534, 0xFF86EFAC); val EnviadoFondo get() = c(0xFFDCFCE7, 0xFF052E16)
    val RechazadoTexto get() = c(0xFF991B1B, 0xFFFCA5A5); val RechazadoFondo get() = c(0xFFFEE2E2, 0xFF450A0A)
    val ErrorTexto get() = c(0xFF7F1D1D, 0xFFFECACA)
    val AvisoTexto get() = c(0xFF78350F, 0xFFFDE68A)     // texto del aviso ámbar (HU-36)
    val AvisoFondo get() = c(0xFFFEF3C7, 0xFF3B2405)

    // Fondo, superficies y vidrio
    val FondoBase get() = c(0xFFEDF1F6, 0xFF0B1220)
    /** Superficie opaca (listas, opciones, píldoras): blanca en claro, pizarra en oscuro. */
    val Superficie get() = c(0xFFFFFFFF, 0xFF1E293B)
    val SuperficieSuave get() = c(0xFFF3F4F6, 0xFF273449)
    val Noche get() = c(0xFF1E293B, 0xFF020617)          // franja de la ilustración en Entrar y Elegir obra
    val Vidrio get() = if (Apariencia.oscuro) Color(0xFF1E293B).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.58f)
    val VidrioFuerte get() = if (Apariencia.oscuro) Color(0xFF1E293B).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.78f)
    val BordeVidrio get() = if (Apariencia.oscuro) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.75f)
    val BordeVidrioFuerte get() = if (Apariencia.oscuro) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)
    val BordeCampo get() = Tinta.copy(alpha = if (Apariencia.oscuro) 0.22f else 0.14f)
    val Separador get() = Tinta.copy(alpha = if (Apariencia.oscuro) 0.12f else 0.07f)
    /** Lo que estaba en gris pizarra fijo (íconos neutros, textos de apoyo). */
    val Neutro get() = c(0xFF334155, 0xFFCBD5E1)
    val NeutroSuave get() = c(0xFF64748B, 0xFF94A3B8)
}
