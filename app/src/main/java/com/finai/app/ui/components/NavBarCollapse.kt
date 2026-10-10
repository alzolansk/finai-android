package com.finai.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity

/**
 * Quando a barra de baixo recolhe ou expande (playbook 3a, "Recolher ao rolar").
 *
 * - Recolhe depois de 6 dp seguidos rolando para baixo, se a tela já passou de 60 dp.
 * - Expande depois de 6 dp seguidos rolando para cima, ou a menos de 30 dp do topo.
 *
 * Os 6 dp são contados na mesma direção: um tremor do dedo para o lado oposto zera a
 * contagem em vez de inverter o estado, e cada troca de estado também zera. É isso que
 * impede a barra de piscar numa rolagem lenta ou indecisa.
 *
 * A distância do topo é a soma do que a lista realmente rolou desde a última troca de tela
 * ([reset]). As abas restauram a posição de rolagem ao voltar, então numa aba que já
 * estava rolada essa soma começa em zero: a barra só recolhe depois de mais 60 dp, nunca
 * antes da hora.
 *
 * Valores em px; [rememberNavBarCollapseState] converte de dp. Kotlin puro fora a
 * conexão de rolagem, para testar sem Compose UI.
 */
@Stable
class NavBarCollapseState(
    private val thresholdPx: Float,
    private val collapseAfterPx: Float,
    private val topZonePx: Float,
) {
    var collapsed by mutableStateOf(false)
        private set

    /** Com as animações do sistema desligadas a barra nunca recolhe (playbook, acessibilidade). */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) collapsed = false
        }

    private var scrolledPx = 0f
    private var runPx = 0f

    /** Rolagem consumida pela tela; positivo = conteúdo indo para baixo (dedo subindo). */
    fun onScrolled(deltaPx: Float) {
        if (deltaPx == 0f) return
        scrolledPx = (scrolledPx + deltaPx).coerceAtLeast(0f)
        runPx = if (runPx == 0f || (runPx > 0f) == (deltaPx > 0f)) runPx + deltaPx else deltaPx
        if (!enabled) return
        val next = when {
            scrolledPx < topZonePx -> false
            runPx > thresholdPx && scrolledPx > collapseAfterPx -> true
            runPx < -thresholdPx -> false
            else -> collapsed
        }
        if (next != collapsed) {
            collapsed = next
            runPx = 0f
        }
    }

    /** Toque na aba já ativa: só expande, sem mexer na contagem de rolagem. */
    fun expand() {
        collapsed = false
        runPx = 0f
    }

    /** Troca de tela: volta expandida e recomeça a contar do topo. */
    fun reset() {
        collapsed = false
        scrolledPx = 0f
        runPx = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Só o que a lista rolou de fato: puxar além do fim (overscroll) não conta.
            onScrolled(-consumed.y)
            return Offset.Zero
        }
    }

    companion object {
        const val THRESHOLD_DP = 6f
        const val COLLAPSE_AFTER_DP = 60f
        const val TOP_ZONE_DP = 30f
    }
}

@Composable
fun rememberNavBarCollapseState(): NavBarCollapseState {
    val density = LocalDensity.current.density
    return remember(density) {
        NavBarCollapseState(
            thresholdPx = NavBarCollapseState.THRESHOLD_DP * density,
            collapseAfterPx = NavBarCollapseState.COLLAPSE_AFTER_DP * density,
            topZonePx = NavBarCollapseState.TOP_ZONE_DP * density,
        )
    }
}
