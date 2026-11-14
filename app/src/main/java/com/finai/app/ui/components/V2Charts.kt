package com.finai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.ui.theme.FinaiColors

/** Uma coluna do gráfico: cada parte é (valor, cor), empilhadas de baixo para cima. */
class ChartColumn(val label: String, val parts: List<Pair<Long, Color>>, val highlighted: Boolean = false) {
    val total: Long get() = parts.sumOf { it.first }
}

/**
 * Barras simples das telas v2 (Gastos, Compromissos, Posso comprar?). Valores negativos descem
 * abaixo da linha de base; [guideCents] desenha uma linha tracejada (a margem de segurança).
 */
@Composable
fun V2BarChart(
    columns: List<ChartColumn>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    guideCents: Long? = null,
    guideColor: Color = FinaiColors.Amber,
) {
    val max = maxOf(columns.maxOfOrNull { c -> c.parts.filter { it.first > 0 }.sumOf { it.first } } ?: 0, guideCents ?: 0, 1)
    val min = minOf(columns.minOfOrNull { c -> c.parts.filter { it.first < 0 }.sumOf { it.first } } ?: 0, 0)
    val span = (max - min).coerceAtLeast(1).toFloat()
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / columns.size.coerceAtLeast(1)
            val barWidth = (slot * 0.56f).coerceAtMost(46.dp.toPx())
            val base = size.height * (max / span)
            columns.forEachIndexed { i, col ->
                val x = slot * i + (slot - barWidth) / 2
                var up = base
                var down = base
                col.parts.forEach { (value, color) ->
                    val h = size.height * (kotlin.math.abs(value) / span)
                    if (h <= 0f) return@forEach
                    val top = if (value >= 0) { up -= h; up } else { val t = down; down += h; t }
                    drawRoundRect(color.copy(alpha = if (col.highlighted || columns.none { it.highlighted }) 1f else 0.45f),
                        Offset(x, top), Size(barWidth, h.coerceAtLeast(2f)), CornerRadius(6.dp.toPx()))
                }
            }
            drawLine(FinaiColors.BorderSubtle, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            guideCents?.let { g ->
                val y = base - size.height * (g / span)
                drawLine(guideColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            columns.forEach { col ->
                Text(col.label, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1,
                    fontWeight = if (col.highlighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (col.highlighted) FinaiColors.TextPrimary else FinaiColors.TextMuted)
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color, 9.dp)
        Text(label, fontSize = 12.sp, color = FinaiColors.TextSecondary)
    }
}
