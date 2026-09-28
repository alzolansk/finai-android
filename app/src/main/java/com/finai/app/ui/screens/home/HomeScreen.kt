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
import androidx.compose.foundation.layout.heightIn
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
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.HomeSituation
import com.finai.app.domain.SituationAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.offset
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.SectionHeader
import com.finai.app.ui.components.TextAction
import com.finai.app.ui.components.tipTarget
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun HomeScreen(
    greeting: String,
    goals: List<Goal>,
    saldoLabel: String,
    saldoPositivo: Boolean,
    situation: HomeSituation?,
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
    onExplainSafe: () -> Unit,
    onOpenAgenda: () -> Unit,
    onAskAbout: (AssistantTopic) -> Unit,
    onNewIncomeEntry: () -> Unit,
    /** "Informar renda": abre o lançamento de receita já marcado como renda principal. */
    onAddMainIncome: () -> Unit,
    onNewEntry: () -> Unit,
) {
    // Ordem por urgência (Fase 7, item 3): situação → o que vence → planejamento.
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary)
        }

        item {
            SituationCard(
                situation = situation,
                safeNote = safeNote,
                onAction = { action ->
                    when (action) {
                        SituationAction.SeeCommitments -> onOpenAgenda()
                        SituationAction.AddIncome -> onAddMainIncome()
                        SituationAction.CanIBuy -> onOpenSimulator()
                    }
                },
                onOpenBudgets = onOpenBudgets,
                onExplain = onExplainSafe,
            )
        }

        item { NextWeekSection(week, onOpenAgenda, onNewEntry) }

        item {
            Text(
                "PLANEJAMENTO", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item { SaldoCard(saldoLabel, saldoPositivo) }

        item { GoalsCarousel(goals, onOpenGoals, onNewGoal) }

        item { DecisionsCard(decisions, onAskAbout) }

        item { TimelineSection(timeline, timelineNote, onNewIncomeEntry) }

        if (showCoach && behaviorPattern != null) {
            item { CoachCard(behaviorPattern, coachInsight) { onAskAbout(AssistantTopics.behaviorPattern(behaviorPattern)) } }
        }
    }
}

/**
 * Balanço do mês = recebimentos − contas a pagar do mês corrente, a mesma conta dos totais
 * da Agenda (MonthCashFlow). Responde "o mês fecha?"; quem paga cada conta é o ciclo do
 * salário, no [SituationCard].
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
            Text("Balanço de $mes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(
                "Entradas − saídas do mês, como na Agenda", fontSize = 12.5.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            saldoLabel, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            color = if (saldoPositivo) FinaiColors.EmeraldDark else FinaiColors.RoseDark,
        )
    }
}

/**
 * O topo da Início: frase de situação, **um** valor com o período explícito (o livre até o
 * salário, ou a falta), o valor por dia como apoio, "Entenda este valor" e uma ação só —
 * a que resolve o estado atual ([HomeSituation.action]). "Ajustar limites" fica aqui como
 * ação secundária: é um dos dois caminhos para Limites (decisão do usuário, 28/09/2026).
 */
@Composable
private fun SituationCard(
    situation: HomeSituation?,
    safeNote: String,
    onAction: (SituationAction) -> Unit,
    onOpenBudgets: () -> Unit,
    onExplain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tipTarget("home.situation")
            .clip(RoundedCornerShape(24.dp))
            .background(FinaiColors.Ink)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
    ) {
        if (situation == null) {
            Text("Calculando…", fontSize = 14.sp, color = FinaiColors.TextOnDarkMuted)
        } else {
            Text(
                situation.headline, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                color = if (situation.shortfall) RoseOnDark else Color.White,
            )
            Text(
                situation.label, style = MaterialTheme.typography.labelSmall,
                color = if (situation.shortfall) RoseOnDark else FinaiColors.TextOnDarkMuted,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                if (situation.shortfall) "Faltam ${formatBrl0(situation.mainCents / 100.0)}" else formatBrl0(situation.mainCents / 100.0),
                style = MaterialTheme.typography.displayLarge,
                color = if (situation.shortfall) RoseOnDark else Color.White,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!situation.shortfall) {
                Text(
                    "cerca de ${formatBrl0(situation.perDayCents / 100.0)} por dia · ${situation.days} ${if (situation.days == 1) "dia" else "dias"}",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextOnDarkMuted,
                )
            }
            Text(
                safeNote, fontSize = 12.5.sp, lineHeight = 17.sp,
                color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(top = 8.dp),
            )
            // Alvo de toque de 48 dp: o valor precisa ser explicável em um toque.
            Box(
                modifier = Modifier.tipTarget("home.explain").heightIn(min = 48.dp).clickable(onClick = onExplain),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Entenda este valor ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.Emerald)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (situation.shortfall) FinaiColors.RoseDark else FinaiColors.EmeraldDark)
                        .clickable { onAction(situation.action) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(situation.action.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenBudgets)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ajustar limites", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

/** Vermelho legível sobre o [FinaiColors.Ink] (6,6:1). */
private val RoseOnDark = Color(0xFFFB7185)

@Composable
private fun GoalsCarousel(goals: List<Goal>, onOpenGoals: () -> Unit, onNewGoal: () -> Unit) {
    Column {
        SectionHeader("Seus objetivos", action = if (goals.isNotEmpty()) "Ver todos" else null, onAction = onOpenGoals)
        Spacer(Modifier.height(6.dp))
        if (goals.isEmpty()) {
            EmptyStateCard(
                "Nenhum objetivo ainda. Uma viagem, uma reserva de emergência ou uma compra planejada: o app diz se o aporte cabe no que sobra.",
                "Criar objetivo", onNewGoal,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(goals.take(2)) { goal -> GoalTeaserCard(goal, onOpenGoals) }
                item { NewGoalCard(onNewGoal) }
            }
        }
    }
}

@Composable
private fun GoalTeaserCard(goal: Goal, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(248.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                goal.kind.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = FinaiColors.TextMuted,
            )
            com.finai.app.ui.components.PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg)
        }
        Text(
            goal.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(goal.eta, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(formatBrl0(goal.saved), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDark)
            Text("de ${formatBrl0(goal.target)}", fontSize = 12.5.sp, color = FinaiColors.TextMuted)
        }
        ProgressTrack(
            progress = goal.progress,
            fillColor = FinaiColors.Emerald,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            goal.note, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextSecondary,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

@Composable
private fun NewGoalCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = FinaiColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text("Novo objetivo", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary)
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
            Text("Decisões para você", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        }
        when (decisions) {
            null, AiText.Loading -> Text(
                "Analisando seus números para sugerir decisões...",
                fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 10.dp),
            )
            is AiText.Unavailable -> Text(
                decisions.reason, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 10.dp),
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
            .heightIn(min = 48.dp)
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
                    fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
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
            Text("Conversar", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
        }
    }
}

@Composable
private fun TimelineSection(timeline: List<TimelineEntry>, timelineNote: String, onNewIncomeEntry: () -> Unit) {
    Column {
        SectionHeader(
            "Linha do tempo do ano", subtitle = "13º, bônus, restituição e outras entradas extras",
            action = "Lançar extra", onAction = onNewIncomeEntry,
        )
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
                            Text(t.month.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(t.tone.line),
                            )
                            Text(t.amount, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = t.tone.color)
                            Text(t.label, fontSize = 12.sp, lineHeight = 16.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 3.dp))
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
                Text(timelineNote, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextBody)
            }
        }
    }
}

@Composable
private fun NextWeekSection(week: List<WeekBill>, onOpenAgenda: () -> Unit, onNewEntry: () -> Unit) {
    Column(modifier = Modifier.tipTarget("home.week")) {
        SectionHeader("Próximos 7 dias", action = "Abrir agenda", onAction = onOpenAgenda)
        Spacer(Modifier.height(4.dp))
        if (week.isEmpty()) {
            EmptyStateCard(
                "Nada vence nos próximos 7 dias. Lance suas contas com data para vê-las aqui antes do vencimento.",
                "Lançar conta", onNewEntry,
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
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FinaiColors.SurfaceMuted),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(w.day, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text(w.mon, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(w.cat, fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(w.amount, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(w.status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = w.statusTone.color, modifier = Modifier.padding(top = 2.dp))
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
    // Superfície neutra (Fase 7, item 7): texto de IA não pode ter mais destaque que os números.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text("PADRÃO DE GASTO DO MÊS", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted)
        Text(
            pattern.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp),
        )
        AiRichText(body, modifier = Modifier.padding(top = 6.dp))
        TextAction("Conversar sobre isso ›", onOpenChat, modifier = Modifier.offset(x = (-6).dp))
    }
}
