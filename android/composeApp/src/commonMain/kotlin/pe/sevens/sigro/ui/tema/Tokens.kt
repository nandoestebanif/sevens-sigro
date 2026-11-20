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
 * Colores de la app con su valor claro y oscuro. Los fondos y superficies coinciden con el
 * colorScheme de Material 3 (Tema.kt); los nombres «Vidrio*» quedan de la versión anterior y hoy
 * son superficies opacas. En pantallas nuevas conviene usar MaterialTheme.colorScheme directamente.
 */
object Paleta {
    private inline fun c(claro: Long, oscuro: Long) = Color(if (Apariencia.oscuro) oscuro else claro)

    // Texto
    val Tinta get() = c(0xFF111827, 0xFFE7E7EA)          // principal y botón principal
    val Texto2 get() = c(0xFF374151, 0xFFC4C4CA)         // secundario fuerte
    val Texto3 get() = c(0xFF4B5563, 0xFF9C9CA4)         // secundario
    val Pizarra get() = c(0xFF475569, 0xFF6B6B73)        // trazos de los planos
    val Gris get() = c(0xFF9CA3AF, 0xFF3A3A40)           // botón deshabilitado, chevrones
    /** Lo que va encima de un relleno [Tinta]. */
    val SobreTinta get() = c(0xFFFFFFFF, 0xFF000000)
    /** Relleno del botón principal y de la opción elegida: negro en claro, naranja en oscuro (un blanco encandila). */
    val Principal get() = c(0xFFC2410C, 0xFFFB923C)
    val SobrePrincipal get() = c(0xFFFFFFFF, 0xFF431407)

    // Acento
    val Naranja get() = c(0xFFC2410C, 0xFFFB923C)
    val NaranjaClaro get() = c(0xFFFFEDD5, 0xFF2A1206)
    val NaranjaOscuro get() = c(0xFF9A3412, 0xFFFDBA74)
    val NaranjaTexto get() = c(0xFF7C2D12, 0xFFFED7AA)   // texto sobre NaranjaClaro
    val NaranjaVivo get() = c(0xFFFB923C, 0xFF3B1503)    // íconos sobre el botón principal (en oscuro el botón es naranja)
    /** Lo que va encima de un relleno [Naranja] o [Ambar]. */
    val SobreAcento get() = c(0xFFFFFFFF, 0xFF1C0A02)
    val Ambar get() = c(0xFFB45309, 0xFFFCD34D)          // cifra «por enviar»
    val Rojo get() = c(0xFFB91C1C, 0xFFFCA5A5)           // cifra «rechazada»

    // Estados (siempre con ícono + palabra)
    val PendienteTexto get() = c(0xFF92400E, 0xFFFCD34D); val PendienteFondo get() = c(0xFFFEF3C7, 0xFF2B1A02)
    val EnviadoTexto get() = c(0xFF166534, 0xFF86EFAC); val EnviadoFondo get() = c(0xFFDCFCE7, 0xFF06200F)
    val RechazadoTexto get() = c(0xFF991B1B, 0xFFFCA5A5); val RechazadoFondo get() = c(0xFFFEE2E2, 0xFF2A0707)
    val ErrorTexto get() = c(0xFF7F1D1D, 0xFFFECACA)
    val AvisoTexto get() = c(0xFF78350F, 0xFFFDE68A)     // texto del aviso ámbar (HU-36)
    val AvisoFondo get() = c(0xFFFEF3C7, 0xFF241703)

    // Fondo, superficies y vidrio
    val FondoBase get() = c(0xFFF8FAFC, 0xFF0F172A)
    /** Superficie opaca (listas, opciones, píldoras): blanca en claro, pizarra en oscuro. */
    val Superficie get() = c(0xFFFFFFFF, 0xFF1E293B)
    val SuperficieSuave get() = c(0xFFF1F5F9, 0xFF334155)
    val Noche get() = c(0xFF1E293B, 0xFF000000)          // franja de la ilustración en Entrar y Elegir obra
    val Vidrio get() = Superficie
    val VidrioFuerte get() = Superficie
    val BordeVidrio get() = c(0xFFE2E8F0, 0xFF334155)
    val BordeVidrioFuerte get() = BordeVidrio
    val BordeCampo get() = Tinta.copy(alpha = if (Apariencia.oscuro) 0.22f else 0.14f)
    val Separador get() = Tinta.copy(alpha = if (Apariencia.oscuro) 0.12f else 0.07f)
    /** Lo que estaba en gris pizarra fijo (íconos neutros, textos de apoyo). */
    val Neutro get() = c(0xFF334155, 0xFFC4C4CA)
    val NeutroSuave get() = c(0xFF64748B, 0xFF8E8E96)
}
