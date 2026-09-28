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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.AlertKind
import com.finai.app.domain.AlertSeverity
import com.finai.app.domain.FinanceAlert
import com.finai.app.ui.theme.FinaiColors

/** Altura da [FinaiTopBar] abaixo da barra de status: 12 dp + botão de 36 dp + 12 dp. */
val TopBarContentHeight = 60.dp

/**
 * Painel de avisos, preso ao sino da topbar. A seta no topo e o crescimento a
 * partir dela (em [com.finai.app.FinaiApp]) mostram de onde o painel veio.
 * Os itens vêm de [com.finai.app.domain.AlertCalculator], agrupados pela
 * urgência: o cabeçalho de cada grupo diz o que fazer com eles ("Agir agora",
 * "Acompanhar"), e cada linha leva à tela onde o problema se resolve.
 *
 * [caretX] é a distância, a partir da borda esquerda do painel, do centro do
 * sino; `null` enquanto o sino ainda não foi medido (a seta só não aparece).
 */
@Composable
fun NotificationsPanel(
    alerts: List<FinanceAlert>,
    caretX: Dp?,
    maxListHeight: Dp,
    onClose: () -> Unit,
    onOpenAlert: (FinanceAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (caretX != null) Caret(caretX)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(22.dp))
                .background(FinaiColors.Surface)
                .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp)),
        ) {
            PanelHeader(alerts, onClose)
            HorizontalDivider(thickness = 1.dp, color = FinaiColors.BorderFaint)
            if (alerts.isEmpty()) {
                EmptyState()
            } else {
                val groups = AlertSeverity.entries.mapNotNull { severity ->
                    alerts.filter { it.severity == severity }.takeIf { it.isNotEmpty() }?.let { severity to it }
                }
                LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                    groups.forEach { (severity, items) ->
                        item(key = "header:${severity.name}") { GroupHeader(severity) }
                        items(items, key = { it.id }) { alert -> AlertRow(alert, onClick = { onOpenAlert(alert) }) }
                    }
                    item(key = "bottom-space") { Box(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Caret(x: Dp) {
    val width = 18.dp
    Canvas(
        modifier = Modifier
            .offset(x = x - width / 2, y = 1.dp)
            .size(width = width, height = 9.dp),
    ) {
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width / 2, 0f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, FinaiColors.Surface)
        val stroke = 1.dp.toPx()
        drawLine(FinaiColors.BorderSubtle, Offset(0f, size.height), Offset(size.width / 2, 0f), stroke)
        drawLine(FinaiColors.BorderSubtle, Offset(size.width / 2, 0f), Offset(size.width, size.height), stroke)
        // Cobre o trecho da borda do cartão logo abaixo da seta, para as duas peças parecerem uma só.
        drawLine(FinaiColors.Surface, Offset(stroke, size.height), Offset(size.width - stroke, size.height), stroke * 2)
    }
}

@Composable
private fun PanelHeader(alerts: List<FinanceAlert>, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Avisos", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text(
                summaryOf(alerts),
                fontSize = 12.5.sp,
                color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(FinaiColors.SurfaceMuted)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar avisos", tint = FinaiColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

private fun summaryOf(alerts: List<FinanceAlert>): String {
    if (alerts.isEmpty()) return "Tudo em dia"
    val urgent = alerts.count { it.severity == AlertSeverity.Urgent }
    val warning = alerts.count { it.severity == AlertSeverity.Warning }
    val positive = alerts.count { it.severity == AlertSeverity.Positive }
    return listOfNotNull(
        urgent.takeIf { it > 0 }?.let { if (it == 1) "1 pede ação agora" else "$it pedem ação agora" },
        warning.takeIf { it > 0 }?.let { "$it para acompanhar" },
        positive.takeIf { it > 0 }?.let { if (it == 1) "1 boa notícia" else "$it boas notícias" },
    ).joinToString(" · ")
}

@Composable
private fun GroupHeader(severity: AlertSeverity) {
    Row(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Dot(severity.strong, size = 7.dp)
        Text(
            severity.groupLabel.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = severity.strong,
        )
    }
}

@Composable
private fun AlertRow(alert: FinanceAlert, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(alert.severity.soft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(alert.kind.icon, contentDescription = null, tint = alert.severity.strong, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(alert.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, color = FinaiColors.TextPrimary)
            Text(
                alert.body, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                alert.kind.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinaiColors.TextMuted,
            modifier = Modifier.padding(top = 9.dp).size(20.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(FinaiColors.EmeraldSoftBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(26.dp))
        }
        Text(
            "Nada pedindo sua atenção",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Nenhuma conta atrasada ou vencendo, nenhum limite perto de estourar e seus objetivos estão no ritmo.",
            fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary, textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp).padding(top = 4.dp),
        )
    }
}

private val AlertSeverity.strong: Color
    get() = when (this) {
        AlertSeverity.Urgent -> FinaiColors.RoseDark
        AlertSeverity.Warning -> FinaiColors.AmberDark
        AlertSeverity.Positive -> FinaiColors.EmeraldDark
    }

private val AlertSeverity.soft: Color
    get() = when (this) {
        AlertSeverity.Urgent -> FinaiColors.RoseSoftBg
        AlertSeverity.Warning -> FinaiColors.AmberSoftBg
        AlertSeverity.Positive -> FinaiColors.EmeraldSoftBg
    }

private val AlertSeverity.groupLabel: String
    get() = when (this) {
        AlertSeverity.Urgent -> "Agir agora"
        AlertSeverity.Warning -> "Acompanhar"
        AlertSeverity.Positive -> "Boas notícias"
    }

private val AlertKind.icon: ImageVector
    get() = when (this) {
        AlertKind.Bill -> Icons.Outlined.Event
        AlertKind.Budget -> Icons.Outlined.DonutLarge
        AlertKind.Subscription -> Icons.Outlined.Autorenew
        AlertKind.Income -> Icons.Outlined.Payments
        AlertKind.Goal -> Icons.Outlined.Flag
    }

private val AlertKind.actionLabel: String
    get() = when (this) {
        AlertKind.Bill, AlertKind.Income -> "Ver na Agenda"
        AlertKind.Budget, AlertKind.Subscription -> "Ver em Limites"
        AlertKind.Goal -> "Ver objetivos"
    }
