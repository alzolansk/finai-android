package com.finai.app.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.model.Debt
import com.finai.app.data.model.PaidDebt
import com.finai.app.domain.ContributionRecord
import com.finai.app.domain.InstallmentPurchase
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.plans.DebtPrepayment
import com.finai.app.domain.plans.GoalConflict
import com.finai.app.domain.plans.GoalConflictResolver
import com.finai.app.domain.plans.GoalSeal
import com.finai.app.domain.toLocalDate
import com.finai.app.state.V2State
import com.finai.app.ui.components.*
import com.finai.app.ui.screens.home.V2Panel
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.themedText
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

private fun brl(cents: Long) = formatBrl0(cents / 100.0)
private fun monthYear(m: YearMonth) = "${MONTH_ABBREV_PT[m.monthValue - 1].lowercase()}/${m.year % 100}"

/**
 * Planos (planning-v2, fase 7): Metas | Compromissos. Metas responde "quanto cabe guardar e o
 * que as metas pedem"; Compromissos junta empréstimos, parcelado no cartão, faturas e contas fixas.
 */
@Composable
fun PlansScreen(
    listState: LazyListState,
    tab: Int,
    onTab: (Int) -> Unit,
    v2: V2State?,
    objetivos: List<ObjetivoEntity>,
    contributions: Map<Long, List<ContributionRecord>>,
    debts: List<Debt>,
    paidDebts: List<PaidDebt>,
    opportunity: DebtPrepayment.Opportunity?,
    negotiationTitle: String,
    negotiationScript: AiText?,
    showFixed: Boolean,
    onShowFixed: (Boolean) -> Unit,
    onNewGoal: () -> Unit,
    onContribute: (ObjetivoEntity) -> Unit,
    onEditGoal: (ObjetivoEntity) -> Unit,
    onAskGoal: (ObjetivoEntity) -> Unit,
    onMoveDeadline: (GoalConflict) -> Unit,
    onKeepConflict: (GoalConflict) -> Unit,
    onNewDebt: () -> Unit,
    onEditDebt: (Debt) -> Unit,
    onDeleteDebt: (Debt) -> Unit,
    onPayInstallment: (Debt) -> Unit,
    onRehearseCall: () -> Unit,
    onDeletePaidDebt: (PaidDebt) -> Unit,
    onOpenInvoice: (ContaEntity) -> Unit,
    onOpenIncome: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("title") {
            PageTitle("Planos", listState, subtitle = if (tab == 0) "O que você quer alcançar" else "O que já está comprometido",
                trailing = { ActionButton(if (tab == 0) "+ Meta" else "+ Dívida", if (tab == 0) onNewGoal else onNewDebt, primary = true) })
        }
        item("tabs") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FinaiColors.SurfaceMuted).padding(4.dp)) {
                listOf("Metas", "Compromissos").forEachIndexed { i, label ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (tab == i) FinaiColors.Surface else Color.Transparent)
                            .clickable(role = Role.Tab) { onTab(i) }.semantics { selected = tab == i }.testTag("plans-tab-$i"),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (tab == i) FinaiColors.TextPrimary else FinaiColors.TextMuted) }
                }
            }
        }
        if (v2 == null) item("loading") { Text("Calculando…", color = FinaiColors.TextMuted) }
        else if (tab == 0) goalsTab(v2, objetivos, contributions, onNewGoal, onContribute, onEditGoal, onAskGoal, onMoveDeadline, onKeepConflict, onOpenIncome, today)
        else commitmentsTab(v2, debts, paidDebts, opportunity, negotiationTitle, negotiationScript, showFixed, onShowFixed, onNewDebt, onEditDebt, onDeleteDebt,
            onPayInstallment, onRehearseCall, onDeletePaidDebt, onOpenInvoice, today)
    }
}

private fun LazyListScope.goalsTab(
    v2: V2State, objetivos: List<ObjetivoEntity>, contributions: Map<Long, List<ContributionRecord>>,
    onNewGoal: () -> Unit, onContribute: (ObjetivoEntity) -> Unit, onEdit: (ObjetivoEntity) -> Unit, onAsk: (ObjetivoEntity) -> Unit,
    onMove: (GoalConflict) -> Unit, onKeep: (GoalConflict) -> Unit, onOpenIncome: () -> Unit, today: LocalDate,
) {
    val fits = v2.capacity.cabeGuardarCents
    val asks = v2.asksCents
    val conflict = v2.visibleConflict
    item("capacity") {
        V2Panel {
            Text("QUANTO CABE GUARDAR POR MÊS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
            Text(brl(fits), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 10.dp).testTag("plans-fits"))
            Text("Média da sobra dos próximos 6 meses, já sem contas, parcelas e o seu gasto do dia a dia." +
                if (v2.capacity.usesEstimatedSalary) " Onde não há salário lançado, repeti o último." else "",
                fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            if (asks > 0) {
                val ok = asks <= fits
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Suas metas pedem", Modifier.weight(1f), fontSize = 13.sp, color = FinaiColors.TextSecondary)
                    Text("${brl(asks)}/mês", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (ok) FinaiColors.EmeraldDark else FinaiColors.AmberDark)
                }
                ProgressTrack(if (fits <= 0) 1f else (asks.toFloat() / fits).coerceIn(0f, 1f), fillColor = if (ok) FinaiColors.Emerald else FinaiColors.Amber, modifier = Modifier.padding(top = 8.dp))
                Text(if (ok) "Cabe, com ${brl(fits - asks)} de folga por mês." else "Passa ${brl(asks - fits)} do que cabe.", fontSize = 12.5.sp,
                    color = if (ok) FinaiColors.TextMuted else FinaiColors.AmberDark, modifier = Modifier.padding(top = 6.dp))
            }
            if (fits == 0L && v2.capacity.months.all { it.cents <= 0 }) TextAction("Conferir minha renda", onOpenIncome)
        }
    }
    if (conflict != null) item("conflict") {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.AmberSoftBg).border(1.dp, FinaiColors.Amber.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(16.dp).testTag("plans-conflict")) {
            Text("\"${conflict.goalName}\" não cabe no prazo", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                if (conflict.newDeadline != null) "Com o prazo em ${monthYear(conflict.newDeadline)}, ela pede ${brl(conflict.newMonthlyCents)}/mês e todas as metas cabem."
                else "Mesmo adiando esta meta, as outras já pedem mais do que cabe guardar. Vale rever os valores.",
                fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (conflict.newDeadline != null) ActionButton("Mudar para ${monthYear(conflict.newDeadline)}", { onMove(conflict) }, Modifier.weight(1f), primary = true)
                ActionButton("Manter", { onKeep(conflict) }, Modifier.weight(if (conflict.newDeadline != null) 0.6f else 1f))
            }
        }
    }
    val active = objetivos.filter { it.concluidoEm == null }.sortedBy { it.prioridade }
    val done = objetivos.filter { it.concluidoEm != null }
    if (active.isEmpty()) item("no-goals") {
        EmptyStateCard("Nenhuma meta ainda. Crie uma viagem, uma compra ou uma reserva e o app diz quanto guardar por mês.", "Criar meta", onNewGoal)
    }
    items(active, key = { "g-${it.id}" }) { goal ->
        GoalCard(goal, GoalConflictResolver.seal(goal, conflict, today), v2.asks.firstOrNull { it.id == goal.id }?.monthlyCents ?: 0,
            contributions[goal.id].orEmpty(), today, onContribute, onEdit, onAsk)
    }
    if (done.isNotEmpty()) {
        item("done-head") { SectionHeader("Concluídas") }
        items(done, key = { "gd-${it.id}" }) { goal -> GoalCard(goal, GoalSeal.Concluida, 0, contributions[goal.id].orEmpty(), today, onContribute, onEdit, onAsk) }
    }
}

@Composable
private fun SealTag(seal: GoalSeal) {
    val (bg, fg) = when (seal) {
        GoalSeal.EmDia, GoalSeal.Concluida -> FinaiColors.EmeraldSoftBg to FinaiColors.EmeraldDark
        GoalSeal.NaoCabe -> FinaiColors.AmberSoftBg to FinaiColors.AmberDark
        GoalSeal.Continua -> FinaiColors.SkySoftBg to FinaiColors.Sky
    }
    PillTag(seal.label, bg, fg)
}

@Composable
private fun GoalCard(
    goal: ObjetivoEntity, seal: GoalSeal, monthlyCents: Long, records: List<ContributionRecord>, today: LocalDate,
    onContribute: (ObjetivoEntity) -> Unit, onEdit: (ObjetivoEntity) -> Unit, onAsk: (ObjetivoEntity) -> Unit,
) {
    var open by rememberSaveable(goal.id) { mutableStateOf(false) }
    val progress = if (goal.valorAlvoCentavos <= 0) 0f else (goal.valorGuardadoCentavos.toFloat() / goal.valorAlvoCentavos).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.Surface).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "recolher" else "ver detalhes") { open = !open }.padding(16.dp).testTag("goal-${goal.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(goal.nome, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            SealTag(seal)
        }
        if (goal.isOpenReserve) {
            Text(brl(goal.valorGuardadoCentavos), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
            Text(if (goal.aporteMensalCentavos > 0) "Guardados · plano de ${brl(goal.aporteMensalCentavos)}/mês" else "Guardados · sem valor-alvo", fontSize = 13.sp, color = FinaiColors.TextSecondary)
        } else {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${brl(goal.valorGuardadoCentavos)} de ${brl(goal.valorAlvoCentavos)}", Modifier.weight(1f), fontSize = 14.sp, color = FinaiColors.TextSecondary)
                Text("${(progress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            }
            ProgressTrack(progress, fillColor = if (seal == GoalSeal.NaoCabe) FinaiColors.Amber else FinaiColors.Emerald, modifier = Modifier.padding(top = 8.dp))
            val deadline = YearMonth.from(goal.prazo.toLocalDate())
            Text(
                when {
                    seal == GoalSeal.Concluida -> "Meta alcançada."
                    goal.prazo.toLocalDate().isBefore(today) -> "O prazo de ${monthYear(deadline)} passou. Toque para mudar."
                    else -> "${brl(monthlyCents)}/mês até ${monthYear(deadline)}"
                },
                fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
        }
        if (open) {
            val last = records.firstOrNull()
            val since = today.minusMonths(3)
            val pace = records.filter { !it.date.isBefore(since) }.sumOf { it.cents } / 3
            Text(last?.let { "Último aporte: ${brl(it.cents)} em %02d/%02d".format(it.date.dayOfMonth, it.date.monthValue) } ?: "Nenhum aporte registrado ainda.",
                fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            if (pace > 0) Text("Ritmo dos últimos 3 meses: ${brl(pace)}/mês", fontSize = 13.sp, color = FinaiColors.TextSecondary)
            if (goal.descricao.isNotBlank()) Text(goal.descricao, fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Guardar agora", { onContribute(goal) }, Modifier.weight(1f), primary = true)
                ActionButton("Perguntar", { onAsk(goal) }, Modifier.weight(1f))
            }
            TextAction("Editar meta", { onEdit(goal) })
        }
    }
}

private fun LazyListScope.commitmentsTab(
    v2: V2State, debts: List<Debt>, paidDebts: List<PaidDebt>, opportunity: DebtPrepayment.Opportunity?,
    negotiationTitle: String, negotiationScript: AiText?, showFixed: Boolean, onShowFixed: (Boolean) -> Unit,
    onNewDebt: () -> Unit, onEditDebt: (Debt) -> Unit, onDeleteDebt: (Debt) -> Unit, onPay: (Debt) -> Unit, onRehearse: () -> Unit,
    onDeletePaid: (PaidDebt) -> Unit, onOpenInvoice: (ContaEntity) -> Unit, today: LocalDate,
) {
    val c = v2.commitments
    val nothing = c.installmentsOpenCents == 0L && debts.isEmpty() && c.openInvoices.isEmpty() && c.fixedBills.isEmpty()
    if (nothing) {
        item("empty") {
            EmptyStateCard(if (paidDebts.isEmpty()) "Nada comprometido: nenhum empréstimo, parcela no cartão, fatura em aberto ou conta fixa." else "Tudo quitado. Nenhum compromisso em aberto.",
                "Cadastrar dívida", onNewDebt)
        }
    } else {
        item("summary") {
            V2Panel {
                Text("AINDA FALTA PAGAR EM PARCELAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                Text(brl(c.installmentsOpenCents), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 10.dp).testTag("commitments-total"))
                Text(
                    if (c.installmentsOpenCents == 0L) "Nenhuma parcela em aberto."
                    else "${brl(c.monthlyCents)} neste mês" + (c.freeFrom?.let { " · livre de parcelas em ${monthYear(it)}" } ?: ""),
                    fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                if (c.months.any { it.totalCents > 0 }) {
                    val shown = c.months.take(12)
                    V2BarChart(
                        shown.map { m ->
                            ChartColumn(MONTH_ABBREV_PT[m.month.monthValue - 1].lowercase().take(3),
                                listOfNotNull(m.loansCents to FinaiColors.Rose, m.cardCents to FinaiColors.Sky, if (showFixed) m.fixedCents to FinaiColors.TextDisabled else null))
                        },
                        description = "Comprometido por mês: " + shown.joinToString { "${MONTH_ABBREV_PT[it.month.monthValue - 1]} ${brl(if (showFixed) it.totalCents else it.installmentsCents)}" },
                        modifier = Modifier.padding(top = 16.dp), height = 110.dp,
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(FinaiColors.Rose, "Empréstimos"); LegendDot(FinaiColors.Sky, "Cartão")
                        if (showFixed) LegendDot(FinaiColors.TextDisabled, "Contas fixas")
                    }
                    if (c.fixedBills.isNotEmpty()) TextAction(if (showFixed) "Esconder contas fixas" else "Somar contas fixas (${brl(c.fixedMonthlyCents)}/mês)", { onShowFixed(!showFixed) })
                }
            }
        }
        if (opportunity != null) item("opportunity") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.EmeraldSoftBg).border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(20.dp)).padding(16.dp)) {
                Text("Oportunidade: antecipar \"${opportunity.name}\"", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text("Juros de ${"%.1f".format(opportunity.ratePercent).replace('.', ',')}% ao mês. Antecipar ${brl(opportunity.amountCents)} elimina as ${opportunity.installmentsCut} últimas parcelas e economiza cerca de ${brl(opportunity.savedCents)}. " +
                    "Peça o valor exato ao credor: o desconto real depende do contrato.", fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (debts.isNotEmpty()) {
            item("loans-head") { SectionHeader("Empréstimos e dívidas", subtitle = "Da mais cara para a mais barata") }
            items(debts, key = { "d-${it.id}" }) { debt -> DebtCard(debt, onPay, onEditDebt, onDeleteDebt) }
            item("negotiation") {
                var open by rememberSaveable { mutableStateOf(false) }
                V2Panel {
                    SectionHeader("Roteiro de negociação", subtitle = negotiationTitle.ifBlank { null }, action = if (open) "Fechar" else "Ver", onAction = { open = !open })
                    if (open) {
                        when (negotiationScript) {
                            is AiText.Ready -> AiRichText(negotiationScript.text)
                            AiText.Loading -> Text("Escrevendo o roteiro…", fontSize = 13.sp, color = FinaiColors.TextMuted)
                            else -> Text("1. Diga há quanto tempo é cliente e que quer quitar.\n2. Peça uma taxa menor citando a atual.\n3. Se recusarem, ofereça uma entrada e o resto sem juros.\n4. Peça o protocolo e a proposta por escrito.",
                                fontSize = 13.sp, color = FinaiColors.TextSecondary)
                        }
                        TextAction("Ensaiar a ligação com o assistente", onRehearse)
                    }
                }
            }
        }
        if (v2.installments.active.isNotEmpty()) {
            item("card-head") { SectionHeader("Parcelado no cartão", subtitle = "${brl(v2.installments.openCents)} em ${v2.installments.openCount} parcelas") }
            items(v2.installments.active, key = { "p-${it.compraId}" }) { p -> PurchaseRow(p) }
        }
        if (c.openInvoices.isNotEmpty()) {
            item("inv-head") { SectionHeader("Faturas em aberto") }
            items(c.openInvoices, key = { "f-${it.id}" }) { conta ->
                val d = conta.vencimento.toLocalDate()
                MoneyRow(conta.nome, brl(conta.valorCentavos), conta.nome.take(2).uppercase(), subtitle = "Vence %02d/%02d".format(d.dayOfMonth, d.monthValue) + if (d.isBefore(today)) " · atrasada" else "",
                    background = MoneyRowBackground.Card, onClick = { onOpenInvoice(conta) }, onClickLabel = "ver itens da fatura")
            }
        }
        if (c.fixedBills.isNotEmpty()) {
            item("fixed-head") { SectionHeader("Contas fixas", subtitle = "${brl(c.fixedMonthlyCents)} por mês") }
            item("fixed") {
                V2Panel {
                    c.fixedBills.forEach { bill ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Text(bill.name, Modifier.weight(1f), fontSize = 14.sp, color = FinaiColors.TextPrimary, maxLines = 1)
                            Text("dia ${bill.day}", fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(horizontal = 10.dp))
                            Text(brl(bill.cents), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
    if (paidDebts.isNotEmpty()) {
        item("paid-head") { SectionHeader("Quitadas") }
        items(paidDebts, key = { "pd-${it.id}" }) { paid ->
            var confirm by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(16.dp)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(paid.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                    Text("${paid.paidLabel} · ${paid.detail}", fontSize = 12.5.sp, color = FinaiColors.TextMuted)
                }
                OverflowMenu(listOf(OverflowAction("Excluir do histórico", destructive = true) { confirm = true }), "Mais ações de ${paid.name}")
            }
            if (confirm) ConfirmDeleteDialog("Excluir do histórico?", "\"${paid.name}\" sai da lista de quitadas.", { confirm = false }, { confirm = false; onDeletePaid(paid) })
        }
    }
}

@Composable
private fun DebtCard(debt: Debt, onPay: (Debt) -> Unit, onEdit: (Debt) -> Unit, onDelete: (Debt) -> Unit) {
    var confirmPay by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.Surface).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp)).padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 14.dp).testTag("debt-${debt.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(debt.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(debt.meta, fontSize = 12.5.sp, color = FinaiColors.TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(debt.amount, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(debt.rate, fontSize = 12.5.sp, color = debt.rateColor.themedText)
            }
            OverflowMenu(listOf(OverflowAction("Editar") { onEdit(debt) }, OverflowAction("Excluir", destructive = true) { confirmDelete = true }), "Mais ações de ${debt.name}")
        }
        ProgressTrack(debt.progressPct / 100f, fillColor = FinaiColors.Emerald, modifier = Modifier.padding(top = 10.dp, end = 12.dp))
        debt.planNote?.let { Text(it, fontSize = 12.5.sp, color = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 8.dp, end = 12.dp)) }
        if (debt.hasParcelaFixa) ActionButton("Paguei a parcela", { confirmPay = true }, Modifier.padding(top = 10.dp, end = 12.dp).fillMaxWidth())
    }
    if (confirmPay) ConfirmDeleteDialog("Registrar pagamento?", "A parcela de \"${debt.name}\" sai do seu saldo e a próxima passa a ser a seguinte.", { confirmPay = false }, { confirmPay = false; onPay(debt) }, confirmLabel = "Paguei")
    if (confirmDelete) ConfirmDeleteDialog("Excluir dívida?", "\"${debt.name}\" e as parcelas futuras saem da Agenda.", { confirmDelete = false }, { confirmDelete = false; onDelete(debt) })
}

@Composable
private fun PurchaseRow(p: InstallmentPurchase) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FinaiColors.Surface).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary, maxLines = 1)
                Text("${p.card} · ${p.paidCount} de ${p.totalInstallments} pagas" + (p.lastDue?.let { " · até ${monthYear(it)}" } ?: "") + if (p.estimated) " · estimado" else "",
                    fontSize = 12.5.sp, color = FinaiColors.TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${brl(p.monthlyCents)}/mês", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text("faltam ${brl(p.openCents)}", fontSize = 12.5.sp, color = FinaiColors.TextMuted)
            }
        }
        ProgressTrack(if (p.totalInstallments == 0) 0f else p.paidCount.toFloat() / p.totalInstallments, fillColor = FinaiColors.Sky, modifier = Modifier.padding(top = 10.dp))
    }
}
