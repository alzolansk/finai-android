package com.finai.app.ui.screens.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.Budget
import com.finai.app.data.model.Subscription
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun BudgetsScreen(onSubscriptionAction: (Subscription) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Limites do mês", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(
                    "Onde o dinheiro está indo em dezembro", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                    .background(FinaiColors.Surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                FinaiFixtures.budgets.forEach { budget -> BudgetRow(budget) }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                    .background(FinaiColors.Surface)
                    .padding(16.dp),
            ) {
                Text("Assinaturas que a IA questionou", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    FinaiFixtures.subscriptions.forEach { sub -> SubscriptionRow(sub, onSubscriptionAction) }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(budget: Budget) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(budget.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(
                "${formatBrl0(budget.spent)} / ${formatBrl0(budget.limit)}", fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = budget.valueColor,
            )
        }
        ProgressTrack(progress = budget.progressPct, fillColor = budget.barColor, modifier = Modifier.padding(top = 7.dp))
        Text(budget.note, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun SubscriptionRow(sub: Subscription, onAction: (Subscription) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FinaiColors.SurfaceSunken)
            .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(sub.note, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
        Text(sub.amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(FinaiColors.Ink)
                .clickable { onAction(sub) }
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Text(sub.cta, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
