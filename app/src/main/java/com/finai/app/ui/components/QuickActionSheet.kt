package com.finai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.QuickAction
import com.finai.app.ui.theme.FinaiColors

/**
 * O menu do "+" (Fase 7, item 10): "Lançar gasto" é a ação primária, em destaque — é o que
 * se faz quase sempre. Importar, simular e novo objetivo ficam abaixo como secundárias.
 * Limites não entra aqui (decisão do usuário, 28/09/2026).
 */
@Composable
fun QuickActionSheet(
    onPick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = FinaiFixtures.quickActions.first()
    val secondary = FinaiFixtures.quickActions.drop(1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp))
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FinaiColors.EmeraldDark)
                .clickable { onPick(primary) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                QuickActionGlyph(primary.icon, Color.White)
            }
            Column {
                Text(primary.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(primary.sub, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Text(
            "MAIS AÇÕES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted,
            modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 2.dp),
        )
        secondary.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPick(action) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(FinaiColors.SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    QuickActionGlyph(action.icon, FinaiColors.TextSecondary, size = 18)
                }
                Column {
                    Text(action.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                    Text(action.sub, fontSize = 12.5.sp, color = FinaiColors.TextTertiary)
                }
            }
        }
    }
}

/** Reference glyphs for the FAB menu: 24-unit grid, rounded 1.9-unit stroke, 20 dp rendered size. */
@Composable
private fun QuickActionGlyph(name: String, color: Color, size: Int = 20) {
    val data = when (name) {
        // Cédula com lápis.
        "gasto" -> "M4 9H13Q14 9 14 10V17Q14 18 13 18H4Q3 18 3 17V10Q3 9 4 9 M6.5 13.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M21 4L14 11 M14 11L15 14L18 13Z"
        // Documento com seta para cima.
        "importar" -> "M12 16V4 M7 9L12 4L17 9 M4 17V19A1 1 0 0 0 5 20H19A1 1 0 0 0 20 19V17"
        // Balança de dois pratos.
        "simular" -> "M12 7V19 M8 19H16 M4 7H20 M4 7L1 13 M4 7L7 13 M1 13Q4 16 7 13 M20 7L17 13 M20 7L23 13 M17 13Q20 16 23 13"
        // Bandeira fincada.
        "objetivo" -> "M6 21V4 M6 4H17L14 8L17 12H6Z M3 21H9"
        else -> "M5 12h.1 M12 12h.1 M19 12h.1"
    }
    val path = remember(data) { androidx.core.graphics.PathParser.createPathFromPathData(data)!!.asComposePath() }
    Canvas(Modifier.size(size.dp)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
