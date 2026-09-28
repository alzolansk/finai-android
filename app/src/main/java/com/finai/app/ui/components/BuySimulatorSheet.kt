package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.Goal
import com.finai.app.data.model.SimEffect
import com.finai.app.data.model.SimVerdict
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0
import kotlin.math.ceil

/** "Posso comprar?" bottom sheet content — presets, verdict, and the effect on slack/goals. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuySimulatorContent(
    amount: Double,
    plan: com.finai.app.domain.FinancialPlan?,
    goals: List<Goal>,
    aiExplain: AiText?,
    onEnsureExplain: (
        amountLabel: String,
        verdictLabel: String,
        monthlyCapacityLabel: String,
        slackAfterLabel: String,
        topGoalName: String?,
        topGoalAffected: Boolean,
    ) -> Unit,
    onPickPreset: (Double) -> Unit,
    onDecideLater: () -> Unit,
    onAsk: (com.finai.app.domain.AssistantTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tudo do plano central: o livre para gastar é o que sobra depois do destino da sobra,
    // e o que passar disso sai do destino (dívida ou meta), não de um "segundo" dinheiro.
    val free = (plan?.livreParaGastarCents ?: 0L) / 100.0
    val available = (plan?.disponivelCents ?: 0L) / 100.0
    val monthly = (plan?.capacidadeMensalCents ?: 0L) / 100.0
    val until = plan?.untilLabel ?: "este mês"
    val verdict = when {
        plan?.shortfall != null -> SimVerdict.DoesNotFit
        amount <= free -> SimVerdict.Fits
        amount <= available -> SimVerdict.FitsButCosts
        else -> SimVerdict.DoesNotFit
    }
    val destino = plan?.destino
    val delayMonths = if (monthly > 0) ceil(((amount - available) / monthly)).toInt().coerceAtLeast(0) else 0
    // Deterministic explanation (planning.md §6) — shown immediately and kept as the fallback
    // while the AI text below is loading or unavailable (planning.md §4's resiliência requirement).
    val fallbackExplain = when (verdict) {
        SimVerdict.Fits -> "O valor cabe no livre $until. O destino da sobra no plano não muda."
        SimVerdict.FitsButCosts -> destino?.let {
            "Você cobre à vista, mas tira dinheiro do destino da sobra: \"${it.name}\" recebe menos $until."
        } ?: "Você cobre à vista, mas usa tudo o que está livre $until."
        SimVerdict.DoesNotFit -> plan?.shortfall?.let { "Já falta dinheiro $until: ${formatBrl0(it.cents / 100.0)}. Nenhuma compra cabe agora." }
            ?: ("O valor passa do disponível de ${formatBrl0(available)} $until" +
                (if (delayMonths >= 1) " — levaria cerca de $delayMonths mês(es) de sobra para cobrir o resto." else "."))
    }
    val explain = (aiExplain as? AiText.Ready)?.text ?: fallbackExplain
    val destinoAffected = destino != null && verdict != SimVerdict.Fits
    LaunchedEffect(amount, free, verdict, destino?.name) {
        onEnsureExplain(formatBrl0(amount), verdict.label, formatBrl0(free), formatBrl0(free - amount), destino?.name, destinoAffected)
    }
    val effects = buildList {
        add(SimEffect("Livre $until", "depois da compra", formatBrl0(free - amount), if (free - amount >= 0) FinaiColors.EmeraldDark else Color(0xFFBE123C)))
        destino?.let { d ->
            add(
                SimEffect(
                    d.name, if (destinoAffected) "destino da sobra recebe menos" else "destino da sobra mantido",
                    if (destinoAffected) "Em risco" else "Mantido",
                    if (destinoAffected) Color(0xFFB45309) else FinaiColors.EmeraldDark,
                ),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(FinaiColors.BorderSubtle),
        )
        Text(
            "Posso comprar?", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
            color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "A IA compara a compra com seus limites, contas e objetivos antes de você decidir.",
            fontSize = 12.5.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 4.dp),
        )

        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FinaiColors.SurfaceSunken)
                .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Text("VALOR DA COMPRA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
            Text(
                formatBrl0(amount), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 4.dp),
            )
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FinaiFixtures.simPresets.forEach { preset ->
                    val selected = preset == amount
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) FinaiColors.Ink else FinaiColors.Surface)
                            .border(1.dp, if (selected) FinaiColors.Ink else FinaiColors.BorderSubtle, RoundedCornerShape(11.dp))
                            .clickable { onPickPreset(preset) }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Text(
                            formatBrl0(preset), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else FinaiColors.TextSecondary,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(verdict.background)
                .padding(16.dp),
        ) {
            Text(
                "VEREDITO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                verdict.label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, modifier = Modifier.padding(top = 6.dp),
            )
            AiRichText(explain, palette = AiTextPalette.OnDark, modifier = Modifier.padding(top = 8.dp))
        }

        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            effects.forEach { effect ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(14.dp))
                        .background(FinaiColors.Surface)
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(effect.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                        Text(effect.detail, fontSize = 12.sp, color = FinaiColors.TextMuted)
                    }
                    Text(effect.delta, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = effect.color)
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Button(
                onClick = onDecideLater,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
            ) {
                Text("Decidir depois", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    onAsk(
                        com.finai.app.domain.AssistantTopics.purchase(
                            formatBrl0(amount), verdict.label, formatBrl0(free), formatBrl0(free - amount), destino?.name, destinoAffected,
                        ),
                    )
                },
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FinaiColors.TextSecondary),
            ) {
                Text("Perguntar", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
