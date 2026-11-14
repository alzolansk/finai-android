package com.finai.app.ui.screens.payments

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.PaymentHistory
import com.finai.app.domain.PaymentRecord
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.FinaiSettingsTopBar
import com.finai.app.ui.components.MoneyRow
import com.finai.app.ui.components.MoneyRowBackground
import com.finai.app.ui.components.MoneyRowState
import com.finai.app.ui.components.SelectChip
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0

private enum class HistoryFilter(val label: String, val kinds: Set<PaymentRecord.Kind>) {
    All("Todos", PaymentRecord.Kind.values().toSet()),
    Invoices("Faturas", setOf(PaymentRecord.Kind.Invoice)),
    Installments("Parcelas", setOf(PaymentRecord.Kind.Installment)),
    Bills("Outras contas", setOf(PaymentRecord.Kind.Bill, PaymentRecord.Kind.Received)),
}

/**
 * Histórico de pagamentos (auditoria): faturas e contas marcadas pagas, com o dia do pagamento e o
 * vencimento, e parcelas de dívida registradas. Uma conta tocada por engano é reaberta daqui; o
 * último pagamento de cada dívida pode ser desfeito. Camada por cima de tudo, como Parcelamentos.
 */
@Composable
fun PaymentHistoryScreen(
    visible: Boolean,
    records: List<PaymentRecord>,
    onBack: () -> Unit,
    onReopenConta: (contaId: Long) -> Unit,
    onUndoInstallment: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit,
) {
    BackHandler(visible) { onBack() }
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(if (reduced) 0 else 300, easing = FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(if (reduced) 0 else 240, easing = FastOutSlowInEasing)) { it },
    ) {
        HistoryContent(records, onBack, onReopenConta, onUndoInstallment)
    }
}

@Composable
private fun HistoryContent(
    records: List<PaymentRecord>,
    onBack: () -> Unit,
    onReopenConta: (Long) -> Unit,
    onUndoInstallment: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit,
) {
    var filterName by rememberSaveable { mutableStateOf(HistoryFilter.All.name) }
    val filter = HistoryFilter.valueOf(filterName)
    var pending by remember { mutableStateOf<PaymentRecord?>(null) }
    val undoable = remember(records) { PaymentHistory.undoableInstallments(records) }
    val months = remember(records, filter) { PaymentHistory.byMonth(records.filter { it.kind in filter.kinds }) }

    Column(Modifier.fillMaxSize().background(FinaiColors.Background)) {
        FinaiSettingsTopBar(
            title = "Histórico de pagamentos",
            subtitle = "Faturas, contas e parcelas",
            onNavigateBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filtros") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryFilter.values().forEach { f -> SelectChip(f.label, f == filter) { filterName = f.name } }
                }
            }
            item(key = "explica") {
                Text(
                    "Cada pagamento com o dia em que foi marcado e o vencimento. Toque numa conta para reabrir; " +
                        "o último pagamento de cada dívida pode ser desfeito.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextMuted,
                )
            }
            if (months.isEmpty()) {
                item(key = "vazio") {
                    EmptyStateCard(
                        if (filter == HistoryFilter.All) "Nenhum pagamento registrado ainda. Ao marcar uma fatura ou conta como paga, ou pagar uma parcela, ela aparece aqui."
                        else "Nenhum pagamento deste tipo.",
                        actionLabel = null, onAction = null,
                    )
                }
            }
            months.forEach { group ->
                val key = group.month?.toString() ?: "sem-data"
                item(key = "mes-$key") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp).semantics(mergeDescendants = true) { heading() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group.month?.let { MONTH_NAMES_PT[it.monthValue - 1].uppercase() + " " + it.year } ?: "SEM O DIA DO PAGAMENTO",
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp,
                            color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f),
                        )
                        if (group.paidCents > 0) {
                            Text("${formatBrl0(group.paidCents / 100.0)} pagos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
                        }
                    }
                }
                if (group.month == null) {
                    item(key = "sem-data-nota") {
                        Text(
                            "Marcadas como pagas antes desta versão do app, que ainda não guardava o dia. Ordenadas pelo vencimento.",
                            fontSize = 12.sp, lineHeight = 16.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(group.records, key = { r -> "r-" + (r.conta?.id?.let { "c$it" } ?: "t${r.transacao?.id}") }) { r ->
                    val canUndo = r.conta != null || (r.transacao != null && r.transacao.id in undoable)
                    MoneyRow(
                        title = r.title + (r.detail?.let { " · $it" }.orEmpty()),
                        amount = (if (r.kind == PaymentRecord.Kind.Received) "+" else "−") + money(r.cents),
                        avatar = when (r.kind) {
                            PaymentRecord.Kind.Received -> "R$"
                            else -> r.title.take(2)
                        },
                        subtitle = kindLabel(r.kind) + " · " + r.timing(),
                        state = if (r.kind == PaymentRecord.Kind.Received) MoneyRowState.Received else MoneyRowState.Paid,
                        income = r.kind == PaymentRecord.Kind.Received,
                        background = MoneyRowBackground.Card,
                        onClick = if (canUndo) ({ pending = r }) else null,
                        onClickLabel = when {
                            r.conta != null -> "reabrir"
                            canUndo -> "desfazer pagamento"
                            else -> null
                        },
                    )
                }
            }
        }
    }

    pending?.let { r ->
        val conta = r.conta
        val tx = r.transacao
        ConfirmDeleteDialog(
            title = if (conta != null) "Reabrir \"${r.title}\"?" else "Desfazer o pagamento da parcela?",
            description = if (conta != null) {
                "Ela volta a ficar em aberto, no vencimento, e ${money(r.cents)} volta ao saldo de hoje."
            } else {
                "${r.title}${r.detail?.let { " · $it" }.orEmpty()} volta a ficar em aberto na dívida e ${money(r.cents)} volta ao saldo."
            },
            confirmLabel = if (conta != null) "Reabrir" else "Desfazer",
            onDismiss = { pending = null },
            onConfirm = {
                if (conta != null) onReopenConta(conta.id) else if (tx != null) onUndoInstallment(tx)
                pending = null
            },
        )
    }
}

private fun kindLabel(kind: PaymentRecord.Kind) = when (kind) {
    PaymentRecord.Kind.Invoice -> "fatura"
    PaymentRecord.Kind.Bill -> "conta"
    PaymentRecord.Kind.Received -> "a receber"
    PaymentRecord.Kind.Installment -> "dívida"
}

private fun money(cents: Long) = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else formatBrl(cents / 100.0)
