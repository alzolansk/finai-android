package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.Celebration
import com.finai.app.domain.CelebrationStyle
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.themedText
import com.finai.app.ui.theme.themedFill
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val ConfettiColors = listOf(
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFF43F5E), Color(0xFF6366F1), Color(0xFF0EA5E9), Color(0xFFFBBF24),
)

private data class Particle(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val wobble: Float,
    val round: Boolean,
)

/**
 * Comemoração de meta concluída / dívida quitada. Viagem ganha um avião
 * cruzando a tela com rastro pontilhado; as outras metas, troféu e confete;
 * dívida quitada, o selo de "feito" e confete. Toque em qualquer lugar ou
 * voltar fecha.
 */
@Composable
fun CelebrationOverlay(celebration: Celebration, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDismiss)
    val cardScale = remember(celebration) { Animatable(0.6f) }
    LaunchedEffect(celebration) {
        cardScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val time = rememberInfiniteTransition(label = "celebration")
    val fall by time.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "fall",
    )
    val flight by time.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart), label = "flight",
    )
    val pulse by time.animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val particles = remember(celebration) {
        val random = Random(celebration.name.hashCode())
        val count = if (celebration.style == CelebrationStyle.Travel) 60 else 110
        List(count) {
            Particle(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                speed = 0.7f + random.nextFloat() * 0.6f,
                size = 5f + random.nextFloat() * 7f,
                color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                spin = (random.nextFloat() - 0.5f) * 720f,
                wobble = 1f + random.nextFloat() * 2f,
                round = random.nextInt(4) == 0,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val density = LocalDensity.current
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val progress = ((fall * p.speed + p.phase) % 1f)
                val y = progress * (size.height + 60f) - 30f
                val x = p.x * size.width + sin((progress * p.wobble * 2 * PI).toFloat()) * 24f
                rotate(progress * p.spin, pivot = Offset(x, y)) {
                    if (p.round) {
                        drawCircle(p.color, radius = p.size * density.density / 2.6f, center = Offset(x, y))
                    } else {
                        val w = p.size * density.density
                        drawRect(p.color, topLeft = Offset(x - w / 2, y - w / 4), size = Size(w, w / 2))
                    }
                }
            }
        }

        if (celebration.style == CelebrationStyle.Travel) {
            FlightPath(flight, maxWidthPx = with(density) { maxWidth.toPx() }, heightPx = with(density) { maxHeight.toPx() })
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .scale(cardScale.value)
                .clip(RoundedCornerShape(28.dp))
                .background(FinaiColors.Surface)
                // Toque no cartão não fecha (só fora dele ou em "Continuar").
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (icon, tint, bg) = when (celebration.style) {
                CelebrationStyle.Travel -> Triple(Icons.Filled.FlightTakeoff, Color(0xFF0284C7).themedText, Color(0xFFE0F2FE).themedFill)
                CelebrationStyle.Confetti -> Triple(Icons.Filled.EmojiEvents, Color(0xFFD97706).themedText, Color(0xFFFEF3C7).themedFill)
                CelebrationStyle.DebtFree -> Triple(Icons.Filled.TaskAlt, FinaiColors.EmeraldDark, FinaiColors.EmeraldSoftBg)
            }
            Box(
                modifier = Modifier.size(84.dp).scale(pulse).clip(CircleShape).background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
            }
            Text(
                celebration.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                celebration.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                celebration.detail, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(FinaiColors.EmeraldSoftBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Text(
                when (celebration.style) {
                    CelebrationStyle.DebtFree -> "Ela sai da estratégia e fica em \"Dívidas quitadas\", no seu histórico."
                    else -> "Ela vai para \"Metas concluídas\". Dá para continuar registrando aportes."
                },
                fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
            Box(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FinaiColors.InkStrong)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continuar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.OnInkStrong)
            }
        }
    }
}

/**
 * Avião cruzando a parte de cima da tela num arco, deixando rastro
 * pontilhado. [t] vai de 0 a 1 e reinicia.
 */
@Composable
private fun FlightPath(t: Float, maxWidthPx: Float, heightPx: Float) {
    val startX = -0.1f * maxWidthPx
    val endX = 1.1f * maxWidthPx
    val baseY = heightPx * 0.24f
    val arc = heightPx * 0.10f
    fun pointAt(f: Float) = Offset(startX + (endX - startX) * f, baseY - sin(f * PI).toFloat() * arc + (0.5f - f) * heightPx * 0.08f)
    val pos = pointAt(t)
    // Direção do voo: derivada numérica da curva, para o nariz seguir o arco.
    val ahead = pointAt((t + 0.01f).coerceAtMost(1f))
    val angle = Math.toDegrees(kotlin.math.atan2((ahead.y - pos.y).toDouble(), (ahead.x - pos.x).toDouble())).toFloat()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val trail = Path()
        val steps = 40
        for (i in 0..steps) {
            val p = pointAt(t * i / steps)
            if (i == 0) trail.moveTo(p.x, p.y) else trail.lineTo(p.x, p.y)
        }
        drawPath(
            trail, Color.White.copy(alpha = 0.75f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f))),
        )
        // Nuvens paradas, só para dar referência de céu.
        listOf(0.18f to 0.14f, 0.72f to 0.1f, 0.55f to 0.3f).forEach { (fx, fy) ->
            val c = Offset(size.width * fx, size.height * fy)
            val r = 16.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.22f), r, c)
            drawCircle(Color.White.copy(alpha = 0.22f), r * 0.8f, c + Offset(r * 1.1f, r * 0.2f))
            drawCircle(Color.White.copy(alpha = 0.22f), r * 0.7f, c + Offset(-r * 1.0f, r * 0.25f))
        }
    }
    val iconSize = 44.dp
    val half = with(LocalDensity.current) { (iconSize / 2).toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset((pos.x - half).roundToInt(), (pos.y - half).roundToInt()) }
            .size(iconSize)
            .graphicsLayer { rotationZ = angle + 90f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Flight, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
