package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.dominio.NuevoInsumo
import pe.sevens.sigro.dominio.RepositorioInsumos
import pe.sevens.sigro.dominio.ValidarInsumo
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Etiqueta
import pe.sevens.sigro.ui.componentes.Marcador
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.TituloSeccion
import pe.sevens.sigro.ui.componentes.planoCatBuscar
import pe.sevens.sigro.ui.componentes.planoCatFicha
import pe.sevens.sigro.ui.componentes.planoCatNuevo
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 3 · Catálogo de insumos (lienzos CatBuscar, CatFicha y CatNuevo): código único en todas las obras
 * (regla 8). Buscar funciona sin señal sobre la copia del celular; registrar un insumo nuevo necesita
 * señal porque el código lo da el servidor (HU-08).
 */

/** Cuántas filas se pintan: el catálogo del cliente tiene más de 2 000 insumos. */
const val MAX_RESULTADOS = 40

/** Sugerencias para empezar a buscar (palabras del catálogo real del cliente). */
val SUGERENCIAS_CATALOGO = listOf("cemento", "fierro corrugado", "alambre", "ladrillo")

data class EstadoCatalogo(
    val q: String = "",
    val resultados: List<FichaInsumo> = emptyList(),
    val total: Int = 0,
    val desdeCelular: Boolean = false,
    val buscando: Boolean = false,
    val error: String? = null,
) {
    val resumen: String
        get() = when {
            q.isBlank() -> "Busca por nombre o código, sin tildes ni mayúsculas"
            buscando -> "Buscando…"
            total == 0 -> "Sin coincidencias"
            total > resultados.size -> "$total coincidencias · se muestran ${resultados.size}: afina la búsqueda"
            else -> "$total " + (if (total == 1) "coincidencia" else "coincidencias") + " por nombre o código"
        }
}

class CatalogoViewModel(private val insumos: RepositorioInsumos) : ViewModel() {
    private val _e = MutableStateFlow(EstadoCatalogo())
    val estado = _e.asStateFlow()
    private var job: Job? = null

    fun buscar(texto: String) {
        _e.update { it.copy(q = texto, error = null) }
        job?.cancel()
        if (texto.trim().length < 2) { _e.update { it.copy(resultados = emptyList(), total = 0, buscando = false) }; return }
        _e.update { it.copy(buscando = true) }
        job = viewModelScope.launch {
            delay(300) // espera a que termine de escribir
            try {
                val r = insumos.buscar(texto)
                _e.update { it.copy(resultados = r.insumos.take(MAX_RESULTADOS), total = r.insumos.size, desdeCelular = r.desdeCelular, buscando = false) }
            } catch (x: ErrorSigro) {
                _e.update { it.copy(buscando = false, error = x.message) }
            }
        }
    }
}

@Composable
fun PantallaCatalogo(alVolver: () -> Unit, alAbrir: (FichaInsumo) -> Unit, alNuevo: () -> Unit, vm: CatalogoViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    PantallaVidrio("Catálogo", plano = { planoCatBuscar(it) }, alVolver = alVolver, subtitulo = "Busca por nombre o código") {
        CampoVidrio(
            e.q, vm::buscar, "Buscar insumo", marcador = "Nombre o código",
            finalIcono = { Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.padding(end = 10.dp)) },
        )
        if (e.q.isBlank()) Opciones(SUGERENCIAS_CATALOGO, null, { it }, vm::buscar)
        Text(e.resumen, fontSize = 14.sp, color = Paleta.Texto3)
        if (e.desdeCelular) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Paleta.PendienteTexto, modifier = Modifier.size(18.dp))
                Text("Sin señal: buscas en la copia del catálogo guardada en el celular.", fontSize = 13.sp, color = Paleta.PendienteTexto)
            }
        }
        e.error?.let { AvisoRojo(it) }
        if (e.resultados.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
                e.resultados.forEachIndexed { i, ins ->
                    if (i > 0) Separador()
                    FilaInsumo(ins) { alAbrir(ins) }
                }
            }
        } else if (e.q.trim().length >= 2 && !e.buscando && e.error == null) {
            TarjetaVidrio {
                Text("No está en el catálogo", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                Text("Revisa cómo lo escribiste. Si de verdad es nuevo, regístralo: el sistema le da su código.", fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2)
            }
        }
        BotonNegro("Registrar insumo nuevo", alNuevo, icono = Icons.Filled.Add, colorIcono = Paleta.NaranjaVivo, contorno = true)
    }
}

@Composable
private fun FilaInsumo(i: FichaInsumo, alPulsar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Ver ficha", onClick = alPulsar).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(Paleta.NaranjaClaro, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(i.codigo.take(5), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.NaranjaOscuro)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Texto.nombre(i.descripcion), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Código ${i.codigo} · ${Texto.unidad(i.unidadCompra)}", fontSize = 13.sp, color = Paleta.Texto3)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Paleta.Gris, modifier = Modifier.size(20.dp))
    }
}

// ── 3.2 Ficha ────────────────────────────────────────────────────────────────────────────────

/** Texto del factor (HU-17): sin factor declarado, ambas unidades son la misma. */
fun textoFactor(i: FichaInsumo): String? = when {
    i.factorConversion == null || i.unidadS10 == null -> null
    i.tieneFactor -> "1 ${i.unidadCompra} = ${sinCerosDeMas(i.factorConversion)} ${i.unidadS10}"
    else -> "Sin factor: el S10 y la compra usan la misma unidad (${i.unidadCompra})"
}

/** «4.5000» → «4.5»; «10» se queda «10». */
fun sinCerosDeMas(decimal: String): String = if ('.' in decimal) decimal.trimEnd('0').trimEnd('.') else decimal

@Composable
fun PantallaFichaInsumo(insumo: FichaInsumo, alVolver: () -> Unit, alVerStock: () -> Unit) {
    PantallaVidrio("Insumo", plano = { planoCatFicha(it) }, alVolver = alVolver, subtitulo = "Código ${insumo.codigo}") {
        Row(
            Modifier.fillMaxWidth().height(150.dp).vidrio(RoundedCornerShape(26.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Paleta.Gris, modifier = Modifier.size(34.dp))
                Text("[Foto del insumo: una por insumo, JPG o PNG · llega con HU-11]", fontSize = 13.sp, color = Paleta.Texto3, textAlign = TextAlign.Center)
            }
        }
        TarjetaVidrio {
            Text(Texto.nombre(insumo.descripcion), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta(insumo.tipoLegible, Paleta.NaranjaOscuro, Paleta.NaranjaClaro)
                when (insumo.activo) {
                    true -> Etiqueta("Activo", Paleta.EnviadoTexto, Paleta.EnviadoFondo, Icons.Filled.CheckCircle)
                    false -> Etiqueta("Inactivo", Paleta.RechazadoTexto, Paleta.RechazadoFondo)
                    null -> {}
                }
                when (insumo.verificado) {
                    true -> Etiqueta("Verificado", Paleta.EnviadoTexto, Paleta.EnviadoFondo, Icons.Filled.CheckCircle)
                    false -> Etiqueta("Sin verificar", Paleta.PendienteTexto, Paleta.PendienteFondo)
                    null -> {}
                }
            }
            Dato("Unidad de compra", insumo.unidadCompra)
            Dato("Unidad del S10", insumo.unidadS10 ?: "[llega con señal]")
        }
        TarjetaVidrio {
            TituloSeccion("Factor de conversión")
            val f = textoFactor(insumo)
            if (f != null) Text(f, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta) else Marcador("El factor se ve con señal: la copia del celular no lo guarda")
            Text(
                "Con el factor el sistema compara requerido, comprado y consumido en la unidad de compra. Lo declara el residente desde la web y cada cambio queda en su historial.",
                fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto2,
            )
        }
        TarjetaVidrio {
            TituloSeccion("Nombres comerciales")
            
        }
        BotonNegro("Ver stock en esta obra", alVerStock, icono = Icons.Filled.ViewInAr, colorIcono = Paleta.NaranjaVivo, contorno = true)
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, fontSize = 14.sp, color = Paleta.Texto3, modifier = Modifier.weight(1f))
        Text(valor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta, textAlign = TextAlign.End)
    }
}

// ── 3.3 Insumo nuevo ─────────────────────────────────────────────────────────────────────────

/** Unidades de compra más usadas en el catálogo real del cliente. */
val UNIDADES_COMPRA = listOf("unidades", "bolsas", "kilogramos", "metros", "metro cúbico", "rollo", "galones", "cajas")

enum class TipoInsumoNuevo(val api: String, val legible: String) { MATERIAL("material", "Material"), EQUIPO("equipo", "Equipo") }

data class EstadoInsumoNuevo(
    val tipo: TipoInsumoNuevo = TipoInsumoNuevo.MATERIAL,
    val material: String = "",
    val medida: String = "",
    val marca: String = "",
    val norma: String = "",
    val unidad: String? = null,
    val parecidos: List<FichaInsumo> = emptyList(),
    val duplicado: FichaInsumo? = null,
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val error: String? = null,
    val creado: FichaInsumo? = null,
) {
    val nuevo get() = NuevoInsumo(tipo.api, material, medida, marca, norma, unidad.orEmpty())
}

class InsumoNuevoViewModel(private val insumos: RepositorioInsumos) : ViewModel() {
    private val _e = MutableStateFlow(EstadoInsumoNuevo())
    val estado = _e.asStateFlow()
    private var job: Job? = null

    fun tipo(t: TipoInsumoNuevo) = _e.update { it.copy(tipo = t) }
    fun material(v: String) { _e.update { it.copy(material = v, errores = it.errores - "material") }; revisar() }
    fun medida(v: String) { _e.update { it.copy(medida = v) }; revisar() }
    fun marca(v: String) { _e.update { it.copy(marca = v) }; revisar() }
    fun norma(v: String) { _e.update { it.copy(norma = v) }; revisar() }
    fun unidad(v: String) = _e.update { it.copy(unidad = v, errores = it.errores - "unidad") }

    /** HU-10: si hay coincidencias se muestran antes de confirmar. Sin señal no se puede saber: no se muestran. */
    private fun revisar() {
        job?.cancel()
        val n = _e.value.nuevo
        if (n.material.trim().length < 3) { _e.update { it.copy(parecidos = emptyList(), duplicado = null) }; return }
        job = viewModelScope.launch {
            delay(350)
            val r = runCatching { insumos.buscar(n.material) }.getOrNull() ?: return@launch
            val actual = _e.value.nuevo
            _e.update { it.copy(parecidos = CatalogoTexto.parecidos(actual, r.insumos).take(3), duplicado = CatalogoTexto.duplicado(actual, r.insumos)) }
        }
    }

    fun guardar() {
        val s = _e.value
        val errores = ValidarInsumo.errores(s.nuevo)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        if (s.duplicado != null) return
        _e.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            try {
                val f = insumos.registrar(s.nuevo)
                _e.update { it.copy(guardando = false, creado = f) }
            } catch (x: ErrorSigro) {
                val msg = if (x.esTransitorio) "Registrar un insumo necesita señal: el código lo da el servidor. Inténtalo cuando haya señal." else x.message
                _e.update { it.copy(guardando = false, error = msg) }
            }
        }
    }
}

@Composable
fun PantallaInsumoNuevo(alVolver: () -> Unit, alAbrir: (FichaInsumo) -> Unit, vm: InsumoNuevoViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    PantallaVidrio("Insumo nuevo", plano = { planoCatNuevo(it) }, alVolver = alVolver, subtitulo = "Solo si no existe en el catálogo") {
        val creado = e.creado
        if (creado != null) {
            TarjetaVidrio(fuerte = true) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Paleta.EnviadoTexto, modifier = Modifier.size(40.dp))
                Text("Insumo registrado", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta)
                Text("${creado.descripcion}\nCódigo ${creado.codigo}: el mismo en todas las obras.", fontSize = 16.sp, lineHeight = 23.sp, color = Paleta.Texto2)
                BotonNegro("Ver la ficha", { alAbrir(creado) }, icono = Icons.Filled.Inventory2, colorIcono = Paleta.NaranjaVivo)
                BotonNegro("Volver al catálogo", alVolver, contorno = true)
            }
            return@PantallaVidrio
        }
        TarjetaVidrio {
            Text("Tipo", fontSize = 14.sp, color = Paleta.Texto3)
            Opciones(TipoInsumoNuevo.entries, e.tipo, { it.legible }, vm::tipo)
            CampoVidrio(e.material, vm::material, "Material *", marcador = "Ej.: Fierro corrugado", error = e.errores["material"])
            CampoVidrio(e.medida, vm::medida, "Medida", marcador = "Ej.: 1/2\" x 9m")
            CampoVidrio(e.marca, vm::marca, "Marca", marcador = "Opcional")
            CampoVidrio(e.norma, vm::norma, "Norma", marcador = "Ej.: ASTM A615")
            Text("Unidad en que se compra *", fontSize = 14.sp, color = Paleta.Texto3)
            Opciones(UNIDADES_COMPRA, e.unidad, { it }, vm::unidad, colorActivo = Paleta.Naranja)
            e.errores["unidad"]?.let { Text(it, fontSize = 13.sp, color = Paleta.RechazadoTexto) }
            if (e.nuevo.descripcion.isNotBlank()) {
                Text("Así queda en el catálogo: ${e.nuevo.descripcion}", fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto2)
            }
        }
        if (e.duplicado != null) {
            AvisoRojo("Ya existe «${e.duplicado!!.descripcion}» (código ${e.duplicado!!.codigo}). Usa ese: un insumo no se registra dos veces.")
        } else if (e.parecidos.isNotEmpty()) {
            TarjetaVidrio {
                TituloSeccion("¿Es alguno de estos?")
                Text("Ya están en el catálogo", fontSize = 13.sp, color = Paleta.Texto3)
                e.parecidos.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().background(Paleta.Superficie.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = "Usar este insumo") { alAbrir(p) }.heightIn(min = 52.dp).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${p.codigo} · ${p.descripcion}", fontSize = 14.sp, color = Paleta.Tinta, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Usar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        e.error?.let { AvisoRojo(it) }
        AvisoNaranja("El código lo pone SIGRO al guardar. Necesita señal.")
        BotonNegro("Registrar insumo", vm::guardar, icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando, habilitado = e.duplicado == null)
    }
}
