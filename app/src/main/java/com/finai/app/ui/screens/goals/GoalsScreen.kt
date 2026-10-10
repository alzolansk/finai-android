package com.finai.app.ui.screens.goals

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.model.Goal
import com.finai.app.data.model.GoalBadge
import com.finai.app.domain.AiPromptBuilder
import com.finai.app.domain.AiReplyFormat
import com.finai.app.domain.FinancialPlan
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
 * Objetivos: o livre até o salário em um cartão curto e, abaixo, um card por meta com só o que
 * decide (progresso, quanto por mês, situação). O resto (falta, sobra prevista, por que o plano
 * não sugere aporte, análise da IA) fica em "Detalhes", e a IA só é chamada quando alguém abre a
 * análise — não a cada visita à tela.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalsScreen(
    goals: List<Goal>,
    completedGoals: List<Goal>,
    plan: FinancialPlan?,
    goalInsights: Map<String, AiText>,
    onNewGoal: () -> Unit,
    onContribute: (Goal) -> Unit,
    onEdit: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    onSimulate: (Goal) -> Unit,
    onExplain: () -> Unit,
    onRequestInsights: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Goal?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var analysisOpen by remember { mutableStateOf(setOf<String>()) }

    // A leitura da IA é de todas as metas numa chamada só (AiViewModel.ensureGoalInsights,
    // memoizada pelos números): pede quando a primeira análise é aberta e de novo se as metas mudarem.
    val requestInsights by rememberUpdatedState(onRequestInsights)
    val wantsInsights = analysisOpen.isNotEmpty()
    LaunchedEffect(goals, wantsInsights) {
        if (wantsInsights) requestInsights()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Objetivos", style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary)
                    if (goals.isNotEmpty()) {
                        Text(
                            "${goals.size} em andamento", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                ActionButton("+ Novo", onNewGoal, primary = true)
            }
        }

        item { FreeUntilPaydayCard(plan, onExplain) }

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
                analysisOpen = goal.id in analysisOpen,
                insight = goalInsights[goal.id],
                onToggleExpanded = { expanded = if (isExpanded) expanded - goal.id else expanded + goal.id },
                onToggleAnalysis = {
                    analysisOpen = if (goal.id in analysisOpen) analysisOpen - goal.id else analysisOpen + goal.id
                },
                onContribute = { onContribute(goal) },
                onEdit = { onEdit(goal) },
                onDelete = { pendingDelete = goal },
                onSimulate = { onSimulate(goal) },
                modifier = Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard))
                    .then(if (first) Modifier.tipTarget("goals.card") else Modifier),
                detailsModifier = if (first) Modifier.tipTarget("goals.details") else Modifier,
            )
        }

        if (completedGoals.isNotEmpty()) {
            item(key = "completed-header") {
                Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Metas concluídas", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    Text(
                        "  ${completedGoals.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary,
                    )
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

/**
 * O mesmo livre da Início, com a data do próximo salário. A recomendação aparece só quando há
 * uma falta prevista ou uma dívida que vem antes das metas ([FinancialPlan.shortRecommendation]);
 * a conta inteira fica em "Entenda este valor".
 */
@Composable
private fun FreeUntilPaydayCard(plan: FinancialPlan?, onExplain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FinaiColors.Ink)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        if (plan == null) {
            Text("Calculando…", fontSize = 14.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(bottom = 12.dp))
            return@Column
        }
        val shortfall = plan.shortfall != null
        Text(plan.untilShortLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextOnDarkMuted)
        Text(
            formatBrl0(plan.livreCents.coerceAtLeast(0) / 100.0),
            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
        plan.shortRecommendation?.let {
            Text(
                it, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                color = if (shortfall) RoseOnDark else Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Box(
            modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onExplain),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Entenda este valor ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.Emerald)
        }
    }
}

private fun progressColor(badge: GoalBadge): Color = when (badge) {
    GoalBadge.Attention -> FinaiColors.Amber
    GoalBadge.Late -> FinaiColors.Rose
    else -> FinaiColors.Emerald
}

@Composable
private fun GoalCard(
    goal: Goal,
    expanded: Boolean,
    analysisOpen: Boolean,
    insight: AiText?,
    onToggleExpanded: () -> Unit,
    onToggleAnalysis: () -> Unit,
    onContribute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSimulate: () -> Unit,
    modifier: Modifier = Modifier,
    detailsModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .clickable(onClick = onToggleExpanded)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
            OverflowMenu(
                listOf(OverflowAction("Editar", onClick = onEdit), OverflowAction("Excluir", destructive = true, onClick = onDelete)),
                contentDescription = "Mais ações para ${goal.name}",
            )
        }
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatBrl0(goal.saved), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(
                    " de ${formatBrl0(goal.target)}", fontSize = 13.sp, color = FinaiColors.TextMuted,
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                )
                Text(
                    "${(goal.progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            ProgressTrack(progress = goal.progress, fillColor = progressColor(goal.badge), height = 8.dp, modifier = Modifier.padding(top = 8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)) {
                        append("${formatBrl0(goal.monthlyNeeded)}/mês")
                    }
                    append(" até ${goal.eta}")
                },
                fontSize = 13.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (goal.headline.isNotBlank()) {
                Text(
                    goal.headline, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = goal.badge.fg,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (goal.recommended > 0) {
                Text(
                    "Sugestão do plano: guardar ${formatBrl0(goal.recommended)} até o salário",
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FinaiColors.EmeraldSoftBg)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
                exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
            ) {
                GoalDetails(goal, analysisOpen, insight, onSimulate, onToggleAnalysis)
            }

            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(goal.action, onContribute, modifier = Modifier.weight(1f), primary = true)
                ActionButton(if (expanded) "Menos" else "Detalhes", onToggleExpanded, modifier = detailsModifier)
            }
        }
    }
}

/** O que sai do card fechado: números de apoio, o porquê do plano, a descrição e a IA. */
@Composable
private fun GoalDetails(
    goal: Goal,
    analysisOpen: Boolean,
    insight: AiText?,
    onSimulate: () -> Unit,
    onToggleAnalysis: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FinaiColors.BorderFaint))
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailRow("Falta guardar", formatBrl0(goal.missing))
            goal.projectedAvailable?.let { DetailRow("Sobra prevista até o prazo", formatBrl0(it.coerceAtLeast(0.0))) }
            DetailRow("Tipo", goal.kind)
        }
        // Por que o plano não sugere aporte agora (dívida antes, falta, outra meta). Quando sugere,
        // o valor já está no card; repetir aqui seria a mesma recomendação duas vezes.
        if (goal.recommended <= 0 && goal.planNote.isNotBlank()) {
            Text(
                goal.planNote, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (goal.description.isNotBlank()) {
            Text(
                goal.description, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Simular cenários", onSimulate, modifier = Modifier.weight(1f))
            ActionButton(if (analysisOpen) "Ocultar análise" else "Ver análise da IA", onToggleAnalysis, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = analysisOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + expandVertically(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + shrinkVertically(finaiTween(FinaiMotion.Standard)),
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FinaiColors.SurfaceSunken)
                    .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text("ANÁLISE DA IA", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted)
                when (insight) {
                    is AiText.Ready -> GoalInsightBody(insight.text)
                    is AiText.Unavailable -> Text(
                        insight.reason, fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    AiText.Loading, null -> Text(
                        "Analisando…", fontSize = 12.5.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
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

/**
 * "Agora / Próximo passo / Risco" ([AiPromptBuilder.goalInsights]). Se o
 * modelo não seguir o formato, mostra o texto inteiro formatado.
 */
@Composable
private fun GoalInsightBody(text: String) {
    val rows = remember(text) { AiReplyFormat.labeled(text, AiPromptBuilder.GOAL_INSIGHT_LABELS) }
    if (rows == null) {
        AiRichText(text, modifier = Modifier.padding(top = 5.dp))
        return
    }
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, body) ->
            val risk = label == "Risco"
            val calm = risk && body.lowercase().startsWith("nenhum")
            // Risco "nenhum relevante" não é informação: some, em vez de ocupar uma linha.
            if (calm) return@forEach
            Column {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        risk -> FinaiColors.AmberDark
                        label == "Próximo passo" -> FinaiColors.EmeraldDark
                        else -> FinaiColors.TextMuted
                    },
                )
                AiRichText(body, modifier = Modifier.padding(top = 2.dp))
            }
        }
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
