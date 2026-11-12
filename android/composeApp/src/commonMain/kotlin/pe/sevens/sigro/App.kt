package pe.sevens.sigro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import pe.sevens.sigro.dominio.RepositorioPreferencias
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.ModoTema
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.plataforma.ManejarAtras
import pe.sevens.sigro.ui.pantallas.Destino
import pe.sevens.sigro.ui.pantallas.EstadoRaiz
import pe.sevens.sigro.ui.pantallas.PantallaAsistencia
import pe.sevens.sigro.ui.pantallas.PantallaCatalogo
import pe.sevens.sigro.ui.pantallas.PantallaEntrada
import pe.sevens.sigro.ui.pantallas.PantallaIngresoSinCompra
import pe.sevens.sigro.ui.pantallas.PantallaStock
import pe.sevens.sigro.ui.pantallas.PantallaTraslado
import pe.sevens.sigro.ui.pantallas.PantallaTrabajadorNuevo
import pe.sevens.sigro.ui.pantallas.PantallaTrabajadores
import pe.sevens.sigro.ui.pantallas.PantallaCierre
import pe.sevens.sigro.ui.pantallas.PantallaConteo
import pe.sevens.sigro.ui.pantallas.PantallaDispositivo
import pe.sevens.sigro.ui.pantallas.PantallaFichaInsumo
import pe.sevens.sigro.ui.pantallas.PantallaInsumoNuevo
import pe.sevens.sigro.ui.pantallas.PantallaEntrar
import pe.sevens.sigro.ui.pantallas.PantallaInicio
import pe.sevens.sigro.ui.pantallas.PantallaObras
import pe.sevens.sigro.ui.pantallas.PantallaPendientes
import pe.sevens.sigro.ui.pantallas.PantallaPerfil
import pe.sevens.sigro.ui.pantallas.PantallaSalida
import pe.sevens.sigro.ui.pantallas.RaizViewModel
import pe.sevens.sigro.ui.tema.TemaSigro

/** Interfaz común de Android e iOS. La misma función la muestran MainActivity y MainViewController. */
@Composable
fun App() {
    KoinContext {
        val preferencias: RepositorioPreferencias = koinInject()
        LaunchedEffect(Unit) {
            preferencias.tema()?.let { t -> ModoTema.entries.firstOrNull { it.name == t }?.let { Apariencia.modo = it } }
        }
        val delSistema = isSystemInDarkTheme()
        Apariencia.oscuro = when (Apariencia.modo) {
            ModoTema.SISTEMA -> delSistema
            ModoTema.CLARO -> false
            ModoTema.OSCURO -> true
        }
        TemaSigro(oscuro = Apariencia.oscuro) {
            val raiz: RaizViewModel = koinViewModel()
            val estado by raiz.estado.collectAsState()
            // Al entrar, la capa «el plano se dibuja» de PantallaEntrar se desvanece y crece 6 % (0,6 s)
            // dejando ver la pantalla siguiente, que va debajo.
            AnimatedContent(
                estado,
                contentKey = { it::class },
                transitionSpec = {
                    if (initialState == EstadoRaiz.SinSesion) {
                        (fadeIn(tween(0)) togetherWith (fadeOut(tween(600)) + scaleOut(tween(600), targetScale = 1.06f)))
                            .apply { targetContentZIndex = -1f }
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
            ) { e ->
                when (e) {
                    EstadoRaiz.Cargando -> Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    EstadoRaiz.SinSesion -> PantallaEntrar()
                    is EstadoRaiz.ElegirObra -> PantallaObras(e.sesion, alElegir = raiz::elegir, alSalir = raiz::salir)
                    is EstadoRaiz.Dentro -> Navegacion(e)
                }
            }
        }
    }
}

/**
 * Navegación dentro de la obra: una pila simple de [Destino]. Cada pantalla recibe `irA` para abrir
 * otra y `volver` para cerrarse; el botón atrás del sistema hace lo mismo.
 */
@Composable
private fun Navegacion(e: EstadoRaiz.Dentro) {
    val pila = remember(e.obra.id) { mutableStateListOf<Destino>(Destino.Inicio) }
    val actual = pila.last()
    val volver: () -> Unit = { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
    ManejarAtras(activo = pila.size > 1, alVolver = volver)

    val irA: (Destino) -> Unit = { pila.add(it) }
    AnimatedContent(actual, transitionSpec = { fadeIn() togetherWith fadeOut() }) { d ->
        when (d) {
            Destino.Inicio -> PantallaInicio(e.sesion, e.obra, irA = irA)
            Destino.Salida -> PantallaSalida(e.obra.id, alVolver = volver)
            is Destino.SalidaDe -> PantallaSalida(e.obra.id, alVolver = volver, base = d.base)
            Destino.Pendientes -> PantallaPendientes(e.obra.id, alVolver = volver, alCorregir = { irA(Destino.SalidaDe(it)) })
            Destino.Rechazados -> PantallaPendientes(e.obra.id, alVolver = volver, soloRechazados = true, alCorregir = { irA(Destino.SalidaDe(it)) })
            Destino.Perfil -> PantallaPerfil(e.sesion, e.obra, alVolver = volver, alCelular = { irA(Destino.Dispositivo) })
            Destino.Dispositivo -> PantallaDispositivo(e.obra.id, alVolver = volver, obraNombre = e.obra.nombre)
            Destino.Catalogo -> PantallaCatalogo(alVolver = volver, alAbrir = { irA(Destino.Ficha(it)) }, alNuevo = { irA(Destino.InsumoNuevo) })
            is Destino.Ficha -> PantallaFichaInsumo(d.insumo, alVolver = volver, alVerStock = { irA(Destino.Stock) })
            Destino.InsumoNuevo -> PantallaInsumoNuevo(alVolver = volver, alAbrir = { volver(); irA(Destino.Ficha(it)) })
            Destino.Trabajadores -> PantallaTrabajadores(e.obra.id, e.obra.nombre, alVolver = volver, irA = irA)
            Destino.TrabajadorNuevo -> PantallaTrabajadorNuevo(e.obra.id, alVolver = volver)
            Destino.Asistencia -> PantallaAsistencia(e.obra.id, alVolver = volver, alRegistros = { irA(Destino.Pendientes) })
            Destino.Stock -> PantallaStock(e.obra.id, alVolver = volver)
            Destino.Entrada -> PantallaEntrada(e.obra.id, alVolver = volver)
            Destino.IngresoSinCompra -> PantallaIngresoSinCompra(e.obra.id, alVolver = volver)
            Destino.Traslado -> PantallaTraslado(e.obra.id, e.obra.nombre, e.sesion.obras, alVolver = volver)
            Destino.Conteo -> PantallaConteo(e.obra.id, alVolver = volver)
            Destino.Cierre -> PantallaCierre(e.obra.id, e.obra.nombre, alVolver = volver)
        }
    }
}
