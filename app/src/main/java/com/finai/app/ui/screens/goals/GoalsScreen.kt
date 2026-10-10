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
import com.finai.app.ui.components.PageTitle
import androidx.compose.foundation.lazy.LazyListState
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
 * Objetivos em três camadas, da leitura mais rápida para a mais detalhada:
 * 1. o card escuro: livre até o salário e, se houver, **uma** recomendação ([GoalPlanning.isPlanTip]);
 *    a comparação "metas pedem × sobra prevista" fica numa linha, com o resto em "Ver planejamento";
 * 2. os pontos de atenção que sobram (a recomendação do card e o conflito de sobra não se repetem);
 * 3. um card compacto por meta, com o que é secundário em "Detalhes".
 * A IA não comenta nada sozinha: roda quando o usuário toca em "Analisar com IA".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalsScreen(
    listState: LazyListState,
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
    dismissedCount: Int = 0,
    onDismissInsight: (GoalInsight) -> Unit = {},
    onRestoreDismissed: () -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<Goal?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    // A análise fica guardada no AiViewModel; aqui só se ela está à vista.
    var planAnalysisOpen by remember { mutableStateOf(false) }
    // Uma recomendação aparece num lugar só: a que trata do livre sobe para o card escuro,
    // e o conflito de sobra já está na linha "metas pedem × sobra" do mesmo card.
    val planTip = remember(insights) { insights.firstOrNull { it.global && GoalPlanning.isPlanTip(it) } }
    val conflict = remember(insights) { insights.firstOrNull { it.id == GoalPlanning.CONFLICT_ID } }
    val listed = remember(insights, planTip) {
        GoalPlanning.topInsights(insights.filter { it != planTip && it.id != GoalPlanning.CONFLICT_ID })
            .take(if (planTip != null) GoalPlanning.MAX_GLOBAL - 1 else GoalPlanning.MAX_GLOBAL)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        // O rodapé de ScreenContentPadding (140 dp + inset) deixa o último card acima da barra de baixo.
        state = listState,
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            PageTitle("Objetivos", listState, trailing = { ActionButton("+ Novo", onNewGoal, primary = true) })
        }

        item(key = "plan") {
            PlanningCard(
                goals = goals, overview = overview, plan = plan, tip = planTip, conflict = conflict,
                onExplain = onExplain, onInsightAction = onInsightAction, onDismissInsight = onDismissInsight,
                onAnalyze = { planAnalysisOpen = true; onAnalyzePlan() },
            )
        }

        if (planAnalysisOpen && planAnalysis != null) {
            item(key = "plan-analysis") {
                AiAnalysisBlock(planAnalysis, onRefresh = onAnalyzePlan, onClose = { planAnalysisOpen = false })
            }
        }

        if (goals.isNotEmpty() && (listed.isNotEmpty() || dismissedCount > 0)) {
            item(key = "insights") {
                InsightsSection(listed, onInsightAction, onDismissInsight, dismissedCount, onRestoreDismissed)
            }
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
                onDismissInsight = onDismissInsight,
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
 * O card escuro. Primeiro o dinheiro de agora (livre até o salário) e, se houver, uma única
 * recomendação. Depois a pergunta "as metas cabem?" numa linha com barra. Nomes das metas, o
 * horizonte da média, salário estimado e metas vencidas abrem em "Ver planejamento".
 */
@Composable
private fun PlanningCard(
    goals: List<Goal>,
    overview: GoalsOverview?,
    plan: FinancialPlan?,
    tip: GoalInsight?,
    conflict: GoalInsight?,
    onExplain: () -> Unit,
    onInsightAction: (GoalInsight) -> Unit,
    onDismissInsight: (GoalInsight) -> Unit,
    onAnalyze: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FinaiColors.Ink)
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
    ) {
        if (plan == null || overview == null) {
            Text("Calculando…", fontSize = 14.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(bottom = 12.dp))
            return@Column
        }

        // 1. O livre até o salário: dinheiro de agora, não média de meses.
        val shortfall = plan.shortfall
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    (if (shortfall != null) plan.untilShortLabel.replaceFirst("Livre", "Falta") else plan.untilShortLabel).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shortfall != null) RoseOnDark else FinaiColors.TextOnDarkMuted,
                )
                Text(
                    if (shortfall != null) "− ${brl(shortfall.cents)}" else brl(plan.livreCents.coerceAtLeast(0)),
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (shortfall != null) RoseOnDark else Color.White,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onExplain).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Entenda ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.Emerald)
            }
        }
        tip?.let { InsightRow(it, onInsightAction, onDismissInsight, onDark = true, divider = false) }

        // 2. As metas cabem? Necessário por mês × sobra média prevista.
        if (overview.totalMonthlyCents > 0) {
            Box(Modifier.padding(top = 6.dp, end = 6.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            val average = overview.averageSurplusCents
            val gap = overview.gapCents
            Row(modifier = Modifier.padding(top = 12.dp, end = 6.dp), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Metas pedem", fontSize = 12.sp, color = FinaiColors.TextOnDarkMuted)
                    Text("${brl(overview.totalMonthlyCents)}/mês", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sobra prevista", fontSize = 12.sp, color = FinaiColors.TextOnDarkMuted)
                    Text(
                        average?.let { "${brl(it)}/mês" } ?: "—",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
            }
            if (average != null && gap != null) {
                // A barra mostra quanto do necessário a sobra cobre; sem sobra, vazia.
                val coverage = (average.coerceAtLeast(0).toFloat() / overview.totalMonthlyCents).coerceIn(0f, 1f)
                val animated by animateFloatAsState(coverage, finaiTween(FinaiMotion.Standard), label = "coverage")
                ProgressTrack(
                    progress = animated,
                    fillColor = if (gap > 0) FinaiColors.Rose else FinaiColors.Emerald,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    height = 6.dp,
                    modifier = Modifier.padding(top = 8.dp, end = 6.dp),
                )
                Text(
                    if (gap > 0) "Faltam ${brl(gap)}/mês para todas as metas" else "Cabe, com ${brl(-gap)}/mês de folga",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (gap > 0) RoseOnDark else FinaiColors.Emerald,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text("Sem renda cadastrada: não dá para saber se cabe.", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        } else if (goals.isNotEmpty() && overview.needs.isEmpty() && overview.reservesBalanceCents > 0) {
            Text("Nenhuma meta com valor-alvo pedindo aporte.", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(top = 8.dp))
        }

        // 3. Sob demanda: de onde vem cada número e o que fazer com o conflito.
        AnimatedVisibility(
            visible = open && overview.totalMonthlyCents > 0,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                overview.needs.forEach { DarkDetailRow(it.name, "${brl(it.neededCents)}/mês") }
                if (overview.reservesPlannedCents > 0) DarkDetailRow("Reservas contínuas", "${brl(overview.reservesPlannedCents)}/mês")
                if (overview.averageSurplusCents != null) {
                    Text(
                        "Sobra: média dos próximos ${overview.monthsInAverage} meses, antes dos aportes" +
                            if (overview.surplusUsesEstimatedSalary) ", com salário estimado." else ".",
                        fontSize = 12.sp, color = FinaiColors.TextOnDarkFaint,
                    )
                }
                if (overview.overdueNames.isNotEmpty()) {
                    Text("Fora do total (prazo vencido): " + overview.overdueNames.joinToString(), fontSize = 12.sp, color = FinaiColors.TextOnDarkFaint)
                }
                conflict?.let {
                    Text(
                        "Comparar cenários ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.Emerald,
                        modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).clickable { onInsightAction(it) }.padding(vertical = 10.dp),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (overview.totalMonthlyCents > 0) {
                DarkTextAction(if (open) "Ocultar planejamento" else "Ver planejamento", { open = !open }, color = FinaiColors.TextOnDarkMuted)
            }
            Box(Modifier.weight(1f))
            if (goals.isNotEmpty()) DarkTextAction("Analisar com IA", onAnalyze, color = FinaiColors.Emerald)
        }
    }
}

@Composable
private fun DarkTextAction(label: String, onClick: () -> Unit, color: Color) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DarkDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

private fun severityColor(s: InsightSeverity): Color = when (s) {
    InsightSeverity.Risk -> FinaiColors.Rose
    InsightSeverity.Attention -> FinaiColors.Amber
    InsightSeverity.Info -> FinaiColors.Emerald
}

/**
 * Os pontos de atenção que não subiram para o card escuro, cada um uma linha que abre o porquê e
 * a ação. Sem nenhum, a seção some (fica só "Mostrar dispensados", se houver).
 */
@Composable
private fun InsightsSection(
    insights: List<GoalInsight>,
    onAction: (GoalInsight) -> Unit,
    onDismiss: (GoalInsight) -> Unit,
    dismissedCount: Int,
    onRestoreDismissed: () -> Unit,
) {
    if (insights.isEmpty()) {
        // Desfaz qualquer dispensa, inclusive "manter separadas".
        TextAction("Mostrar pontos dispensados ($dismissedCount)", onRestoreDismissed, color = FinaiColors.TextTertiary)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
    ) {
        Text("Pontos de atenção", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        insights.forEachIndexed { i, insight -> InsightRow(insight, onAction, onDismiss, divider = i > 0) }
        if (dismissedCount > 0) {
            TextAction("Mostrar dispensados ($dismissedCount)", onRestoreDismissed, color = FinaiColors.TextTertiary)
        }
    }
}

/** Uma linha com o ponto; o porquê, a ação e a dispensa só abrem ao tocar. */
@Composable
private fun InsightRow(
    insight: GoalInsight,
    onAction: (GoalInsight) -> Unit,
    onDismiss: (GoalInsight) -> Unit,
    onDark: Boolean = false,
    divider: Boolean = true,
) {
    var open by remember(insight.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, finaiTween(FinaiMotion.Quick), label = "chevron")
    val titleColor = if (onDark) Color.White else FinaiColors.TextPrimary
    val detailColor = if (onDark) FinaiColors.TextOnDarkMuted else FinaiColors.TextSecondary
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(if (onDark) Color.White.copy(alpha = 0.12f) else FinaiColors.BorderFaint))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(severityColor(insight.severity)))
            Text(
                insight.title, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = titleColor,
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 6.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Fechar" else "Ver o porquê",
                tint = if (onDark) FinaiColors.TextOnDarkMuted else FinaiColors.TextTertiary, modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 8.dp, bottom = 8.dp)) {
                Text(insight.detail, fontSize = 13.sp, lineHeight = 18.sp, color = detailColor)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val actionColor = if (onDark) FinaiColors.Emerald else FinaiColors.EmeraldDark
                    if (insight.action != InsightAction.None) TextAction(insight.actionLabel + " ›", { onAction(insight) }, color = actionColor)
                    // Sem saída, um aviso já resolvido ficaria na tela até a meta acabar.
                    TextAction(insight.dismissLabel, { onDismiss(insight) }, color = detailColor)
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
private fun AiAnalysisBlock(
    analysis: AiText,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FinaiColors.SurfaceSunken)
            .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ANÁLISE DA IA", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            // A análise é memorizada pelos números: refazer só faz sentido quando falhou.
            if (onRefresh != null && analysis is AiText.Unavailable) TextAction("Tentar de novo", onRefresh, color = FinaiColors.EmeraldDark)
            if (onClose != null) TextAction("Fechar", onClose, color = FinaiColors.TextTertiary)
        }
        Column(modifier = Modifier.padding(end = 8.dp)) {
            when (analysis) {
                AiText.Loading -> Text("Analisando…", fontSize = 13.sp, color = FinaiColors.TextTertiary)
                is AiText.Unavailable -> Text(analysis.reason, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary)
                is AiText.Ready -> {
                    if (AiPromptBuilder.isNothingRelevant(analysis.text)) {
                        Text("Nada novo além do que já está na tela.", fontSize = 13.sp, color = FinaiColors.TextSecondary)
                    } else {
                        val rows = remember(analysis.text) { AiReplyFormat.decisions(analysis.text) }
                        if (rows.isEmpty()) AiRichText(analysis.text)
                        rows.forEachIndexed { i, row -> AnalysisRow(row, first = i == 0) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisRow(row: AiReplyFormat.Decision, first: Boolean) {
    var open by remember(row) { mutableStateOf(false) }
    val question = row.action.startsWith("Pergunta", ignoreCase = true)
    Column(
        modifier = Modifier
            .padding(top = if (first) 0.dp else 8.dp)
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
    GoalBadge.NotStarted -> FinaiColors.TextTertiary
    else -> FinaiColors.Emerald
}

/**
 * Card recolhido, em quatro linhas: nome e selo; guardado de alvo e percentual; barra; prazo e
 * aporte mensal. Por baixo, "Adicionar valor" e o chevron de detalhes. Tocar no card também abre
 * os detalhes. Reserva contínua troca alvo, barra e prazo pelo aporte planejado.
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
    onDismissInsight: (GoalInsight) -> Unit,
    modifier: Modifier = Modifier,
    detailsModifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, finaiTween(FinaiMotion.Quick), label = "chevron")
    val progress by animateFloatAsState(goal.progress, finaiTween(FinaiMotion.Standard), label = "progress")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Fechar detalhes" else "Ver detalhes", onClick = onToggleExpanded)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    goal.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg, modifier = Modifier.padding(start = 8.dp))
            }

            if (goal.openReserve) {
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(formatBrl0(goal.saved), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        if (goal.plannedMonthly > 0) "${formatBrl0(goal.plannedMonthly)}/mês" else "Sem aporte planejado",
                        fontSize = if (goal.plannedMonthly > 0) 14.sp else 13.sp,
                        fontWeight = if (goal.plannedMonthly > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (goal.plannedMonthly > 0) FinaiColors.TextPrimary else FinaiColors.TextTertiary,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            } else {
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(formatBrl0(goal.saved), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Text(
                        " de ${formatBrl0(goal.target)}", fontSize = 13.sp, color = FinaiColors.TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(bottom = 3.dp),
                    )
                    Text(
                        "${(goal.progress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                ProgressTrack(progress = progress, fillColor = progressColor(goal.badge), height = 6.dp, modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (goal.overdue) "Prazo vencido · ${goal.eta}" else "Até ${goal.eta}",
                        fontSize = 13.sp, color = if (goal.overdue) FinaiColors.RoseDark else FinaiColors.TextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!goal.overdue && goal.monthlyNeeded > 0) {
                        Text("${formatBrl0(goal.monthlyNeeded)}/mês", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction("+ " + goal.action, onContribute)
            Box(Modifier.weight(1f))
            Box(
                modifier = detailsModifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleExpanded),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "Fechar detalhes" else "Ver detalhes",
                    tint = FinaiColors.TextTertiary, modifier = Modifier.size(22.dp).rotate(rotation),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
        ) {
            GoalDetails(goal, insights, analysis, onSimulate, onAnalyze, onInsightAction, onDismissInsight, onEdit, onDelete)
        }
    }
}

/**
 * O que sai do card recolhido: números de apoio, o porquê do selo em uma linha, pontos da meta,
 * as ações e a análise da IA (só quando pedida).
 */
@Composable
private fun GoalDetails(
    goal: Goal,
    insights: List<GoalInsight>,
    analysis: AiText?,
    onSimulate: () -> Unit,
    onAnalyze: () -> Unit,
    onInsightAction: (GoalInsight) -> Unit,
    onDismissInsight: (GoalInsight) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var analysisOpen by remember(goal.id) { mutableStateOf(analysis != null) }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FinaiColors.BorderFaint))
        // O porquê do selo (Atenção/Atrasado), quando ele não se explica sozinho.
        if (goal.headline.isNotBlank()) {
            Text(goal.headline, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = goal.badge.fg, modifier = Modifier.padding(top = 10.dp))
        }
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!goal.openReserve) DetailRow("Falta guardar", formatBrl0(goal.missing))
            goal.projectedAvailable?.let { DetailRow("Sobra prevista até o prazo", formatBrl0(it.coerceAtLeast(0.0))) }
            if (goal.recommended > 0) DetailRow("Sugestão do plano até o salário", formatBrl0(goal.recommended), FinaiColors.EmeraldDark)
            DetailRow("Prioridade", goal.priority.toString())
        }
        // Por que o plano não sugere aporte agora, em uma linha.
        if (goal.planShort.isNotBlank()) {
            Text(goal.planShort, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (goal.description.isNotBlank()) {
            Text(goal.description, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 8.dp))
        }
        if (insights.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 6.dp)) { insights.forEach { InsightRow(it, onInsightAction, onDismissInsight) } }
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Simular aporte", onSimulate, modifier = Modifier.weight(1f))
            ActionButton(
                if (analysisOpen && analysis != null) "Ocultar análise" else "Analisar com IA",
                {
                    if (analysisOpen && analysis != null) analysisOpen = false
                    else { analysisOpen = true; onAnalyze() }
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (analysisOpen && analysis != null) {
            AiAnalysisBlock(analysis, modifier = Modifier.padding(top = 10.dp), onRefresh = onAnalyze)
        }
        Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("Editar", onEdit)
            TextAction("Excluir", onDelete, color = FinaiColors.RoseDark)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = FinaiColors.TextPrimary) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
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
