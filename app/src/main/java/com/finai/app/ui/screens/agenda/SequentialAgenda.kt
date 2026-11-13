package com.finai.app.ui.screens.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.AgendaAhead
import com.finai.app.domain.AgendaBalance
import com.finai.app.domain.CycleSource
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.MovementStatus
import com.finai.app.domain.PayCycle
import com.finai.app.domain.TimelineMovement
import com.finai.app.domain.TransactionType
import com.finai.app.domain.isAdjustment
import com.finai.app.domain.isGoalContribution
import com.finai.app.ui.components.MoneyRow
import com.finai.app.ui.components.MoneyRowBackground
import com.finai.app.ui.components.MoneyRowState
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/** "A seguir" × <mês>, no topo da Agenda (quadro `v2/Agenda`). A aba do mês leva o nome do mês na tela. */
@Composable
internal fun AgendaModeTabs(sequential: Boolean, monthLabel: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(FinaiColors.SurfaceMuted).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("A seguir" to true, monthLabel to false).forEach { (label, value) ->
            val selected = value == sequential
            val bg by animateColorAsState(if (selected) FinaiColors.InkStrong else Color.Transparent, finaiTween(FinaiMotion.Quick), label = "agendaTabBg")
            val fg by animateColorAsState(if (selected) FinaiColors.OnInkStrong else FinaiColors.TextTertiary, finaiTween(FinaiMotion.Quick), label = "agendaTabFg")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(bg)
                    .selectable(selected, role = Role.Tab, onClick = { onChange(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * A Agenda "A seguir" (fase 4): saldo em conta depois de cada movimento, sem zerar no salário.
 * Linhas vêm prontas de [AgendaAhead.rows]; aqui só se desenha. Tocar numa conta paga ou reabre;
 * na parcela mais antiga em aberto, pede confirmação e paga; num lançamento, abre a folha dele.
 * Segurar a conta abre as outras ações (editar, excluir, itens da fatura).
 */
internal fun LazyListScope.sequentialAgendaItems(
    balance: AgendaBalance?,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onOpenTransaction: (TransacaoEntity) -> Unit,
    onOpenConta: (ContaEntity) -> Unit,
    invoiceItemCount: (contaId: Long) -> Int,
) {
    item(key = "seq-intro") {
        Text(
            "Saldo em conta depois de cada movimento. Não zera no salário: a sobra continua. Toque para pagar; toque de novo para reabrir.",
            fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextMuted,
        )
    }
    if (balance == null) {
        item(key = "seq-vazio") {
            com.finai.app.ui.components.EmptyStateCard(
                "Não foi possível montar a Agenda agora. Tente reabrir o app.", actionLabel = null, onAction = null,
            )
        }
        return
    }
    if (balance.past.isNotEmpty()) {
        item(key = "seq-anteriores") { PastBlock(balance.past, balance.today, onToggleContaPaga, onOpenTransaction, onOpenConta, invoiceItemCount) }
    }
    val rows = AgendaAhead.rows(balance)
    rows.forEachIndexed { index, row ->
        when (row) {
            is AgendaAhead.Row.Today -> item(key = "seq-hoje") {
                DayLabel("HOJE · ${weekday(row.date)} ${dm(row.date)}", "em conta ${signed(row.accountCents)}", FinaiColors.EmeraldDark)
            }
            is AgendaAhead.Row.MonthStart -> item(key = "seq-mes-${row.month}") {
                DayLabel(MONTH_NAMES_PT[row.month.monthValue - 1].uppercase() + if (row.month.year != balance.today.year) " ${row.month.year}" else "",
                    "começa com ${signed(row.startCents)} · nada zera", FinaiColors.TextTertiary)
            }
            is AgendaAhead.Row.IncomeDay -> item(key = "seq-renda-$index-${row.date}") {
                DayLabel("${weekday(row.date)} ${dm(row.date)}", row.label, FinaiColors.EmeraldDark)
            }
            is AgendaAhead.Row.Move -> item(key = "seq-$index-${row.movement.date}-${row.movement.descricao}") {
                AheadRow(row, onToggleContaPaga, onRequestPayInstallment, onOpenTransaction, onOpenConta, invoiceItemCount)
            }
        }
    }
    if (rows.none { it is AgendaAhead.Row.Move }) {
        item(key = "seq-nada") {
            com.finai.app.ui.components.EmptyStateCard(
                "Nada previsto daqui para frente. Lance uma conta ou uma entrada para ver o saldo seguir.", actionLabel = null, onAction = null,
            )
        }
    }
}

/** Cabeçalho de dia/mês: rótulo em caixa alta à esquerda e uma nota à direita. */
@Composable
private fun DayLabel(label: String, note: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp, color = color, modifier = Modifier.weight(1f))
        Text(note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun AheadRow(
    row: AgendaAhead.Row.Move,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onOpenTransaction: (TransacaoEntity) -> Unit,
    onOpenConta: (ContaEntity) -> Unit,
    invoiceItemCount: (contaId: Long) -> Int,
) {
    val m = row.movement
    val income = m.cents > 0
    val after = when (row.kind) {
        AgendaAhead.MoveKind.Counted -> m.balanceAfterCents?.let { (if (row.lowest) "menor saldo " else "fica ") + signed(it) }
        AgendaAhead.MoveKind.SettledAhead -> if (income) "já entrou no saldo" else "já saiu do saldo"
        AgendaAhead.MoveKind.Unconfirmed -> "fora do saldo"
    }
    val afterColor = when {
        row.kind != AgendaAhead.MoveKind.Counted -> null
        (m.balanceAfterCents ?: 0) < 0 -> FinaiColors.RoseDark
        row.lowest -> FinaiColors.AmberDark
        else -> null
    }
    MovementRow(
        m, invoiceItemCount, onToggleContaPaga, onRequestPayInstallment, onOpenTransaction, onOpenConta,
        balanceAfter = after, balanceAfterColor = afterColor,
        background = when {
            row.lowest -> MoneyRowBackground.Low
            income && row.kind == AgendaAhead.MoveKind.Counted && !isAdjustment(m) -> MoneyRowBackground.Income
            else -> MoneyRowBackground.Card
        },
        unconfirmed = row.kind == AgendaAhead.MoveKind.Unconfirmed,
    )
}

/**
 * Uma linha de movimento com [MoneyRow] e as ações da Agenda (as mesmas da aba do mês): conta
 * alterna paga/pendente, parcela mais antiga em aberto paga com confirmação, lançamento abre a
 * folha dele. Segurar a conta abre editar/excluir/itens da fatura.
 */
@Composable
internal fun MovementRow(
    m: TimelineMovement,
    invoiceItemCount: (contaId: Long) -> Int,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onOpenTransaction: (TransacaoEntity) -> Unit,
    onOpenConta: (ContaEntity) -> Unit,
    modifier: Modifier = Modifier,
    balanceAfter: String? = null,
    balanceAfterColor: Color? = null,
    background: MoneyRowBackground = MoneyRowBackground.Card,
    unconfirmed: Boolean = false,
) {
    val conta = (m.source as? CycleSource.Conta)?.conta
    val parcela = (m.source as? CycleSource.Parcela)?.parcela
    // Salário estimado de uma projeção tem id negativo: não existe no banco para editar.
    val lancamento = (m.source as? CycleSource.Lancamento)?.transacao?.takeIf { it.id > 0 }
    val paid = conta?.status == "pago"
    val onClick: (() -> Unit)? = when {
        conta != null -> { { onToggleContaPaga(conta.id, !paid) } }
        parcela != null && parcela.isNext -> { { onRequestPayInstallment(m) } }
        lancamento != null -> { { onOpenTransaction(lancamento) } }
        else -> null
    }
    val income = m.cents > 0
    MoneyRow(
        title = titleOf(m),
        amount = signedMoney(m),
        avatar = if (income && !isAdjustment(m)) "R$" else titleOf(m).take(2),
        modifier = modifier,
        subtitle = subtitleOf(m, conta?.let { invoiceItemCount(it.id) } ?: 0, unconfirmed),
        state = stateOf(m),
        income = income && !isAdjustment(m),
        balanceAfter = balanceAfter,
        balanceAfterColor = balanceAfterColor,
        background = background,
        onClick = onClick,
        onClickLabel = when {
            conta != null && paid -> "reabrir"
            conta != null -> if (conta.tipo == "a_receber") "marcar como recebida" else "marcar como paga"
            parcela != null -> "registrar pagamento da parcela"
            else -> "ver ações"
        },
        onLongClick = conta?.let { { onOpenConta(it) } },
        onLongClickLabel = conta?.let { "editar, excluir ou ver itens" },
    )
}

private fun stateOf(m: TimelineMovement): MoneyRowState {
    val conta = (m.source as? CycleSource.Conta)?.conta
    return when {
        conta != null && conta.status == "pago" -> if (conta.tipo == "a_receber") MoneyRowState.Received else MoneyRowState.Paid
        conta != null || m.source is CycleSource.Parcela -> MoneyRowState.Pending
        m.status == MovementStatus.Expected || m.status == MovementStatus.Estimated -> MoneyRowState.Pending
        else -> MoneyRowState.Plain
    }
}

/** Já pago ou passado no mês: recolhido; cada linha ainda toca para reabrir/abrir a folha. */
@Composable
private fun PastBlock(
    past: List<TimelineMovement>,
    today: LocalDate,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onOpenTransaction: (TransacaoEntity) -> Unit,
    onOpenConta: (ContaEntity) -> Unit,
    invoiceItemCount: (contaId: Long) -> Int,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val reduced = com.finai.app.ui.theme.rememberReducedMotion()
    val rotation by animateFloatAsState(if (open) 180f else 0f, if (reduced) snap() else tween(200), label = "pastChevron")
    val shape = RoundedCornerShape(16.dp)
    val monthName = MONTH_NAMES_PT[today.monthValue - 1].lowercase()
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, FinaiColors.BorderHairline, shape).background(FinaiColors.Surface)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Recolher movimentações anteriores" else "Ver movimentações anteriores") { open = !open }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(FinaiColors.SurfaceMuted), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = FinaiColors.TextTertiary, modifier = Modifier.size(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Movimentações anteriores", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary)
                Text("${past.size} em $monthName até hoje · toque para ver", fontSize = 12.sp, color = FinaiColors.TextTertiary)
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = FinaiColors.TextTertiary, modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(open, enter = if (reduced) EnterTransition.None else expandVertically(tween(200)), exit = if (reduced) ExitTransition.None else shrinkVertically(tween(200))) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp)
                past.forEach { m ->
                    MovementRow(
                        m, invoiceItemCount, onToggleContaPaga, onRequestPayInstallment = {}, onOpenTransaction, onOpenConta,
                        background = MoneyRowBackground.None,
                    )
                }
            }
        }
    }
}

internal fun isAdjustment(m: TimelineMovement) = (m.source as? CycleSource.Lancamento)?.transacao?.isAdjustment == true

internal fun titleOf(m: TimelineMovement): String {
    val t = (m.source as? CycleSource.Lancamento)?.transacao
    val parcela = (m.source as? CycleSource.Parcela)?.parcela
    val base = m.descricao.ifBlank { t?.categoria ?: "Movimento" }
    return base + (parcela?.numero?.let { " · parcela $it de ${parcela.total}" } ?: "")
}

/** "dd/MM · o que é": a informação que o nome e o valor não dizem (quadro `v2/Agenda`). */
internal fun subtitleOf(m: TimelineMovement, invoiceItems: Int, unconfirmed: Boolean = false): String {
    val conta = (m.source as? CycleSource.Conta)?.conta
    val parcela = (m.source as? CycleSource.Parcela)?.parcela
    val t = (m.source as? CycleSource.Lancamento)?.transacao
    val date = when {
        unconfirmed -> "prevista ${dm(m.movedFrom ?: m.date)}"
        m.movedFrom != null && m.status == MovementStatus.Overdue -> "venceu ${dm(m.movedFrom)}"
        parcela != null && parcela.vencimento == null -> "sem dia definido"
        else -> dm(m.date)
    }
    val kind = when {
        unconfirmed -> "não entra no saldo até receber"
        conta != null && invoiceItems > 0 -> "cartão · $invoiceItems ${if (invoiceItems == 1) "compra" else "compras"}"
        conta != null -> if (conta.tipo == "a_receber") "a receber" else "conta"
        parcela != null -> "dívida"
        t == null -> null
        t.isAdjustment -> "conferido com o banco"
        t.isGoalContribution -> "aporte"
        t.tipo == TransactionType.Transferencia.name -> t.contaDestino?.let { "para $it" } ?: "transferência"
        t.tipo == TransactionType.Receita.name -> when {
            t.extra -> "entrada extra"
            t.competenciaDeslocamento != 0 -> "competência " + MONTH_ABBREV_PT[m.date.plusMonths(t.competenciaDeslocamento.toLong()).monthValue - 1].lowercase()
            PayCycle.isMainIncome(t) || t.rendaParte != null -> "renda"
            else -> "entrada"
        }
        else -> t.categoria
    }
    val tag = when {
        m.status == MovementStatus.Overdue -> "atrasada"
        m.status == MovementStatus.Estimated -> "estimado"
        t?.origem == "importado" -> "importado"
        t?.origem == com.finai.app.domain.capture.NOTIFICATION_ORIGIN -> "notificação"
        t?.recorrente == true && t.faturaId == null -> "todo mês"
        else -> null
    }
    return listOfNotNull(date, kind, tag).joinToString(" · ")
}

private val WEEKDAYS_SHORT = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
private fun weekday(d: LocalDate) = WEEKDAYS_SHORT[d.dayOfWeek.value - 1]
private fun brl(cents: Long) = formatBrl0(cents / 100.0)
private fun signed(cents: Long) = if (cents < 0) "−" + brl(-cents) else brl(cents)

/** "+R$ 2.400" entra; "−R$ 1.320" sai (como no quadro). */
internal fun signedMoney(m: TimelineMovement): String {
    // Transferência entre contas é neutra (0 no saldo): mostra o valor dela, sem sinal.
    if (m.cents == 0L) return money(kotlin.math.abs((m.source as? CycleSource.Lancamento)?.transacao?.valorCentavos ?: 0L))
    return (if (m.cents > 0) "+" else "−") + money(kotlin.math.abs(m.cents))
}

internal fun money(cents: Long) = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else formatBrl(cents / 100.0)
internal fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
