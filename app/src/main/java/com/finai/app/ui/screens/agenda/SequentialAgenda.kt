package com.finai.app.ui.screens.agenda

import androidx.compose.animation.animateColorAsState
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
        listOf("Mensal" to false, "Sequencial" to true).forEach { (label, value) ->
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
 * A Agenda sequencial: os ciclos entre salários, em ordem, com o saldo depois de cada movimento.
 * Os números vêm prontos de [CashFlowTimeline] (os mesmos ciclos do "Livre até o salário"); aqui
 * só se desenha. O mês aparece como divisor, sem zerar o saldo.
 */
internal fun LazyListScope.sequentialAgendaItems(
    timeline: CashFlowTimeline?,
    today: LocalDate,
    showPrevious: Boolean,
    onShowPrevious: () -> Unit,
    futureShown: Int,
    onShowMoreFuture: () -> Unit,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onAddMainIncome: () -> Unit,
    /** Toque num lançamento: a mesma folha de ações da Agenda mensal. */
    onOpenTransaction: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit = {},
) {
    val cycle = timeline?.cycle
    if (timeline == null || cycle == null) {
        item(key = "seq-sem-renda") {
            com.finai.app.ui.components.EmptyStateCard(
                "Sem renda principal cadastrada, o app não sabe quando o dinheiro entra e não calcula o saldo entre salários.",
                "Informar renda", onAddMainIncome,
            )
        }
        val list = timeline?.unprojected.orEmpty()
        list.groupBy { YearMonth.from(it.date) }.forEach { (month, movements) ->
            item(key = "seq-sem-renda-$month") { MonthLabel(month) }
            item(key = "seq-sem-renda-lista-$month") {
                MovementGroup(movements, allowPay = true, null, null, null, onToggleContaPaga, onRequestPayInstallment, onOpenTransaction)
            }
        }
        return
    }

    item(key = "seq-resumo") { CycleSummaryCard(cycle) }

    val previous = timeline.segments.firstOrNull { it.kind == SegmentKind.Previous }
    val future = timeline.segments.filter { it.kind == SegmentKind.Future }
    val shown = buildList {
        if (showPrevious && previous != null) add(previous)
        timeline.current?.let(::add)
        addAll(future.take(futureShown))
    }
    if (previous != null && !showPrevious) {
        item(key = "seq-ver-anterior") {
            TextAction("Ver ciclo anterior (${dm(previous.start)} a ${dm(previous.end)}) ›", onShowPrevious)
        }
    }
    shown.forEach { segment ->
        val k = "seq-${segment.start}"
        item(key = "$k-cabecalho") { SegmentHeader(segment) }
        val isCurrent = segment.kind == SegmentKind.Current
        // A marca de hoje entra uma vez: antes do primeiro movimento que ainda não aconteceu, se ele
        // é deste mês; senão, depois do último que já aconteceu (para não ficar sob o mês seguinte).
        val firstAhead = if (isCurrent) segment.movements.firstOrNull { it.date.isAfter(today) } else null
        val lastPast = if (isCurrent) segment.movements.lastOrNull { !it.date.isAfter(today) } else null
        val markerBefore = firstAhead?.takeIf { YearMonth.from(it.date) == YearMonth.from(today) || lastPast == null }
        val markerAfter = if (markerBefore == null) lastPast else null
        segment.movements.groupBy { YearMonth.from(it.date) }.forEach { (month, movements) ->
            item(key = "$k-mes-$month") { MonthLabel(month) }
            item(key = "$k-lista-$month") {
                MovementGroup(movements, allowPay = isCurrent, markerBefore, markerAfter, cycle.saldoHojeCents, onToggleContaPaga, onRequestPayInstallment, onOpenTransaction)
            }
        }
        item(key = "$k-rodape") { SegmentFooter(segment) }
    }
    if (future.size > futureShown) {
        item(key = "seq-mais") { TextAction("Ver mais um ciclo ›", onShowMoreFuture) }
    }
}

/** O período em andamento: o livre (= menor saldo até o salário) separado da sobra final. */
@Composable
private fun CycleSummaryCard(cycle: PayCycle) {
    val falta = cycle.shortfall
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(14.dp),
    ) {
        val desde = cycle.inicio?.let { "Salário de ${dm(it)}" } ?: "Desde ${dm(cycle.contaDesde ?: cycle.today)}"
        Text(
            "$desde → próximo em ${dm(cycle.proximo)}" + if (cycle.proximoEstimado) " (estimado)" else "",
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary,
        )
        Text(
            if (falta != null) "Vai faltar antes do salário" else "Livre até o salário",
            fontSize = 12.5.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (falta != null) "Faltam ${brl(falta.cents)}" else brl(cycle.availableCents),
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = if (falta != null) FinaiColors.RoseDark else FinaiColors.TextPrimary,
        )
        Text(
            if (falta != null) "a partir de ${dm(falta.date)}" + (falta.causa?.let { " · $it" } ?: "")
            else "menor saldo previsto, em ${dm(cycle.floor.date)}",
            fontSize = 12.5.sp, color = FinaiColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Saldo hoje", signed(cycle.saldoHojeCents), Modifier.weight(1f))
            Stat("A receber", "+ " + brl(cycle.aReceberCents), Modifier.weight(1f), FinaiColors.EmeraldDark)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Stat("A pagar", brl(cycle.comprometidoCents), Modifier.weight(1f))
            Stat("Sobra no fim", signed(cycle.livreCents), Modifier.weight(1f), if (cycle.livreCents < 0) FinaiColors.RoseDark else FinaiColors.TextPrimary)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color = FinaiColors.TextPrimary) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = FinaiColors.TextTertiary)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun SegmentHeader(segment: TimelineSegment) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (segment.kind) {
                SegmentKind.Previous -> "CICLO ANTERIOR"
                SegmentKind.Current -> "CICLO ATUAL"
                SegmentKind.Future -> "PRÓXIMO CICLO"
            },
            fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
            color = if (segment.kind == SegmentKind.Current) FinaiColors.EmeraldDark else FinaiColors.TextSecondary,
        )
        Text("${dm(segment.start)} a ${dm(segment.end)}", fontSize = 12.5.sp, color = FinaiColors.TextTertiary)
        if (segment.incomeEstimated) StatusChip("Salário estimado", BillStatus.Pending)
    }
}

@Composable
private fun MonthLabel(month: YearMonth) {
    Text(
        MONTH_NAMES_PT[month.monthValue - 1].uppercase() + " " + month.year,
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** Os movimentos de um mês do ciclo, com a marca de hoje entre o que já foi e o que vem. */
@Composable
private fun MovementGroup(
    movements: List<TimelineMovement>,
    allowPay: Boolean,
    todayMarkerBefore: TimelineMovement?,
    todayMarkerAfter: TimelineMovement?,
    todayBalanceCents: Long?,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onOpenTransaction: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FinaiColors.Surface),
    ) {
        val todayIndex = todayMarkerBefore?.let { marker -> movements.indexOfFirst { it === marker } } ?: -1
        movements.forEachIndexed { index, m ->
            if (index == todayIndex) {
                TodayMarker(todayBalanceCents, first = index == 0)
            } else if (index > 0) {
                HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(start = 58.dp))
            }
            MovementRow(m, allowPay, onToggleContaPaga, onRequestPayInstallment, onOpenTransaction)
            if (m === todayMarkerAfter) TodayMarker(todayBalanceCents, first = false)
        }
    }
}

/** "Hoje": o saldo do ciclo agora, entre o realizado e o previsto. */
@Composable
private fun TodayMarker(balanceCents: Long?, first: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FinaiColors.EmeraldSoftBg)
            .padding(horizontal = 12.dp, vertical = if (first) 8.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("HOJE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDark, modifier = Modifier.weight(1f))
        balanceCents?.let { Text("Saldo ${signed(it)}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark) }
    }
}

@Composable
private fun MovementRow(
    m: TimelineMovement,
    allowPay: Boolean,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onRequestPayInstallment: (TimelineMovement) -> Unit,
    onOpenTransaction: (com.finai.app.data.local.entity.TransacaoEntity) -> Unit,
) {
    val conta = (m.source as? CycleSource.Conta)?.conta
    val parcela = (m.source as? CycleSource.Parcela)?.parcela
    // Salário estimado de um ciclo futuro tem id negativo: não existe no banco para editar.
    val lancamento = (m.source as? CycleSource.Lancamento)?.transacao?.takeIf { it.id > 0 }
    // Mesmas ações da Agenda mensal: tocar na conta alterna paga/pendente; na parcela mais antiga em
    // aberto, registra o pagamento (com confirmação). Projeção de ciclo futuro não é editável aqui.
    val onClick: (() -> Unit)? = when {
        conta != null -> { { onToggleContaPaga(conta.id, conta.status != "pago") } }
        parcela != null && parcela.isNext && allowPay -> { { onRequestPayInstallment(m) } }
        lancamento != null -> { { onOpenTransaction(lancamento) } }
        else -> null
    }
    val entrada = m.cents > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (m.mainIncome) Modifier.background(FinaiColors.EmeraldSoftBg.copy(alpha = 0.55f)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%02d".format(m.date.dayOfMonth), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text(MONTH_ABBREV_PT[m.date.monthValue - 1].lowercase(), fontSize = 12.sp, color = FinaiColors.TextTertiary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                title(m), fontSize = 14.sp, fontWeight = if (m.mainIncome) FontWeight.Bold else FontWeight.SemiBold,
                color = FinaiColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(m.status.label, styleOf(m.status))
                if (m.lowest) StatusChip("Menor saldo", BillStatus.DueToday)
                detail(m)?.let { Text(it, fontSize = 12.sp, color = FinaiColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (entrada) "+ " else "− ") + money(kotlin.math.abs(m.cents)),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (entrada) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
            )
            m.balanceAfterCents?.let {
                Text(
                    "Saldo ${signed(it)}", fontSize = 12.sp, fontWeight = if (m.lowest) FontWeight.Bold else FontWeight.Normal,
                    color = if (it < 0) FinaiColors.RoseDark else FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
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

@Composable
private fun SegmentFooter(segment: TimelineSegment) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sobra no fim do ciclo", fontSize = 12.5.sp, color = FinaiColors.TextTertiary)
            if (segment.kind != SegmentKind.Previous) {
                Text("Não é somada ao próximo ciclo", fontSize = 12.sp, color = FinaiColors.TextMuted)
            }
        }
        Text(
            signed(segment.sobraCents), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (segment.sobraCents < 0) FinaiColors.RoseDark else FinaiColors.TextSecondary,
        )
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
private fun money(cents: Long) = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else formatBrl(cents / 100.0)
private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
