package com.finai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Menu-only optical tuning. Header material and rendering remain independent. */
internal object NavGlassSpec {
    val Material = GlassMaterial(
        tint = Color(0xFF18181B).copy(alpha = 0.56f),
        fallbackTint = Color(0xFF27272A).copy(alpha = 0.92f),
        blurRadius = 10.dp,
        saturation = 1.25f,
        border = Color.Transparent,
        topHighlight = Color.Transparent,
        shadowColor = Color.Black.copy(alpha = 0.28f),
        shadowOffsetY = 8.dp,
        shadowBlur = 24.dp,
        refract = true,
    )
    const val RefractionBand = 0.65f
    const val RefractionStrength = 0.16f
    const val ScrimOpacity = 0.55f
    const val TouchOpacity = 0.13f
    const val TouchReleaseMillis = 320
    val RimWidth = 1.dp
    val InnerWidth = 3.dp
    val RimColors = listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.16f))
    val InnerColors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.18f))
    val IndicatorColors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.12f))
}

/** Observe touches without consuming them; selectable/clickable retain gesture ownership.
 * All brushes/paths are cached per size. Only alpha and translation change during a touch.
 */
@Composable
internal fun Modifier.navGlassFinish(reduced: Boolean): Modifier {
    var touch by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val light = animateFloatAsState(
        if (pressed) 1f else 0f,
        if (reduced) snap() else tween(if (pressed) FinaiNavBarSpec.PressMillis else NavGlassSpec.TouchReleaseMillis),
        label = "navGlassTouch",
    )
    return this.pointerInput(Unit) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val finger = event.changes.firstOrNull { it.pressed }
                    pressed = finger != null
                    if (finger != null) touch = finger.position
                }
            }
        } finally { pressed = false }
    }.drawWithCache {
        val rim = Brush.linearGradient(NavGlassSpec.RimColors, Offset.Zero, Offset(size.width * 0.3f, size.height))
        val inner = Brush.verticalGradient(NavGlassSpec.InnerColors)
        val radius = size.height * 1.6f
        val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = NavGlassSpec.TouchOpacity), Color.Transparent), Offset.Zero, radius)
        val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2))) }
        val rimWidth = NavGlassSpec.RimWidth.toPx()
        val innerWidth = NavGlassSpec.InnerWidth.toPx()
        val rimStroke = Stroke(rimWidth)
        val innerStroke = Stroke(innerWidth)
        onDrawBehind {
            clipPath(pill) {
                drawRoundRect(inner, Offset(innerWidth / 2, innerWidth / 2), Size(size.width - innerWidth, size.height - innerWidth), CornerRadius((size.height - innerWidth) / 2), style = innerStroke)
                drawRoundRect(rim, Offset(rimWidth / 2, rimWidth / 2), Size(size.width - rimWidth, size.height - rimWidth), CornerRadius((size.height - rimWidth) / 2), style = rimStroke)
                if (light.value > 0f) translate(touch.x, touch.y) {
                    drawCircle(glow, radius, Offset.Zero, alpha = light.value)
                }
            }
        }
    }
}
