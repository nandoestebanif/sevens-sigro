package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.Cotejo
import pe.sevens.sigro.dominio.CotejoQr
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.PermisoCamara
import pe.sevens.sigro.dominio.QrFactura
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.ResultadoCotejo
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.plataforma.LectorQr
import pe.sevens.sigro.plataforma.ManejarAtras
import pe.sevens.sigro.plataforma.lectorQrDisponible
import pe.sevens.sigro.plataforma.rememberCamara
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.tema.Paleta

/*
 * HU-73 · Escanear la factura electrónica (contrato: docs/contratos/hu-73-escanear-factura.md · secuencia HU-73).
 * Vista (PantallaEscanear) → EscanearViewModel → dominio (QrFactura, CotejoQr) → RepositorioObra (cotejar-qr).
 * Con señal manda el servidor; sin señal la app coteja con las compras que ya bajó y lo dice.
 */

/** Lo que se muestra después de leer un QR. */
data class ResultadoEscaneo(
    val qr: QrFactura,
    val cotejo: Cotejo,
    /** La compra de la lista de Entrada que corresponde (si el resultado deja recibir y está en la lista). */
    val compra: CompraParaRecibir?,
    /** Cotejado en el celular: el servidor lo confirma al enviar la entrada. */
    val enElCelular: Boolean,
)

data class EstadoEscaneo(
    val procesando: Boolean = false,
    val resultado: ResultadoEscaneo? = null,
    /** Mensaje breve mientras se sigue escaneando (QR que no es de una factura, etc.). */
    val aviso: String? = null,
)

class EscanearViewModel(private val obra: RepositorioObra) : ViewModel() {
    private val _e = MutableStateFlow(EstadoEscaneo())
    val estado = _e.asStateFlow()
    private var ultimoTexto: String? = null

    /** La cámara entrega el mismo QR varias veces por segundo: se atiende una vez y mientras se muestra el resultado, nada más. */
    fun leido(obraId: Int, texto: String, compras: List<CompraParaRecibir>) {
        val s = _e.value
        if (s.procesando || s.resultado != null || texto == ultimoTexto) return
        ultimoTexto = texto
        val qr = QrFactura.leer(texto)
        if (qr == null) {
            _e.update { it.copy(aviso = "Ese código no es el QR de una factura electrónica. Apunta al QR impreso en la factura o boleta.") }
            return
        }
        _e.update { it.copy(procesando = true, aviso = null) }
        viewModelScope.launch {
            val delServidor = try {
                obra.cotejarQr(obraId, texto)
            } catch (x: ErrorSigro) {
                if (x is ErrorSigro.Validacion) { // 422 qr_no_reconocido: el servidor no lo acepta como factura
                    _e.update { it.copy(procesando = false, aviso = x.message) }
                    return@launch
                }
                null // sin señal, servidor dormido, sin permiso…: se coteja en el celular
            }
            val cotejo = delServidor ?: CotejoQr.cotejar(qr, compras.mapNotNull { it.cotejable() })
            val compra = if (cotejo.resultado.sePuedeRecibir) compras.firstOrNull { it.id == cotejo.compraId } else null
            _e.update { it.copy(procesando = false, resultado = ResultadoEscaneo(qr, cotejo, compra, enElCelular = delServidor == null)) }
        }
    }

    /** «Escanear otra»: vuelve a la cámara (el mismo QR se puede leer de nuevo). */
    fun otra() {
        ultimoTexto = null
        _e.value = EstadoEscaneo()
    }

    companion object {
        /** Título y explicación de cada resultado, en palabras del almacenero. */
        fun explicar(r: ResultadoEscaneo): Pair<String, String> = when (r.cotejo.resultado) {
            ResultadoCotejo.RECIBIR -> "Coincide con la compra" to "Proveedor, comprobante y montos cuadran con lo registrado."
            ResultadoCotejo.RECIBIR_CON_AVISOS -> "Es la compra, pero no cuadra todo" to "Revisa los avisos antes de recibir y coméntalos con el residente."
            ResultadoCotejo.YA_RECIBIDA -> "Esta compra ya se recibió completa" to "No hay nada pendiente de este comprobante. Si llegó material, avisa al residente."
            ResultadoCotejo.ANULADA -> "Esta compra está anulada" to "No se puede recibir. Consulta con el residente antes de descargar el material."
            ResultadoCotejo.SIN_COMPRA ->
                if (r.enElCelular) "No está en tus compras por recibir" to
                    "No la encontré entre las compras que bajaste con señal: puede estar ya recibida, anulada o aún no registrada. Con señal se confirma."
                else "No hay una compra con este comprobante" to "Avisa al residente para que la registre; el almacenero no registra compras."
            ResultadoCotejo.NO_ES_DE_SEVENS -> "Esta factura no es para SEVENS" to
                "El comprobante está emitido a otro RUC (${r.qr.adquirenteDocumento}). No lo recibas a nombre de la obra."
        }
    }
}

/**
 * Pantalla completa sobre Entrada: la cámara con un marco y, al leer, el resultado con sus avisos.
 * [alRecibir] abre la recepción de esa compra; los avisos viajan a Entrada y quedan a la vista hasta guardar.
 */
@Composable
fun PantallaEscanear(
    obraId: Int,
    compras: List<CompraParaRecibir>,
    alRecibir: (CompraParaRecibir, List<String>) -> Unit,
    alCerrar: () -> Unit,
    vm: EscanearViewModel = koinViewModel(),
) {
    val e by vm.estado.collectAsState()
    val camara = rememberCamara()
    var permiso by remember { mutableStateOf(camara.permiso()) }
    val alcance = rememberCoroutineScope()
    ManejarAtras(activo = true, alVolver = alCerrar)
    // Se llegó aquí tocando «Escanear factura»: ese toque es el que pide el permiso (no antes).
    LaunchedEffect(Unit) {
        if (permiso == PermisoCamara.SIN_PREGUNTAR || permiso == PermisoCamara.DENEGADO) permiso = camara.pedirPermiso()
    }
    Box(Modifier.fillMaxSize().background(Paleta.Noche)) {
        if (lectorQrDisponible && permiso == PermisoCamara.CONCEDIDO && e.resultado == null) {
            LectorQr(Modifier.fillMaxSize()) { vm.leido(obraId, it, compras) }
            // marco donde poner el QR
            Box(Modifier.align(Alignment.Center).size(250.dp).border(3.dp, Paleta.TicketAmarillo, RoundedCornerShape(28.dp)))
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = alCerrar) { Icon(Icons.Filled.Close, contentDescription = "Cerrar y volver a Entrada", tint = Color.White) }
            Text("Escanear factura", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .background(Paleta.Superficie, RoundedCornerShape(24.dp)).padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val r = e.resultado
            when {
                // un QR ya leído se muestra siempre (también si después se quitó el permiso)
                r != null -> Resultado(r, alRecibir, vm::otra)
                !lectorQrDisponible || permiso == PermisoCamara.NO_DISPONIBLE ->
                    Texto2("Leer el QR con la cámara está disponible en la app de Android. Puedes elegir la compra en la lista de Entrada.")
                permiso == PermisoCamara.DENEGADO_PARA_SIEMPRE -> {
                    Texto2("El permiso de cámara está bloqueado. Actívalo en Ajustes → Permisos → Cámara, o elige la compra en la lista.")
                    BotonNegro("Abrir ajustes", { camara.abrirAjustes() }, icono = Icons.Filled.Settings, contorno = true)
                    TextButton(onClick = { permiso = camara.permiso() }) { Text("Ya lo activé", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
                }
                permiso != PermisoCamara.CONCEDIDO -> {
                    Texto2("La cámara solo se usa para leer el QR de la factura; no se guarda ninguna foto.")
                    BotonNegro("Dar permiso de cámara", { alcance.launch { permiso = camara.pedirPermiso() } }, icono = Icons.Filled.QrCodeScanner)
                }
                e.procesando -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Paleta.Naranja, strokeWidth = 3.dp)
                    Texto2("Buscando la compra de esta factura…")
                }
                else -> {
                    Text("Apunta al QR de la factura", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                    Texto2("Funciona sin señal. Con señal, el servidor confirma la compra.")
                    e.aviso?.let { Text(it, fontSize = 14.sp, lineHeight = 19.sp, color = Paleta.RechazadoTexto, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }
            }
            TextButton(onClick = alCerrar) { Text("Elegir la compra de la lista", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Resultado(r: ResultadoEscaneo, alRecibir: (CompraParaRecibir, List<String>) -> Unit, alOtra: () -> Unit) {
    val (titulo, detalle) = EscanearViewModel.explicar(r)
    val bien = r.cotejo.resultado == ResultadoCotejo.RECIBIR
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (bien) Paleta.EnviadoTexto else if (r.cotejo.resultado.sePuedeRecibir) Paleta.PendienteTexto else Paleta.RechazadoTexto)
        Text("${r.qr.tipoComprobante.replaceFirstChar { it.uppercase() }} ${r.qr.numeroComprobante} · RUC ${r.qr.rucEmisor}", fontSize = 14.sp, color = Paleta.Tinta)
        Text("Total ${CotejoQr.soles(QrFactura.centimos(r.qr.total) ?: 0)} · IGV ${CotejoQr.soles(QrFactura.centimos(r.qr.igv) ?: 0)} · ${Texto.fecha(r.qr.fecha)}", fontSize = 13.sp, color = Paleta.Texto3)
        Texto2(detalle)
    }
    r.cotejo.avisos.forEach { aviso ->
        Text(aviso, fontSize = 14.sp, lineHeight = 19.sp, color = Paleta.AvisoTexto,
            modifier = Modifier.fillMaxWidth().background(Paleta.AvisoFondo, RoundedCornerShape(14.dp)).padding(12.dp))
    }
    if (r.enElCelular) Text("Revisado en el celular con las compras que bajaste. Al enviar la entrada, el servidor lo vuelve a revisar.",
        fontSize = 12.sp, lineHeight = 17.sp, color = Paleta.Texto3)
    val compra = r.compra
    if (compra != null) {
        BotonNegro("Recibir esta compra", { alRecibir(compra, r.cotejo.avisos) }, icono = Icons.Filled.Inventory2)
    } else if (r.cotejo.resultado.sePuedeRecibir) {
        Texto2("La compra está por recibir, pero no estaba en tu lista: cierra y desliza hacia abajo en Entrada para actualizarla.")
    }
    BotonNegro("Escanear otra", alOtra, icono = Icons.Filled.QrCodeScanner, contorno = true)
}

@Composable
private fun Texto2(t: String) = Text(t, fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto2)
