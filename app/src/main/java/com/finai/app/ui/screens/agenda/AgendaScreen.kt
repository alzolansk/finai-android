package com.finai.app.ui.screens.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.finai.app.domain.DebtInstallment
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Bill
import com.finai.app.data.model.BillStatus
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl0

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun AgendaScreen(
    monthIndex: Int,
    year: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    bills: List<Bill>,
    toPayCents: Long,
    toPayCount: Int,
    toGetCents: Long,
    toGetCount: Int,
    recurringTransactions: List<TransacaoEntity>,
    transactions: List<TransacaoEntity>,
    invoiceItemsByAccountId: Map<Long, List<TransacaoEntity>>,
    debtsDue: List<DebtInstallment>,
    onPayInstallment: (Long) -> Unit,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onDeleteTransaction: (TransacaoEntity) -> Unit,
    onDeleteConta: (Bill) -> Unit,
    onEditConta: (Bill) -> Unit,
    onOpenInvoice: (contaId: Long) -> Unit = {},
    onToggleExtra: (TransacaoEntity, Boolean) -> Unit = { _, _ -> },
) {
    var pendingDeleteTransaction by remember { mutableStateOf<TransacaoEntity?>(null) }
    var pendingDeleteBill by remember { mutableStateOf<Bill?>(null) }

    // Tracks whether the month moved forward or back so the label/totals slide in from the
    // matching side — a plain crossfade reads ambiguous when the user is clearly paging.
    // Updated via SideEffect (not inline) so mutating it never happens mid-composition.
    val monthKey = year * 12 + monthIndex
    var previousMonthKey by remember { mutableStateOf(monthKey) }
    val monthForward = monthKey >= previousMonthKey
    androidx.compose.runtime.SideEffect { previousMonthKey = monthKey }
    // AnimatedContent's transitionSpec isn't a @Composable scope, so it can't call
    // finaiTween() directly; this precomputes the same reduced-motion collapse (0ms) once.
    val reducedMotion = com.finai.app.ui.theme.rememberReducedMotion()
    val quickMillis = if (reducedMotion) 0 else FinaiMotion.Quick
    val standardMillis = if (reducedMotion) 0 else FinaiMotion.Standard

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
                AnimatedContent(
                    targetState = monthIndex to year,
                    transitionSpec = {
                        val distance = if (monthForward) { w: Int -> w / 4 } else { w: Int -> -w / 4 }
                        (slideInHorizontally(tween(quickMillis), distance) + fadeIn(tween(quickMillis)))
                            .togetherWith(slideOutHorizontally(tween(quickMillis)) { -distance(it) } + fadeOut(tween(quickMillis)))
                    },
                    label = "agendaMonth",
                ) { (m, y) ->
                    Text(
                        "${MONTH_NAMES_PT[m]} de $y", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = FinaiColors.TextPrimary,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = "Próximo mês", tint = FinaiColors.TextMuted,
                    modifier = Modifier.clickable(onClick = onNextMonth),
                )
            }
        }

        item {
            AnimatedContent(
                targetState = monthIndex to year,
                transitionSpec = {
                    fadeIn(tween(standardMillis)) togetherWith fadeOut(tween(quickMillis))
                },
                label = "agendaTotals",
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(
                        label = "Contas a pagar", value = formatBrl0(toPayCents / 100.0),
                        note = "$toPayCount conta(s)", valueColor = FinaiColors.TextPrimary, modifier = Modifier.weight(1f),
                    )
                    SummaryTile(
                        label = "Recebimentos", value = "+ " + formatBrl0(toGetCents / 100.0),
                        note = "$toGetCount entrada(s)", valueColor = FinaiColors.EmeraldDark, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (debtsDue.isNotEmpty()) {
            item {
                Text(
                    "Parcelas de dívidas", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                )
            }
            items(debtsDue, key = { "divida-" + it.divida.id }) { parcela -> DebtInstallmentRow(parcela, onPay = { onPayInstallment(parcela.divida.id) }) }
        }

        item {
            Text(
                "Contas e recorrências", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
            )
        }

        if (bills.isEmpty() && recurringTransactions.isEmpty()) {
            item {
                com.finai.app.ui.components.FadeInAppear {
                    Text(
                        "Nenhuma conta ou recorrência cadastrada para este mês.", fontSize = 13.sp, color = FinaiColors.TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        items(bills, key = { "conta-" + it.id }) { bill ->
            BillRow(
                bill, onToggleContaPaga,
                onRequestEdit = { onEditConta(bill) }, onRequestDelete = { pendingDeleteBill = bill },
                onOpenItems = invoiceItemsByAccountId[bill.id]?.let { { onOpenInvoice(bill.id) } },
                modifier = Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)),
            )
        }

        // Gasto/receita lançado manualmente como recorrente entra aqui, junto das
        // contas fixas do onboarding — antes ficava misturado com lançamentos
        // avulsos em "Lançamentos deste mês", como se fosse um gasto único.
        items(recurringTransactions, key = { "transacao-rec-" + it.id }) { transacao ->
            TransacaoRow(transacao, Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)), onToggleExtra) { pendingDeleteTransaction = transacao }
        }

        item {
            Text(
                "Lançamentos deste mês", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (transactions.isEmpty()) {
            item {
                com.finai.app.ui.components.FadeInAppear {
                    Text(
                        "Nenhum gasto lançado ou importado este mês.", fontSize = 13.sp, color = FinaiColors.TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        } else {
            items(transactions, key = { "transacao-" + it.id }) { transacao ->
                TransacaoRow(transacao, Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)), onToggleExtra) { pendingDeleteTransaction = transacao }
            }
        }
    }

    pendingDeleteTransaction?.let { transacao ->
        ConfirmDeleteDialog(
            title = "Excluir lançamento?",
            description = "${transacao.descricao} · ${com.finai.app.util.formatBrl(transacao.valorCentavos / 100.0)} será removido permanentemente.",
            onDismiss = { pendingDeleteTransaction = null },
            onConfirm = { onDeleteTransaction(transacao); pendingDeleteTransaction = null },
        )
    }

    pendingDeleteBill?.let { bill ->
        ConfirmDeleteDialog(
            title = "Excluir conta?",
            description = "${bill.name} · ${bill.amount} será removida permanentemente.",
            onDismiss = { pendingDeleteBill = null },
            onConfirm = { onDeleteConta(bill); pendingDeleteBill = null },
        )
    }

}

@Composable
private fun SummaryTile(label: String, value: String, note: String, valueColor: Color, modifier: Modifier = Modifier) {
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

/**
 * Dívida não tem dia de vencimento cadastrado, só "está ativa este mês" — por isso não
 * usa o mesmo toggle "paga/pendente" de [BillRow] (que depende de status por data). Tocar
 * chama [onPay], que desconta uma parcela em [com.finai.app.state.FinanceViewModel.pagarParcela].
 */
@Composable
private fun DebtInstallmentRow(parcela: DebtInstallment, onPay: () -> Unit) {
    // Só a parcela mais antiga em aberto é "pagável": marcar uma de um mês futuro como paga
    // pagaria, na prática, a anterior a ela (o modelo guarda só a próxima em aberto).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (parcela.atrasada) Color(0xFFFECDD3) else FinaiColors.BorderHairline, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .then(if (parcela.isNext) Modifier.clickable(onClick = onPay) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(parcela.divida.nome, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            val numero = if (parcela.numero != null) "Parcela ${parcela.numero} de ${parcela.total}" else "Parcela"
            val dia = parcela.vencimento?.let { " · vence dia ${it.dayOfMonth}" } ?: " · sem dia definido"
            Text(
                numero + dia, fontSize = 11.sp,
                color = if (parcela.atrasada) Color(0xFFE11D48) else FinaiColors.TextMuted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatBrl0(parcela.valorCentavos / 100.0), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                when {
                    parcela.atrasada && parcela.isNext -> "Atrasada · toque se já pagou"
                    parcela.atrasada -> "Atrasada"
                    parcela.isNext -> "Toque para marcar paga"
                    else -> "Prevista"
                },
                fontSize = 9.sp, color = if (parcela.atrasada) Color(0xFFE11D48) else FinaiColors.TextMuted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * Tocar numa conta a pagar alterna paga/pendente — a única ação que existia
 * antes era criar/excluir contas; sem isto uma conta atrasada nunca saía dos
 * avisos e a Fase 5 não tinha como ser testada de verdade (planning.md §9/§10).
 */
@Composable
private fun BillRow(
    bill: Bill,
    onTogglePaga: (id: Long, pago: Boolean) -> Unit,
    onRequestEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onOpenItems: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val estaPaga = bill.status == BillStatus.Paid
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .clickable { onTogglePaga(bill.id, !estaPaga) }
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
            Text(
                if (estaPaga) "Toque para reabrir" else "Toque para marcar como paga",
                fontSize = 9.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 3.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onOpenItems?.let {
                    Text(
                        "Itens", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                        modifier = Modifier.clickable(onClick = it),
                    )
                }
                Text(
                    "Editar", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
                    modifier = Modifier.clickable(onClick = onRequestEdit),
                )
                Text(
                    "Excluir", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48),
                    modifier = Modifier.clickable(onClick = onRequestDelete),
                )
            }
        }
    }
}

/**
 * Gasto já realizado (manual ou importado) — diferente de [BillRow], que é um
 * compromisso futuro (planning.md §8: `Transacao` vs `Conta` são entidades
 * distintas). Não tem toggle de pago porque já é dinheiro gasto; só exclusão,
 * espelhando a ação que já existe na tela Limites.
 */
@Composable
private fun TransacaoRow(
    transacao: TransacaoEntity,
    modifier: Modifier = Modifier,
    onToggleExtra: (TransacaoEntity, Boolean) -> Unit,
    onDelete: (TransacaoEntity) -> Unit,
) {
    val d = transacao.data.toLocalDate()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(transacao.descricao, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(
                ("${if (transacao.tipo == "Transferencia") "Transferência" else transacao.tipo} · ${transacao.categoria}" +
                    (if (transacao.recorrente) " · recorrente" else "") + (if (transacao.extra) " · extra" else "") +
                    " · %02d/%02d").format(d.dayOfMonth, d.monthValue) +
                    if (transacao.origem == "importado") " · importado" else "",
                fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text(com.finai.app.util.formatBrl(transacao.valorCentavos / 100.0), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when (transacao.tipo) {
                "Receita" -> Color(0xFF059669)
                "Transferencia" -> Color(0xFF4F46E5)
                else -> FinaiColors.TextPrimary
            })
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Receita já lançada pode entrar/sair da Linha do tempo do ano sem relançar.
            if (transacao.tipo == "Receita" && transacao.faturaId == null) {
                Text(
                    if (transacao.extra) "Extra ✓" else "Marcar extra", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (transacao.extra) Color(0xFF059669) else FinaiColors.TextSecondary,
                    modifier = Modifier.clickable { onToggleExtra(transacao, !transacao.extra) },
                )
            }
            Text(
                "Excluir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48),
                modifier = Modifier.clickable { onDelete(transacao) },
            )
        }
    }
}
