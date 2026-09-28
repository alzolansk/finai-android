package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.ExplainStep
import com.finai.app.domain.SpendExplanation
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

private const val UPCOMING_PREVIEW = 6

/**
 * "Entenda este valor" (planning.md §9 Fase 7, item 2): a conta do "Pode gastar hoje" passo a
 * passo, os compromissos descontados, o que o app supõe e o que falta cadastrar. Só mostra o
 * que [SpendExplanation] montou — nenhum cálculo aqui.
 */
@Composable
fun SpendExplanationContent(explanation: SpendExplanation, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Entenda este valor", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = FinaiColors.TextSecondary)
            }
        }
        Text(explanation.period, fontSize = 14.sp, lineHeight = 20.sp, color = FinaiColors.TextSecondary)

        explanation.shortfall?.let { falta ->
            Text(
                "Vai faltar até ${formatBrl0(falta.cents / 100.0)}" + (falta.causa?.let { ", a partir de \"$it\"" } ?: "") +
                    ". Por isso não há valor livre para gastar.",
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.RoseDark,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FinaiColors.RoseSoftBg)
                    .padding(12.dp),
            )
        }

        SectionTitle("A conta")
        explanation.steps.forEach { StepRow(it) }
        if (explanation.shortfall == null) {
            Text(
                "${formatBrl0(explanation.steps.last().cents / 100.0)} ÷ ${explanation.days} dia(s) = " +
                    "${formatBrl0(explanation.perDayCents / 100.0)} por dia",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (explanation.upcoming.isNotEmpty()) {
            SectionTitle("O que ainda vai sair")
            var showAll by remember { mutableStateOf(false) }
            val shown = if (showAll) explanation.upcoming else explanation.upcoming.take(UPCOMING_PREVIEW)
            shown.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%02d/%02d".format(item.date.dayOfMonth, item.date.monthValue),
                        fontSize = 14.sp, color = FinaiColors.TextSecondary, modifier = Modifier.width(56.dp),
                    )
                    Text(item.label, fontSize = 15.sp, color = FinaiColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text("− ${formatBrl0(item.cents / 100.0)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                }
            }
            val hidden = explanation.upcoming.size - UPCOMING_PREVIEW
            if (!showAll && hidden > 0) {
                Box(
                    modifier = Modifier.heightIn(min = 48.dp).clickable { showAll = true },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Ver mais $hidden", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark)
                }
            }
        }

        if (explanation.missing.isNotEmpty()) {
            SectionTitle("Para o valor ficar mais fiel")
            explanation.missing.forEach { Bullet(it) }
        }

        SectionTitle("O que o app considera")
        explanation.assumptions.forEach { Bullet(it) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun StepRow(step: ExplainStep) {
    if (step.total) HorizontalDivider(color = FinaiColors.BorderHairline, modifier = Modifier.padding(vertical = 4.dp))
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                step.label, fontSize = 15.sp,
                fontWeight = if (step.total) FontWeight.Bold else FontWeight.Normal,
                color = FinaiColors.TextPrimary, modifier = Modifier.weight(1f),
            )
            val sign = when {
                step.total -> ""
                step.cents < 0 -> "− "
                else -> "+ "
            }
            Text(
                sign + formatBrl0(kotlin.math.abs(step.cents) / 100.0).let { if (step.total && step.cents < 0) "−$it" else it },
                fontSize = 15.sp, fontWeight = if (step.total) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (step.total && step.cents < 0) FinaiColors.RoseDark else FinaiColors.TextPrimary,
            )
        }
        step.detail?.let {
            Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", fontSize = 14.sp, color = FinaiColors.TextSecondary)
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = FinaiColors.TextPrimary)
    }
}
