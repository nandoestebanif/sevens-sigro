package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.Etiqueta
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.planoCelular
import pe.sevens.sigro.ui.tema.Paleta

/* 1.4 · Mi celular (lienzo Celular.dc.html, HU-15): el celular pide ser el dispositivo autorizado de la obra. */

data class EstadoPantallaDispositivo(
    val dispositivo: EstadoDispositivo? = null,
    val cargando: Boolean = false,
    val error: String? = null,
) {
    /** Sin solicitar o revocado: puede (volver a) pedir autorización. Cambiar de celular exige al administrador. */
    val puedePedir: Boolean get() = dispositivo != null && (dispositivo.estado == null || dispositivo.estado == "revocado")
}

class DispositivoViewModel(private val repo: RepositorioDispositivo) : ViewModel() {
    private val _e = MutableStateFlow(EstadoPantallaDispositivo())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) = ejecutar { repo.estado(obraId) }
    fun solicitar(obraId: Int) = ejecutar { repo.solicitarAutorizacion(obraId) }

    private fun ejecutar(accion: suspend () -> EstadoDispositivo) {
        _e.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val d = accion()
                _e.update { it.copy(dispositivo = d, cargando = false) }
            } catch (x: ErrorSigro) {
                val id = runCatching { repo.identificador() }.getOrNull()
                _e.update { it.copy(cargando = false, error = x.message, dispositivo = it.dispositivo ?: id?.let { EstadoDispositivo(it, null) }) }
            }
        }
    }
}

private data class ChipCelular(val icono: ImageVector, val color: Color, val fondo: Color)

private fun chip(estado: String?): ChipCelular = when (estado) {
    "autorizado" -> ChipCelular(Icons.Filled.CheckCircle, Paleta.EnviadoTexto, Paleta.EnviadoFondo)
    "pendiente", EstadoDispositivo.YA_SOLICITADO -> ChipCelular(Icons.Filled.Schedule, Paleta.PendienteTexto, Paleta.PendienteFondo)
    "revocado" -> ChipCelular(Icons.Filled.ErrorOutline, Paleta.RechazadoTexto, Paleta.RechazadoFondo)
    else -> ChipCelular(Icons.Filled.HelpOutline, Color(0xFF334155), Color(0xFFE2E8F0))
}

@Composable
fun PantallaDispositivo(obraId: Int, alVolver: () -> Unit, obraNombre: String? = null, vm: DispositivoViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio(
        "Mi celular", plano = { planoCelular(it) }, alVolver = alVolver,
        subtitulo = obraNombre?.let { "Autorización en $it" } ?: "Autorización para registrar en esta obra",
    ) {
        TarjetaVidrio(Modifier.semantics(mergeDescendants = true) {}) {
            Text("Código de este celular", fontSize = 14.sp, color = Paleta.Texto3)
            val id = e.dispositivo?.identificador
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id?.let { Texto.codigoCorto(it) } ?: "…", fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, modifier = Modifier.weight(1f))
                if (id != null) {
                    val portapapeles = LocalClipboardManager.current
                    TextButton(onClick = { portapapeles.setText(AnnotatedString(id)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(18.dp))
                        Text("  Copiar", color = Paleta.Naranja, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado:", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                val d = e.dispositivo
                if (d == null) {
                    Text("consultando…", fontSize = 15.sp, color = Paleta.Texto3)
                } else {
                    val c = chip(d.estado)
                    Etiqueta(d.estadoLegible, c.color, c.fondo, c.icono)
                }
            }
        }
        AnimatedVisibility(e.error != null) { AvisoRojo(e.error.orEmpty()) }
        if (e.dispositivo?.otroCelularActivo == true && e.dispositivo?.estado != EstadoDispositivo.AUTORIZADO) {
            AvisoNaranja(
                "Tu usuario ya tiene otro celular autorizado en esta obra (por ejemplo, si reinstalaste la app cambia el código). " +
                    "Pide al administrador que, en la web, revoque el anterior y autorice este.",
            )
        }
        if (e.puedePedir) {
            BotonNegro("Pedir autorización", { vm.solicitar(obraId) }, icono = Icons.Filled.VerifiedUser, colorIcono = Paleta.NaranjaVivo, cargando = e.cargando)
        }
        TarjetaVidrio {
            Text(
                "El administrador lo autoriza desde la web, en Administración. Mientras no esté autorizado, las salidas no se registran.",
                fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFF7C2D12),
            )
        }
    }
}
