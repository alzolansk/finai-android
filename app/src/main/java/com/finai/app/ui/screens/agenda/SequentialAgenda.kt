package com.finai.app.ui.screens.agenda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextDecoration
import com.finai.app.domain.AgendaBalance
import com.finai.app.domain.isAdjustment
import com.finai.app.domain.BalanceTone
import com.finai.app.domain.balanceTone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.model.BillStatus
import com.finai.app.domain.CashFlowTimeline
import com.finai.app.domain.CycleSource
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.MovementStatus
import com.finai.app.domain.PayCycle
import com.finai.app.domain.SegmentKind
import com.finai.app.domain.TimelineMovement
import com.finai.app.domain.TimelineSegment
import com.finai.app.ui.components.ActionButton
import com.finai.app.ui.components.TextAction
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/** Mensal × Sequencial, no topo da Agenda. */
@Composable
internal fun AgendaModeTabs(sequential: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(FinaiColors.SurfaceMuted).padding(3.dp),
    ) {
        listOf("Mensal" to false, "A seguir" to true).forEach { (label, value) ->
            val selected = value == sequential
            val bg by animateColorAsState(if (selected) FinaiColors.InkStrong else Color.Transparent, finaiTween(FinaiMotion.Quick), label = "agendaTabBg")
            val fg by animateColorAsState(if (selected) FinaiColors.OnInkStrong else FinaiColors.TextTertiary, finaiTween(FinaiMotion.Quick), label = "agendaTabFg")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(bg)
                    .selectable(selected, role = Role.Tab, onClick = { onChange(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * A Agenda com foco no futuro (ref. 2a): o que já aconteceu no mês fica num bloco recolhido no
 * topo e o que ainda vai acontecer vem em ordem, cada item com o saldo corrente depois dele. O
 * saldo atravessa os meses sem recomeçar. Os números vêm prontos de [AgendaBalance] (as mesmas
 * regras do mês que a Agenda mensal usa); aqui só se desenha.
 */
internal fun LazyListScope.sequentialAgendaItems(
    balance: AgendaBalance?,
    today: LocalDate,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    /** Toque num lançamento: a mesma folha de ações da Agenda mensal. */
    onOpenTransaction: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit = {},
) {
    if (balance == null) {
        item(key = "seq-vazio") {
            com.finai.app.ui.components.EmptyStateCard(
                "Não foi possível montar a Agenda agora. Tente reabrir o app.", actionLabel = null, onAction = null,
            )
        }
        return
    }
    item(key = "seq-resumo") { BalanceHeader(balance, today) }
    if (balance.past.isNotEmpty()) {
        item(key = "seq-anteriores") { PastBlock(balance.past, today) }
    }
    if (balance.future.isEmpty()) {
        item(key = "seq-nada") {
            com.finai.app.ui.components.EmptyStateCard(
                "Nada previsto daqui para frente. Lance uma conta ou uma entrada para ver o saldo seguir.", actionLabel = null, onAction = null,
            )
        }
        return
    }
    val firstMonth = YearMonth.from(balance.future.first().date)
    var lastMonth: YearMonth? = null
    balance.future.forEachIndexed { index, m ->
        val month = YearMonth.from(m.date)
        if (month != lastMonth) {
            if (month != firstMonth) {
                item(key = "seq-mes-$month") { MonthDivider(month, balance.monthStart[month] ?: balance.todayCents) }
            }
            lastMonth = month
        }
        item(key = "seq-$index-${m.date}-${m.descricao}") {
            FutureRow(m, onToggleContaPaga, onRequestPayInstallment, onOpenTransaction)
        }
    }
}

/** "Saldo hoje" → "Fim de <mês seguinte>", da mesma projeção dos itens abaixo. */
@Composable
private fun BalanceHeader(balance: AgendaBalance, today: LocalDate) {
    val nextMonth = YearMonth.from(today).plusMonths(1)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.InkStrong).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("SALDO HOJE · ${dm(today)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.OnInkStrong.copy(alpha = 0.6f))
            Text(signed(balance.todayCents), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.OnInkStrong, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FinaiColors.OnInkStrong.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                "FIM DE ${MONTH_NAMES_PT[nextMonth.monthValue - 1].uppercase()}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = FinaiColors.OnInkStrong.copy(alpha = 0.6f), maxLines = 1,
            )
            Text(
                signed(balance.endOfNextMonthCents), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                color = if (balance.endOfNextMonthCents < 0) FinaiColors.Rose else FinaiColors.Emerald, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** Já pago ou passado no mês: recolhido, riscado, com data e valor em cinza (entradas em verde). */
@Composable
private fun PastBlock(past: List<TimelineMovement>, today: LocalDate) {
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
                Text("${past.size} ${if (past.size == 1) "paga" else "pagas"} em $monthName · toque para ver", fontSize = 12.sp, color = FinaiColors.TextTertiary)
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = FinaiColors.TextTertiary, modifier = Modifier.size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(open, enter = if (reduced) EnterTransition.None else expandVertically(tween(200)), exit = if (reduced) ExitTransition.None else shrinkVertically(tween(200))) {
            Column {
                HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp)
                past.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dm(m.date), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted, modifier = Modifier.width(48.dp))
                        Text(
                            title(m), fontSize = 13.sp, color = FinaiColors.TextTertiary, textDecoration = TextDecoration.LineThrough,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Text(
                            signedMoney(m),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (m.cents > 0) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** Mês seguinte: o saldo atravessa a virada, e o divisor diz com quanto o mês começa. */
@Composable
private fun MonthDivider(month: YearMonth, startCents: Long) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(MONTH_NAMES_PT[month.monthValue - 1].uppercase() + " " + month.year, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
        Box(Modifier.weight(1f).height(1.dp).background(FinaiColors.BorderSubtle))
        Text("começa com ${signed(startCents)}", fontSize = 12.sp, color = FinaiColors.TextTertiary, maxLines = 1)
    }
}

@Composable
private fun FutureRow(
    m: TimelineMovement,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onOpenTransaction: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit,
) {
    val conta = (m.source as? CycleSource.Conta)?.conta
    val parcela = (m.source as? CycleSource.Parcela)?.parcela
    // Salário estimado de uma projeção tem id negativo: não existe no banco para editar.
    val lancamento = (m.source as? CycleSource.Lancamento)?.transacao?.takeIf { it.id > 0 }
    // Mesmas ações da Agenda mensal: tocar na conta alterna paga/pendente; na parcela mais antiga em
    // aberto, registra o pagamento (com confirmação); no lançamento, abre a folha de ações.
    val onClick: (() -> Unit)? = when {
        conta != null -> { { onToggleContaPaga(conta.id, conta.status != "pago") } }
        parcela != null && parcela.isNext -> { { onRequestPayInstallment(m) } }
        lancamento != null -> { { onOpenTransaction(lancamento) } }
        else -> null
    }
    val entrada = m.cents > 0
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, FinaiColors.BorderHairline, shape).background(FinaiColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(if (entrada) FinaiColors.EmeraldSoftBg else FinaiColors.SurfaceMuted),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("%02d".format(m.date.dayOfMonth), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (entrada) FinaiColors.EmeraldDark else FinaiColors.TextPrimary)
            Text(MONTH_ABBREV_PT[m.date.monthValue - 1].uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary)
        }
        Column(Modifier.weight(1f)) {
            Text(title(m), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.status == MovementStatus.Overdue || m.status == MovementStatus.DueToday || m.status == MovementStatus.Estimated) {
                    StatusChip(m.status.label, styleOf(m.status))
                }
                detail(m)?.let { Text(it, fontSize = 12.sp, color = FinaiColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedMoney(m), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (entrada) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
            )
            m.balanceAfterCents?.let {
                Text(
                    "saldo ${signed(it)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
                    color = when (balanceTone(it)) {
                        BalanceTone.Negative -> FinaiColors.RoseDark
                        BalanceTone.Low -> FinaiColors.AmberDark
                        BalanceTone.Normal -> FinaiColors.TextTertiary
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, style: BillStatus) {
    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(style.bg).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = style.fg, maxLines = 1)
    }
}

/** Mesmas cores dos selos da Agenda mensal. */
private fun styleOf(status: MovementStatus): BillStatus = when (status) {
    MovementStatus.Done, MovementStatus.Paid, MovementStatus.Received -> BillStatus.Paid
    MovementStatus.Overdue -> BillStatus.Overdue
    MovementStatus.DueToday -> BillStatus.DueToday
    MovementStatus.Expected -> BillStatus.Expected
    MovementStatus.Estimated -> BillStatus.Pending
}

private fun title(m: TimelineMovement): String {
    val t = (m.source as? CycleSource.Lancamento)?.transacao
    return m.descricao.ifBlank { t?.categoria ?: "Movimento" }
}

/** A informação que a data e o valor não dizem: parcela, dia original, renda principal. */
private fun detail(m: TimelineMovement): String? {
    val parcela = (m.source as? CycleSource.Parcela)?.parcela
    return when {
        m.movedFrom != null && m.status == MovementStatus.Overdue -> "venceu ${dm(m.movedFrom)}"
        m.movedFrom != null && (m.status == MovementStatus.Paid || m.status == MovementStatus.Received) -> "vence ${dm(m.movedFrom)}"
        parcela != null && parcela.vencimento == null -> "sem dia definido"
        parcela?.numero != null -> "${parcela.numero} de ${parcela.total}"
        m.mainIncome -> "Renda principal"
        else -> null
    }
}

private fun brl(cents: Long) = formatBrl0(cents / 100.0)
private fun signed(cents: Long) = if (cents < 0) "− " + brl(-cents) else brl(cents)
/** Entrada com "+"; saída sem sinal; o ajuste de saldo negativo com "−" (o valor dele já vem com sinal). */
private fun signedMoney(m: TimelineMovement): String {
    val adjustment = (m.source as? CycleSource.Lancamento)?.transacao?.isAdjustment == true
    return (if (m.cents > 0) "+ " else if (adjustment) "− " else "") + money(kotlin.math.abs(m.cents))
}
private fun money(cents: Long) = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else formatBrl(cents / 100.0)
private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
