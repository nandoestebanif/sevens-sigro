package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.componentes.BarraSuperior
import pe.sevens.sigro.ui.componentes.TarjetaAccion

/* Elegir obra: solo aparecen las obras donde la persona está asignada (regla 9). */

@Composable
fun PantallaObras(sesion: Sesion, alElegir: (ObraAsignada) -> Unit, alSalir: () -> Unit) {
    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = "Hola, ${sesion.nombre.substringBefore(' ')}",
                subtitulo = "¿En qué obra trabajas hoy?",
                acciones = {
                    IconButton(onClick = alSalir) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir") }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sesion.obras, key = { it.id }) { o ->
                TarjetaAccion(Icons.Filled.Apartment, o.nombre, o.rolLegible, alPulsar = { alElegir(o) })
            }
            item {
                Text(
                    "¿Falta una obra? El administrador te asigna desde la web.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
