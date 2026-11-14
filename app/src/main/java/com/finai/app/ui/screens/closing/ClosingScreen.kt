package com.finai.app.ui.screens.closing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.closing.ClosingStep
import com.finai.app.domain.closing.ClosingStepId
import com.finai.app.domain.closing.MonthClosing
import com.finai.app.ui.components.*
import com.finai.app.ui.screens.home.V2Panel
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

private fun brl(cents: Long) = formatBrl0(cents / 100.0)

/** Cartão verde no fim da Hoje (dias 1 a 7, ou enquanto faltar passo de um fechamento começado). */
@Composable
fun ClosingCard(closing: MonthClosing, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(FinaiColors.EmeraldSoftBg).border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onOpen).padding(16.dp).testTag("closing-card"),
    ) {
        Text("FECHAMENTO DE ${closing.monthName.uppercase()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark)
        Text(if (closing.leftCents >= 0) "Sobraram ${brl(closing.leftCents)}" else "Faltaram ${brl(-closing.leftCents)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
        Text("${closing.doneCount} de ${closing.steps.size} passos · leva um minuto", fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        Text("Fechar o mês ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Fechamento do mês (planning-v2, fase 10): três números, resumo e quatro passos com ação real. */
@Composable
fun ClosingScreen(
    closing: MonthClosing,
    summary: String,
    onAct: (ClosingStep) -> Unit,
    onSkip: (ClosingStep) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(FinaiColors.Background)) {
        FinaiSettingsTopBar(title = "Fechamento de ${closing.monthName.lowercase()}", onNavigateBack = onClose, subtitle = "${closing.doneCount} de ${closing.steps.size}")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            V2Panel {
                Row(Modifier.fillMaxWidth()) {
                    listOf(Triple("Entrou", closing.inCents, FinaiColors.EmeraldDark), Triple("Saiu", closing.outCents, FinaiColors.TextPrimary),
                        Triple(if (closing.leftCents >= 0) "Sobrou" else "Faltou", kotlin.math.abs(closing.leftCents), if (closing.leftCents >= 0) FinaiColors.EmeraldDark else FinaiColors.RoseDark)).forEach { (label, cents, color) ->
                        Column(Modifier.weight(1f)) {
                            Text(label.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                            Text(brl(cents), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                AiRichText(summary, modifier = Modifier.padding(top = 14.dp))
            }
            closing.steps.forEachIndexed { index, step -> StepCard(index + 1, step, onAct, onSkip) }
            if (!closing.pending) {
                Text("Mês fechado. As respostas ficam guardadas e não são perguntadas de novo.", fontSize = 13.sp, color = FinaiColors.TextSecondary)
                ActionButton("Concluir", onClose, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, step: ClosingStep, onAct: (ClosingStep) -> Unit, onSkip: (ClosingStep) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinaiColors.Surface).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp)).padding(14.dp)
            .testTag("closing-step-${step.id.key}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (step.done) FinaiColors.EmeraldButton else FinaiColors.SurfaceMuted), contentAlignment = Alignment.Center) {
            Text(if (step.done) "✓" else number.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (step.done) androidx.compose.ui.graphics.Color.White else FinaiColors.TextSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            if (step.done) {
                Text(answerLabel(step), fontSize = 13.sp, color = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 2.dp))
            } else {
                Text(step.detail, fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    step.action?.let { ActionButton(it, { onAct(step) }, Modifier.weight(1f), primary = true) }
                    ActionButton(step.skip, { onSkip(step) }, Modifier.weight(if (step.action != null) 0.7f else 1f))
                }
            }
        }
    }
}

private fun answerLabel(step: ClosingStep): String = when (step.answer) {
    "feito" -> when (step.id) {
        ClosingStepId.Missing -> "Lançado."
        ClosingStepId.Limits -> "Limites aplicados."
        ClosingStepId.Surplus -> "Guardado."
        ClosingStepId.Pattern -> "Aviso ligado."
    }
    else -> when (step.id) {
        ClosingStepId.Missing -> "Não teve cobrança."
        ClosingStepId.Limits -> "Limites mantidos."
        ClosingStepId.Surplus -> "Sem aporte agora."
        ClosingStepId.Pattern -> "Você já sabe."
    }
}
