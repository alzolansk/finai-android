package com.finai.app.ui.screens.debts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.AiText
import com.finai.app.data.model.Debt
import com.finai.app.data.model.PaidDebt
import androidx.compose.material.icons.filled.TaskAlt
import com.finai.app.ui.components.ConfirmDeleteDialog
import com.finai.app.ui.components.FadeInAppear
import com.finai.app.ui.components.ProgressTrack
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween

/** Fallback shown while the AI script is loading/unavailable, or once no debt is cadastrada. */
private val genericNegotiationSteps = listOf(
    "Diga há quanto tempo você é cliente e se está em dia com os pagamentos.",
    "Peça o parcelamento do saldo devedor citando a taxa atual e pedindo uma taxa menor.",
    "Se recusarem, ofereça uma entrada à vista e peça o saldo restante em parcelas sem juros.",
    "Peça o número do protocolo e a proposta por escrito antes de aceitar.",
)

@Composable
fun DebtsScreen(
    debts: List<Debt>,
    totalOpenLabel: String,
    monthlyInterestLabel: String,
    debtFreeLabel: String,
    strategyNote: String,
    negotiationTitle: String,
    negotiationScript: AiText?,
    onNewDebt: () -> Unit,
    onEditDebt: (Debt) -> Unit,
    onDeleteDebt: (Debt) -> Unit,
    onPayInstallment: (Debt) -> Unit,
    onRehearseCall: () -> Unit,
    paidDebts: List<PaidDebt> = emptyList(),
    onPayOff: (Debt, registrarPagamento: Boolean) -> Unit = { _, _ -> },
    onDeletePaidDebt: (PaidDebt) -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<Debt?>(null) }
    // Confirmação antes de pagar: um toque por engano em "Paguei a parcela" avançava a dívida.
    var pendingPay by remember { mutableStateOf<Debt?>(null) }
    var pendingPayOff by remember { mutableStateOf<Debt?>(null) }
    var pendingDeletePaid by remember { mutableStateOf<PaidDebt?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Dívidas", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Text(
                        "Estratégia de quitação e negociação", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FinaiColors.Ink)
                        .clickable(onClick = onNewDebt)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nova dívida", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(FinaiColors.RoseDeepest, FinaiColors.RoseDeep)))
                    .padding(18.dp),
            ) {
                Text("TOTAL EM ABERTO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.55f))
                Text(
                    totalOpenLabel, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, modifier = Modifier.padding(top = 5.dp),
                )
                Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        Text("Juros por mês", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.55f))
                        Text(monthlyInterestLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Text("Livre em", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.55f))
                        Text(debtFreeLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        if (debts.isEmpty()) {
            item {
                FadeInAppear {
                    Text(
                        "Nenhuma dívida cadastrada. Toque em \"+\" para adicionar.",
                        fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                        .background(FinaiColors.Surface)
                        .padding(16.dp),
                ) {
                    Text("Ordem sugerida de ataque", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    Text(
                        strategyNote, fontSize = 11.5.sp, color = FinaiColors.TextMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                    )
                    // `debts` isn't a LazyColumn here (it's one card with a fixed inner
                    // order, not an independently scrollable list), so a deleted row can't
                    // get its own exit transition — animateContentSize() at least makes the
                    // card resize smoothly instead of the remaining rows jumping into place.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.animateContentSize(finaiTween(FinaiMotion.Standard)),
                    ) {
                        debts.forEach { debt ->
                            DebtRow(
                                debt, onEdit = { onEditDebt(debt) }, onDelete = { pendingDelete = debt },
                                onPay = { pendingPay = debt }, onPayOff = { pendingPayOff = debt },
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, FinaiColors.IndigoSoftBorder, RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(FinaiColors.IndigoSoftBg, FinaiColors.Surface)))
                    .padding(16.dp),
            ) {
                Text("ROTEIRO DE NEGOCIAÇÃO", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.Indigo)
                Text(
                    negotiationTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val steps = when (negotiationScript) {
                    is AiText.Ready -> negotiationScript.text.lines()
                        .map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.):-]\\s*"), "") }
                        .filter { it.isNotBlank() }
                        .ifEmpty { genericNegotiationSteps }
                    else -> genericNegotiationSteps
                }
                val statusNote = when (negotiationScript) {
                    AiText.Loading -> "Gerando um roteiro específico para essa dívida com IA..."
                    is AiText.Unavailable -> negotiationScript.reason
                    else -> null
                }
                statusNote?.let {
                    Text(it, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    steps.forEachIndexed { index, text ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.padding(top = 1.dp).size(18.dp).clip(CircleShape).background(FinaiColors.Indigo),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text((index + 1).toString(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            Text(com.finai.app.ui.components.rememberAiAnnotated(text), fontSize = 12.5.sp, lineHeight = 18.sp, color = FinaiColors.TextBody)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(FinaiColors.Indigo)
                        .clickable(onClick = onRehearseCall)
                        .padding(11.dp),
                ) {
                    Text(
                        "Ensaiar a ligação com a IA", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (paidDebts.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Dívidas quitadas", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    Text(
                        "${paidDebts.size} " + (if (paidDebts.size == 1) "quitada" else "quitadas") + " · seu histórico, fora de todas as contas",
                        fontSize = 12.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            items(paidDebts.size) { i -> PaidDebtRow(paidDebts[i], onDelete = { pendingDeletePaid = paidDebts[i] }) }
        }
    }

    pendingPay?.let { debt ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingPay = null },
            title = { Text("Registrar pagamento?") },
            text = {
                Text(
                    "Uma parcela de \"${debt.name}\" vira um gasto de hoje na Agenda e a dívida avança uma parcela. " +
                        "Para desfazer, exclua esse gasto na Agenda.",
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onPayInstallment(debt); pendingPay = null }) { Text("Paguei") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingPay = null }) { Text("Cancelar") } },
        )
    }

    pendingPayOff?.let { debt ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingPayOff = null },
            title = { Text("Quitar \"${debt.name}\"?") },
            text = {
                Text(
                    "Registrar ${debt.amount} como pagamento de hoje? Se você já lançou esse pagamento, escolha \"Só marcar\". " +
                        "A dívida vai para \"Dívidas quitadas\".",
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onPayOff(debt, true); pendingPayOff = null }) { Text("Registrar e quitar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { onPayOff(debt, false); pendingPayOff = null }) { Text("Só marcar") } },
        )
    }

    pendingDeletePaid?.let { paid ->
        ConfirmDeleteDialog(
            title = "Apagar do histórico?",
            description = "\"${paid.name}\" sai da lista de dívidas quitadas. Os pagamentos já lançados continuam na Agenda.",
            onDismiss = { pendingDeletePaid = null },
            onConfirm = { onDeletePaidDebt(paid); pendingDeletePaid = null },
        )
    }

    pendingDelete?.let { debt ->
        ConfirmDeleteDialog(
            title = "Excluir dívida?",
            description = "\"${debt.name}\" (${debt.amount} em aberto) será removida permanentemente.",
            onDismiss = { pendingDelete = null },
            onConfirm = { onDeleteDebt(debt); pendingDelete = null },
        )
    }
}

@Composable
private fun PaidDebtRow(paid: PaidDebt, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(FinaiColors.EmeraldSoftBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(paid.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text("${paid.paidLabel} · ${paid.detail}", fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(paid.originalLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary)
            Text(
                "Apagar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted,
                modifier = Modifier.padding(top = 3.dp).clickable(onClick = onDelete),
            )
        }
    }
}

@Composable
private fun DebtRow(debt: Debt, onEdit: () -> Unit, onDelete: () -> Unit, onPay: () -> Unit, onPayOff: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(FinaiColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(debt.rank.toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextSecondary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(debt.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(debt.meta, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
            ProgressTrack(progress = debt.progressPct, fillColor = debt.barColor, height = 6.dp, modifier = Modifier.padding(top = 6.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(debt.amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(debt.rate, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = debt.rateColor, modifier = Modifier.padding(top = 2.dp))
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (debt.hasParcelaFixa) {
                    Text(
                        "Paguei a parcela", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                        modifier = Modifier.clickable(onClick = onPay),
                    )
                }
                Text(
                    "Quitar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
                    modifier = Modifier.clickable(onClick = onPayOff),
                )
                Text(
                    "Editar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
                    modifier = Modifier.clickable(onClick = onEdit),
                )
                Text(
                    "Excluir", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48),
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        }
    }
}
