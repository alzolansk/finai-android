package com.finai.app.ui.screens.goals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import com.finai.app.domain.AiPromptBuilder
import com.finai.app.domain.AiReplyFormat
import com.finai.app.ui.components.AiRichText
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.model.Goal
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.FadeInAppear
import com.finai.app.ui.components.PillTag
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl0

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalsScreen(
    goals: List<Goal>,
    completedGoals: List<Goal>,
    monthlyCapacityLabel: String,
    goalInsights: Map<String, AiText>,
    onNewGoal: () -> Unit,
    onContribute: (Goal) -> Unit,
    onEdit: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    onSimulate: (Goal) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Objetivos", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Text(
                        "Compras, viagens e reservas em andamento", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FinaiColors.Ink)
                        .clickable(onClick = onNewGoal)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Novo objetivo", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(FinaiColors.Ink)
                    .padding(16.dp),
            ) {
                Text("CAPACIDADE DE POUPANÇA", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextOnDarkFaint)
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(monthlyCapacityLabel, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("por mês", fontSize = 12.sp, color = FinaiColors.TextOnDarkMuted)
                }
                Text(
                    "Balanço deste mês na Agenda, sem entradas extras. As metas também contam com a sobra projetada dos próximos meses até o prazo.",
                    fontSize = 12.sp, lineHeight = 18.sp, color = FinaiColors.TextOnDarkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (goals.isEmpty()) {
            item {
                FadeInAppear {
                    Text(
                        if (completedGoals.isEmpty()) "Nenhum objetivo cadastrado ainda. Toque em \"+\" para criar o primeiro."
                        else "Nenhuma meta em andamento. Toque em \"+\" para criar a próxima.",
                        fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }

        items(goals, key = { it.id }) { goal ->
            GoalCard(
                goal, goalInsights[goal.id], onContribute, onEdit, { pendingDelete = goal }, onSimulate,
                modifier = Modifier.animateItemPlacement(finaiTween(FinaiMotion.Standard)),
            )
        }

        if (completedGoals.isNotEmpty()) {
            item(key = "completed-header") {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Metas concluídas", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    Text(
                        "${completedGoals.size} " + (if (completedGoals.size == 1) "conquista" else "conquistas") +
                            " · aportes continuam valendo",
                        fontSize = 12.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
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
 * "Agora / Próximo passo / Risco" ([AiPromptBuilder.goalInsight]). Se o
 * modelo não seguir o formato, mostra o texto inteiro formatado.
 */
@Composable
private fun GoalInsightBody(text: String) {
    val rows = remember(text) { AiReplyFormat.labeled(text, AiPromptBuilder.GOAL_INSIGHT_LABELS) }
    if (rows == null) {
        AiRichText(text, modifier = Modifier.padding(top = 5.dp))
        return
    }
    Column(modifier = Modifier.padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (label, body) ->
            val risk = label == "Risco"
            val calm = risk && body.lowercase().startsWith("nenhum")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    label.uppercase(),
                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 12.sp,
                    color = when {
                        risk && !calm -> Color(0xFFB45309)
                        label == "Próximo passo" -> FinaiColors.EmeraldDark
                        else -> FinaiColors.TextMuted
                    },
                    modifier = Modifier.width(64.dp).padding(top = 3.dp),
                )
                AiRichText(body, modifier = Modifier.weight(1f))
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
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(42.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (travel) Color(0xFFE0F2FE) else Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (travel) Icons.Filled.FlightTakeoff else Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = if (travel) Color(0xFF0284C7) else Color(0xFFD97706),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(goal.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                "${goal.completedLabel ?: "Concluída"} · ${formatBrl0(goal.saved)} guardados",
                fontSize = 11.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
            )
            if (goal.exceeded > 0) {
                Text(
                    "+${formatBrl0(goal.exceeded)} além do alvo de ${formatBrl0(goal.target)}",
                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Registrar aporte", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark, modifier = Modifier.clickable(onClick = onContribute))
                Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary, modifier = Modifier.clickable(onClick = onEdit))
                Text("Excluir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48), modifier = Modifier.clickable(onClick = onDelete))
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    insight: AiText?,
    onContribute: (Goal) -> Unit,
    onEdit: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    onSimulate: (Goal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(goal.kind.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
                Text(
                    goal.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (goal.description.isNotBlank()) {
                    Text(
                        goal.description, fontSize = 11.5.sp, lineHeight = 16.sp, color = FinaiColors.TextTertiary,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg)
        }
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatBrl0(goal.saved), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.EmeraldDark)
            Text("de ${formatBrl0(goal.target)}", fontSize = 12.sp, color = FinaiColors.TextMuted)
        }
        ProgressTrack(progress = goal.progress, fillColor = FinaiColors.Emerald, height = 8.dp, modifier = Modifier.padding(top = 9.dp))
        Text(goal.note, fontSize = 11.5.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FinaiColors.SurfaceSunken)
                .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Text("LEITURA DA IA", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
            when (insight) {
                is AiText.Ready -> GoalInsightBody(insight.text)
                is AiText.Unavailable -> Text(
                    insight.reason, fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 5.dp),
                )
                AiText.Loading, null -> Text(
                    "Analisando este objetivo com IA...", fontSize = 12.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FinaiColors.Ink)
                    .clickable { onContribute(goal) }
                    .padding(10.dp),
            ) {
                Text(goal.action, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onEdit(goal) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar objetivo", tint = FinaiColors.TextTertiary, modifier = Modifier.size(16.dp))
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onSimulate(goal) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Text("Simular", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onDelete(goal) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Text("Excluir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE11D48))
            }
        }
    }
}
