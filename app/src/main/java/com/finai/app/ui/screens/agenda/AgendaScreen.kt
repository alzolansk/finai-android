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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.PayCycle
import com.finai.app.ui.components.EntryGlyph
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl0
import java.time.LocalDate

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
    onEditDebt: (Long) -> Unit = {},
    today: LocalDate = LocalDate.now(),
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

        // Timeline do mês: tudo que entra ou sai, por dia, na ordem em que acontece. O tipo
        // (dívida, recorrente, fatura…) vira chip do item, não seção da página.
        val timeline = agendaTimeline(bills, recurringTransactions, transactions, debtsDue)
        if (timeline.isEmpty()) {
            item {
                com.finai.app.ui.components.FadeInAppear {
                    Text(
                        "Nada agendado para este mês.", fontSize = 13.sp, color = FinaiColors.TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        timeline.forEach { (date, entries) ->
            item(key = "dia-" + (date?.toString() ?: "sem-dia")) {
                DayHeader(date, entries, today, Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)))
            }
            items(entries, key = { it.key }) { entry ->
                val mod = Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard))
                when (entry) {
                    is AgendaEntry.Conta -> BillRow(
                        entry.bill, onToggleContaPaga,
                        onRequestEdit = { onEditConta(entry.bill) }, onRequestDelete = { pendingDeleteBill = entry.bill },
                        onOpenItems = invoiceItemsByAccountId[entry.bill.id]?.let { { onOpenInvoice(entry.bill.id) } },
                        modifier = mod,
                    )
                    is AgendaEntry.Parcela -> DebtInstallmentRow(
                        entry.parcela, onPay = { onPayInstallment(entry.parcela.divida.id) },
                        onEdit = { onEditDebt(entry.parcela.divida.id) }, modifier = mod,
                    )
                    is AgendaEntry.Lancamento -> TransacaoRow(
                        entry.transacao, today, mod, onToggleExtra,
                    ) { pendingDeleteTransaction = entry.transacao }
                }
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

private sealed interface AgendaEntry {
    val key: String
    val date: LocalDate?
    /** Dentro do dia: o que entra primeiro, depois contas, recorrentes, avulsos e parcelas. */
    val order: Int
    /** Positivo entra, negativo sai — soma do dia no cabeçalho. */
    val signedCents: Long

    data class Conta(val bill: Bill) : AgendaEntry {
        override val key get() = "conta-" + bill.id
        override val date get() = bill.date
        override val order get() = if (bill.aReceber) 0 else 1
        override val signedCents get() = if (bill.aReceber) bill.valorCentavos else -bill.valorCentavos
    }
    data class Parcela(val parcela: DebtInstallment) : AgendaEntry {
        override val key get() = "divida-" + parcela.divida.id
        override val date get() = parcela.vencimento
        override val order get() = 4
        override val signedCents get() = -parcela.valorCentavos
    }
    data class Lancamento(val transacao: TransacaoEntity) : AgendaEntry {
        override val key get() = (if (transacao.recorrente) "transacao-rec-" else "transacao-") + transacao.id
        override val date: LocalDate get() = transacao.data.toLocalDate()
        override val order get() = when {
            transacao.tipo == "Receita" -> 0
            transacao.recorrente -> 2
            else -> 3
        }
        override val signedCents get() = when (transacao.tipo) {
            "Receita" -> transacao.valorCentavos
            "Transferencia" -> 0L
            else -> -transacao.valorCentavos
        }
    }
}

/** Agrupa por dia em ordem crescente; parcela sem dia definido fica no fim. */
private fun agendaTimeline(
    bills: List<Bill>,
    recorrentes: List<TransacaoEntity>,
    avulsas: List<TransacaoEntity>,
    parcelas: List<DebtInstallment>,
): List<Pair<LocalDate?, List<AgendaEntry>>> {
    val entries = bills.map { AgendaEntry.Conta(it) } +
        parcelas.map { AgendaEntry.Parcela(it) } +
        (recorrentes + avulsas).map { AgendaEntry.Lancamento(it) }
    return entries
        .sortedWith(compareBy<AgendaEntry, LocalDate?>(nullsLast()) { it.date }.thenBy { it.order })
        .groupBy { it.date }
        .toList()
}

private val WEEKDAYS_PT = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

@Composable
private fun DayHeader(date: LocalDate?, entries: List<AgendaEntry>, today: LocalDate, modifier: Modifier = Modifier) {
    val net = entries.sumOf { it.signedCents }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (date == null) {
            Text("SEM DIA DEFINIDO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextSecondary)
        } else {
            Text(
                "%02d %s".format(date.dayOfMonth, MONTH_ABBREV_PT[date.monthValue - 1].uppercase()),
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
            )
            Text(WEEKDAYS_PT[date.dayOfWeek.value - 1], fontSize = 12.sp, color = FinaiColors.TextMuted)
            if (date == today) Chip("Hoje", FinaiColors.EmeraldDark, Color(0xFFECFDF5))
        }
        Box(Modifier.weight(1f))
        if (net != 0L) {
            Text(
                (if (net > 0) "+ " else "− ") + formatBrl0(kotlin.math.abs(net) / 100.0),
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                color = if (net > 0) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
            )
        }
    }
}

private enum class Tag(val label: String, val fg: Color, val bg: Color) {
    Divida("Dívida", Color(0xFFBE123C), Color(0xFFFFF1F2)),
    Recorrente("Recorrente", Color(0xFF4338CA), Color(0xFFEEF2FF)),
    Receita("Receita", Color(0xFF047857), Color(0xFFECFDF5)),
    Fatura("Fatura", Color(0xFF6D28D9), Color(0xFFF5F3FF)),
    Conta("Conta", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Extra("Extra", Color(0xFFB45309), Color(0xFFFFFBEB)),
    Importado("Importado", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Transferencia("Transferência", Color(0xFF4F46E5), Color(0xFFEEF2FF)),
}

@Composable
private fun Chip(label: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun Tags(tags: List<Tag>, extra: String? = null) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { Chip(it.label, it.fg, it.bg) }
        extra?.let { Text(it, fontSize = 10.5.sp, color = FinaiColors.TextMuted, maxLines = 1) }
    }
}

/** Ícone do item: diz o que ele é (casa, dívida, salário…) em vez de iniciais do nome. */
private class ItemIcon(val glyph: String, val bg: Color, val ink: Color)

private val DebtIcon = ItemIcon("installment", Color(0xFFFFF1F2), Color(0xFFBE123C))
private val InvoiceIcon = ItemIcon("card", Color(0xFFF5F3FF), Color(0xFF6D28D9))
private val IncomeIcon = ItemIcon("wallet", Color(0xFFECFDF5), Color(0xFF047857))
private val SalaryIcon = ItemIcon("briefcase", Color(0xFFECFDF5), Color(0xFF047857))
private val TransferIcon = ItemIcon("transfer", Color(0xFFEEF2FF), Color(0xFF4F46E5))
private val BillIcon = ItemIcon("calendar", Color(0xFFF4F4F5), Color(0xFF3F3F46))

/** Um tom por categoria, o mesmo glifo da tela de lançamento. */
private fun categoryIcon(categoria: String): ItemIcon = when (categoria) {
    "Alimentação" -> ItemIcon(categoria, Color(0xFFFEFCE8), Color(0xFFA16207))
    "Transporte" -> ItemIcon(categoria, Color(0xFFEFF6FF), Color(0xFF1D4ED8))
    "Moradia" -> ItemIcon(categoria, Color(0xFFFFF7ED), Color(0xFFC2410C))
    "Saúde" -> ItemIcon(categoria, Color(0xFFF0FDFA), Color(0xFF0F766E))
    "Lazer" -> ItemIcon(categoria, Color(0xFFECFEFF), Color(0xFF0E7490))
    "Educação" -> ItemIcon(categoria, Color(0xFFF7FEE7), Color(0xFF4D7C0F))
    "Compras" -> ItemIcon(categoria, Color(0xFFFDF2F8), Color(0xFFBE185D))
    "Serviços" -> ItemIcon(categoria, Color(0xFFF1F5F9), Color(0xFF334155))
    else -> ItemIcon(categoria, Color(0xFFF4F4F5), Color(0xFF3F3F46))
}

private fun iconFor(t: TransacaoEntity): ItemIcon = when {
    t.origem == DebtSchedule.PAYMENT_ORIGIN -> DebtIcon
    t.tipo == "Receita" && PayCycle.isSalary(t) -> SalaryIcon
    t.tipo == "Receita" -> IncomeIcon
    t.tipo == "Transferencia" -> TransferIcon
    else -> categoryIcon(t.categoria)
}

@Composable
private fun Avatar(icon: ItemIcon) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(icon.bg),
        contentAlignment = Alignment.Center,
    ) {
        EntryGlyph(icon.glyph, icon.ink, size = 19)
    }
}

private class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** Ações secundárias (editar, excluir…) ficam aqui em vez de soltas no card. */
@Composable
private fun RowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mais ações", tint = FinaiColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Text(a.label, fontSize = 13.sp, color = if (a.destructive) Color(0xFFE11D48) else FinaiColors.TextPrimary)
                    },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}

@Composable
private fun RowCard(
    modifier: Modifier,
    borderColor: Color = FinaiColors.BorderHairline,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/**
 * Parcela de dívida. Só a mais antiga em aberto é "pagável": marcar uma de um mês futuro
 * pagaria, na prática, a anterior a ela (o modelo guarda só a próxima em aberto). Tocar
 * chama [onPay], que desconta uma parcela em [com.finai.app.state.FinanceViewModel.pagarParcela].
 */
@Composable
private fun DebtInstallmentRow(parcela: DebtInstallment, onPay: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    RowCard(
        modifier,
        borderColor = if (parcela.atrasada) Color(0xFFFECDD3) else FinaiColors.BorderHairline,
        onClick = if (parcela.isNext) onPay else null,
    ) {
        Avatar(DebtIcon)
        Column(modifier = Modifier.weight(1f)) {
            Text(parcela.divida.nome, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Tags(listOf(Tag.Divida), if (parcela.numero != null) "${parcela.numero} de ${parcela.total}" else null)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatBrl0(parcela.valorCentavos / 100.0), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            val status = if (parcela.atrasada) BillStatus.Overdue else BillStatus.Expected
            Box(Modifier.padding(top = 3.dp)) { Chip(if (parcela.atrasada) "Atrasada" else "Prevista", status.fg, status.bg) }
            if (parcela.isNext) {
                Text(
                    if (parcela.atrasada) "Toque se já pagou" else "Toque para marcar paga",
                    fontSize = 9.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        RowMenu(listOfNotNull(
            if (parcela.isNext) MenuAction("Marcar parcela como paga", onClick = onPay) else null,
            MenuAction("Editar dívida", onClick = onEdit),
        ))
    }
}

/**
 * Conta com vencimento — a referência visual da lista (a fatura usa este card). Tocar
 * alterna paga/pendente; sem isto uma conta atrasada nunca saía dos avisos (planning.md §9/§10).
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
    RowCard(modifier, onClick = { onTogglePaga(bill.id, !estaPaga) }) {
        Avatar(
            when {
                onOpenItems != null -> InvoiceIcon
                bill.aReceber -> IncomeIcon
                else -> BillIcon
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Tags(listOfNotNull(
                when {
                    onOpenItems != null -> Tag.Fatura
                    bill.aReceber -> Tag.Receita
                    else -> Tag.Conta
                },
                if (bill.recorrente) Tag.Recorrente else null,
            ))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                bill.amount, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                color = if (bill.aReceber) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
            )
            Box(Modifier.padding(top = 3.dp)) { Chip(bill.status.label, bill.status.fg, bill.status.bg) }
            Text(
                if (estaPaga) "Toque para reabrir" else "Toque para marcar como paga",
                fontSize = 9.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 3.dp),
            )
            onOpenItems?.let {
                Text(
                    "Ver itens", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = it),
                )
            }
        }
        RowMenu(listOfNotNull(
            onOpenItems?.let { MenuAction("Ver itens da fatura", onClick = it) },
            MenuAction("Editar", onClick = onRequestEdit),
            MenuAction("Excluir", destructive = true, onClick = onRequestDelete),
        ))
    }
}

/**
 * Lançamento (manual, importado ou ocorrência de um recorrente) — diferente de [BillRow],
 * que é um compromisso com status (planning.md §8: `Transacao` vs `Conta`). Não tem toggle
 * de pago; o que tiver data futura aparece como "Previsto".
 */
@Composable
private fun TransacaoRow(
    transacao: TransacaoEntity,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onToggleExtra: (TransacaoEntity, Boolean) -> Unit,
    onDelete: (TransacaoEntity) -> Unit,
) {
    val receita = transacao.tipo == "Receita"
    RowCard(modifier, borderColor = FinaiColors.BorderFaint) {
        Avatar(iconFor(transacao))
        Column(modifier = Modifier.weight(1f)) {
            Text(transacao.descricao, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Tags(
                listOfNotNull(
                    when (transacao.tipo) {
                        "Receita" -> Tag.Receita
                        "Transferencia" -> Tag.Transferencia
                        else -> null
                    },
                    if (transacao.recorrente) Tag.Recorrente else null,
                    if (transacao.extra) Tag.Extra else null,
                    if (transacao.origem == "importado") Tag.Importado else null,
                ),
                transacao.categoria.takeIf { transacao.tipo == "Gasto" },
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val cents = transacao.valorCentavos
            val valor = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else com.finai.app.util.formatBrl(cents / 100.0)
            Text(
                if (receita) "+ $valor" else valor, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                color = when (transacao.tipo) {
                    "Receita" -> FinaiColors.EmeraldDark
                    "Transferencia" -> Color(0xFF4F46E5)
                    else -> FinaiColors.TextPrimary
                },
            )
            if (transacao.data.toLocalDate().isAfter(today)) {
                Box(Modifier.padding(top = 3.dp)) { Chip(BillStatus.Expected.label, BillStatus.Expected.fg, BillStatus.Expected.bg) }
            }
        }
        RowMenu(listOfNotNull(
            // Receita já lançada pode entrar/sair da Linha do tempo do ano sem relançar.
            if (receita && transacao.faturaId == null)
                MenuAction(if (transacao.extra) "Desmarcar entrada extra" else "Marcar como entrada extra") { onToggleExtra(transacao, !transacao.extra) }
            else null,
            MenuAction("Excluir", destructive = true) { onDelete(transacao) },
        ))
    }
}
