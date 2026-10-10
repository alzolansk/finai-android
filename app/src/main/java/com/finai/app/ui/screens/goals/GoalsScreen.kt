package com.finai.app.ui.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.model.Goal
import com.finai.app.data.model.GoalBadge
import com.finai.app.domain.AiPromptBuilder
import com.finai.app.domain.AiReplyFormat
import com.finai.app.domain.FinancialPlan
import com.finai.app.domain.GoalInsight
import com.finai.app.domain.GoalPlanning
import com.finai.app.domain.GoalsOverview
import com.finai.app.domain.InsightAction
import com.finai.app.domain.InsightSeverity
import com.finai.app.ui.components.ActionButton
import com.finai.app.ui.components.AiRichText
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.FadeInAppear
import com.finai.app.ui.components.OverflowAction
import com.finai.app.ui.components.OverflowMenu
import com.finai.app.ui.components.PillTag
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.components.TextAction
import com.finai.app.ui.components.tipTarget
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.ui.theme.themedFill
import com.finai.app.ui.theme.themedText
import com.finai.app.util.formatBrl0

/** Rosa legível sobre o cartão escuro (o mesmo da Início). */
private val RoseOnDark = Color(0xFFFDA4AF)

/**
 * Objetivos como central de planejamento: no topo, quanto as metas pedem por mês ao lado do que
 * sobra; depois, só os pontos que mudam uma decisão (calculados em [GoalPlanning], sem IA); por
 * fim, um card compacto por meta. A IA não comenta nada sozinha: roda quando o usuário toca em
 * "Analisar com IA", no planejamento inteiro ou numa meta, e só pode citar os números do app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalsScreen(
    goals: List<Goal>,
    completedGoals: List<Goal>,
    plan: FinancialPlan?,
    overview: GoalsOverview?,
    insights: List<GoalInsight>,
    planAnalysis: AiText?,
    goalAnalyses: Map<String, AiText>,
    onNewGoal: () -> Unit,
    onContribute: (Goal) -> Unit,
    onEdit: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    onSimulate: (Goal) -> Unit,
    onExplain: () -> Unit,
    onAnalyzePlan: () -> Unit,
    onAnalyzeGoal: (Goal) -> Unit,
    onInsightAction: (GoalInsight) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Goal?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val topInsights = remember(insights) { GoalPlanning.topInsights(insights) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        // O rodapé de 132 dp de ScreenContentPadding deixa o último card acima da barra de baixo.
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Objetivos", style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary, modifier = Modifier.weight(1f))
                ActionButton("+ Novo", onNewGoal, primary = true)
            }
        }

        item { PlanningCard(goals, overview, plan, onExplain) }

        if (goals.isNotEmpty()) {
            item(key = "insights") { InsightsSection(topInsights, planAnalysis, onAnalyzePlan, onInsightAction) }
        }

        if (goals.isEmpty()) {
            item {
                FadeInAppear {
                    EmptyStateCard(
                        if (completedGoals.isEmpty()) "Nenhum objetivo ainda. Crie uma meta e o app mostra quanto guardar por mês."
                        else "Nenhuma meta em andamento. Que tal a próxima?",
                        "Criar objetivo", onNewGoal,
                    )
                }
            }
        }

        items(goals, key = { it.id }) { goal ->
            val first = goal.id == goals.first().id
            val isExpanded = goal.id in expanded
            GoalCard(
                goal = goal,
                expanded = isExpanded,
                insights = remember(insights, goal.id) { GoalPlanning.insightsFor(insights, goal.id.toLong()) },
                analysis = goalAnalyses[goal.id],
                onToggleExpanded = { expanded = if (isExpanded) expanded - goal.id else expanded + goal.id },
                onContribute = { onContribute(goal) },
                onEdit = { onEdit(goal) },
                onDelete = { pendingDelete = goal },
                onSimulate = { onSimulate(goal) },
                onAnalyze = { onAnalyzeGoal(goal) },
                onInsightAction = onInsightAction,
                modifier = Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard))
                    .then(if (first) Modifier.tipTarget("goals.card") else Modifier),
                detailsModifier = if (first) Modifier.tipTarget("goals.details") else Modifier,
            )
        }

        if (completedGoals.isNotEmpty()) {
            item(key = "completed-header") {
                Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Metas concluídas", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    Text("  ${completedGoals.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
                }
            }
            items(completedGoals, key = { "done-" + it.id }) { goal ->
                CompletedGoalCard(
                    goal, onContribute = { onContribute(goal) }, onEdit = { onEdit(goal) }, onDelete = { pendingDelete = goal },
                    modifier = Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)),
                )
            }
        }
    }

    pendingDelete?.let { goal ->
        ConfirmDeleteDialog(
            title = "Excluir objetivo?",
            description = "\"${goal.name}\" será removido permanentemente, junto com o progresso guardado nele (${formatBrl0(goal.saved)}).",
            onDismiss = { pendingDelete = null },
            onConfirm = { onDelete(goal); pendingDelete = null },
        )
    }
}

private fun brl(cents: Long) = formatBrl0(cents / 100.0)

/**
 * Visão consolidada: o necessário por mês (todas as metas com alvo e prazo à frente), a sobra
 * prevista para comparar e, à parte, o livre até o salário. São janelas de tempo diferentes, e
 * por isso ficam em linhas separadas, cada uma com o rótulo do que mede.
 */
@Composable
private fun PlanningCard(goals: List<Goal>, overview: GoalsOverview?, plan: FinancialPlan?, onExplain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FinaiColors.Ink)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
    ) {
        if (plan == null || overview == null) {
            Text("Calculando…", fontSize = 14.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(bottom = 12.dp))
            return@Column
        }
        if (overview.totalMonthlyCents > 0) {
            Text("APORTES MENSAIS NECESSÁRIOS", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextOnDarkMuted)
            Text(
                "${brl(overview.totalMonthlyCents)}/mês", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            val names = overview.needs.map { it.name } + if (overview.reservesPlannedCents > 0) listOf("reservas") else emptyList()
            Text(
                names.joinToString(" + "), fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            )
            val average = overview.averageSurplusCents
            Column(modifier = Modifier.padding(top = 12.dp, end = 4.dp)) {
                if (average == null) {
                    Text("Sem renda cadastrada: não dá para comparar com o orçamento.", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sobra prevista", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.weight(1f))
                        Text("${brl(average)}/mês", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    val gap = overview.gapCents ?: 0
                    // A barra mostra quanto do necessário a sobra cobre; sem sobra, vazia.
                    val coverage = if (overview.totalMonthlyCents <= 0) 1f
                    else (average.coerceAtLeast(0).toFloat() / overview.totalMonthlyCents).coerceIn(0f, 1f)
                    ProgressTrack(
                        progress = coverage,
                        fillColor = if (gap > 0) FinaiColors.Rose else FinaiColors.Emerald,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        height = 6.dp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        if (gap > 0) "Faltam ${brl(gap)}/mês para todas as metas" else "Cabe: sobram ${brl(-gap)}/mês depois das metas",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (gap > 0) RoseOnDark else FinaiColors.Emerald,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "Média dos próximos ${overview.monthsInAverage} meses, antes de aportes" +
                            if (overview.surplusUsesEstimatedSalary) " · com salário estimado" else "",
                        fontSize = 12.sp, color = FinaiColors.TextOnDarkFaint, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (overview.overdueNames.isNotEmpty()) {
                    Text(
                        "Fora do total (prazo vencido): " + overview.overdueNames.joinToString(),
                        fontSize = 12.sp, color = FinaiColors.TextOnDarkFaint, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Box(Modifier.padding(top = 12.dp, end = 4.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        } else if (goals.isNotEmpty() && overview.reservesBalanceCents > 0 && overview.needs.isEmpty()) {
            Text("Nenhuma meta com valor-alvo pedindo aporte agora.", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(bottom = 8.dp))
        }

        // O livre até o salário: dinheiro de agora, não média de meses.
        val shortfall = plan.shortfall
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    if (shortfall != null) plan.untilShortLabel.replaceFirst("Livre", "Falta") else plan.untilShortLabel,
                    fontSize = 12.sp, color = if (shortfall != null) RoseOnDark else FinaiColors.TextOnDarkMuted,
                )
                Text(
                    if (shortfall != null) "Faltam ${brl(shortfall.cents)}" else brl(plan.livreCents.coerceAtLeast(0)),
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (shortfall != null) RoseOnDark else Color.White,
                )
            }
            Box(
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onExplain).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Entenda ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.Emerald)
            }
        }
    }
}

private fun severityColor(s: InsightSeverity): Color = when (s) {
    InsightSeverity.Risk -> FinaiColors.Rose
    InsightSeverity.Attention -> FinaiColors.Amber
    InsightSeverity.Info -> FinaiColors.Emerald
}

/**
 * Até três pontos que pedem atenção, cada um uma linha que abre o porquê e a ação. Sem nenhum,
 * uma linha só. A análise da IA, quando pedida, aparece embaixo, separada do que o app calculou.
 */
@Composable
private fun InsightsSection(
    insights: List<GoalInsight>,
    analysis: AiText?,
    onAnalyze: () -> Unit,
    onAction: (GoalInsight) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (insights.isEmpty()) "Nada pede atenção agora" else "Pontos de atenção",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (insights.isEmpty()) FinaiColors.TextSecondary else FinaiColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TextAction(if (analysis == null) "Analisar com IA" else "Atualizar análise", onAnalyze)
        }
        insights.forEach { insight -> InsightRow(insight, onAction) }
        if (analysis != null) AiAnalysisBlock(analysis, modifier = Modifier.padding(end = 8.dp, bottom = 10.dp))
    }
}

@Composable
private fun InsightRow(insight: GoalInsight, onAction: (GoalInsight) -> Unit) {
    var open by remember(insight.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, finaiTween(FinaiMotion.Quick), label = "chevron")
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FinaiColors.BorderFaint))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(severityColor(insight.severity)))
            Text(
                insight.title, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary,
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 6.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Fechar" else "Ver o porquê",
                tint = FinaiColors.TextTertiary, modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 8.dp, bottom = 10.dp)) {
                Text(insight.detail, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary)
                if (insight.action != InsightAction.None) {
                    ActionButton(insight.actionLabel, { onAction(insight) }, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/**
 * Resposta da IA em até três linhas "ação | porquê" ([AiPromptBuilder.goalsPlanAnalysis]). O
 * porquê abre ao tocar. "NADA" vira uma frase curta, não um parágrafo de enchimento.
 */
@Composable
private fun AiAnalysisBlock(analysis: AiText, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FinaiColors.SurfaceSunken)
            .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text("ANÁLISE DA IA", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted)
        when (analysis) {
            AiText.Loading -> Text("Cruzando metas, pagamentos e sobra prevista…", fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 6.dp))
            is AiText.Unavailable -> Text(analysis.reason, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 6.dp))
            is AiText.Ready -> {
                if (AiPromptBuilder.isNothingRelevant(analysis.text)) {
                    Text(
                        "Nada além do que já está na tela. O planejamento não tem conflito que a IA consiga apontar com os seus dados.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    val rows = remember(analysis.text) { AiReplyFormat.decisions(analysis.text) }
                    if (rows.isEmpty()) AiRichText(analysis.text, modifier = Modifier.padding(top = 6.dp))
                    rows.forEach { AnalysisRow(it) }
                }
                Text(
                    "Feita só com os números do app. Resposta com valor fora deles é descartada.",
                    fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AnalysisRow(row: AiReplyFormat.Decision) {
    var open by remember(row) { mutableStateOf(false) }
    val question = row.action.startsWith("Pergunta", ignoreCase = true)
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .then(if (row.reason != null) Modifier.clickable { open = !open } else Modifier),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(if (question) "?" else "•", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (question) FinaiColors.AmberDark else FinaiColors.EmeraldDark)
            AiRichText(
                row.action.removePrefix("Pergunta:").removePrefix("Pergunta").trim(), fontSize = 13.5.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (row.reason != null) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Fechar" else "Ver o porquê",
                    tint = FinaiColors.TextTertiary, modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                )
            }
        }
        AnimatedVisibility(visible = open && row.reason != null) {
            AiRichText(row.reason.orEmpty(), modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

private fun progressColor(badge: GoalBadge): Color = when (badge) {
    GoalBadge.Attention -> FinaiColors.Amber
    GoalBadge.Late -> FinaiColors.Rose
    else -> FinaiColors.Emerald
}

/**
 * Card recolhido: nome e selo, guardado sobre o alvo com o percentual, barra, prazo e aporte
 * mensal, e as duas ações. Tudo o mais abre em "Detalhes". Reserva contínua troca alvo, barra e
 * prazo pelo aporte planejado.
 */
@Composable
private fun GoalCard(
    goal: Goal,
    expanded: Boolean,
    insights: List<GoalInsight>,
    analysis: AiText?,
    onToggleExpanded: () -> Unit,
    onContribute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSimulate: () -> Unit,
    onAnalyze: () -> Unit,
    onInsightAction: (GoalInsight) -> Unit,
    modifier: Modifier = Modifier,
    detailsModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg, modifier = Modifier.padding(start = 8.dp))
        }

        if (goal.openReserve) {
            Text(
                formatBrl0(goal.saved), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Saldo, sem valor-alvo", fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f))
                Text(
                    if (goal.plannedMonthly > 0) "${formatBrl0(goal.plannedMonthly)}/mês" else "Sem aporte planejado",
                    fontSize = if (goal.plannedMonthly > 0) 15.sp else 13.sp,
                    fontWeight = if (goal.plannedMonthly > 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (goal.plannedMonthly > 0) FinaiColors.TextPrimary else FinaiColors.TextTertiary,
                )
            }
        } else {
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatBrl0(goal.saved), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Text("/ ${formatBrl0(goal.target)}", fontSize = 13.sp, color = FinaiColors.TextMuted)
                }
                Text("${(goal.progress * 100).toInt()}%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary)
            }
            ProgressTrack(progress = goal.progress, fillColor = progressColor(goal.badge), height = 7.dp, modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (goal.overdue) "Prazo vencido: ${goal.eta}" else "Meta: ${goal.eta}",
                    fontSize = 13.sp, color = if (goal.overdue) FinaiColors.RoseDark else FinaiColors.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (!goal.overdue && goal.monthlyNeeded > 0) {
                    Text("${formatBrl0(goal.monthlyNeeded)}/mês", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                }
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(goal.action, onContribute, modifier = Modifier.weight(1f), primary = true)
            ActionButton(if (expanded) "Fechar" else "Detalhes", onToggleExpanded, modifier = detailsModifier.weight(1f))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
        ) {
            GoalDetails(goal, insights, analysis, onSimulate, onAnalyze, onInsightAction, onEdit, onDelete)
        }
    }
}

/** O que sai do card recolhido: números de apoio, o porquê do selo, pontos da meta e a IA. */
@Composable
private fun GoalDetails(
    goal: Goal,
    insights: List<GoalInsight>,
    analysis: AiText?,
    onSimulate: () -> Unit,
    onAnalyze: () -> Unit,
    onInsightAction: (GoalInsight) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FinaiColors.BorderFaint))
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!goal.openReserve) DetailRow("Falta guardar", formatBrl0(goal.missing))
            goal.projectedAvailable?.let { DetailRow("Sobra prevista até o prazo", formatBrl0(it.coerceAtLeast(0.0))) }
            if (goal.recommended > 0) DetailRow("Sugestão do plano até o salário", formatBrl0(goal.recommended))
            DetailRow("Prioridade", goal.priority.toString())
        }
        if (goal.headline.isNotBlank()) {
            Text(goal.headline, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = goal.badge.fg, modifier = Modifier.padding(top = 10.dp))
        }
        // Por que o plano não sugere aporte agora (dívida antes, falta, outra meta).
        if (!goal.openReserve && goal.recommended <= 0 && goal.planNote.isNotBlank()) {
            Text(goal.planNote, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (goal.description.isNotBlank()) {
            Text(goal.description, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 8.dp))
        }
        if (insights.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 8.dp)) { insights.forEach { InsightRow(it, onInsightAction) } }
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Simular cenários", onSimulate, modifier = Modifier.weight(1f))
            ActionButton(if (analysis == null) "Analisar com IA" else "Atualizar análise", onAnalyze, modifier = Modifier.weight(1f))
        }
        analysis?.let { AiAnalysisBlock(it, modifier = Modifier.padding(top = 4.dp)) }
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("Editar", onEdit)
            TextAction("Excluir", onDelete, color = FinaiColors.RoseDark)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
    }
}

/** Meta concluída: compacta, com data, total guardado e o que passou do alvo. */
@Composable
private fun CompletedGoalCard(
    goal: Goal,
    onContribute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val travel = goal.kind.equals("Viagem", ignoreCase = true)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background((if (travel) Color(0xFFE0F2FE) else Color(0xFFFEF3C7)).themedFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (travel) Icons.Filled.FlightTakeoff else Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = (if (travel) Color(0xFF0284C7) else Color(0xFFD97706)).themedText,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(goal.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${goal.completedLabel ?: "Concluída"} · ${formatBrl0(goal.saved)}" +
                    if (goal.exceeded > 0) " (+${formatBrl0(goal.exceeded)})" else "",
                fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
            )
        }
        TextAction("Adicionar", onContribute)
        OverflowMenu(
            listOf(OverflowAction("Editar", onClick = onEdit), OverflowAction("Excluir", destructive = true, onClick = onDelete)),
            contentDescription = "Mais ações para ${goal.name}",
        )
    }
}
