package com.finai.app.ui.screens.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.Bill
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun AgendaScreen(
    monthIndex: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Agenda", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(
                    "Pagamentos e recebimentos do mês", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                    .background(FinaiColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ChevronLeft, contentDescription = "Mês anterior", tint = FinaiColors.TextMuted,
                    modifier = Modifier.clickable(onClick = onPrevMonth),
                )
                Text(
                    "${FinaiFixtures.months[monthIndex]} de 2025", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = FinaiColors.TextPrimary,
                )
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = "Próximo mês", tint = FinaiColors.TextMuted,
                    modifier = Modifier.clickable(onClick = onNextMonth),
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile(
                    label = "Contas a pagar", value = formatBrl0(FinaiFixtures.toPayTotalCents),
                    note = FinaiFixtures.toPayCount, valueColor = FinaiColors.TextPrimary, modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    label = "Recebimentos", value = "+ " + formatBrl0(FinaiFixtures.toGetTotalCents),
                    note = FinaiFixtures.toGetCount, valueColor = FinaiColors.EmeraldDark, modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FinaiColors.EmeraldSoftBg)
                    .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(18.dp))
                    .padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(FinaiColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(13.dp))
                }
                Text(FinaiFixtures.agendaTip, fontSize = 12.5.sp, lineHeight = 18.sp, color = FinaiColors.EmeraldDeep)
            }
        }

        items(FinaiFixtures.bills) { bill -> BillRow(bill) }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, note: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(13.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, modifier = Modifier.padding(top = 5.dp))
        Text(note, fontSize = 10.5.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun BillRow(bill: Bill) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bill.tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(bill.initials, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = bill.ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(bill.meta, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(bill.amount, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(bill.status.bg)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(bill.status.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = bill.status.fg)
            }
        }
    }
}
