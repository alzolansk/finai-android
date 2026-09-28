package com.finai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.Tip
import com.finai.app.ui.theme.FinaiColors

/**
 * Onde cada componente com dica está na tela, em coordenadas da raiz (Fase 7, item 6).
 * Nada de coordenada fixa: o componente se registra com [tipTarget] e sai do registro quando
 * deixa a composição (rolou para fora de uma `LazyColumn`, cartão sumiu).
 */
class TipTargetRegistry {
    val bounds = mutableStateMapOf<String, Rect>()

    /** Alvos inteiramente visíveis numa janela de [heightPx] — dica para o que está fora da tela não ajuda. */
    fun visibleTargets(heightPx: Float): Set<String> =
        bounds.filterValues { it.height > 0f && it.top >= 0f && it.bottom <= heightPx }.keys
}

val LocalTipTargets = staticCompositionLocalOf<TipTargetRegistry?> { null }

/** Registra este componente como alvo da dica [id] (ver `domain/TipScript.kt`). */
fun Modifier.tipTarget(id: String): Modifier = composed {
    val registry = LocalTipTargets.current
    if (registry == null) {
        this
    } else {
        DisposableEffect(registry, id) { onDispose { registry.bounds.remove(id) } }
        this.onGloballyPositioned { registry.bounds[id] = it.boundsInRoot() }
    }
}

/**
 * Camada de dicas: escurece a tela (~60%) com um recorte no formato do componente explicado e
 * mostra um balão acima ou abaixo dele, conforme o espaço. O toque fora do balão avança e não
 * passa para a tela de trás. [onDone] é chamado ao concluir ou em "Entendi".
 */
@Composable
fun TipOverlay(tips: List<Tip>, registry: TipTargetRegistry, onDone: () -> Unit) {
    var index by rememberSaveable(tips) { mutableIntStateOf(0) }
    val tip = tips.getOrNull(index)
    val rect = tip?.let { registry.bounds[it.targetId] }
    val advance: () -> Unit = { if (index + 1 < tips.size) index += 1 else onDone() }
    if (tip == null || rect == null) {
        // Alvo sumiu no meio do roteiro: pula para a próxima em vez de apontar para o vazio.
        androidx.compose.runtime.LaunchedEffect(index) { advance() }
    } else {
        TipLayer(tip, rect, "${index + 1} de ${tips.size}", index == tips.lastIndex, advance, onDone)
    }
}

@Composable
private fun TipLayer(tip: Tip, rect: Rect, position: String, last: Boolean, advance: () -> Unit, onDone: () -> Unit) {
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val radius = with(density) { 16.dp.toPx() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = advance),
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val hole = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(
                        rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad,
                        CornerRadius(radius, radius),
                    ),
                )
            }
            drawPath(hole, Color.Black.copy(alpha = 0.6f))
        }
        val below = rect.center.y < heightPx / 2
        val gap = with(density) { (pad + 12.dp.toPx()).toDp() }
        val balloonModifier = if (below) {
            Modifier.align(Alignment.TopCenter).padding(top = with(density) { rect.bottom.toDp() } + gap)
        } else {
            Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (heightPx - rect.top).toDp() } + gap)
        }
        TipBalloon(
            tip = tip,
            position = position,
            last = last,
            onNext = advance,
            onClose = onDone,
            modifier = balloonModifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun TipBalloon(
    tip: Tip,
    position: String,
    last: Boolean,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            // O balão consome o toque: só o fundo avança.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(tip.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(end = 8.dp))
        Text(
            tip.body, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, end = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(position, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextMuted)
            Spacer(Modifier.weight(1f))
            if (!last) TipButton("Entendi", primary = false, onClick = onClose)
            TipButton(if (last) "Concluir" else "Próximo", primary = true, onClick = if (last) onClose else onNext)
        }
    }
}

@Composable
private fun TipButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (primary) FinaiColors.EmeraldDark else FinaiColors.TextSecondary,
        )
    }
}
