package com.finai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion

private val EntryKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "erase")
private val KeypadEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)

/** Four 52 dp rows plus spacing: the complete keypad stays within 240 dp. */
@Composable
internal fun EntryKeypad(visible: Boolean, enabled: Boolean, onKey: (String) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val duration = if (reducedMotion) 0 else 350
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(duration, easing = KeypadEasing), expandFrom = Alignment.Bottom),
        exit = shrinkVertically(tween(duration, easing = KeypadEasing), shrinkTowards = Alignment.Bottom),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(240.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
        ) {
            items(EntryKeys, key = { it }) { key ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    if (pressed && enabled) .97f else 1f,
                    tween(if (reducedMotion) 0 else 80), label = "keyPress",
                )
                val auxiliary = key == "00" || key == "erase"
                val background = when {
                    pressed && enabled -> if (FinaiColors.isDark) Color(0xFF27272A) else FinaiColors.SurfaceMuted
                    auxiliary -> Color.Transparent
                    else -> FinaiColors.Surface
                }
                Box(
                    Modifier.fillMaxWidth().height(52.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(EntryShape).background(background)
                        .semantics {
                            contentDescription = when (key) {
                                "erase" -> "Apagar dígito"
                                "00" -> "Dois zeros"
                                else -> key
                            }
                        }
                        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                            role = Role.Button, onClick = { onKey(key) }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (key == "erase") EntryGlyph("erase", FinaiColors.TextSecondary)
                    else Text(key, color = if (auxiliary) FinaiColors.TextSecondary else EntryInk,
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
