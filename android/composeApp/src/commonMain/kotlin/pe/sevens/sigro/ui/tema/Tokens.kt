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
    // el oscuro es el de la marca y va por defecto; la persona puede elegir otro en Perfil
    var modo by mutableStateOf(ModoTema.OSCURO)
    var oscuro by mutableStateOf(false)
}

enum class ModoTema(val texto: String) { SISTEMA("Como el celular"), CLARO("Claro"), OSCURO("Oscuro") }

/*
 * Colores de la app con su valor claro y oscuro · rediseño negro y amarillo (24/11), el mismo de la web:
 * negro #161717 / #242424 / #2B2C2C y el amarillo de seguridad de obra #FAD85D como acción principal.
 * Los nombres «Naranja*» se conservan (los usan todas las pantallas) pero hoy son el ACENTO amarillo:
 * amarillo en oscuro y amarillo oscuro en claro (el amarillo puro no se lee sobre blanco).
 * Cada pareja texto/fondo pasa WCAG AA en los dos modos (TemaOscuroTest).
 */
object Paleta {
    private inline fun c(claro: Long, oscuro: Long) = Color(if (Apariencia.oscuro) oscuro else claro)

    // Texto
    val Tinta get() = c(0xFF161717, 0xFFFFFFFF)          // principal
    val Texto2 get() = c(0xFF3D3E3E, 0xFFD2D3D3)         // secundario fuerte
    val Texto3 get() = c(0xFF5C5D5D, 0xFFB4B5B5)         // secundario
    val Pizarra get() = c(0xFF6D6E6E, 0xFF5C5D5D)        // trazos de los planos
    val Gris get() = c(0xFFA9AAAA, 0xFF424343)           // botón deshabilitado, chevrones
    /** Lo que va encima de un relleno [Tinta]. */
    val SobreTinta get() = c(0xFFFFFFFF, 0xFF161717)
    /** Relleno del botón principal y de la opción elegida: el amarillo de la marca en los dos modos. */
    val Principal get() = c(0xFFFAD85D, 0xFFFAD85D)
    val SobrePrincipal get() = c(0xFF161717, 0xFF161717)

    // Acento (amarillo)
    val Naranja get() = c(0xFF7A5D00, 0xFFFAD85D)
    val NaranjaClaro get() = c(0xFFFFF6D1, 0xFF2E2A1C)
    val NaranjaOscuro get() = c(0xFF5C4500, 0xFFFFE27F)
    val NaranjaTexto get() = c(0xFF5C4500, 0xFFFAD85D)   // texto sobre NaranjaClaro
    val NaranjaVivo get() = c(0xFF161717, 0xFF161717)    // íconos sobre el botón principal (amarillo)
    /** Lo que va encima de un relleno [Naranja] o [Ambar]. */
    val SobreAcento get() = c(0xFFFFFFFF, 0xFF161717)
    val Ambar get() = c(0xFF8A5A00, 0xFFF5C451)          // cifra «por enviar»
    val Rojo get() = c(0xFFB42318, 0xFFFF9A9A)           // cifra «rechazada»

    // Estados (siempre con ícono + palabra)
    val PendienteTexto get() = c(0xFF8A5A00, 0xFFF5C451); val PendienteFondo get() = c(0xFFFFF4D6, 0xFF2E2A1C)
    val EnviadoTexto get() = c(0xFF0B7A64, 0xFF2EE6C5); val EnviadoFondo get() = c(0xFFDDF8F2, 0xFF12302B)
    val RechazadoTexto get() = c(0xFFB42318, 0xFFFF9A9A); val RechazadoFondo get() = c(0xFFFDE8E6, 0xFF3A1F1F)
    val ErrorTexto get() = c(0xFF7F1D1D, 0xFFFFC2C2)
    val AvisoTexto get() = c(0xFF5C3D00, 0xFFF5C451)     // texto del aviso ámbar (HU-36)
    val AvisoFondo get() = c(0xFFFFF4D6, 0xFF2E2A1C)

    // Fondo y superficies (los nombres «Vidrio*» quedan de la versión anterior: hoy son opacas)
    val FondoBase get() = c(0xFFF1F1EE, 0xFF242424)
    /** Superficie de tarjetas, listas y píldoras: blanca en claro, #2B2C2C en oscuro. */
    val Superficie get() = c(0xFFFFFFFF, 0xFF2B2C2C)
    val SuperficieSuave get() = c(0xFFF4F4F1, 0xFF353636)
    val Noche get() = c(0xFF161717, 0xFF161717)          // portada negra de Entrar y Elegir obra (negra en los dos modos)
    val Vidrio get() = Superficie
    val VidrioFuerte get() = Superficie
    val BordeVidrio get() = c(0xFFE2E2DD, 0xFF3D3E3E)
    val BordeVidrioFuerte get() = BordeVidrio
    val BordeCampo get() = Tinta.copy(alpha = if (Apariencia.oscuro) 0.22f else 0.14f)
    val Separador get() = Tinta.copy(alpha = if (Apariencia.oscuro) 0.12f else 0.07f)
    /** Íconos neutros y textos de apoyo. */
    val Neutro get() = c(0xFF3D3E3E, 0xFFD2D3D3)
    val NeutroSuave get() = c(0xFF6D6E6E, 0xFFA9AAAA)

    // Tickets de color (rediseño): se turnan en accesos, cifras y tarjetas. Texto encima: siempre #161717.
    val TicketMenta = Color(0xFF2EE6C5)
    val TicketAmarillo = Color(0xFFFAD85D)
    val TicketLila = Color(0xFFF2A6FF)
    val TicketRosa = Color(0xFFFFB3C1)
    val TicketDurazno = Color(0xFFFFC58F)
    val SobreTicket = Color(0xFF161717)
    val Tickets get() = listOf(TicketMenta, TicketAmarillo, TicketLila, TicketRosa, TicketDurazno)
}
