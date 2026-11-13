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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.finai.app.domain.freeCashSign
import com.finai.app.domain.isAdjustment
import androidx.compose.foundation.gestures.scrollBy
import com.finai.app.ui.components.EntryGlyph
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.components.PageTitle
import androidx.compose.foundation.lazy.LazyListState
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.themedFill
import com.finai.app.ui.theme.themedText
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.heading
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.domain.CycleSource
import com.finai.app.domain.TimelineMovement
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun AgendaScreen(
    listState: LazyListState,
    monthIndex: Int,
    year: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    /** Movimentos do mês na tela, em ordem ([com.finai.app.domain.CashFlowTimeline.monthMovements] + transferências). */
    monthMovements: List<TimelineMovement>,
    /** Entra / Sai / Sobra do mês ([com.finai.app.domain.MonthCashFlow]; Sobra = o antigo balanço da Início). */
    entraCents: Long,
    saiCents: Long,
    sobraCents: Long,
    competenceShifted: Boolean = false,
    /** Compras dentro da fatura de uma conta (0 = conta comum). */
    invoiceItemCount: (contaId: Long) -> Int = { 0 },
    onPayInstallment: (Long) -> Unit,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onDeleteTransaction: (TransacaoEntity) -> Unit,
    onDeleteConta: (ContaEntity) -> Unit,
    onEditConta: (Long) -> Unit,
    onOpenInvoice: (contaId: Long) -> Unit = {},
    onToggleExtra: (TransacaoEntity, Boolean) -> Unit = { _, _ -> },
    onToggleMainIncome: (TransacaoEntity, Boolean) -> Unit = { _, _ -> },
    onEditDebt: (Long) -> Unit = {},
    onNewEntry: () -> Unit = {},
    onImport: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    /** "A seguir" (padrão) em vez da aba do mês. */
    sequential: Boolean = false,
    onSequentialChange: (Boolean) -> Unit = {},
    /** Saldo corrente contínuo da "A seguir" ([com.finai.app.domain.AgendaBalance]). */
    agendaBalance: com.finai.app.domain.AgendaBalance? = null,
    /** Item recém-lançado ("Ver na agenda" do lançamento): a lista rola até ele e o destaca. */
    highlight: com.finai.app.domain.AgendaHighlight? = null,
    onHighlightShown: () -> Unit = {},
    /** Registro gravado de uma série recorrente, pelo id (a linha mostra a ocorrência do mês). */
    seriesOf: (Long) -> TransacaoEntity? = { null },
    /** Abre o formulário do lançamento preenchido com a ocorrência tocada. */
    onEditTransaction: (TransacaoEntity) -> Unit = {},
    onDeleteOccurrence: (TransacaoEntity, com.finai.app.domain.EditScope) -> Unit = { _, _ -> },
    onAdjustInstallmentDate: suspend (dividaId: Long, month: YearMonth, date: LocalDate) -> Unit = { _, _, _ -> },
    onChangeInstallmentDay: suspend (dividaId: Long, month: YearMonth, day: Int) -> Unit = { _, _, _ -> },
) {
    var pendingDeleteTransaction by remember { mutableStateOf<TransacaoEntity?>(null) }
    /** Lançamento tocado: a folha com informações e ações. */
    var sheetTransaction by remember { mutableStateOf<TransacaoEntity?>(null) }
    /** Parcela tocada e o mês dela. */
    var sheetInstallment by remember { mutableStateOf<Pair<DebtInstallment, YearMonth>?>(null) }
    /** Conta segurada: editar, excluir, itens da fatura. */
    var sheetConta by remember { mutableStateOf<ContaEntity?>(null) }
    var pendingDeleteConta by remember { mutableStateOf<ContaEntity?>(null) }

    val monthKey = year * 12 + monthIndex
    var previousMonthKey by remember { mutableStateOf(monthKey) }
    val monthForward = monthKey >= previousMonthKey
    androidx.compose.runtime.SideEffect { previousMonthKey = monthKey }
    val reducedMotion = com.finai.app.ui.theme.rememberReducedMotion()
    val quickMillis = if (reducedMotion) 0 else FinaiMotion.Quick

    // Parcela: a folha dela (pagar, editar a data, mudar o dia), como antes (decisão de 11/10).
    val openInstallment: (TimelineMovement) -> Unit = { m ->
        (m.source as? CycleSource.Parcela)?.parcela?.let { p ->
            sheetInstallment = p to YearMonth.from(p.vencimento ?: m.movedFrom ?: m.date)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            PageTitle("Agenda", listState, subtitle = if (sequential) "O que ainda vai acontecer" else "Tudo o que entra e sai no mês")
        }

        item(key = "agenda-modo") {
            Row(
                Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AgendaModeTabs(sequential, MONTH_NAMES_PT[monthIndex] + if (year != today.year) " $year" else "", onSequentialChange, Modifier.weight(1f))
                AddEntryButton(onNewEntry)
            }
        }

        if (sequential) {
            sequentialAgendaItems(
                balance = agendaBalance,
                onToggleContaPaga = onToggleContaPaga,
                onRequestPayInstallment = openInstallment,
                onOpenTransaction = { sheetTransaction = it },
                onOpenConta = { sheetConta = it },
                invoiceItemCount = invoiceItemCount,
            )
        } else {
            item(key = "mes-nav") {
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
                        Text("${MONTH_NAMES_PT[m]} de $y", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    }
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês", tint = FinaiColors.TextSecondary)
                    }
                }
            }

            item(key = "mes-numeros") {
                Column {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonthNumber("Entra", "+" + money(entraCents), FinaiColors.EmeraldDark, Modifier.weight(1f))
                        MonthNumber("Sai", "−" + money(saiCents), FinaiColors.TextPrimary, Modifier.weight(1f))
                        MonthNumber(
                            "Sobra", (if (sobraCents < 0) "−" else "") + money(kotlin.math.abs(sobraCents)),
                            if (sobraCents < 0) FinaiColors.RoseDark else FinaiColors.TextPrimary, Modifier.weight(1f),
                        )
                    }
                    if (competenceShifted) {
                        Text(
                            "Salário conta no mês trabalhado, mesmo quando cai no mês seguinte.",
                            fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        )
                    }
                }
            }

            if (monthMovements.isEmpty()) {
                item(key = "mes-vazio") {
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

            val highlightKeys = highlight?.keys.orEmpty()
            val keyed = monthMovements.map { movementKey(it) to it }
            val highlightKey = keyed.firstOrNull { it.first.first in highlightKeys }?.let { rowKey(it.first) }
            if (highlightKey != null) item(key = "destaque-efeito") {
                // Rola até o item: as linhas fora da tela ainda não existem, então anda de tela em tela.
                androidx.compose.runtime.LaunchedEffect(highlightKey) {
                    for (step in 0 until 40) {
                        val found = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == highlightKey }
                        if (found != null) {
                            if (reducedMotion) listState.scrollToItem(found.index, -80) else listState.animateScrollToItem(found.index, -80)
                            break
                        }
                        if (!listState.canScrollForward) break
                        listState.scrollBy(listState.layoutInfo.viewportSize.height * .8f)
                    }
                    kotlinx.coroutines.delay(2600)
                    onHighlightShown()
                }
            }

            val (passed, ahead) = keyed.partition { it.second.date.isBefore(today) }
            val firstBillKey = keyed.firstOrNull { it.second.source is CycleSource.Conta }?.first
            val firstLancamentoKey = keyed.firstOrNull { it.second.source is CycleSource.Lancamento }?.first
            listOf("JÁ PASSOU" to passed, "A PARTIR DE HOJE" to ahead).forEach { (label, group) ->
                if (group.isEmpty()) return@forEach
                item(key = "grupo-$label") {
                    GroupHeader(label, "${group.size} ${if (group.size == 1) "item" else "itens"}")
                }
                group.forEach { (key, m) ->
                    item(key = rowKey(key)) {
                        val mod = when (key) {
                            firstBillKey -> Modifier.tipTarget("agenda.bill")
                            firstLancamentoKey -> Modifier.tipTarget("agenda.transactions")
                            else -> Modifier
                        }.then(if (key.first in highlightKeys) Modifier.highlightFlash(reducedMotion) else Modifier)
                        MovementRow(
                            m, invoiceItemCount, onToggleContaPaga,
                            onRequestPayInstallment = openInstallment,
                            onOpenTransaction = { sheetTransaction = it },
                            onOpenConta = { sheetConta = it },
                            modifier = mod,
                        )
                    }
                }
            }
        }
    }

    sheetTransaction?.let { t ->
        TransactionSheet(
            transacao = t, series = seriesOf(t.id), today = today,
            onDismiss = { sheetTransaction = null },
            onEdit = { sheetTransaction = null; onEditTransaction(t) },
            onToggleMainIncome = onToggleMainIncome, onToggleExtra = onToggleExtra,
            onDelete = { pendingDeleteTransaction = it },
            onDeleteOccurrence = onDeleteOccurrence,
        )
    }

    sheetInstallment?.let { (parcela, month) ->
        InstallmentSheet(
            parcela = parcela, month = month, today = today,
            onDismiss = { sheetInstallment = null },
            onPay = { onPayInstallment(parcela.divida.id) },
            onEditDebt = { onEditDebt(parcela.divida.id) },
            onSaveDate = { date -> onAdjustInstallmentDate(parcela.divida.id, month, date) },
            onChangeDayFrom = { day -> onChangeInstallmentDay(parcela.divida.id, month, day) },
        )
    }

    sheetConta?.let { conta ->
        BillSheet(
            conta = conta, invoiceItems = invoiceItemCount(conta.id), today = today,
            onDismiss = { sheetConta = null },
            onTogglePaid = { onToggleContaPaga(conta.id, conta.status != "pago") },
            onOpenItems = { onOpenInvoice(conta.id) },
            onEdit = { onEditConta(conta.id) },
            onDelete = { pendingDeleteConta = conta },
        )
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

    pendingDeleteConta?.let { conta ->
        ConfirmDeleteDialog(
            title = "Excluir conta?",
            description = "${conta.nome} · ${com.finai.app.util.formatBrl(conta.valorCentavos / 100.0)} será removida permanentemente.",
            onDismiss = { pendingDeleteConta = null },
            onConfirm = { onDeleteConta(conta); pendingDeleteConta = null },
        )
    }
}

/** Chave da linha, a mesma de [com.finai.app.domain.AgendaHighlight.keys], e a data (ocorrências de um recorrente). */
private fun movementKey(m: TimelineMovement): Pair<String, LocalDate> {
    val key = when (val s = m.source) {
        is CycleSource.Conta -> "conta-" + s.conta.id
        is CycleSource.Parcela -> "divida-" + s.parcela.divida.id
        is CycleSource.Lancamento -> (if (s.transacao.recorrente) "transacao-rec-" else "transacao-") + s.transacao.id
        null -> "mov-" + m.descricao
    }
    return key to m.date
}

private fun rowKey(k: Pair<String, LocalDate>) = "linha-${k.first}-${k.second}"

/** Um dos três números do mês (quadro `v2/Agenda`, aba do mês). */
@Composable
private fun MonthNumber(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(14.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 12.sp, color = FinaiColors.TextMuted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun GroupHeader(label: String, note: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp, color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f))
        Text(note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
    }
}

/** Fundo verde que aparece e se desfaz em ~2,4 s no item recém-lançado. Sem animação: fica fixo. */
@Composable
private fun Modifier.highlightFlash(reduced: Boolean): Modifier {
    val alpha = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        alpha.animateTo(1f, tween(300))
        kotlinx.coroutines.delay(1400)
        alpha.animateTo(0f, tween(700))
    }
    return this.background(FinaiColors.EmeraldSoftBg.copy(alpha = alpha.value))
}

/** "+" da Agenda: abre o mesmo lançamento do "+" central, já no mês que está na tela. */
@Composable
private fun AddEntryButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(FinaiColors.InkStrong)
            .clickable(onClickLabel = "Adicionar lançamento", role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = "Adicionar lançamento" },
        contentAlignment = Alignment.Center,
    ) {
        EntryGlyph("plus", FinaiColors.OnInkStrong, size = 20)
    }
}
