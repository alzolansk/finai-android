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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Budget
import com.finai.app.data.model.Categorias
import com.finai.app.data.model.Subscription
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun BudgetsScreen(
    budgets: List<Budget>,
    subscriptions: List<Subscription>,
    recentTransactions: List<TransacaoEntity>,
    onEditLimit: (categoria: String) -> Unit,
    onSubscriptionAction: (Subscription) -> Unit,
    onNewTransaction: () -> Unit,
    onDeleteTransaction: (TransacaoEntity) -> Unit,
) {
    val configured = budgets.map { it.name }.toSet()
    val missing = Categorias.all.filter { it !in configured }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Limites do mês", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Text(
                        "Onde o dinheiro está indo este mês", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FinaiColors.Ink)
                        .clickable(onClick = onNewTransaction)
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                ) {
                    Text("Lançar gasto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (budgets.isNotEmpty()) {
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
                    budgets.forEach { budget -> BudgetRow(budget, onEditLimit) }
                }
            }
        }

        if (missing.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Text("Sem limite definido", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        missing.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                                    .clickable { onEditLimit(cat) }
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                            ) {
                                Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        if (subscriptions.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                        .background(FinaiColors.Surface)
                        .padding(16.dp),
                ) {
                    Text("Assinaturas ativas", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    Text(
                        "Sinalizadas quando sem uso há 45 dias ou mais", fontSize = 11.sp, color = FinaiColors.TextMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        subscriptions.forEach { sub -> SubscriptionRow(sub, onSubscriptionAction) }
                    }
                }
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
                Text("Lançamentos deste mês", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                if (recentTransactions.isEmpty()) {
                    Text(
                        "Nenhum lançamento ainda.", fontSize = 12.sp, color = FinaiColors.TextMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        recentTransactions.forEach { t -> TransactionRow(t, onDeleteTransaction) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(budget: Budget, onEditLimit: (String) -> Unit) {
    Column(modifier = Modifier.clickable { onEditLimit(budget.name) }) {
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
            Text(sub.cta, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun TransactionRow(t: TransacaoEntity, onDelete: (TransacaoEntity) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(t.descricao, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(t.categoria, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
        Text(formatBrl0(t.valorCentavos / 100.0), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Text(
            "Excluir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48),
            modifier = Modifier.clickable { onDelete(t) },
        )
    }
}
