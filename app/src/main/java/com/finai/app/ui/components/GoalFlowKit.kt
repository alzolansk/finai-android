package com.finai.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0
import kotlin.math.roundToInt

/**
 * Peças das telas de objetivo (aporte, novo objetivo, edição), desenhadas a partir das
 * referências em `uploads/design-metas`. As referências são do tema escuro: os valores escuros
 * abaixo são os delas; no claro, o mesmo layout usa as superfícies claras do app.
 * Getters, não valores: acompanham a troca de tema (ver [FinaiColors]).
 */
internal object GoalUi {
    private val dark get() = FinaiColors.isDark
    private fun pick(light: Color, darkColor: Long) = if (dark) Color(darkColor) else light

    val Page get() = pick(FinaiColors.Background, 0xFF0A0A0B)
    val Sheet get() = pick(Color.White, 0xFF1C1C1F)
    val Card get() = pick(Color.White, 0xFF161618)
    /** Cartão dentro da folha (fundo um degrau abaixo dela). */
    val Inset get() = pick(FinaiColors.Background, 0xFF0E0E10)
    val Border get() = pick(Color(0xFFE4E4E7), 0xFF2A2A2E)
    val Control get() = pick(Color(0xFFF4F4F5), 0xFF232327)
    val Track get() = pick(Color(0xFFE4E4E7), 0xFF2A2A2E)
    val Ink get() = pick(Color(0xFF18181B), 0xFFFAFAFA)
    val Muted get() = pick(Color(0xFF6B6B73), 0xFF9A9AA3)
    val Faint get() = pick(Color(0xFFA1A1AA), 0xFF5A5A62)

    /** Verde de acento e do botão principal — o mesmo nos dois temas, como na referência. */
    val Emerald = Color(0xFF10B981)
    /** Texto sobre o botão verde. */
    val OnEmerald = Color(0xFF052E1F)
    val EmeraldText get() = pick(Color(0xFF047857), 0xFF34D399)
    val EmeraldSoft get() = pick(Color(0xFFECFDF5), 0xFF0E2A20)
    val EmeraldLine get() = pick(Color(0xFFA7F3D0), 0xFF1F5A47)
    val Rose get() = pick(Color(0xFFBE123C), 0xFFF87171)
    val RoseLine get() = pick(Color(0xFFFDA4AF), 0xFF5C2229)
    val Amber get() = pick(Color(0xFFB45309), 0xFFFBBF24)
    val AmberSoft get() = pick(Color(0xFFFFFBEB), 0xFF33280F)
    val RoseSoft get() = pick(Color(0xFFFFF1F2), 0xFF3A1A20)

    /** Selecionado neutro (chip de atalho, prioridade, objetivo na edição): branco no escuro. */
    val Selected get() = pick(Color(0xFF18181B), 0xFFFFFFFF)
    val OnSelected get() = pick(Color.White, 0xFF0A0A0B)

    val DisabledButton get() = pick(Color(0xFFF4F4F5), 0xFF1F1F23)
    val DisabledText get() = pick(Color(0xFFA1A1AA), 0xFF5A5A62)
}

/** Ease-out cubic: começa rápido e desacelera no fim (contagem e barra do aporte). */
internal val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

internal fun brl(cents: Long): String = formatBrl0(cents / 100.0)

/** Ícones das telas de objetivo: grade 24, traço 1,8 — o mesmo desenho do [EntryGlyph]. */
@Composable
internal fun GoalGlyph(name: String, color: Color, size: Int = 20, description: String? = null, stroke: Float = 1.8f) {
    val data = when (name) {
        "Viagem" -> "M21 3L3 10.5l7.5 3L13.5 21Z M10.5 13.5L21 3"
        "Compra" -> "M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z M3 6h18 M16 10a4 4 0 0 1-8 0"
        "Reserva" -> "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"
        "Outro" -> "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01Z"
        "target" -> "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16Z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6Z"
        "back" -> "M15 6l-6 6 6 6"
        "close" -> "M6 6l12 12 M18 6L6 18"
        "minus" -> "M5 12h14"
        "plus" -> "M12 5v14 M5 12h14"
        "check" -> "M5 12.5l4.5 4.5L19 7.5"
        "arrow" -> "M5 12h14 M13 6l6 6-6 6"
        else -> "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16Z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6Z"
    }
    val path = remember(data) { androidx.core.graphics.PathParser.createPathFromPathData(data)!!.asComposePath() }
    Canvas(Modifier.size(size.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Controle deslizante das referências: trilho de 8 dp, parte cheia verde e pino branco. Enquanto o
 * dedo está nele, aparece o halo verde em volta do pino e o balão com o valor ([tooltip]).
 */
@Composable
internal fun GoalSlider(
    fraction: Float,
    onFraction: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    tooltip: Boolean = true,
    description: String = "",
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumb = 26.dp
    val thumbPx = with(density) { thumb.toPx() }
    val f = fraction.coerceIn(0f, 1f)
    val latest by rememberUpdatedState(onFraction)
    fun fractionAt(x: Float): Float = if (widthPx <= thumbPx) 0f else ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                if (description.isNotEmpty()) contentDescription = description
                stateDescription = valueLabel
                progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
                setProgress { v -> latest(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    latest(fractionAt(down.position.x))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        latest(fractionAt(change.position.x))
                        change.consume()
                    }
                    dragging = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = (widthPx - thumbPx).coerceAtLeast(0f)
        val thumbX = travel * f
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(GoalUi.Track))
        Box(
            Modifier.width(with(density) { (thumbX + thumbPx / 2).toDp() }).height(8.dp)
                .clip(CircleShape).background(GoalUi.Emerald),
        )
        if (dragging) {
            val halo = 46.dp
            val haloPx = with(density) { halo.toPx() }
            Box(
                Modifier.offset { IntOffset((thumbX + thumbPx / 2 - haloPx / 2).roundToInt(), 0) }
                    .size(halo).clip(CircleShape).background(GoalUi.Emerald.copy(alpha = .24f)),
            )
        }
        Box(
            Modifier.offset { IntOffset(thumbX.roundToInt(), 0) }
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .then(if (!FinaiColors.isDark) Modifier.border(1.dp, Color(0xFFE4E4E7), CircleShape) else Modifier),
        )
        if (dragging && tooltip) {
            var tipWidth by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .offset {
                        val x = (thumbX + thumbPx / 2 - tipWidth / 2).coerceIn(0f, (widthPx - tipWidth).coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), -with(density) { 31.dp.toPx() }.roundToInt())
                    }
                    .onSizeChanged { tipWidth = it.width.toFloat() }
                    .shadow(4.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(GoalUi.Selected)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(valueLabel, color = GoalUi.OnSelected, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Check de confirmação: círculo verde com dois anéis concêntricos estáticos (halo). */
@Composable
internal fun HaloCheck(modifier: Modifier = Modifier) {
    Box(modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(GoalUi.Emerald.copy(alpha = if (FinaiColors.isDark) .08f else .10f)))
        Box(Modifier.size(80.dp).clip(CircleShape).background(GoalUi.Emerald.copy(alpha = if (FinaiColors.isDark) .14f else .18f)))
        Box(Modifier.size(60.dp).clip(CircleShape).background(GoalUi.Emerald), contentAlignment = Alignment.Center) {
            GoalGlyph("check", GoalUi.OnEmerald, size = 26, stroke = 2.4f, description = "Confirmado")
        }
    }
}

@Composable
internal fun GoalPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(54.dp)
            .then(if (enabled) Modifier.shadow(14.dp, shape, ambientColor = GoalUi.Emerald, spotColor = GoalUi.Emerald) else Modifier)
            .clip(shape)
            .background(if (enabled) GoalUi.Emerald else GoalUi.DisabledButton)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) GoalUi.OnEmerald else GoalUi.DisabledText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun GoalSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = GoalUi.Ink, border: Color = GoalUi.Border) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(54.dp).clip(shape).border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Botão redondo: "−"/"+" contornados ao lado do valor, "voltar"/"fechar" preenchidos. */
@Composable
internal fun GoalRoundButton(icon: String, description: String, onClick: () -> Unit, filled: Boolean = false, size: Int = 40, enabled: Boolean = true) {
    Box(
        Modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size.dp).clip(CircleShape)
                .then(if (filled) Modifier.background(GoalUi.Control) else Modifier.border(1.dp, GoalUi.Border, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            GoalGlyph(icon, if (enabled) GoalUi.Ink else GoalUi.Faint, size = if (filled) 18 else 18, description = description)
        }
    }
}

/** Atalho de valor ("R$ 250", "5 mil"): contornado; selecionado vira o neutro cheio. */
@Composable
internal fun GoalPresetChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.heightIn(min = 34.dp).clip(shape)
            .background(if (selected) GoalUi.Selected else Color.Transparent)
            .border(1.dp, if (selected) GoalUi.Selected else GoalUi.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) GoalUi.OnSelected else GoalUi.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Alta / Média / Baixa. */
@Composable
internal fun GoalSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, background: Color = GoalUi.Card) {
    val shape = RoundedCornerShape(14.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(background).border(1.dp, GoalUi.Border, shape).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (isSel) GoalUi.Selected else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (isSel) GoalUi.OnSelected else GoalUi.Muted, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun GoalCard(modifier: Modifier = Modifier, background: Color = GoalUi.Card, border: Color = GoalUi.Border, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(background).border(1.dp, border, shape).padding(16.dp), content = content)
}

@Composable
internal fun GoalSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = description },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = GoalUi.Emerald, checkedBorderColor = GoalUi.Emerald,
            uncheckedThumbColor = GoalUi.Muted, uncheckedTrackColor = GoalUi.Control, uncheckedBorderColor = GoalUi.Border,
        ),
    )
}

/** Selo "Folgado"/"Apertado"/"Não cabe"/"Sem renda". */
@Composable
internal fun FitPill(level: com.finai.app.domain.GoalFlow.FitLevel, label: String) {
    val (bg, fg) = when (level) {
        com.finai.app.domain.GoalFlow.FitLevel.Folgado -> GoalUi.EmeraldSoft to GoalUi.EmeraldText
        com.finai.app.domain.GoalFlow.FitLevel.Apertado -> GoalUi.AmberSoft to GoalUi.Amber
        com.finai.app.domain.GoalFlow.FitLevel.NaoCabe -> GoalUi.RoseSoft to GoalUi.Rose
        com.finai.app.domain.GoalFlow.FitLevel.SemRenda -> GoalUi.Control to GoalUi.Muted
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

internal fun fitBarColor(level: com.finai.app.domain.GoalFlow.FitLevel): Color = when (level) {
    com.finai.app.domain.GoalFlow.FitLevel.Folgado -> GoalUi.Emerald
    com.finai.app.domain.GoalFlow.FitLevel.Apertado -> GoalUi.Amber
    com.finai.app.domain.GoalFlow.FitLevel.NaoCabe -> GoalUi.Rose
    com.finai.app.domain.GoalFlow.FitLevel.SemRenda -> GoalUi.Faint
}

/** Barra fina de progresso (cartão do aporte mensal, prévia da edição, sucesso do aporte). */
@Composable
internal fun GoalBar(fraction: Float, color: Color = GoalUi.Emerald, modifier: Modifier = Modifier, height: Int = 6) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(GoalUi.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}
