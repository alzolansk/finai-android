package com.finai.app.ui.screens.budgets

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import com.finai.app.ui.components.ActionButton
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.OverflowAction
import com.finai.app.ui.components.OverflowMenu
import com.finai.app.ui.components.SectionHeader
import com.finai.app.ui.components.tipTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.FadeInAppear
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.themedText
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl0

@OptIn(ExperimentalLayoutApi::class)
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
    var pendingDelete by remember { mutableStateOf<TransacaoEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Limites do mês", style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary)
                    Text(
                        "Onde o dinheiro está indo este mês", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ActionButton("Lançar gasto", onNewTransaction, primary = true)
            }
        }

        item {
            Column(modifier = Modifier.tipTarget("budgets.list")) {
                if (budgets.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                            .background(FinaiColors.Surface)
                            .padding(vertical = 6.dp),
                    ) {
                        budgets.forEachIndexed { index, budget ->
                            if (index > 0) HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BudgetRow(budget, onEditLimit)
                        }
                    }
                } else {
                    // Sem cadastro ≠ zero real (Fase 7, item 9).
                    EmptyStateCard(
                        "Nenhum limite definido. Escolha uma categoria abaixo para definir quanto quer gastar nela por mês; " +
                            "o app avisa quando estiver perto de estourar.",
                        null, null,
                    )
                }
            }
        }

        if (missing.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Sem limite definido · toque para definir", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        missing.forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                                    .clickable { onEditLimit(cat) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+ $cat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        if (subscriptions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Assinaturas ativas", subtitle = "Sinalizadas quando sem uso há 45 dias ou mais")
                    Column(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(FinaiColors.Surface)
                            .animateContentSize(finaiTween(FinaiMotion.Standard)),
                    ) {
                        subscriptions.forEachIndexed { index, sub ->
                            if (index > 0) HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            SubscriptionRow(sub, onSubscriptionAction)
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Lançamentos deste mês")
                if (recentTransactions.isEmpty()) {
                    FadeInAppear {
                        EmptyStateCard(
                            "Nenhum gasto lançado neste mês. Lance o que gastou, ou importe a fatura do cartão, para ver os limites se movendo.",
                            "Lançar gasto", onNewTransaction,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(FinaiColors.Surface)
                            .animateContentSize(finaiTween(FinaiMotion.Standard)),
                    ) {
                        recentTransactions.forEachIndexed { index, t ->
                            if (index > 0) HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            TransactionRow(t) { pendingDelete = t }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { t ->
        ConfirmDeleteDialog(
            title = "Excluir lançamento?",
            description = "${t.descricao} · ${formatBrl0(t.valorCentavos / 100.0)} será removido permanentemente.",
            onDismiss = { pendingDelete = null },
            onConfirm = { onDeleteTransaction(t); pendingDelete = null },
        )
    }
}

@Composable
private fun BudgetRow(budget: Budget, onEditLimit: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onEditLimit(budget.name) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(budget.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                "${formatBrl0(budget.spent)} / ${formatBrl0(budget.limit)}", fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = budget.valueColor.themedText,
            )
        }
        ProgressTrack(progress = budget.progressPct, fillColor = budget.barColor, modifier = Modifier.padding(top = 7.dp))
        Row(modifier = Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(budget.note, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f))
            Text("Ajustar ›", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark)
        }
    }
}

@Composable
private fun SubscriptionRow(sub: Subscription, onAction: (Subscription) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(sub.note, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 1.dp))
        }
        Text(sub.amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        ActionButton(sub.cta, { onAction(sub) })
    }
}

@Composable
private fun TransactionRow(t: TransacaoEntity, onDelete: (TransacaoEntity) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(t.descricao, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(t.categoria, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 1.dp))
        }
        Text(formatBrl0(t.valorCentavos / 100.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        OverflowMenu(listOf(OverflowAction("Excluir", destructive = true) { onDelete(t) }), contentDescription = "Mais ações para ${t.descricao}")
    }
}
