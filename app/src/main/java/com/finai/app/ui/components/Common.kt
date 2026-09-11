package com.finai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mirrors the prototype's `.fin-press` class — a light scale-down while pressed. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return this.scale(scale)
}

@Composable
fun rememberPressInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** A rounded progress track + fill, used for goals, debts and budgets. */
@Composable
fun ProgressTrack(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp,
    trackColor: Color = Color(0xFFF4F4F5),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(99.dp))
                .background(fillColor),
        )
    }
}

/** Small uppercase pill, used for badges (goal status, budget/decision tags). */
@Composable
fun PillTag(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = foreground, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
