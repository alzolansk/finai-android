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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.SimVerdict
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

/** "Posso comprar?" bottom sheet content — presets, verdict, and the effect on slack/goals/reserve. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuySimulatorContent(
    amount: Double,
    onPickPreset: (Double) -> Unit,
    onDecideLater: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val free = FinaiFixtures.monthlyCapacity
    val verdict = when {
        amount <= free -> SimVerdict.Fits
        amount <= free * 2 -> SimVerdict.FitsButCosts
        else -> SimVerdict.DoesNotFit
    }
    val explain = when (verdict) {
        SimVerdict.Fits -> "O valor sai da folga do mês. Nenhum objetivo é adiado e a reserva continua intacta."
        SimVerdict.FitsButCosts -> "Você cobre à vista, mas o aporte de dezembro para Portugal fica de fora. A viagem passa de julho para agosto."
        SimVerdict.DoesNotFit -> "O valor supera sua capacidade de poupança em dois meses. Comprar agora significaria usar o rotativo a 13,9% ao mês — o custo real sobe para ${formatBrl0(Math.round(amount * 1.31))}."
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
            Text("VALOR DA COMPRA", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted)
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
                "VEREDITO", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                verdict.label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                explain, fontSize = 12.5.sp, lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.82f), modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FinaiFixtures.simEffects(amount).forEach { effect ->
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
                        Text(effect.detail, fontSize = 11.sp, color = FinaiColors.TextMuted)
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
                Text("Decidir depois", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onAsk,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FinaiColors.TextSecondary),
            ) {
                Text("Perguntar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
