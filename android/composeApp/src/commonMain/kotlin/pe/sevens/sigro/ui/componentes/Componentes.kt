package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.ui.tema.LocalColoresEstado

/* Piezas reutilizables. Cada pantalla se arma con estas para que todo se vea y se comporte igual. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    subtitulo: String? = null,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(titulo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitulo != null) {
                    Text(
                        subtitulo, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = { acciones() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Botón principal: 60 dp de alto para usarlo con guantes. Muestra «cargando» sin moverse de lugar. */
@Composable
fun BotonGrande(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    secundario: Boolean = false,
) {
    val contenido: @Composable () -> Unit = {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColorOrPrimary(secundario))
            Spacer(Modifier.width(12.dp))
        } else if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
    val m = modifier.fillMaxWidth().heightIn(min = 60.dp)
    if (secundario) {
        OutlinedButton(onClick = alPulsar, enabled = habilitado && !cargando, modifier = m, shape = MaterialTheme.shapes.medium) { contenido() }
    } else {
        Button(onClick = alPulsar, enabled = habilitado && !cargando, modifier = m, shape = MaterialTheme.shapes.medium) { contenido() }
    }
}

@Composable
private fun LocalContentColorOrPrimary(secundario: Boolean): Color =
    if (secundario) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary

/** Campo de texto con error debajo y teclado según el dato. */
@Composable
fun Campo(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    ayuda: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    sufijo: String? = null,
    transformacion: VisualTransformation = VisualTransformation.None,
    finalIcono: @Composable (() -> Unit)? = null,
    unaLinea: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = (error ?: ayuda)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        suffix = sufijo?.let { { Text(it, fontWeight = FontWeight.SemiBold) } },
        visualTransformation = transformacion,
        trailingIcon = finalIcono,
        singleLine = unaLinea,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Mensaje de error de la API o de la red: qué pasó y qué hacer, nunca una pantalla vacía. */
@Composable
fun AvisoError(mensaje: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = "Error")
            Spacer(Modifier.width(12.dp))
            Text(mensaje, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Aviso neutro (información o advertencia que no bloquea: regla 7). */
@Composable
fun AvisoInfo(mensaje: String, modifier: Modifier = Modifier, icono: ImageVector = Icons.Filled.WarningAmber) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(mensaje, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Ficha grande del inicio: ícono, título, una línea y, si hace falta, una insignia con número. */
@Composable
fun TarjetaAccion(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    insignia: Int? = null,
    habilitada: Boolean = true,
    destacada: Boolean = false,
) {
    val fondo = if (destacada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val texto = if (destacada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = alPulsar,
        enabled = habilitada,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = texto, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (destacada) 3.dp else 1.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 88.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(
                    if (destacada) texto.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(14.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icono, contentDescription = null, modifier = Modifier.size(28.dp),
                    tint = if (destacada) texto else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(
                    detalle, style = MaterialTheme.typography.bodyMedium,
                    color = if (destacada) texto.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (insignia != null && insignia > 0) {
                Box(
                    Modifier.size(34.dp).background(MaterialTheme.colorScheme.error, CircleShape)
                        .semantics { contentDescription = "$insignia por enviar" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$insignia", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Estado de envío con ícono + palabra + color (nunca solo color). */
@Composable
fun ChipEstado(estado: EstadoEnvio) {
    val c = LocalColoresEstado.current
    val (texto, icono, color, fondo) = when (estado) {
        EstadoEnvio.PENDIENTE -> Cuarteto("Por enviar", Icons.Filled.Schedule, c.pendiente, c.pendienteFondo)
        EstadoEnvio.ENVIADO -> Cuarteto("Enviado", Icons.Filled.CheckCircle, c.enviado, c.enviadoFondo)
        EstadoEnvio.RECHAZADO -> Cuarteto("Rechazado", Icons.Filled.ErrorOutline, c.rechazado, c.rechazadoFondo)
    }
    Row(
        Modifier.background(fondo, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(texto, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private data class Cuarteto(val a: String, val b: ImageVector, val c: Color, val d: Color)

/** Estado vacío con explicación (no una lista en blanco). */
@Composable
fun Vacio(icono: ImageVector, titulo: String, detalle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
