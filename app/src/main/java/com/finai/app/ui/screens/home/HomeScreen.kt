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
import androidx.compose.material.icons.filled.ChevronRight
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
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.Decision
import com.finai.app.data.model.Goal
import com.finai.app.data.model.RecommendationSurface
import com.finai.app.data.model.WeekBill
import com.finai.app.ui.components.PillTag
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun HomeScreen(
    surface: RecommendationSurface,
    decisions: List<Decision>,
    showCoach: Boolean,
    onPickSurface: (RecommendationSurface) -> Unit,
    onDismissDecision: (String) -> Unit,
    onAcceptDecision: (Decision) -> Unit,
    onAskDecision: (Decision) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenChat: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column {
                Text(FinaiFixtures.greeting, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                Text(
                    FinaiFixtures.subGreeting, fontSize = 13.sp, color = FinaiColors.TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item { GoalsCarousel(onOpenGoals) }

        item { SafeToSpendCard(onOpenSimulator, onOpenBudgets) }

        item {
            DecisionsSection(
                surface = surface,
                decisions = decisions,
                onPickSurface = onPickSurface,
                onDismiss = onDismissDecision,
                onAccept = onAcceptDecision,
                onAsk = onAskDecision,
            )
        }

        item { TimelineSection() }

        item { NextWeekSection(onOpenAgenda) }

        if (showCoach) {
            item { CoachCard(onOpenChat) }
        }
    }
}

@Composable
private fun GoalsCarousel(onOpenGoals: () -> Unit) {
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
            items(FinaiFixtures.goals.take(2)) { goal -> GoalTeaserCard(goal, onOpenGoals) }
            item { NewGoalCard(onOpenGoals) }
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
            PillTag(goal.badge.label, goal.badge.bg, goal.badge.fg)
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
private fun SafeToSpendCard(onOpenSimulator: () -> Unit, onOpenBudgets: () -> Unit) {
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
                    Text("PODE GASTAR HOJE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextOnDarkFaint)
                    Text(
                        FinaiFixtures.safeToday, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        FinaiFixtures.safeNote, fontSize = 12.sp, lineHeight = 17.sp,
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
                                FinaiFixtures.dayProgressFraction to FinaiColors.Emerald,
                                FinaiFixtures.dayProgressFraction to Color.White.copy(alpha = 0.13f),
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
                            Text(FinaiFixtures.dayLeftLabel, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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

@Composable
private fun DecisionsSection(
    surface: RecommendationSurface,
    decisions: List<Decision>,
    onPickSurface: (RecommendationSurface) -> Unit,
    onDismiss: (String) -> Unit,
    onAccept: (Decision) -> Unit,
    onAsk: (Decision) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Decisões para você", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(
                    "${decisions.size} sugestões · você aprova cada uma", fontSize = 11.sp, color = FinaiColors.TextMuted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(FinaiColors.SurfaceMuted)
                    .padding(3.dp),
            ) {
                RecommendationSurface.entries.forEach { s ->
                    val selected = s == surface
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) FinaiColors.Ink else Color.Transparent)
                            .clickable { onPickSurface(s) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            s.label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else FinaiColors.TextTertiary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (surface) {
            RecommendationSurface.Cards -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(decisions) { d -> DecisionCard(d, onAccept, onDismiss) }
            }
            RecommendationSurface.Feed -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                    .background(FinaiColors.Surface),
            ) {
                decisions.forEachIndexed { index, d ->
                    if (index > 0) androidx.compose.material3.HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp)
                    DecisionFeedRow(d, onAccept)
                }
            }
            RecommendationSurface.Chat -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(FinaiColors.EmeraldSoftBg, FinaiColors.Surface)))
                    .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(FinaiColors.Surface)
                            .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(14.dp))
                    }
                    Text(FinaiFixtures.chatPitch, fontSize = 13.sp, lineHeight = 20.sp, color = FinaiColors.TextBody)
                }
                Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    decisions.forEach { d ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(13.dp))
                                .background(FinaiColors.Surface)
                                .clickable { onAsk(d) }
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                        ) {
                            Text(d.question, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextBody)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionCard(decision: Decision, onAccept: (Decision) -> Unit, onDismiss: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(272.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(FinaiColors.EmeraldSoftBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(12.dp))
            }
            Text(decision.tag.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
        }
        Text(
            decision.title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            decision.why, fontSize = 12.5.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary,
            modifier = Modifier.padding(top = 7.dp),
        )
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(FinaiColors.SurfaceSunken)
                .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(13.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("IMPACTO", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
            Text(decision.impact, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 3.dp))
        }
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FinaiColors.Ink)
                    .clickable { onAccept(decision) }
                    .padding(10.dp),
            ) {
                Text(decision.cta, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onDismiss(decision.id) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Text("Depois", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun DecisionFeedRow(decision: Decision, onAccept: (Decision) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAccept(decision) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(decision.dotColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(decision.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(decision.why, fontSize = 11.5.sp, lineHeight = 16.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 3.dp))
            Text(decision.impact, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 7.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FinaiColors.BorderSubtle, modifier = Modifier.padding(top = 4.dp).size(15.dp))
    }
}

@Composable
private fun TimelineSection() {
    Column {
        Text("Linha do tempo do ano", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Text(
            "Entradas extras previstas e o que elas destravam", fontSize = 11.sp, color = FinaiColors.TextMuted,
            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                .background(FinaiColors.Surface)
                .padding(vertical = 16.dp),
        ) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(FinaiFixtures.timeline) { t ->
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
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FinaiColors.SurfaceSunken)
                    .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text(FinaiFixtures.timelineNote, fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextBody)
            }
        }
    }
}

@Composable
private fun NextWeekSection(onOpenAgenda: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Próximos 7 dias", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                "Abrir agenda", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark,
                modifier = Modifier.clickable(onClick = onOpenAgenda),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                .background(FinaiColors.Surface),
        ) {
            FinaiFixtures.week.forEachIndexed { index, w ->
                if (index > 0) androidx.compose.material3.HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp)
                WeekBillRow(w)
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

@Composable
private fun CoachCard(onOpenChat: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(FinaiColors.IndigoDeepStart, FinaiColors.IndigoDeepEnd)))
            .padding(16.dp),
    ) {
        Text("PADRÃO DE COMPORTAMENTO", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.55f))
        Text(
            FinaiFixtures.coachTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
            lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            FinaiFixtures.coachBody, fontSize = 12.5.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 7.dp),
        )
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
