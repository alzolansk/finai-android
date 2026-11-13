package com.finai.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.model.Goal
import com.finai.app.domain.*
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.ui.components.*
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal fun money(cents: Long) = if (cents % 100 == 0L) formatBrl0(cents / 100.0) else com.finai.app.util.formatBrl(cents / 100.0)
internal fun dm(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd/MM"))

@Composable
fun HomeScreen(
    listState: LazyListState,
    goals: List<Goal>, situation: HomeSituation?, free: FreeToSpend?, reconciliation: ReconciliationState?,
    week: List<TimelineMovement>, behaviorPattern: BehaviorPattern?, showCoach: Boolean, goalPlan: String,
    onOpenGoals: () -> Unit, onNewGoal: () -> Unit, onOpenSimulator: () -> Unit, onExplainSafe: () -> Unit,
    onOpenAgenda: () -> Unit, onNewEntry: () -> Unit, onAddMainIncome: () -> Unit,
    onMovement: (TimelineMovement) -> Unit, onDismissCoach: (String) -> Unit, onCoachDetails: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("title") {
            PageTitle("Hoje", listState, subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR"))).replaceFirstChar { it.titlecase() })
        }
        item("free") { FreeCard(situation, free, reconciliation, today, onOpenSimulator, onExplainSafe, onAddMainIncome) }
        item("week") {
            Column {
                SectionHeader("Próximos 7 dias", action = "Agenda ›", onAction = onOpenAgenda)
                if (week.isEmpty()) EmptyStateCard("Nada previsto nos próximos 7 dias.", "Lançar conta", onNewEntry)
                else V2Panel { week.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = FinaiColors.BorderFaint)
                    HomeMoneyRow(m, onClick = { onMovement(m) })
                } }
                Text("Toque para pagar ou reabrir. Parcelas e lançamentos abrem suas ações.", fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (showCoach && behaviorPattern != null) item("coach") {
            V2Panel {
                Text("NOVIDADE NO SEU GASTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                Text(behaviorPattern.title, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                Text(behaviorPattern.detail, fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                TextAction("Ver gastos ›", onCoachDetails)
                TextAction("Já sei, não avisar", { onDismissCoach(behaviorPattern.id) })
            }
        }
        item("goals") {
            V2Panel {
                SectionHeader("Metas", action = "Planos ›", onAction = onOpenGoals)
                if (goals.isEmpty()) TextAction("Criar meta", onNewGoal)
                goals.take(2).forEach { goal ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenGoals), verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, color = FinaiColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (goal.openReserve) "Reserva contínua" else "${(goal.progress * 100).toInt()}%", color = FinaiColors.EmeraldDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (!goal.openReserve) ProgressTrack(goal.progress, fillColor = FinaiColors.Emerald)
                }
                if (goalPlan.isNotBlank()) Text(goalPlan, fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
internal fun V2Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(22.dp)).background(FinaiColors.Surface).padding(16.dp), content = content)
}

@Composable
fun FreeCard(situation: HomeSituation?, free: FreeToSpend?, reconciliation: ReconciliationState?, today: LocalDate, onSimulator: () -> Unit, onExplain: () -> Unit, onIncome: () -> Unit) {
    V2Panel {
        if (situation == null) Text("Calculando…", color = FinaiColors.TextMuted)
        else {
            val lack = free?.shortfall
            val short = situation.shortfall
            val accent = if (short) FinaiColors.RoseDark else FinaiColors.TextPrimary
            Text(if (short) "FALTA PREVISTA · ${lack?.date?.let(::dm) ?: "NESTE MÊS"}" else situation.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (short) accent else FinaiColors.TextMuted)
            Text((if (short) "Faltam " else "") + money(situation.mainCents), fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, color = accent, modifier = Modifier.padding(top = 14.dp))
            Text(if (short) lack?.causa ?: situation.headline else "cerca de ${money(situation.perDayCents)} por dia nos próximos ${situation.days} dias", fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            if (free != null) {
                if (!short) {
                    val parts = listOf("Vai sair" to free.goingOutCents, "Margem" to minOf(free.marginCents, free.lowest.cents.coerceAtLeast(0)), "Livre" to free.freeCents)
                    val colors = listOf(FinaiColors.TextDisabled, FinaiColors.Amber, FinaiColors.Emerald)
                    val total = parts.sumOf { it.second }.coerceAtLeast(1)
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)).background(FinaiColors.SurfaceMuted).semantics { contentDescription = parts.joinToString { "${it.first} ${money(it.second)}" } }) {
                        parts.forEachIndexed { i, part -> if (part.second > 0) Box(Modifier.weight((part.second.toDouble() / total).toFloat().coerceAtLeast(0.0001f)).fillMaxHeight().background(colors[i])) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        parts.forEachIndexed { i, part -> Column(Modifier.weight(1f)) {
                            Text("■ ${part.first}", color = when (i) { 1 -> FinaiColors.AmberDark; 2 -> FinaiColors.EmeraldDark; else -> FinaiColors.TextMuted }, fontSize = 12.sp)
                            Text(money(part.second), color = if (i == 2) FinaiColors.EmeraldDark else FinaiColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } }
                    }
                    if (free.insideMargin) Text("O saldo previsto usa parte da margem de ${money(free.marginCents)}. Nada livre por enquanto.", color = FinaiColors.AmberDark, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                HorizontalDivider(Modifier.padding(top = 14.dp), color = FinaiColors.BorderFaint)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onExplain), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Em conta ${money(free.accountCents)} · ${reconciliationLabel(reconciliation, today)}", fontSize = 12.sp, color = FinaiColors.TextSecondary, modifier = Modifier.weight(1f))
                    Text("Por quê ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
                }
            } else {
                Text("Informe uma renda recorrente para calcular a janela e a margem. Por enquanto, o livre considera o mês inteiro.", color = FinaiColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                TextAction("Informar renda", onIncome)
            }
            FreeActions(onSimulator, onExplain)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeActions(onSimulator: () -> Unit, onExplain: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V2Button("Posso comprar?", onSimulator, primary = true)
        V2Button("Conferir saldo", onExplain)
    }
}

@Composable
internal fun V2Button(label: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (primary) FinaiColors.InkStrong else FinaiColors.SurfaceMuted).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (!enabled) FinaiColors.TextDisabled else if (primary) FinaiColors.OnInkStrong else FinaiColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun reconciliationLabel(state: ReconciliationState?, today: LocalDate): String {
    if (state == null) return "ainda não conferido"
    val days = ChronoUnit.DAYS.between(state.date, today).coerceAtLeast(0)
    return if (days == 0L) "conferido hoje" else "conferido há $days ${if (days == 1L) "dia" else "dias"}"
}

@Composable
internal fun HomeMoneyRow(m: TimelineMovement, onClick: (() -> Unit)? = null) {
    val state = when (m.status) {
        MovementStatus.Paid -> MoneyRowState.Paid
        MovementStatus.Received -> MoneyRowState.Received
        MovementStatus.Done -> MoneyRowState.Plain
        else -> MoneyRowState.Pending
    }
    val installment = (m.source as? CycleSource.Parcela)?.parcela
    val title = m.descricao + (installment?.numero?.let { " · parcela $it de ${installment.total}" } ?: "")
    MoneyRow(title, money(kotlin.math.abs(m.cents)), m.descricao.take(2), subtitle = "${dm(m.date)} · ${m.status.label}", state = state, income = m.cents > 0, onClick = onClick)
}
