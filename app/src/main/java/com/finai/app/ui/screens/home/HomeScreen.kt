package com.finai.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.model.Goal
import com.finai.app.data.model.TimelineEntry
import com.finai.app.data.model.WeekBill
import com.finai.app.domain.BehaviorPattern
import com.finai.app.ui.components.AiRichText
import com.finai.app.ui.components.rememberAiAnnotated
import androidx.compose.runtime.remember
import com.finai.app.domain.AiReplyFormat
import com.finai.app.domain.AssistantTopic
import com.finai.app.domain.AssistantTopics
import com.finai.app.ui.components.AiTextPalette
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.PayCycle
import com.finai.app.domain.SafeToSpendResult
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun HomeScreen(
    greeting: String,
    subGreeting: String,
    goals: List<Goal>,
    saldoLabel: String,
    saldoPositivo: Boolean,
    payCycle: PayCycle?,
    safeToday: SafeToSpendResult?,
    safeTodayLabel: String,
    safeNote: String,
    week: List<WeekBill>,
    timeline: List<TimelineEntry>,
    timelineNote: String,
    showCoach: Boolean,
    behaviorPattern: BehaviorPattern?,
    coachInsight: AiText?,
    decisions: AiText?,
    onOpenGoals: () -> Unit,
    onNewGoal: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenAgenda: () -> Unit,
    onAskAbout: (com.finai.app.domain.AssistantTopic) -> Unit,
    onNewIncomeEntry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column {
                Text(greeting, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(
                    subGreeting, fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item { PayCycleCard(payCycle, onNewIncomeEntry) }

        item { SaldoCard(saldoLabel, saldoPositivo) }

        item { GoalsCarousel(goals, onOpenGoals, onNewGoal) }

        item { SafeToSpendCard(safeToday, safeTodayLabel, safeNote, onOpenSimulator, onOpenBudgets) }

        item { DecisionsCard(decisions, onAskAbout) }

        item { TimelineSection(timeline, timelineNote, onNewIncomeEntry) }

        item { NextWeekSection(week, onOpenAgenda) }

        if (showCoach && behaviorPattern != null) {
            item { CoachCard(behaviorPattern, coachInsight) { onAskAbout(com.finai.app.domain.AssistantTopics.behaviorPattern(behaviorPattern)) } }
        }
    }
}

/**
 * Balanço do mês = recebimentos − contas a pagar do mês corrente, a mesma conta dos totais
 * da Agenda (MonthCashFlow). Responde "o mês fecha?"; quem paga cada conta é o
 * [PayCycleCard], que olha o dinheiro entre um salário e o próximo.
 */
@Composable
private fun SaldoCard(saldoLabel: String, saldoPositivo: Boolean) {
    val mes = MONTH_NAMES_PT[java.time.LocalDate.now().monthValue - 1]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("BALANÇO DE ${mes.uppercase()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
            Text(
                "Entradas − saídas do mês, como na Agenda", fontSize = 11.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            saldoLabel, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = if (saldoPositivo) FinaiColors.EmeraldDark else Rose,
        )
    }
}

private val Rose = Color(0xFFE11D48)
private val RoseWash = Color(0xFFFFF1F2)

private fun dayMonth(d: java.time.LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

/**
 * "Até o próximo salário": o dinheiro do salário que já caiu (mais outras entradas do
 * ciclo) contra tudo que sai até a véspera do próximo — ver [PayCycle]. Sem receita
 * recorrente cadastrada o app não sabe quando é o salário, e o cartão diz como ensinar.
 */
@Composable
private fun PayCycleCard(cycle: PayCycle?, onNewIncomeEntry: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, FinaiColors.BorderHairline, shape)
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        // Sem `return@Column`: retorno antecipado dentro de lambda inline do Compose
        // (compilador 1.5.8) desbalanceia os grupos e derruba o app na abertura.
        if (cycle == null) NoCycleContent(onNewIncomeEntry) else CycleContent(cycle)
    }
}

@Composable
private fun NoCycleContent(onNewIncomeEntry: () -> Unit) {
        Text("ATÉ O PRÓXIMO SALÁRIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
        Text(
            "Lance uma receita com \"salário\" na descrição para o app saber até quando o dinheiro precisa durar.",
            fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Lançar receita", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
            modifier = Modifier.padding(top = 10.dp).clickable(onClick = onNewIncomeEntry),
        )
}

@Composable
private fun CycleContent(cycle: PayCycle) {
    val livre = cycle.livreCents
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ATÉ O PRÓXIMO SALÁRIO · ${dayMonth(cycle.proximo)}" + if (cycle.proximoEstimado) " (ESTIMADO)" else "",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted,
            )
            // A falta prevista manda no título: "R$ X livres" ao lado de "vai faltar" se contradiz.
            val falta = cycle.shortfall
            Text(
                when {
                    falta != null -> "Faltam ${formatBrl0(falta.cents / 100.0)}"
                    else -> "${formatBrl0(livre / 100.0)} livres"
                },
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = if (falta == null) FinaiColors.EmeraldDark else Rose,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                cycle.inicio?.let { "${cycle.salarioNome} caiu em ${dayMonth(it)}" }
                    ?: "Nenhum salário lançado antes de hoje",
                fontSize = 11.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(cycle.diasAteProximo.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text("DIAS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
        }
    }
    ProgressTrack(
        progress = cycle.progress,
        fillColor = FinaiColors.Emerald,
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CycleStat("Entradas", cycle.entradasCents, Modifier.weight(1f))
        CycleStat("Já saiu", cycle.jaSaiuCents, Modifier.weight(1f))
        CycleStat("A pagar", cycle.comprometidoCents, Modifier.weight(1f))
    }
    if (cycle.aReceberCents > 0) {
        Text(
            "Entradas inclui ${formatBrl0(cycle.aReceberCents / 100.0)} que ainda vão cair antes do salário.",
            fontSize = 11.sp, lineHeight = 15.sp, color = FinaiColors.TextTertiary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    cycle.shortfall?.let { falta ->
        val quando = if (falta.date.isAfter(cycle.today)) "Em ${dayMonth(falta.date)}" else "Desde ${dayMonth(falta.date)}"
        val porque = falta.causa?.let { ", quando sai $it" }.orEmpty()
        val sobraFinal = if (livre > 0) " O ciclo fecha com ${formatBrl0(livre / 100.0)}, mas essa entrada chega tarde." else ""
        Text(
            "$quando o dinheiro não fecha$porque: faltam até ${formatBrl0(falta.cents / 100.0)} antes do próximo salário.$sobraFinal",
            fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Rose,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RoseWash)
                .padding(10.dp),
        )
    }
}

@Composable
private fun CycleStat(label: String, cents: Long, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FinaiColors.SurfaceMuted)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextMuted)
        Text(
            formatBrl0(cents / 100.0), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun GoalsCarousel(goals: List<Goal>, onOpenGoals: () -> Unit, onNewGoal: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Seus objetivos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                "Ver todos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
                modifier = Modifier.clickable(onClick = onOpenGoals),
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(goals.take(2)) { goal -> GoalTeaserCard(goal, onOpenGoals) }
            item { NewGoalCard(onNewGoal) }
        }
    }
}

@Composable
private fun GoalTeaserCard(goal: Goal, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(236.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                goal.kind.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = FinaiColors.TextMuted,
            )
            com.finai.app.ui.components.PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg)
        }
        Text(
            goal.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(goal.eta, fontSize = 11.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(formatBrl0(goal.saved), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDark)
            Text("de ${formatBrl0(goal.target)}", fontSize = 11.sp, color = FinaiColors.TextMuted)
        }
        ProgressTrack(
            progress = goal.progress,
            fillColor = FinaiColors.Emerald,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            goal.note, fontSize = 11.sp, lineHeight = 15.sp, color = FinaiColors.TextSecondary,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

@Composable
private fun NewGoalCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = FinaiColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text("Novo objetivo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextMuted)
    }
}

@Composable
private fun SafeToSpendCard(
    safeToday: SafeToSpendResult?,
    safeTodayLabel: String,
    safeNote: String,
    onOpenSimulator: () -> Unit,
    onOpenBudgets: () -> Unit,
) {
    val dayLeftLabel = safeToday?.daysRemaining?.toString() ?: "—"
    val dayProgressFraction = safeToday?.monthProgressFraction ?: 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(FinaiColors.Ink)
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    // Falta prevista vem antes de qualquer sugestão de gasto (planning.md §9 Fase 7, item 1).
                    val falta = safeToday?.shortfall
                    Text(
                        if (falta != null) "VAI FALTAR ANTES DO SALÁRIO" else "PODE GASTAR HOJE",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (falta != null) FinaiColors.Rose else FinaiColors.TextOnDarkFaint,
                    )
                    Text(
                        if (falta != null) "Faltam ${formatBrl0(falta.cents / 100.0)}" else safeTodayLabel,
                        fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (falta != null) FinaiColors.Rose else Color.White,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        safeNote, fontSize = 12.sp, lineHeight = 17.sp,
                        color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(top = 7.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                0f to FinaiColors.Emerald,
                                dayProgressFraction to FinaiColors.Emerald,
                                dayProgressFraction to Color.White.copy(alpha = 0.13f),
                                1f to Color.White.copy(alpha = 0.13f),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(58.dp).clip(CircleShape).background(FinaiColors.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayLeftLabel, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("DIAS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextOnDarkFaint)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FinaiColors.Emerald)
                        .clickable(onClick = onOpenSimulator)
                        .padding(11.dp),
                ) {
                    Text(
                        "Posso comprar?", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable(onClick = onOpenBudgets)
                        .padding(11.dp),
                ) {
                    Text(
                        "Ajustar limites", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * "Decisões para você" — short, AI-written suggestions over numbers already
 * computed locally (dívida de maior custo, orçamento estourado, assinatura
 * parada, objetivo a reavaliar; planning.md §6/§9 Fase 2). [decisions] is
 * requested by [com.finai.app.state.AiViewModel.ensureDecisions]; this
 * composable only renders whatever state it's in.
 */
@Composable
private fun DecisionsCard(decisions: AiText?, onAskAbout: (AssistantTopic) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(FinaiColors.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FinaiColors.TextMuted, modifier = Modifier.size(14.dp))
            }
            Text("Decisões para você", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        }
        when (decisions) {
            null, AiText.Loading -> Text(
                "Analisando seus números para sugerir decisões...",
                fontSize = 11.5.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 10.dp),
            )
            is AiText.Unavailable -> Text(
                decisions.reason, fontSize = 11.5.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 10.dp),
            )
            is AiText.Ready -> {
                // Uma linha só, sem "porquê", é o "está tudo sob controle": texto, não decisão.
                val items = remember(decisions.text) {
                    AiReplyFormat.decisions(decisions.text).takeUnless { it.size == 1 && it[0].reason == null }.orEmpty()
                }
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    if (items.isEmpty()) {
                        AiRichText(decisions.text, modifier = Modifier.padding(top = 4.dp))
                    }
                    items.forEachIndexed { index, decision ->
                        if (index > 0) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(FinaiColors.BorderFaint))
                        }
                        DecisionRow(decision, onAsk = {
                            onAskAbout(AssistantTopics.decision(listOfNotNull(decision.action, decision.reason).joinToString(". ")))
                        })
                    }
                }
            }
        }
    }
}

/** Uma decisão: ação em destaque, o porquê embaixo, e o atalho para conversar sobre ela. */
@Composable
private fun DecisionRow(decision: AiReplyFormat.Decision, onAsk: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAsk)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rememberAiAnnotated(decision.action),
                fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary,
            )
            decision.reason?.let {
                Text(
                    rememberAiAnnotated(it),
                    fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(FinaiColors.EmeraldSoftBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Conversar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
        }
    }
}

@Composable
private fun TimelineSection(timeline: List<TimelineEntry>, timelineNote: String, onNewIncomeEntry: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Linha do tempo do ano", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(
                    "13º, bônus, restituição e outras entradas extras", fontSize = 11.sp, color = FinaiColors.TextMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(FinaiColors.Ink)
                    .clickable(onClick = onNewIncomeEntry)
                    .padding(7.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Registrar entrada extra (13º, bônus, restituição...)", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                .background(FinaiColors.Surface)
                .padding(vertical = 16.dp),
        ) {
            if (timeline.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(timeline) { t ->
                        Column(modifier = Modifier.width(96.dp)) {
                            Text(t.month.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(t.tone.line),
                            )
                            Text(t.amount, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.tone.color)
                            Text(t.label, fontSize = 10.5.sp, lineHeight = 14.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (timeline.isNotEmpty()) 14.dp else 0.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FinaiColors.SurfaceSunken)
                    .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text(timelineNote, fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextBody)
            }
        }
    }
}

@Composable
private fun NextWeekSection(week: List<WeekBill>, onOpenAgenda: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Próximos 7 dias", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                "Abrir agenda", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
                modifier = Modifier.clickable(onClick = onOpenAgenda),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (week.isEmpty()) {
            Text(
                "Nenhuma conta nos próximos 7 dias.", fontSize = 12.sp, color = FinaiColors.TextMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                    .background(FinaiColors.Surface),
            ) {
                week.forEachIndexed { index, w ->
                    if (index > 0) androidx.compose.material3.HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp)
                    WeekBillRow(w)
                }
            }
        }
    }
}

@Composable
private fun WeekBillRow(w: WeekBill) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(FinaiColors.SurfaceMuted),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(w.day, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text(w.mon, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(w.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(w.cat, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(w.amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(w.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = w.statusTone.color, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * "Coach de comportamento" (planning.md §3.1/§9 Fase 5). [pattern] já foi
 * decidido por [com.finai.app.domain.BehaviorCoach] — 100% local; [insight]
 * é só a IA reescrevendo [pattern.detail] em linguagem mais natural, com o
 * texto determinístico como fallback imediato (mesma convenção do
 * simulador em `BuySimulatorSheet.kt`), nunca "carregando" para um dado que
 * já está pronto para mostrar.
 */
@Composable
private fun CoachCard(pattern: BehaviorPattern, insight: AiText?, onOpenChat: () -> Unit) {
    val body = (insight as? AiText.Ready)?.text ?: pattern.detail
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(FinaiColors.IndigoDeepStart, FinaiColors.IndigoDeepEnd)))
            .padding(16.dp),
    ) {
        Text("PADRÃO DE GASTO DO MÊS", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.55f))
        Text(
            pattern.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
            lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp),
        )
        AiRichText(body, palette = AiTextPalette.OnDark, modifier = Modifier.padding(top = 7.dp))
        Box(
            modifier = Modifier
                .padding(top = 13.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onOpenChat)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("Conversar sobre isso", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
