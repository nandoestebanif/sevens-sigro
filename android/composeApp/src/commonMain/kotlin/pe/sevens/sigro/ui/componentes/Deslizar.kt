package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pe.sevens.sigro.ui.tema.Paleta

/**
 * «Deslizar para actualizar» (Material 3 PullToRefreshBox): al jalar la pantalla hacia abajo desde arriba, se vuelve a
 * pedir al servidor lo que muestra. Sin [alActualizar] es una caja normal (pantallas de formulario: no se borra lo escrito).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeslizarParaActualizar(
    actualizando: Boolean,
    alActualizar: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.() -> Unit,
) {
    if (alActualizar == null) {
        Box(modifier, content = contenido)
        return
    }
    val estado = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = actualizando,
        onRefresh = alActualizar,
        modifier = modifier,
        state = estado,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = estado, isRefreshing = actualizando, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Paleta.Superficie, color = Paleta.Naranja,
            )
        },
        content = contenido,
    )
}
