package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Piezas de pantalla sobre Material 3: Scaffold + TopAppBar para el marco, Card para las secciones,
 * FilterChip para las opciones y FilledTonalIconButton para el contador. Las pantallas usan estos
 * nombres; aquí se decide qué componente de Material va debajo.
 */

/**
 * Pantalla estándar: `Scaffold` con `TopAppBar` (flecha «volver», título, subtítulo y acciones)
 * y el contenido en una columna que se desplaza. `encima` queda sobre todo (diálogos, hojas).
 */
@Composable
fun PantallaVidrio(
    titulo: String,
    plano: DrawScope.(TextMeasurer) -> Unit,
    alVolver: () -> Unit,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    encima: @Composable BoxScope.() -> Unit = {},
    /** Rediseño v2: con un módulo, la barra superior es la cabecera de su color con su ilustración. */
    modulo: Modulo? = null,
    /** Con [alActualizar], deslizar hacia abajo desde arriba vuelve a cargar la pantalla; [actualizando] muestra el giro. */
    actualizando: Boolean = false,
    alActualizar: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    FondoPlano(plano = plano) {
        Scaffold(
            topBar = {
                if (modulo != null) CabeceraModulo(modulo, titulo, alVolver, subtitulo, acciones = acciones)
                else EncabezadoVidrio(titulo, subtitulo, alVolver, acciones, conInsets = true)
            },
            containerColor = Color.Transparent, // deja ver la cuadrícula de plano de FondoPlano
        ) { margen ->
            // `margen` es el espacio que ocupan la barra superior y las del sistema: el contenido empieza debajo.
            DeslizarParaActualizar(actualizando, alActualizar, Modifier.fillMaxSize().padding(margen)) {
                Column(
                    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = if (modulo != null) 16.dp else 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = contenido,
                    )
                }
            }
        }
        encima()
    }
}

/**
 * Barra superior de Material 3 (`TopAppBar`). Fuera de un Scaffold (pantallas que ya manejan los
 * bordes del sistema) va sin insets, para no sumar dos veces el alto de la barra de estado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncabezadoVidrio(
    titulo: String,
    subtitulo: String?,
    alVolver: () -> Unit,
    acciones: @Composable RowScope.() -> Unit = {},
    conInsets: Boolean = false,
) {
    TopAppBar(
        title = {
            Column {
                Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (subtitulo != null) {
                    Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
        },
        actions = acciones,
        windowInsets = if (conInsets) TopAppBarDefaults.windowInsets else WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Sección de contenido: `Card` de Material 3 con su relleno. «fuerte» = un poco más de elevación. */
@Composable
fun TarjetaVidrio(modifier: Modifier = Modifier, fuerte: Boolean = false, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (fuerte) 3.dp else 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
    }
}

@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(texto, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** Etiqueta de estado: siempre texto (y color), nunca solo color. Card chica con el color de fondo dado. */
@Composable
fun Etiqueta(texto: String, color: Color, fondo: Color, icono: ImageVector? = null) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = color),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icono != null) Icon(icono, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Opciones excluyentes (categoría, estado de asistencia, origen, motivo…) con `FilterChip`
 * de Material 3: la elegida se rellena y lleva un check, así no depende solo del color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Opciones(
    opciones: List<T>,
    elegida: T?,
    texto: (T) -> String,
    alElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") colorActivo: Color = Color.Unspecified,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { o ->
            val activa = o == elegida
            FilterChip(
                selected = activa,
                onClick = { alElegir(o) },
                label = { Text(texto(o)) },
                leadingIcon = if (activa) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

/** − valor + para horas (HU-63), con `FilledTonalIconButton` de Material 3 (48 dp, se pulsa con guantes). */
@Composable
fun Contador(etiqueta: String, valor: Int, alMenos: () -> Unit, alMas: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalIconButton(onClick = alMenos) { Icon(Icons.Filled.Remove, contentDescription = "Menos $etiqueta") }
        Text("$valor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 22.dp))
        FilledTonalIconButton(onClick = alMas) { Icon(Icons.Filled.Add, contentDescription = "Más $etiqueta") }
    }
}

/** Dato que la app no tiene todavía (no se inventa): se dice de dónde vendrá. */
@Composable
fun Marcador(texto: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text("[$texto]", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
    }
}

/** Aviso de las pantallas de almacén: se guarda en el celular y se envía cuando exista el servicio. */
@Composable
fun AvisoSinServicioAlmacen(modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Paleta.PendienteFondo, contentColor = Paleta.PendienteTexto),
    ) {
        Row(
            Modifier.padding(14.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null)
            Text(
                "Se envía cuando exista el servicio almacén. Mientras tanto queda guardado en el celular, «por enviar».",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Error de la operación: Card con «errorContainer» del tema e ícono. */
@Composable
fun AvisoRojo(mensaje: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = mensaje },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(mensaje, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Separador fino entre filas de una tarjeta (`HorizontalDivider` de Material 3). */
@Composable
fun Separador() = HorizontalDivider()
