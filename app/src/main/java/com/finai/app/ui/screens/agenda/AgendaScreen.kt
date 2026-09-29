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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextOverflow
import com.finai.app.ui.components.ActionButton
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.TextAction
import com.finai.app.ui.components.OverflowAction
import com.finai.app.ui.components.OverflowMenu
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
import com.finai.app.ui.theme.themedFill
import com.finai.app.ui.theme.themedText
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
    onToggleMainIncome: (TransacaoEntity, Boolean) -> Unit = { _, _ -> },
    onEditDebt: (Long) -> Unit = {},
    onNewEntry: () -> Unit = {},
    onImport: () -> Unit = {},
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Agenda", style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary)
                Text(
                    "Pagamentos e recebimentos do mês", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tipTarget("agenda.month")
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                    .background(FinaiColors.Surface),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior", tint = FinaiColors.TextSecondary)
                }
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
                        "${MONTH_NAMES_PT[m]} de $y", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = FinaiColors.TextPrimary,
                    )
                }
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês", tint = FinaiColors.TextSecondary)
                }
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
                        note = if (toPayCount == 1) "1 conta" else "$toPayCount contas", valueColor = FinaiColors.TextPrimary, modifier = Modifier.weight(1f),
                    )
                    SummaryTile(
                        label = "Recebimentos", value = "+ " + formatBrl0(toGetCents / 100.0),
                        note = if (toGetCount == 1) "1 entrada" else "$toGetCount entradas", valueColor = FinaiColors.EmeraldDark, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Timeline do mês: tudo que entra ou sai, por dia, na ordem em que acontece. O tipo
        // (dívida, recorrente, fatura…) vira chip do item, não seção da página. Cada dia é uma
        // lista simples com divisor (Fase 7, item 7): cartão fica para resumo e decisão.
        val timeline = agendaTimeline(bills, recurringTransactions, transactions, debtsDue)
        if (timeline.isEmpty()) {
            item {
                com.finai.app.ui.components.FadeInAppear {
                    EmptyStateCard(
                        "Nada lançado para ${MONTH_NAMES_PT[monthIndex].lowercase()}. Lance contas, gastos e receitas com data, " +
                            "ou importe a fatura do cartão, para ver o mês dia a dia.",
                        "Lançar conta", onNewEntry,
                        secondaryLabel = "Importar fatura", onSecondary = onImport,
                    )
                }
            }
        }
        val firstBillKey = timeline.flatMap { it.second }.firstOrNull { it is AgendaEntry.Conta }?.key
        val firstLancamentoKey = timeline.flatMap { it.second }.firstOrNull { it is AgendaEntry.Lancamento }?.key
        timeline.forEach { (date, entries) ->
            item(key = "dia-" + (date?.toString() ?: "sem-dia")) {
                DayHeader(date, entries, today, Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)))
            }
            item(key = "lista-" + (date?.toString() ?: "sem-dia")) {
                Column(
                    modifier = Modifier
                        .animateItemPlacement(finaiTween(FinaiMotion.Standard))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(FinaiColors.Surface),
                ) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(start = 62.dp))
                        val mod = when (entry.key) {
                            firstBillKey -> Modifier.tipTarget("agenda.bill")
                            firstLancamentoKey -> Modifier.tipTarget("agenda.transactions")
                            else -> Modifier
                        }
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
                                entry.transacao, today, mod, onToggleExtra, onToggleMainIncome,
                            ) { pendingDeleteTransaction = entry.transacao }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteTransaction?.let { transacao ->
        ConfirmDeleteDialog(
            title = if (transacao.origem == DebtSchedule.PAYMENT_ORIGIN) "Desfazer pagamento?" else "Excluir lançamento?",
            description = if (transacao.origem == DebtSchedule.PAYMENT_ORIGIN) {
                "Desfaz o pagamento de ${transacao.descricao} (${com.finai.app.util.formatBrl(transacao.valorCentavos / 100.0)}): a parcela volta para a dívida, em aberto."
            } else {
                "${transacao.descricao} · ${com.finai.app.util.formatBrl(transacao.valorCentavos / 100.0)} será removido permanentemente."
            },
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
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, modifier = Modifier.padding(top = 4.dp))
        Text(note, fontSize = 12.5.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 2.dp))
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
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (date == null) {
            Text("SEM DIA DEFINIDO", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextSecondary)
        } else {
            Text(
                "%02d %s".format(date.dayOfMonth, MONTH_ABBREV_PT[date.monthValue - 1].uppercase()),
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
            )
            Text(WEEKDAYS_PT[date.dayOfWeek.value - 1], fontSize = 12.5.sp, color = FinaiColors.TextTertiary)
            if (date == today) Chip("Hoje", FinaiColors.EmeraldDark, FinaiColors.EmeraldSoftBg)
        }
        Box(Modifier.weight(1f))
        if (net != 0L) {
            Text(
                (if (net > 0) "+ " else "− ") + formatBrl0(kotlin.math.abs(net) / 100.0),
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = if (net > 0) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
            )
        }
    }
}

/**
 * Cor com significado (Fase 7, item 7): verde = dinheiro que entra, vermelho = problema
 * (atrasado), âmbar = atenção. O tipo do item (dívida, recorrente, fatura) é informação e
 * fica neutro — antes cada tipo tinha a sua cor (rosa, índigo, violeta) e nada se destacava.
 */
private enum class Tag(val label: String, private val lightFg: Color, private val lightBg: Color) {
    Divida("Dívida", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Recorrente("Recorrente", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Receita("Receita", Color(0xFF047857), Color(0xFFECFDF5)),
    RendaPrincipal("Renda principal", Color(0xFF047857), Color(0xFFECFDF5)),
    Fatura("Fatura", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Conta("Conta", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Extra("Extra", Color(0xFF047857), Color(0xFFECFDF5)),
    Importado("Importado", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    Transferencia("Transferência", Color(0xFF3F3F46), Color(0xFFF4F4F5)),
    ;

    val fg: Color get() = lightFg.themedText
    val bg: Color get() = lightBg.themedFill
}

@Composable
private fun Chip(label: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(tags: List<Tag>, extra: String? = null) {
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { Chip(it.label, it.fg, it.bg) }
        extra?.let { Text(it, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, maxLines = 1) }
    }
}

/** Dica curta sob o status: diz o que o toque na linha faz. */
@Composable
private fun TapHint(text: String) {
    Text(text, fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 3.dp))
}

/** Ícone do item: diz o que ele é (casa, dívida, salário…) em vez de iniciais do nome. */
private class ItemIcon(val glyph: String, private val lightBg: Color, private val lightInk: Color) {
    val bg: Color get() = lightBg.themedFill
    val ink: Color get() = lightInk.themedText
}

private val DebtIcon = ItemIcon("installment", Color(0xFFF4F4F5), Color(0xFF3F3F46))
private val InvoiceIcon = ItemIcon("card", Color(0xFFF4F4F5), Color(0xFF3F3F46))
private val IncomeIcon = ItemIcon("wallet", Color(0xFFECFDF5), Color(0xFF047857))
private val SalaryIcon = ItemIcon("briefcase", Color(0xFFECFDF5), Color(0xFF047857))
private val TransferIcon = ItemIcon("transfer", Color(0xFFF4F4F5), Color(0xFF3F3F46))
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
    t.tipo == "Receita" && PayCycle.isMainIncome(t) -> SalaryIcon
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

/**
 * Uma linha da lista do dia: ícone, nome e selos, valor e status, menu ⋮ (editar/excluir) e,
 * embaixo, a ação frequente como botão de 48 dp — "Marcar paga", "Paguei a parcela" (Fase 7,
 * item 8). Antes a ação era "toque no card" com uma dica de 9 sp.
 */
@Composable
private fun ListRow(
    modifier: Modifier,
    icon: ItemIcon,
    title: String,
    tags: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    menu: List<OverflowAction>,
    action: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, top = 10.dp, bottom = if (action != null) 4.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                tags()
            }
            Column(horizontalAlignment = Alignment.End) { trailing() }
            if (menu.isNotEmpty()) OverflowMenu(menu, contentDescription = "Mais ações para $title") else Box(Modifier.size(12.dp))
        }
        if (action != null) Box(Modifier.padding(start = 42.dp)) { action() }
    }
}

/**
 * Parcela de dívida. Só a mais antiga em aberto é "pagável": marcar uma de um mês futuro
 * pagaria, na prática, a anterior a ela (o modelo guarda só a próxima em aberto). Tocar na
 * linha chama [onPay] direto — preferência do usuário (28/09/2026): "clicou na linha, pagou".
 * Para desfazer, basta excluir o gasto do pagamento, que a parcela volta.
 */
@Composable
private fun DebtInstallmentRow(parcela: DebtInstallment, onPay: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    ListRow(
        modifier = modifier,
        icon = DebtIcon,
        title = parcela.divida.nome,
        tags = { Tags(listOf(Tag.Divida), if (parcela.numero != null) "${parcela.numero} de ${parcela.total}" else null) },
        trailing = {
            Text(formatBrl0(parcela.valorCentavos / 100.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            val status = if (parcela.atrasada) BillStatus.Overdue else BillStatus.Expected
            Box(Modifier.padding(top = 3.dp)) { Chip(if (parcela.atrasada) "Atrasada" else "Prevista", status.fg, status.bg) }
            if (parcela.isNext) TapHint(if (parcela.atrasada) "Toque se já pagou" else "Toque para marcar paga")
        },
        menu = listOfNotNull(
            if (parcela.isNext) OverflowAction("Marcar parcela como paga", onClick = onPay) else null,
            OverflowAction("Editar dívida", onClick = onEdit),
        ),
        onClick = if (parcela.isNext) onPay else null,
    )
}

/**
 * Conta com vencimento — a referência visual da lista (a fatura usa esta linha). Tocar na
 * linha alterna paga/pendente, como antes da Fase 7 (o usuário preferiu assim ao botão);
 * sem isto uma conta atrasada nunca saía dos avisos (planning.md §9/§10).
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
    ListRow(
        modifier = modifier,
        icon = when {
            onOpenItems != null -> InvoiceIcon
            bill.aReceber -> IncomeIcon
            else -> BillIcon
        },
        title = bill.name,
        tags = {
            Tags(listOfNotNull(
                when {
                    onOpenItems != null -> Tag.Fatura
                    bill.aReceber -> Tag.Receita
                    else -> Tag.Conta
                },
                if (bill.recorrente) Tag.Recorrente else null,
            ))
        },
        trailing = {
            Text(
                bill.amount, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (bill.aReceber) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
            )
            Box(Modifier.padding(top = 3.dp)) { Chip(bill.status.label, bill.status.fg, bill.status.bg) }
            TapHint(
                when {
                    estaPaga -> "Toque para reabrir"
                    bill.aReceber -> "Toque se já recebeu"
                    else -> "Toque para marcar paga"
                },
            )
        },
        menu = listOfNotNull(
            onOpenItems?.let { OverflowAction("Ver itens da fatura", onClick = it) },
            OverflowAction("Editar", onClick = onRequestEdit),
            OverflowAction("Excluir", destructive = true, onClick = onRequestDelete),
        ),
        action = onOpenItems?.let { open -> { TextAction("Ver itens da fatura ›", open) } },
        onClick = { onTogglePaga(bill.id, !estaPaga) },
    )
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
    onToggleMainIncome: (TransacaoEntity, Boolean) -> Unit,
    onDelete: (TransacaoEntity) -> Unit,
) {
    val receita = transacao.tipo == "Receita"
    val principal = receita && PayCycle.isMainIncome(transacao)
    ListRow(
        modifier = modifier,
        icon = iconFor(transacao),
        title = transacao.descricao,
        tags = {
            Tags(
                listOfNotNull(
                    when {
                        principal -> Tag.RendaPrincipal
                        receita -> Tag.Receita
                        transacao.tipo == "Transferencia" -> Tag.Transferencia
                        else -> null
                    },
                    if (transacao.recorrente) Tag.Recorrente else null,
                    if (transacao.extra) Tag.Extra else null,
                    if (transacao.origem == "importado") Tag.Importado else null,
                ),
                transacao.categoria.takeIf { transacao.tipo == "Gasto" },
            )
        },
        trailing = {
            val cents = transacao.valorCentavos
            val valor = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else com.finai.app.util.formatBrl(cents / 100.0)
            Text(
                if (receita) "+ $valor" else valor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (receita) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
            )
            if (transacao.data.toLocalDate().isAfter(today)) {
                Box(Modifier.padding(top = 3.dp)) { Chip(BillStatus.Expected.label, BillStatus.Expected.fg, BillStatus.Expected.bg) }
            }
        },
        menu = listOfNotNull(
            // Renda principal define o ciclo até o próximo salário (Fase 7, item 5).
            if (receita && transacao.faturaId == null && !transacao.extra)
                OverflowAction(if (transacao.rendaPrincipal) "Desmarcar renda principal" else "Marcar como renda principal") {
                    onToggleMainIncome(transacao, !transacao.rendaPrincipal)
                }
            else null,
            // Receita já lançada pode entrar/sair da Linha do tempo do ano sem relançar.
            if (receita && transacao.faturaId == null)
                OverflowAction(if (transacao.extra) "Desmarcar entrada extra" else "Marcar como entrada extra") { onToggleExtra(transacao, !transacao.extra) }
            else null,
            OverflowAction(if (transacao.origem == DebtSchedule.PAYMENT_ORIGIN) "Desfazer pagamento" else "Excluir", destructive = true) { onDelete(transacao) },
        ),
    )
}
