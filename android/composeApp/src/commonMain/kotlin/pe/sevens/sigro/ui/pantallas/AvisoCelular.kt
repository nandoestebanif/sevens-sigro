package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.ui.tema.Paleta

/*
 * HU-15 · ¿Este celular puede registrar en esta obra? (prueba en celular, 27/11: se registraba una salida y recién el
 * servidor la rechazaba por celular no autorizado). Se dice en Inicio y antes de registrar, no después.
 */

/** Qué se le dice a la persona según el estado del celular en la obra. `null`: no se muestra nada. */
enum class AvisoDeCelular { AUTORIZADO, PENDIENTE, SIN_AUTORIZAR }

fun avisoDeCelular(d: EstadoDispositivo?): AvisoDeCelular? = when {
    d == null || (!d.visible && d.estado == null) -> null // sin dato (sin señal) o el rol no lo ve: no se inventa
    d.estado == EstadoDispositivo.AUTORIZADO -> AvisoDeCelular.AUTORIZADO
    d.estado == "pendiente" || d.estado == EstadoDispositivo.YA_SOLICITADO -> AvisoDeCelular.PENDIENTE
    else -> AvisoDeCelular.SIN_AUTORIZAR // sin pedir o revocado
}

class CelularObraViewModel(private val repo: RepositorioDispositivo) : ViewModel() {
    private val _e = MutableStateFlow<Pair<Int, EstadoDispositivo>?>(null)
    val estado = _e.asStateFlow()

    /** Se pregunta al servidor; sin señal se queda lo último que se supo de esta obra (o nada). */
    fun revisar(obraId: Int) {
        viewModelScope.launch { runCatching { repo.estado(obraId) }.onSuccess { _e.value = obraId to it } }
    }
}

/**
 * Tarjeta del estado del celular en la obra. Autorizado: una línea verde y discreta. Sin autorizar o pendiente: aviso
 * naranja que explica qué pasa con lo que se registre y lleva a «Mi celular» para pedir la autorización.
 */
@Composable
fun AvisoCelular(obraId: Int, alPedir: () -> Unit, compacto: Boolean = false) {
    // sin Koin (capturas de escritorio, vistas previas) no hay a quién preguntar: no se muestra
    if (org.koin.mp.KoinPlatformTools.defaultContext().getOrNull() == null) return
    AvisoCelularCon(obraId, alPedir, compacto, koinViewModel())
}

@Composable
fun AvisoCelularCon(obraId: Int, alPedir: () -> Unit, compacto: Boolean, vm: CelularObraViewModel) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.revisar(obraId) }
    val aviso = avisoDeCelular(e?.takeIf { it.first == obraId }?.second) ?: return
    if (aviso == AvisoDeCelular.AUTORIZADO) {
        if (compacto) return
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Paleta.EnviadoTexto, modifier = Modifier.size(18.dp))
            Text("Este celular está autorizado para registrar en esta obra.", fontSize = 13.sp, color = Paleta.EnviadoTexto, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    val (titulo, detalle) = when (aviso) {
        AvisoDeCelular.PENDIENTE -> "Tu celular espera la autorización" to
            "Lo que registres se guarda en el celular, pero el servidor no lo acepta hasta que el administrador autorice este celular."
        else -> "Este celular no está autorizado en esta obra" to
            "Pide la autorización antes de registrar: si no, lo que guardes quedará rechazado al enviarse."
    }
    Column(
        Modifier.fillMaxWidth().background(Paleta.AvisoFondo, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Abrir Mi celular", onClick = alPedir).heightIn(min = 56.dp).padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.PhonelinkLock, contentDescription = null, tint = Paleta.PendienteTexto, modifier = Modifier.size(20.dp))
            Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.PendienteTexto)
        }
        Text(detalle, fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.AvisoTexto)
        Text(if (aviso == AvisoDeCelular.PENDIENTE) "Ver Mi celular ›" else "Pedir autorización ›", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja)
    }
}
