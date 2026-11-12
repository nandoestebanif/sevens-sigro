package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.dominio.RepositorioPreferencias
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.planoCelular
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.ModoTema
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Perfil: quién eres, en qué obra estás y lo de tu celular. Antes la pestaña «Perfil» abría «Elegir obra»,
 * la misma pantalla que el nombre de la obra en el inicio: ahora cada botón hace una sola cosa.
 */

data class EstadoPerfil(
    val insumosEnCelular: Int? = null,
    val actualizando: Boolean = false,
    val codigoCelular: String? = null,
    val mensaje: String? = null,
)

class PerfilViewModel(
    private val sesiones: RepositorioSesion,
    private val catalogo: RepositorioCatalogo,
    private val dispositivo: RepositorioDispositivo,
    private val preferencias: RepositorioPreferencias? = null,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoPerfil())
    val estado = _e.asStateFlow()

    fun cargar() = viewModelScope.launch {
        val n = runCatching { catalogo.cuantosHay() }.getOrNull()
        val id = runCatching { dispositivo.identificador() }.getOrNull()
        _e.update { it.copy(insumosEnCelular = n, codigoCelular = id) }
    }

    fun actualizarCatalogo() {
        _e.update { it.copy(actualizando = true, mensaje = null) }
        viewModelScope.launch {
            try {
                val n = catalogo.actualizar()
                _e.update { it.copy(insumosEnCelular = n, actualizando = false, mensaje = "Catálogo al día") }
            } catch (x: ErrorSigro) {
                _e.update { it.copy(actualizando = false, mensaje = if (x.esTransitorio) "Sin señal: se actualiza cuando vuelva" else x.message) }
            }
        }
    }

    /** Claro, oscuro o como el celular: se aplica al instante y queda guardado en el celular. */
    fun tema(m: ModoTema) {
        Apariencia.modo = m
        viewModelScope.launch { runCatching { preferencias?.guardarTema(m.name) } }
    }

    fun cambiarDeObra() = viewModelScope.launch { sesiones.cambiarDeObra() }
    fun salir() = viewModelScope.launch { sesiones.cerrarSesion() }
}

@Composable
fun PantallaPerfil(
    sesion: Sesion,
    obra: ObraAsignada,
    alVolver: () -> Unit,
    alCelular: () -> Unit,
    vm: PerfilViewModel = koinViewModel(),
) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(Unit) { vm.cargar() }

    PantallaVidrio("Perfil", plano = { planoCelular(it) }, alVolver = alVolver) {
        TarjetaVidrio(Modifier.semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).background(Paleta.NaranjaClaro, CircleShape), contentAlignment = Alignment.Center) {
                    Text(sesion.nombre.trim().take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.NaranjaOscuro)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sesion.nombre, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                    Text("@${sesion.usuario} · ${obra.rolLegible}", fontSize = 14.sp, color = Paleta.Texto2)
                }
            }
        }

        TarjetaVidrio {
            FilaPerfil(Icons.Filled.Apartment, "Obra", obra.nombre, accion = if (sesion.obras.size > 1) "Cambiar" else null, alPulsar = if (sesion.obras.size > 1) ({ vm.cambiarDeObra() }) else null)
            Linea()
            FilaPerfil(Icons.Filled.PhoneAndroid, "Mi celular", e.codigoCelular?.let { "Código ${Texto.codigoCorto(it)}" } ?: "…", accion = "Ver", alPulsar = alCelular)
            Linea()
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilaPerfil(
                    Icons.Filled.Sync, "Catálogo en el celular",
                    e.insumosEnCelular?.let { "$it insumos, para buscar sin señal" } ?: "…",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::actualizarCatalogo, enabled = !e.actualizando) {
                    if (e.actualizando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
                    else Text("Actualizar", fontWeight = FontWeight.Bold, color = Paleta.Naranja)
                }
            }
            e.mensaje?.let { Text(it, fontSize = 13.sp, color = Paleta.Texto2) }
        }

        TarjetaVidrio {
            Text("Apariencia", fontSize = 13.sp, color = Paleta.Texto3)
            Opciones(ModoTema.entries, Apariencia.modo, { it.texto }, vm::tema)
        }

        Spacer(Modifier.size(4.dp))
        BotonNegro("Cerrar sesión", { vm.salir() }, icono = Icons.AutoMirrored.Filled.Logout, contorno = true)
        Text(
            "Cerrar sesión no borra lo que está por enviar: se envía cuando vuelvas a entrar.",
            fontSize = 13.sp, color = Paleta.Texto3, modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun FilaPerfil(
    icono: ImageVector, titulo: String, valor: String, modifier: Modifier = Modifier,
    accion: String? = null, alPulsar: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (alPulsar != null) Modifier.clickable(role = Role.Button, onClickLabel = accion, onClick = alPulsar) else Modifier)
            .heightIn(min = 52.dp).padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(titulo, fontSize = 13.sp, color = Paleta.Texto3)
            Text(valor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
        }
        if (alPulsar != null) {
            if (accion != null) Text(accion, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Paleta.Texto3)
        }
    }
}

@Composable
private fun Linea() {
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(Paleta.Separador))
}
