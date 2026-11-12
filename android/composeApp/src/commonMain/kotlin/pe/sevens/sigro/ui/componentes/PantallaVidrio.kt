package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Piezas de pantalla del estilo vidrio para las pantallas del Sprint 2. Usan [FondoPlano] y
 * [vidrio] de Vidrio.kt tal cual (no los cambian): aquí solo se arma el encabezado, la columna
 * con desplazamiento y los controles que se repiten (opciones, contador, avisos).
 */

/** Pantalla con su plano de fondo, encabezado con «volver» y contenido que se desplaza. */
@Composable
fun PantallaVidrio(
    titulo: String,
    plano: DrawScope.(TextMeasurer) -> Unit,
    alVolver: () -> Unit,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    encima: @Composable BoxScope.() -> Unit = {},
    contenido: @Composable ColumnScope.() -> Unit,
) {
    FondoPlano(plano = plano) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding()
                .navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            EncabezadoVidrio(titulo, subtitulo, alVolver, acciones)
            Column(
                Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = contenido,
            )
        }
        encima()
    }
}

@Composable
fun EncabezadoVidrio(titulo: String, subtitulo: String?, alVolver: () -> Unit, acciones: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = alVolver, modifier = Modifier.size(48.dp).vidrio(CircleShape, sombra = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Paleta.Tinta)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                titulo, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, color = Paleta.Tinta,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitulo != null) Text(subtitulo, fontSize = 14.sp, color = Paleta.Texto3)
        }
        acciones()
    }
}

/** Tarjeta de vidrio de 26 dp con su relleno. */
@Composable
fun TarjetaVidrio(modifier: Modifier = Modifier, fuerte: Boolean = false, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp), fuerte = fuerte).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = contenido,
    )
}

@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta,
        modifier = modifier.semantics { heading() },
    )
}

/** Etiqueta en píldora: siempre texto (y color), nunca solo color. */
@Composable
fun Etiqueta(texto: String, color: Color, fondo: Color, icono: ImageVector? = null) {
    Row(
        Modifier.background(fondo, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icono != null) Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Opciones excluyentes en píldoras (categoría, estado de asistencia, origen, motivo…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Opciones(
    opciones: List<T>,
    elegida: T?,
    texto: (T) -> String,
    alElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    colorActivo: Color = Paleta.Tinta,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { o ->
            val activa = o == elegida
            Box(
                Modifier.heightIn(min = 44.dp)
                    .background(if (activa) colorActivo else Color.White.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                    .border(1.dp, if (activa) colorActivo else Paleta.BordeCampo, RoundedCornerShape(22.dp))
                    .clickable(role = Role.RadioButton) { alElegir(o) }
                    .semantics { selected = activa }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(texto(o), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (activa) Color.White else Paleta.Tinta)
            }
        }
    }
}

/** − valor + para horas (HU-63). */
@Composable
fun Contador(etiqueta: String, valor: Int, alMenos: () -> Unit, alMas: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(etiqueta, fontSize = 13.sp, color = Paleta.Texto3)
        IconButton(onClick = alMenos, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.85f), CircleShape)) {
            Icon(Icons.Filled.Remove, contentDescription = "Menos $etiqueta", tint = Paleta.Tinta, modifier = Modifier.size(18.dp))
        }
        Text("$valor", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta, modifier = Modifier.widthIn(min = 22.dp))
        IconButton(onClick = alMas, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.85f), CircleShape)) {
            Icon(Icons.Filled.Add, contentDescription = "Más $etiqueta", tint = Paleta.Tinta, modifier = Modifier.size(18.dp))
        }
    }
}

/** Dato que la app no tiene todavía (no se inventa): se dice de dónde vendrá. */
@Composable
fun Marcador(texto: String, modifier: Modifier = Modifier) {
    Text(
        "[$texto]", fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto3,
        modifier = modifier.fillMaxWidth().background(Paleta.Tinta.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, Paleta.Tinta.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(12.dp),
    )
}

/** Aviso de las pantallas de almacén: se guarda en el celular y se envía cuando exista el servicio. */
@Composable
fun AvisoSinServicioAlmacen(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Paleta.PendienteFondo, RoundedCornerShape(16.dp)).padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Paleta.PendienteTexto, modifier = Modifier.size(22.dp))
        Text(
            "Se envía cuando exista el servicio almacén. Mientras tanto queda guardado en el celular, «por enviar».",
            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Paleta.PendienteTexto,
        )
    }
}

/** Error de la operación sobre rojo claro, con ícono. */
@Composable
fun AvisoRojo(mensaje: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Paleta.RechazadoFondo, RoundedCornerShape(14.dp)).padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = mensaje },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Paleta.RechazadoTexto, modifier = Modifier.size(20.dp))
        Text(mensaje, fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.RechazadoTexto)
    }
}

/** Separador fino entre filas de una tarjeta. */
@Composable
fun Separador() = Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Separador))
