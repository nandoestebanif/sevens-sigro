package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.componentes.DibujoObra
import pe.sevens.sigro.ui.componentes.Ilustracion
import pe.sevens.sigro.ui.componentes.Ticket
import pe.sevens.sigro.ui.componentes.hundirAlTocar
import pe.sevens.sigro.ui.componentes.rememberFuente
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Elegir y cambiar de obra (rediseño v3, 25/11). Cada obra es un ticket de color con su dibujo, el rol de la
 * persona en ella y el estado de ESTE celular ahí (regla 5). Solo las obras donde está asignada (regla 9): la
 * lista es la que devolvió el servidor al entrar. Cambiar de obra no toca la cola: cada registro guardó su obra
 * y se envía a ella (RepositorioSalidasImpl/RepositorioRegistrosImpl.sincronizar).
 */

private val DIBUJOS = listOf(Ilustracion.GRUA, Ilustracion.CASCO, Ilustracion.CAMION, Ilustracion.CEMENTO, Ilustracion.FACTURA)
private val COLORES = listOf(Paleta.TicketAmarillo, Paleta.TicketMenta, Paleta.TicketLila, Paleta.TicketDurazno, Paleta.TicketRosa)

/** Dibujo de la obra según su lugar en la sesión: es el mismo en «Elegir obra», en la hoja y al entrar. */
fun dibujoDeObra(indice: Int): Ilustracion = DIBUJOS[((indice % DIBUJOS.size) + DIBUJOS.size) % DIBUJOS.size]

/** Color de la obra según su lugar en la sesión (la primera, amarilla). */
fun colorDeObra(indice: Int): Color = COLORES[((indice % COLORES.size) + COLORES.size) % COLORES.size]

/** Las obras que se ofrecen para cambiar: todas menos la actual, en el orden de la sesión. */
fun otrasObras(obras: List<ObraAsignada>, actualId: Int): List<ObraAsignada> = obras.filter { it.id != actualId }

/** El buscador aparece cuando ya cuesta encontrar la obra a ojo (más de tres). */
fun conBuscador(cuantas: Int): Boolean = cuantas > 3

/** Color del estado del celular sobre la etiqueta negra: igual en modo claro y oscuro (AA sobre #161717). */
fun colorCelular(c: CelularEnObra): Color = when (c) {
    CelularEnObra.AUTORIZADO -> Color(0xFF2EE6C5)
    CelularEnObra.PENDIENTE -> Color(0xFFF5C451)
    CelularEnObra.REVOCADO -> Color(0xFFFF9A9A)
    else -> Color(0xFFB4B5B5)
}

/** Aviso de la cola al cambiar: los registros no se pierden, cada uno va a su obra. `null` si no hay nada por enviar. */
fun avisoColaAlCambiar(porEnviar: Int): String? = when {
    porEnviar <= 0 -> null
    porEnviar == 1 -> "1 registro por enviar. Cambiar de obra no lo pierde: se envía a la obra donde lo hiciste."
    else -> "$porEnviar registros por enviar. Cambiar de obra no los pierde: cada uno se envía a la obra donde lo hiciste."
}

@Composable
private fun EtiquetaCelular(c: CelularEnObra, modifier: Modifier = Modifier) {
    val color = colorCelular(c)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Paleta.SobreTicket).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(c.texto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una obra para entrar: ticket de su color, rol, nombre, estado del celular y la flecha. Todo el ticket se toca. */
@Composable
fun TicketObra(o: ObraAsignada, indice: Int, celular: CelularEnObra, habilitado: Boolean = true, alPulsar: () -> Unit) {
    val color = colorDeObra(indice)
    val fuente = rememberFuente()
    Ticket(
        color,
        Modifier.fillMaxWidth().heightIn(min = 128.dp).hundirAlTocar(fuente)
            .clickable(fuente, indication = null, enabled = habilitado, role = Role.Button, onClickLabel = "Entrar a ${o.nombre}", onClick = alPulsar)
            .semantics(mergeDescendants = true) {},
        ilustracion = dibujoDeObra(indice), anchoIlustracion = 84.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                o.rolLegible.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em, color = Paleta.SobreTicket,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Paleta.SobreTicket.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Text(
                o.nombre, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
                color = Paleta.SobreTicket, modifier = Modifier.padding(end = 92.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EtiquetaCelular(celular, Modifier.weight(1f, fill = false).padding(end = 10.dp))
                Box(Modifier.size(40.dp).background(Paleta.SobreTicket, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** La obra donde ya está: negra con borde amarillo, «Estás aquí» y «Seguir en esta obra» (cierra la hoja). */
@Composable
private fun ObraActual(o: ObraAsignada, indice: Int, celular: CelularEnObra, alSeguir: () -> Unit) {
    val amarillo = Paleta.TicketAmarillo
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paleta.Noche)
            .border(2.dp, amarillo, RoundedCornerShape(24.dp)),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp).size(170.dp)
                .background(Brush.radialGradient(listOf(amarillo.copy(alpha = 0.30f), Color.Transparent)), CircleShape),
        )
        DibujoObra(dibujoDeObra(indice), Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 6.dp), ancho = 92.dp)
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(amarillo).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).background(Paleta.SobreTicket, CircleShape))
                    Text("ESTÁS AQUÍ", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em, color = Paleta.SobreTicket)
                }
                Text(
                    o.nombre, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
                    color = Color.White, modifier = Modifier.padding(top = 6.dp, end = 96.dp),
                )
                Text(o.rolLegible, fontSize = 13.sp, color = Color(0xFFB4B5B5))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = colorCelular(celular), modifier = Modifier.size(14.dp))
                    Text(celular.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colorCelular(celular))
                }
            }
            OutlinedButton(
                onClick = alSeguir, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, amarillo),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = amarillo),
            ) { Text("Seguir en esta obra", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AvisoCola(texto: String) {
    val menta = Paleta.TicketMenta
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(menta.copy(alpha = 0.12f))
            .border(1.dp, menta.copy(alpha = 0.4f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(menta, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Paleta.SobreTicket, modifier = Modifier.size(18.dp))
        }
        val (negrita, resto) = texto.substringBefore(". ") + "." to texto.substringAfter(". ")
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Paleta.Tinta)) { append(negrita) }
                append(" "); append(resto)
            },
            fontSize = 12.sp, lineHeight = 17.sp, color = Paleta.Texto2,
        )
    }
}

/**
 * Hoja «Cambiar de obra» sobre el inicio: la obra actual arriba, el aviso de la cola, el buscador (si hay más de
 * tres) y las otras obras como tickets. Elegir una cierra la hoja y entra (ver [PantallaEntrando]).
 */
@Composable
fun HojaCambiarObra(
    sesion: Sesion,
    actual: ObraAsignada,
    alElegir: (ObraAsignada) -> Unit,
    alCerrar: () -> Unit,
    vm: ObrasViewModel = koinViewModel(),
) {
    val celular by vm.celular.collectAsState()
    val q by vm.q.collectAsState()
    val porEnviar by vm.porEnviar.collectAsState()
    LaunchedEffect(sesion.obras) { vm.consultar(sesion.obras) }
    val otras = remember(sesion.obras, actual.id, q) { filtrarObras(otrasObras(sesion.obras, actual.id), q) }
    val indice = { o: ObraAsignada -> sesion.obras.indexOfFirst { it.id == o.id } }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).background(Paleta.Tinta.copy(alpha = 0.18f), RoundedCornerShape(3.dp)))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(15.dp))
                    Text("CAMBIAR DE OBRA", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, color = Paleta.Naranja)
                }
                Text(
                    if (sesion.obras.size == 1) "Tu obra" else "Tus ${sesion.obras.size} obras",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = Paleta.Tinta,
                    modifier = Modifier.semantics { heading() },
                )
                Text("Solo las que te asignaron. En cada una ves lo que tu rol permite.", fontSize = 12.sp, lineHeight = 17.sp, color = Paleta.Texto3)
            }
            IconButton(onClick = alCerrar, modifier = Modifier.size(44.dp).background(Paleta.Tinta.copy(alpha = 0.06f), CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Paleta.Tinta, modifier = Modifier.size(18.dp))
            }
        }
        ObraActual(actual, indice(actual), celular[actual.id] ?: CelularEnObra.SIN_CONSULTAR, alSeguir = alCerrar)
        avisoColaAlCambiar(porEnviar)?.let { AvisoCola(it) }
        if (sesion.obras.size == 1) {
            Text(
                "No tienes otras obras asignadas. El administrador te asigna desde la web.",
                fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            return@Column
        }
        if (conBuscador(sesion.obras.size)) BuscadorObra(q, vm::buscar, "Buscar entre tus obras")
        Text("Otras obras", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Texto2, modifier = Modifier.padding(top = 2.dp))
        otras.forEach { o -> TicketObra(o, indice(o), celular[o.id] ?: CelularEnObra.SIN_CONSULTAR) { alElegir(o) } }
        if (otras.isEmpty()) {
            Text("Ninguna otra obra con ese nombre.", fontSize = 14.sp, color = Paleta.Texto3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}

/** Mínimo que se muestra «Entrando a …»: lo bastante para leerlo, lo bastante corto para no estorbar. */
const val ENTRANDO_MS = 900L

/**
 * «Entrando a {obra} · como {rol}»: el círculo con el color y el dibujo de la obra y una barra que se llena en
 * [ENTRANDO_MS]. Tapa el cambio de pantalla para que no sea de golpe. El lector de pantalla lo anuncia.
 */
@Composable
fun PantallaEntrando(o: ObraAsignada, indice: Int) {
    val avance = remember(o.id) { Animatable(0f) }
    LaunchedEffect(o.id) { avance.animateTo(1f, tween(ENTRANDO_MS.toInt(), easing = LinearEasing)) }
    val color = colorDeObra(indice)
    Box(
        Modifier.fillMaxSize().background(Paleta.Noche)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.Center).offset(y = (-80).dp).size(420.dp)
                .background(Brush.radialGradient(listOf(Paleta.TicketAmarillo.copy(alpha = 0.26f), Color.Transparent)), CircleShape),
        )
        Column(
            Modifier.widthIn(max = 420.dp).padding(horizontal = 28.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = "Entrando a ${o.nombre} como ${o.rolLegible}" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(180.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                DibujoObra(dibujoDeObra(indice), ancho = 130.dp)
            }
            Text("ENTRANDO A", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em, color = Paleta.TicketAmarillo, modifier = Modifier.padding(top = 28.dp))
            Text(
                o.nombre, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                buildAnnotatedString { append("como "); withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(o.rolLegible) } },
                fontSize = 14.sp, color = Color(0xFFB4B5B5), modifier = Modifier.padding(top = 4.dp),
            )
            Box(Modifier.padding(top = 26.dp).width(220.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Color(0xFF2B2C2C))) {
                Box(Modifier.fillMaxWidth(avance.value).height(6.dp).clip(RoundedCornerShape(50)).background(Paleta.TicketAmarillo))
            }
            Text("Abriendo la obra…", fontSize = 12.sp, color = Color(0xFF8F9090), modifier = Modifier.padding(top = 12.dp))
        }
    }
}
