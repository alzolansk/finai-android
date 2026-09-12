package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.AlertSeverity
import com.finai.app.domain.FinanceAlert
import com.finai.app.ui.theme.FinaiColors

/**
 * Dropdown de avisos, ancorado sob o sino da topbar. Os itens vêm de
 * [com.finai.app.domain.AlertCalculator] — contas atrasadas/vencendo,
 * categoria estourando o limite, assinatura parada e entrada prevista, tudo
 * calculado do Room (planning.md §3.9/§6). A Fase 5 acrescenta a entrega
 * proativa fora do app (`WorkManager`) e o texto redigido por IA; a lista em
 * si já é real.
 */
@Composable
fun NotificationsCard(alerts: List<FinanceAlert>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(22.dp)),
    ) {
        Text(
            "Avisos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        if (alerts.isEmpty()) {
            Text(
                "Nada exigindo atenção agora: nenhuma conta atrasada ou vencendo, e nenhum limite perto de estourar.",
                fontSize = 11.5.sp, lineHeight = 16.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(alerts, key = { it.id }) { alert ->
                    AlertRow(alert)
                }
            }
        }
        Text(
            "Fechar", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(13.dp),
        )
    }
}

private val AlertSeverity.dotColor: Color
    get() = when (this) {
        AlertSeverity.Urgent -> Color(0xFFE11D48)
        AlertSeverity.Warning -> Color(0xFFF59E0B)
        AlertSeverity.Positive -> Color(0xFF10B981)
    }

@Composable
private fun AlertRow(alert: FinanceAlert) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Dot(alert.severity.dotColor, modifier = Modifier.padding(top = 5.dp))
        Column {
            Text(alert.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                alert.body, fontSize = 11.5.sp, lineHeight = 16.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
