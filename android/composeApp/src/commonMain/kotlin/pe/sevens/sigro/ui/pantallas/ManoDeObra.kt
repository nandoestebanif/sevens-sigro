package pe.sevens.sigro.ui.pantallas

import pe.sevens.sigro.dominio.ValidacionEnVivo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.Asistencia
import pe.sevens.sigro.dominio.AsistenciaDia
import pe.sevens.sigro.dominio.Categoria
import pe.sevens.sigro.dominio.CorreccionJornada
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoAsistencia
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioJornadas
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.dominio.ValidarBaja
import pe.sevens.sigro.dominio.ValidarTrabajador
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Contador
import pe.sevens.sigro.ui.componentes.Etiqueta
import pe.sevens.sigro.ui.componentes.Marcador
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.TituloSeccion
import pe.sevens.sigro.ui.componentes.planoAsistencia
import pe.sevens.sigro.ui.componentes.planoTrabajadorNuevo
import pe.sevens.sigro.ui.componentes.planoTrabajadores
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.ui.componentes.Modulo

/*
 * 5 · Mano de obra (lienzos MOTrabajadores, MONuevo y MOAsistencia; HU-62 y HU-63).
 * La lista de trabajadores se guarda en el celular para tomar asistencia sin señal. La asistencia
 * va por la cola sin señal: cada jornada nace con su uuid_cliente, se guarda antes de enviarse y un
 * rechazo queda con su motivo en «Mis registros».
 */

/** Lo que la lista de trabajadores pinta. Los de baja van al final y no cuentan como activos (regla 10). */
data class ResumenTrabajadores(
    val activos: List<Trabajador>,
    val deBaja: List<Trabajador>,
    val porCategoria: Map<Categoria, Int>,
    val porCuadrilla: List<Pair<String, List<Trabajador>>>,
)

fun resumirTrabajadores(lista: List<Trabajador>, hoy: String): ResumenTrabajadores {
    val (activos, baja) = lista.partition { it.activoEl(hoy) || it.fechaIngreso > hoy }
    return ResumenTrabajadores(
        activos = activos,
        deBaja = baja,
        porCategoria = Categoria.entries.associateWith { c -> activos.count { it.categoria == c.api } },
        porCuadrilla = activos.groupBy { it.cuadrilla }.toSortedMap().map { (k, v) -> k to v.sortedBy { it.nombre } },
    )
}

/** Iniciales para el avatar: «Juan Pérez» → «JP». */
fun iniciales(nombre: String): String =
    nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

data class EstadoTrabajadores(
    val actualizando: Boolean = false,
    val aviso: String? = null,
    val marcadosHoy: Set<Int> = emptySet(),
    // HU-62 c2 · diálogo de baja
    val bajaDe: Trabajador? = null,
    val fechaBaja: String = "",
    val errorBaja: String? = null,
    val dandoDeBaja: Boolean = false,
    val confirmacion: String? = null,
)

class TrabajadoresViewModel(
    private val trabajadores: RepositorioTrabajadores,
    private val registros: RepositorioRegistros,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoTrabajadores())
    val estado = _e.asStateFlow()

    fun de(obraId: Int) = trabajadores.deObra(obraId)

    fun actualizar(obraId: Int, hoy: String) {
        _e.update { it.copy(actualizando = true, aviso = null) }
        viewModelScope.launch {
            val aviso = try {
                trabajadores.actualizar(obraId); null
            } catch (x: ErrorSigro) {
                if (x.esTransitorio) "Sin señal: ves la lista guardada en el celular." else x.message
            }
            val marcados = runCatching { registros.yaMarcados(obraId, hoy) }.getOrDefault(emptySet())
            _e.update { it.copy(actualizando = false, aviso = aviso, marcadosHoy = marcados) }
        }
    }

    // ── HU-62 c2 · dar de baja: no se borra; queda su fecha de salida y sus jornadas se conservan (regla 10) ──
    fun abrirBaja(t: Trabajador, hoy: String) = _e.update { it.copy(bajaDe = t, fechaBaja = hoy, errorBaja = null, confirmacion = null) }
    fun fechaBaja(v: String) = _e.update { it.copy(fechaBaja = v.filter { c -> c.isDigit() || c == '-' }.take(10), errorBaja = null) }
    fun cerrarBaja() = _e.update { it.copy(bajaDe = null, errorBaja = null) }

    fun confirmarBaja(obraId: Int, hoy: String) {
        val t = _e.value.bajaDe ?: return
        ValidarBaja.error(t, _e.value.fechaBaja, hoy)?.let { m -> _e.update { it.copy(errorBaja = m) }; return }
        _e.update { it.copy(dandoDeBaja = true, errorBaja = null) }
        viewModelScope.launch {
            try {
                val hecho = trabajadores.darDeBaja(obraId, t.id, _e.value.fechaBaja)
                _e.update { it.copy(dandoDeBaja = false, bajaDe = null, confirmacion = "${hecho.nombre} quedó de baja desde el ${hecho.fechaSalida}. Sus jornadas se conservan.") }
            } catch (x: ErrorSigro) {
                val m = if (x.esTransitorio) "Sin señal: la baja necesita conexión. Inténtalo cuando vuelva." else x.message
                _e.update { it.copy(dandoDeBaja = false, errorBaja = m) }
            }
        }
    }
}

@Composable
fun PantallaTrabajadores(obraId: Int, obraNombre: String, alVolver: () -> Unit, irA: (Destino) -> Unit, vm: TrabajadoresViewModel = koinViewModel()) {
    val hoy = remember { Plataforma.fechaIso(Plataforma.ahoraMs()) }
    val flujo = remember(obraId) { vm.de(obraId) }
    val lista by flujo.collectAsState(initial = emptyList())
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.actualizar(obraId, hoy) }
    val r = remember(lista, hoy) { resumirTrabajadores(lista, hoy) }
    val faltan = r.activos.count { it.id !in e.marcadosHoy && it.activoEl(hoy) }

    PantallaVidrio(
        "Trabajadores", plano = { planoTrabajadores(it) }, alVolver = alVolver,
        subtitulo = "$obraNombre · ${r.activos.size} ${if (r.activos.size == 1) "activo" else "activos"}",
        encima = { e.bajaDe?.let { t -> DialogoBaja(t, e, vm::fechaBaja, { vm.confirmarBaja(obraId, hoy) }, vm::cerrarBaja) } },
        acciones = {
            IconButton(onClick = { vm.actualizar(obraId, hoy) }, enabled = !e.actualizando, modifier = Modifier.size(48.dp).vidrio(CircleShape, sombra = 4.dp)) {
                if (e.actualizando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
                else Icon(Icons.Filled.Sync, contentDescription = "Actualizar lista", tint = Paleta.Tinta)
            }
        }, modulo = Modulo.PERSONAL) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).background(Paleta.Principal, RoundedCornerShape(24.dp))
                .clickable(onClickLabel = "Tomar asistencia") { irA(Destino.Asistencia) }.padding(16.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = Paleta.NaranjaVivo, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Asistencia de hoy", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.SobrePrincipal)
                Text(
                    if (faltan == 0 && r.activos.isNotEmpty()) "Registrada para todos" else "Falta registrar · $faltan ${if (faltan == 1) "trabajador" else "trabajadores"}",
                    fontSize = 14.sp, color = Paleta.SobrePrincipal.copy(alpha = 0.8f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Paleta.SobrePrincipal)
        }
        e.aviso?.let { AvisoNaranja(it) }
        e.confirmacion?.let { AvisoNaranja(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Categoria.entries.forEach { c ->
                Cifra(r.porCategoria[c] ?: 0, when (c) { Categoria.OPERARIO -> "Operarios"; Categoria.OFICIAL -> "Oficiales"; Categoria.PEON -> "Peones" })
            }
        }
        if (lista.isEmpty() && !e.actualizando) {
            TarjetaVidrio {
                Text("Aún no hay trabajadores en esta obra", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                Text("Regístralos con su categoría y cuadrilla. La lista se guarda en el celular para tomar asistencia sin señal.", fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2)
            }
        }
        r.porCuadrilla.forEach { (cuadrilla, gente) ->
            TituloSeccion(cuadrilla, Modifier.padding(start = 4.dp, top = 4.dp))
            Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(24.dp))) {
                gente.forEachIndexed { i, t -> if (i > 0) Separador(); FilaTrabajador(t, deBaja = false) { vm.abrirBaja(t, hoy) } }
            }
        }
        if (r.deBaja.isNotEmpty()) {
            TituloSeccion("De baja", Modifier.padding(start = 4.dp, top = 4.dp))
            Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(24.dp))) {
                r.deBaja.forEachIndexed { i, t -> if (i > 0) Separador(); FilaTrabajador(t, deBaja = true) }
            }
        }
        BotonNegro("Registrar trabajador", { irA(Destino.TrabajadorNuevo) }, icono = Icons.Filled.PersonAdd, colorIcono = Paleta.NaranjaVivo)
        BotonNegro("Horas acumuladas", { irA(Destino.Horas) }, icono = Icons.Filled.Schedule, contorno = true)
        if (r.activos.isNotEmpty()) {
            Text("Toca a un trabajador para darlo de baja cuando deje la obra.", fontSize = 13.sp, color = Paleta.Texto3)
        }
    }
}

/** HU-62 c2 · confirmar la baja con su fecha de salida. Dice claro que no se borra nada (regla 10). */
@Composable
private fun DialogoBaja(t: Trabajador, e: EstadoTrabajadores, alCambiar: (String) -> Unit, alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!e.dandoDeBaja) alCerrar() },
        title = { Text("Dar de baja a ${t.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Deja de aparecer en la asistencia desde el día siguiente. No se borra: sus jornadas ya registradas se conservan.",
                    fontSize = 15.sp, lineHeight = 21.sp,
                )
                CampoVidrio(
                    e.fechaBaja, alCambiar, "Fecha de salida", marcador = "AAAA-MM-DD", error = e.errorBaja,
                    ayuda = "Ingresó el ${t.fechaIngreso}", teclado = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alConfirmar, enabled = !e.dandoDeBaja) {
                if (e.dandoDeBaja) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Dar de baja")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar, enabled = !e.dandoDeBaja) { Text("Cancelar") } },
    )
}

@Composable
private fun RowScope.Cifra(valor: Int, etiqueta: String) {
    Column(Modifier.weight(1f).vidrio(RoundedCornerShape(20.dp), sombra = 4.dp).padding(12.dp).semantics(mergeDescendants = true) {}) {
        Text("$valor", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta)
        Text(etiqueta, fontSize = 13.sp, color = Paleta.Texto2)
    }
}

@Composable
private fun FilaTrabajador(t: Trabajador, deBaja: Boolean, alTocar: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    Row(
        (if (alTocar != null) base.clickable(onClickLabel = "Dar de baja", onClick = alTocar) else base)
            .padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(if (deBaja) Paleta.SuperficieSuave else Paleta.NaranjaClaro, CircleShape), contentAlignment = Alignment.Center) {
            Text(iniciales(t.nombre), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (deBaja) Paleta.Texto3 else Paleta.NaranjaOscuro)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (deBaja) Paleta.Texto3 else Paleta.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (deBaja) "Salió el ${t.fechaSalida} · sus jornadas se conservan" else "${t.tipoDocumento} ${t.documento} · desde ${t.fechaIngreso}",
                fontSize = 13.sp, color = Paleta.Texto3, maxLines = 2,
            )
        }
        if (deBaja) Etiqueta("De baja", Paleta.Texto3, Paleta.SuperficieSuave) else Etiqueta(t.categoriaLegible, Paleta.NaranjaOscuro, Paleta.NaranjaClaro)
    }
}

// ── 5.2 Nuevo trabajador ─────────────────────────────────────────────────────────────────────

data class EstadoTrabajadorNuevo(
    val documento: String = "",
    val nombre: String = "",
    val categoria: Categoria? = null,
    val cuadrilla: String = "",
    val cuadrillaNueva: Boolean = false,
    val fechaIngreso: String = "",
    val errores: Map<String, String> = emptyMap(),
    /** Campos ya revisados que están bien (✓), de la validación en vivo. */
    val correctos: Set<String> = emptySet(),
    val guardando: Boolean = false,
    val error: String? = null,
    val creado: Trabajador? = null,
) {
    val nuevo get() = NuevoTrabajador(nombre, "DNI", documento, categoria, cuadrilla, fechaIngreso)
}

class TrabajadorNuevoViewModel(private val trabajadores: RepositorioTrabajadores) : ViewModel() {
    private val _e = MutableStateFlow(EstadoTrabajadorNuevo(fechaIngreso = Plataforma.fechaIso(Plataforma.ahoraMs())))
    val estado = _e.asStateFlow()

    /** Los trabajadores que ya tiene la obra (copia del celular): así un DNI repetido se avisa al escribirlo. */
    private var deLaObra: List<Trabajador> = emptyList()
    private val enVivo = ValidacionEnVivo(
        viewModelScope,
        validar = { ValidarTrabajador.errores(_e.value.nuevo, deLaObra) },
        publicar = { r -> _e.update { it.copy(errores = r.errores, correctos = r.correctos) } },
    )

    fun conocer(gente: List<Trabajador>) { deLaObra = gente; enVivo.revisarYa() }

    /** Solo dígitos, máximo 8 (DNI). Con los 8 se revisa ya (un DNI repetido se ve sin esperar). */
    fun documento(v: String) {
        val d = v.filter(Char::isDigit).take(8)
        _e.update { it.copy(documento = d) }
        if (d.length == 8) enVivo.salir("documento") else enVivo.cambio("documento")
    }
    fun nombre(v: String) { _e.update { it.copy(nombre = v) }; enVivo.cambio("nombre") }
    fun categoria(c: Categoria) { _e.update { it.copy(categoria = c) }; enVivo.salir("categoria") }
    fun cuadrilla(c: String) { _e.update { it.copy(cuadrilla = c, cuadrillaNueva = false) }; enVivo.salir("cuadrilla") }
    fun cuadrillaNueva() { _e.update { it.copy(cuadrilla = "", cuadrillaNueva = true) }; enVivo.revisarYa() }
    fun escribirCuadrilla(v: String) { _e.update { it.copy(cuadrilla = v) }; enVivo.cambio("cuadrilla") }
    fun fechaIngreso(v: String) { _e.update { it.copy(fechaIngreso = v) }; enVivo.cambio("fechaIngreso") }
    fun salirDe(campo: String) = enVivo.salir(campo)

    fun guardar(obraId: Int) {
        viewModelScope.launch {
            deLaObra = trabajadores.deObra(obraId).first()
            val s = _e.value
            if (enVivo.alGuardar().isNotEmpty()) return@launch
            _e.update { it.copy(guardando = true, error = null) }
            try {
                val t = trabajadores.registrar(obraId, s.nuevo)
                _e.update { it.copy(guardando = false, creado = t) }
            } catch (x: ErrorSigro) {
                val msg = if (x.esTransitorio) "Registrar un trabajador necesita señal. Inténtalo cuando haya señal." else x.message
                _e.update { it.copy(guardando = false, error = msg) }
            }
        }
    }

    fun otro() { _e.update { EstadoTrabajadorNuevo(cuadrilla = it.cuadrilla, fechaIngreso = it.fechaIngreso) }; enVivo.reiniciar() }
}

@Composable
fun PantallaTrabajadorNuevo(obraId: Int, alVolver: () -> Unit, vm: TrabajadorNuevoViewModel = koinViewModel(), lista: TrabajadoresViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val flujo = remember(obraId) { lista.de(obraId) }
    val gente by flujo.collectAsState(initial = emptyList())
    val cuadrillas = remember(gente) { gente.map { it.cuadrilla }.distinct().sorted() }
    LaunchedEffect(gente) { vm.conocer(gente) }

    PantallaVidrio("Nuevo trabajador", plano = { planoTrabajadorNuevo(it) }, alVolver = alVolver, subtitulo = "Se incorpora a la obra con su fecha", modulo = Modulo.PERSONAL) {
        val creado = e.creado
        if (creado != null) {
            TarjetaVidrio(fuerte = true) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Paleta.EnviadoTexto, modifier = Modifier.size(40.dp))
                Text("Trabajador registrado", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta)
                Text("${creado.nombre} · ${creado.categoriaLegible} · ${creado.cuadrilla}, desde ${creado.fechaIngreso}.", fontSize = 16.sp, lineHeight = 23.sp, color = Paleta.Texto2)
                BotonNegro("Registrar otro", vm::otro, icono = Icons.Filled.PersonAdd, colorIcono = Paleta.NaranjaVivo)
                BotonNegro("Volver a la lista", alVolver, contorno = true)
            }
            return@PantallaVidrio
        }
        TarjetaVidrio {
            CampoVidrio(
                e.documento, vm::documento, "DNI *", teclado = KeyboardType.Number, error = e.errores["documento"], marcador = "8 dígitos",
                ayuda = if (e.documento.isNotEmpty() && e.documento.length < 8) "Van ${e.documento.length} de 8 dígitos" else null,
                alSalir = { vm.salirDe("documento") }, correcto = "documento" in e.correctos,
            )
            CampoVidrio(
                e.nombre, vm::nombre, "Nombres y apellidos *", error = e.errores["nombre"],
                alSalir = { vm.salirDe("nombre") }, correcto = "nombre" in e.correctos,
            )
            Text("Categoría *", fontSize = 14.sp, color = Paleta.Texto3)
            Opciones(Categoria.entries, e.categoria, { it.legible }, vm::categoria, colorActivo = Paleta.Naranja)
            Text(e.errores["categoria"] ?: "Define el costo por hora que se aplica después.", fontSize = 13.sp, color = if ("categoria" in e.errores) Paleta.RechazadoTexto else Paleta.Texto3)
            Text("Cuadrilla *", fontSize = 14.sp, color = Paleta.Texto3)
            val opciones = cuadrillas + "+ Nueva"
            Opciones(opciones, if (e.cuadrillaNueva) "+ Nueva" else e.cuadrilla.takeIf { it in cuadrillas }, { it }, { if (it == "+ Nueva") vm.cuadrillaNueva() else vm.cuadrilla(it) })
            if (e.cuadrillaNueva || cuadrillas.isEmpty()) {
                CampoVidrio(
                    e.cuadrilla, vm::escribirCuadrilla, "Nombre de la cuadrilla", marcador = "Ej.: Cuadrilla de concreto", error = e.errores["cuadrilla"],
                    alSalir = { vm.salirDe("cuadrilla") }, correcto = "cuadrilla" in e.correctos,
                )
            } else {
                e.errores["cuadrilla"]?.let { Text(it, fontSize = 13.sp, color = Paleta.RechazadoTexto) }
            }
            CampoVidrio(
                e.fechaIngreso, vm::fechaIngreso, "Fecha de ingreso *", error = e.errores["fechaIngreso"], ayuda = "Año-mes-día, como 2026-11-01",
                alSalir = { vm.salirDe("fechaIngreso") }, correcto = "fechaIngreso" in e.correctos,
            )
        }
        e.error?.let { AvisoRojo(it) }
        BotonNegro("Registrar trabajador", { vm.guardar(obraId) }, icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando)
        AvisoNaranja("Si se retira, se da de baja con su fecha de salida: no se borra y sus jornadas se conservan.")
    }
}

// ── 5.3 Asistencia del día ───────────────────────────────────────────────────────────────────

data class EstadoAsistenciaDia(
    val fecha: String,
    val marcas: List<MarcaAsistencia> = emptyList(),
    val yaMarcados: Set<Int> = emptySet(),
    val guardando: Boolean = false,
    val guardadas: Int? = null,
    val busqueda: String = "",
    val dias: List<String> = emptyList(),
    /** HU-63 c3: la jornada que se está corrigiendo (estado y horas nuevos) y su motivo. */
    val corrigiendo: MarcaAsistencia? = null,
    val motivo: String = "",
    val errorCorreccion: String? = null,
    val enviandoCorreccion: Boolean = false,
    val corregida: String? = null,
) {
    /** Solo los que faltan registrar hoy: el día ya guardado no admite dos asistencias (HU-63). */
    val porMarcar: List<MarcaAsistencia> get() = marcas.filter { it.trabajador.id !in yaMarcados }
}

class AsistenciaViewModel(
    private val trabajadores: RepositorioTrabajadores,
    private val registros: RepositorioRegistros,
    private val programador: ProgramadorSincronizacion,
    private val jornadas: RepositorioJornadas,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoAsistenciaDia(
        fecha = Plataforma.fechaIso(Plataforma.ahoraMs()),
        dias = AsistenciaDia.diasElegibles(Plataforma.ahoraMs(), Plataforma::fechaIso),
    ))
    val estado = _e.asStateFlow()

    fun buscar(t: String) = _e.update { it.copy(busqueda = t) }

    /** Otro día de la semana (lo que se olvidó marcar); se vuelve a armar la lista para esa fecha. */
    fun elegirDia(obraId: Int, fecha: String) {
        _e.update { it.copy(fecha = fecha, guardadas = null, corrigiendo = null, corregida = null) }
        cargar(obraId)
    }

    /** Arma la lista con los activos de la fecha (los de baja no aparecen, HU-62). Todos empiezan presentes con 8 h. */
    fun cargar(obraId: Int) {
        viewModelScope.launch {
            val fecha = _e.value.fecha
            val activos = trabajadores.deObra(obraId).first().filter { it.activoEl(fecha) }
            val ya = registros.yaMarcados(obraId, fecha)
            _e.update { it.copy(marcas = activos.map { t -> MarcaAsistencia(t, EstadoAsistencia.PRESENTE) }, yaMarcados = ya) }
        }
    }

    private fun cambiar(id: Int, f: (MarcaAsistencia) -> MarcaAsistencia) =
        _e.update { s -> s.copy(marcas = s.marcas.map { if (it.trabajador.id == id) f(it) else it }) }

    fun estado(id: Int, e: EstadoAsistencia) = cambiar(id) { it.copy(estado = e) }
    fun normales(id: Int, d: Int) = cambiar(id) { Asistencia.ajustarNormales(it, d) }
    fun extra(id: Int, d: Int) = cambiar(id) { Asistencia.ajustarExtra(it, d) }

    // ── HU-63 c3 · corregir una jornada ya registrada (registro nuevo con motivo; la anterior queda como constancia)
    fun empezarCorreccion(trabajadorId: Int) = _e.update { s ->
        val t = s.marcas.firstOrNull { it.trabajador.id == trabajadorId }?.trabajador ?: return@update s
        s.copy(corrigiendo = MarcaAsistencia(t, EstadoAsistencia.PRESENTE), motivo = "", errorCorreccion = null, corregida = null)
    }
    fun cancelarCorreccion() = _e.update { it.copy(corrigiendo = null, errorCorreccion = null) }
    private fun cambiarCorreccion(f: (MarcaAsistencia) -> MarcaAsistencia) =
        _e.update { s -> s.copy(corrigiendo = s.corrigiendo?.let(f), errorCorreccion = null) }
    fun estadoCorreccion(e: EstadoAsistencia) = cambiarCorreccion { it.copy(estado = e) }
    fun normalesCorreccion(d: Int) = cambiarCorreccion { Asistencia.ajustarNormales(it, d) }
    fun extraCorreccion(d: Int) = cambiarCorreccion { Asistencia.ajustarExtra(it, d) }
    fun motivo(t: String) = _e.update { it.copy(motivo = t, errorCorreccion = null) }

    fun confirmarCorreccion(obraId: Int) {
        val s = _e.value
        val m = s.corrigiendo ?: return
        CorreccionJornada.errorMotivo(s.motivo)?.let { err -> _e.update { it.copy(errorCorreccion = err) }; return }
        _e.update { it.copy(enviandoCorreccion = true, errorCorreccion = null) }
        viewModelScope.launch {
            try {
                jornadas.corregir(obraId, s.fecha, m, s.motivo)
                _e.update { it.copy(enviandoCorreccion = false, corrigiendo = null, motivo = "", corregida = CorreccionJornada.textoHecha(m.trabajador.nombre, m)) }
            } catch (e: ErrorSigro) {
                val texto = if (e.codigo == "sin_conexion" || e.codigo == "servidor_no_responde")
                    "Para corregir hace falta señal: se corrige la jornada que tiene el servidor. ${e.message}" else e.message
                _e.update { it.copy(enviandoCorreccion = false, errorCorreccion = texto) }
            }
        }
    }

    /** Guarda en el celular (cola sin señal) y pide enviar cuando haya señal. */
    fun guardar(obraId: Int) {
        val s = _e.value
        if (s.porMarcar.isEmpty()) return
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val r = registros.registrarAsistencia(obraId, s.fecha, s.porMarcar)
            programador.programar()
            val ya = registros.yaMarcados(obraId, s.fecha)
            _e.update { it.copy(guardando = false, guardadas = r.size, yaMarcados = ya) }
        }
    }
}

private val ColoresEstado = mapOf(
    EstadoAsistencia.PRESENTE to Paleta.EnviadoTexto,
    EstadoAsistencia.AUSENTE to Paleta.RechazadoTexto,
    EstadoAsistencia.PERMISO to Paleta.PendienteTexto,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaAsistencia(obraId: Int, alVolver: () -> Unit, alRegistros: () -> Unit, vm: AsistenciaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }
    val r = remember(e.porMarcar) { Asistencia.resumir(e.porMarcar) }
    val esHoy = e.dias.firstOrNull() == e.fecha
    val diaTxt = if (esHoy) "hoy" else "del ${Texto.fecha(e.fecha)}"
    val registrados = e.marcas.count { it.trabajador.id in e.yaMarcados }

    PantallaVidrio("Asistencia", plano = { planoAsistencia(it) }, alVolver = alVolver, subtitulo = "Uno por trabajador y día", modulo = Modulo.PERSONAL) {
        TarjetaVidrio {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${if (esHoy) "Hoy" else "Día"} · ${Texto.fecha(e.fecha)}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                    Text(
                        "Ya registrados: $registrados de ${e.marcas.size}" + if (e.porMarcar.isNotEmpty()) " · por guardar: ${e.porMarcar.size}" else "",
                        fontSize = 14.sp, color = Paleta.Texto2,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mini(r.presentes, "Presentes", ColoresEstado.getValue(EstadoAsistencia.PRESENTE))
                Mini(r.ausentes, "Ausentes", ColoresEstado.getValue(EstadoAsistencia.AUSENTE))
                Mini(r.permisos, "Permiso", ColoresEstado.getValue(EstadoAsistencia.PERMISO))
            }
            Text("Por guardar: " + AsistenciaDia.textoHoras(r), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
            // Día a registrar: hoy o lo olvidado de la semana (el servidor valida el plazo de la obra).
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                e.dias.forEachIndexed { i, d ->
                    val activo = d == e.fecha
                    Box(
                        Modifier.heightIn(min = 40.dp)
                            .background(if (activo) Paleta.NaranjaClaro else Paleta.Superficie.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                            .clickable(onClickLabel = "Registrar el ${Texto.fecha(d)}") { vm.elegirDia(obraId, d) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(when (i) { 0 -> "Hoy"; 1 -> "Ayer"; else -> Texto.fecha(d) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = Paleta.Tinta, maxLines = 1)
                    }
                }
            }
        }
        if (e.marcas.size > 3) {
            CampoVidrio(e.busqueda, vm::buscar, "Buscar trabajador", marcador = "Nombre o cuadrilla")
        }
        e.guardadas?.let { n ->
            Row(
                Modifier.fillMaxWidth().background(Paleta.EnviadoFondo, RoundedCornerShape(16.dp)).clickable(onClickLabel = "Ver en Mis registros", onClick = alRegistros).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Paleta.EnviadoTexto)
                Text(
                    "$n ${if (n == 1) "jornada guardada" else "jornadas guardadas"} en el celular. Se envían solas cuando haya señal; míralas en «Mis registros».",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.EnviadoTexto,
                )
            }
        }
        e.corregida?.let { texto ->
            Row(
                Modifier.fillMaxWidth().background(Paleta.EnviadoFondo, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Paleta.EnviadoTexto)
                Text(texto, fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.EnviadoTexto)
            }
        }
        if (e.marcas.isEmpty()) {
            TarjetaVidrio {
                Text("No hay trabajadores activos en el celular", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                Text("Abre «Trabajadores» con señal para bajar la lista; después la asistencia se toma sin señal.", fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2)
            }
        }
        val visibles = AsistenciaDia.filtrar(e.marcas, e.busqueda)
        if (e.marcas.isNotEmpty() && visibles.isEmpty()) Text("Nadie de la obra coincide con «${e.busqueda}».", fontSize = 15.sp, color = Paleta.Texto2)
        visibles.forEach { m ->
            val ya = m.trabajador.id in e.yaMarcados
            TarjetaVidrio {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.trabajador.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                        Text("${m.trabajador.categoriaLegible} · ${m.trabajador.cuadrilla}", fontSize = 13.sp, color = Paleta.Texto3)
                    }
                    if (ya) Etiqueta(if (esHoy) "Ya registrado hoy" else "Ya registrado", Paleta.EnviadoTexto, Paleta.EnviadoFondo, Icons.Filled.CheckCircle)
                }
                val c = e.corrigiendo
                if (ya && c?.trabajador?.id == m.trabajador.id) {
                    // HU-63 c3: cómo queda la jornada + motivo; no se borra la anterior
                    Text("Corregir la jornada del ${Texto.fecha(e.fecha)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                    Opciones(
                        EstadoAsistencia.entries, c.estado, { it.legible }, vm::estadoCorreccion,
                        colorActivo = ColoresEstado.getValue(c.estado),
                    )
                    if (c.estado == EstadoAsistencia.PRESENTE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Contador("Normales", c.horasNormales, { vm.normalesCorreccion(-1) }, { vm.normalesCorreccion(+1) })
                            Contador("Extra", c.horasExtra, { vm.extraCorreccion(-1) }, { vm.extraCorreccion(+1) })
                        }
                    }
                    CampoVidrio(
                        e.motivo, vm::motivo, "Motivo de la corrección", marcador = "p. ej. Llegó tarde: trabajó 4 h, no 8",
                        error = e.errorCorreccion,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotonNegro("Cancelar", vm::cancelarCorreccion, Modifier.weight(1f), contorno = true, habilitado = !e.enviandoCorreccion)
                        BotonNegro("Guardar corrección", { vm.confirmarCorreccion(obraId) }, Modifier.weight(1f), cargando = e.enviandoCorreccion)
                    }
                } else if (ya && c == null) {
                    Text(
                        "Corregir",
                        modifier = Modifier.clickable(onClickLabel = "Corregir la jornada de ${m.trabajador.nombre}") { vm.empezarCorreccion(m.trabajador.id) }
                            .padding(vertical = 6.dp),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Naranja,
                    )
                }
                if (!ya) {
                    Opciones(
                        EstadoAsistencia.entries, m.estado, { it.legible }, { vm.estado(m.trabajador.id, it) },
                        colorActivo = ColoresEstado.getValue(m.estado),
                    )
                    if (m.estado == EstadoAsistencia.PRESENTE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Contador("Normales", m.horasNormales, { vm.normales(m.trabajador.id, -1) }, { vm.normales(m.trabajador.id, +1) })
                            Contador("Extra", m.horasExtra, { vm.extra(m.trabajador.id, -1) }, { vm.extra(m.trabajador.id, +1) })
                        }
                    }
                }
            }
        }
        BotonNegro(
            "Guardar asistencia de $diaTxt", { vm.guardar(obraId) }, icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo,
            cargando = e.guardando, habilitado = e.porMarcar.isNotEmpty(),
        )
        AvisoNaranja(
            "Sin señal se guarda en el celular y se envía después, como las salidas. Un trabajador ya registrado ese día " +
                "no se vuelve a marcar: se corrige con «Corregir» (necesita señal), con un motivo; la jornada anterior queda como constancia.",
        )
    }
}

@Composable
private fun RowScope.Mini(valor: Int, etiqueta: String, color: Color) {
    Column(Modifier.weight(1f).background(Paleta.Superficie.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(10.dp).semantics(mergeDescendants = true) {}) {
        Text("$valor", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(etiqueta, fontSize = 12.sp, color = Paleta.Texto3)
    }
}
