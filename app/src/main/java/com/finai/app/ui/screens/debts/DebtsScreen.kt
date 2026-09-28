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
import com.finai.app.ui.components.ActionButton
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.OverflowAction
import com.finai.app.ui.components.OverflowMenu
import com.finai.app.ui.components.SectionHeader
import com.finai.app.ui.components.tipTarget
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
    onDeletePaidDebt: (PaidDebt) -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<Debt?>(null) }
    // Confirmação antes de pagar: um toque por engano em "Paguei a parcela" avançava a dívida.
    var pendingPay by remember { mutableStateOf<Debt?>(null) }
    var pendingDeletePaid by remember { mutableStateOf<PaidDebt?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dívidas", style = MaterialTheme.typography.headlineSmall, color = FinaiColors.TextPrimary)
                    Text(
                        "Estratégia de quitação e negociação", fontSize = 14.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ActionButton("+ Nova", onNewDebt, primary = true)
            }
        }

        if (debts.isEmpty()) {
            // Sem cadastro não é "R$ 0 em aberto" (Fase 7, item 9): o resumo e o roteiro só
            // aparecem quando há dívida de verdade.
            item {
                FadeInAppear {
                    EmptyStateCard(
                        if (paidDebts.isEmpty())
                            "Nenhuma dívida cadastrada. Cadastre empréstimos, parcelamentos e cartão rotativo: o app ordena pelo " +
                                "custo do juro, projeta as parcelas na Agenda e diz quando você fica livre."
                        else "Nenhuma dívida em aberto. Se surgir uma nova, cadastre para acompanhar as parcelas.",
                        "Cadastrar dívida", onNewDebt,
                    )
                }
            }
        } else {
            item {
                // Neutro, não vermelho (Fase 7, item 7): ter dívida não é, por si, um problema;
                // vermelho fica para o que está atrasado.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(FinaiColors.Ink)
                        .padding(18.dp),
                ) {
                    Text("TOTAL EM ABERTO", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextOnDarkMuted)
                    Text(
                        totalOpenLabel, style = MaterialTheme.typography.displayLarge,
                        color = Color.White, modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("Juros por mês", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted)
                            Text(monthlyInterestLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                        }
                        Column {
                            Text("Livre em", fontSize = 13.sp, color = FinaiColors.TextOnDarkMuted)
                            Text(debtFreeLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().tipTarget("debts.list")) {
                    SectionHeader("Ordem sugerida de ataque", subtitle = strategyNote)
                    // Lista simples com divisor (Fase 7, item 7). Não é uma LazyColumn própria:
                    // animateContentSize() evita que as linhas restantes pulem ao excluir.
                    Column(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(FinaiColors.Surface)
                            .animateContentSize(finaiTween(FinaiMotion.Standard)),
                    ) {
                        val firstPayable = debts.firstOrNull { it.hasParcelaFixa }?.id
                        debts.forEachIndexed { index, debt ->
                            if (index > 0) HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(start = 52.dp))
                            DebtRow(
                                debt, onEdit = { onEditDebt(debt) }, onDelete = { pendingDelete = debt },
                                onPay = { pendingPay = debt },
                                payModifier = if (debt.id == firstPayable) Modifier.tipTarget("debts.pay") else Modifier,
                            )
                        }
                    }
                }
            }

            item {
                // Superfície de IA neutra: não pode chamar mais atenção que os números.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
                        .background(FinaiColors.Surface)
                        .padding(16.dp),
                ) {
                    Text("ROTEIRO DE NEGOCIAÇÃO", style = MaterialTheme.typography.labelSmall, color = FinaiColors.TextMuted)
                    Text(
                        negotiationTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
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
                        Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        steps.forEachIndexed { index, text ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(FinaiColors.SurfaceMuted),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text((index + 1).toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextSecondary)
                                }
                                Text(com.finai.app.ui.components.rememberAiAnnotated(text), fontSize = 14.sp, lineHeight = 20.sp, color = FinaiColors.TextBody)
                            }
                        }
                    }
                    ActionButton(
                        "Ensaiar a ligação com a IA", onRehearseCall,
                        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().tipTarget("debts.call"),
                    )
                }
            }
        }

        if (paidDebts.isNotEmpty()) {
            item {
                SectionHeader(
                    "Dívidas quitadas",
                    subtitle = "${paidDebts.size} " + (if (paidDebts.size == 1) "quitada" else "quitadas") + " · seu histórico, fora de todas as contas",
                    modifier = Modifier.padding(top = 8.dp),
                )
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
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(FinaiColors.EmeraldSoftBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(paid.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text("${paid.paidLabel} · ${paid.detail}", fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 1.dp))
        }
        Text(paid.originalLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary)
        OverflowMenu(listOf(OverflowAction("Apagar do histórico", destructive = true, onClick = onDelete)), contentDescription = "Mais ações para ${paid.name}")
    }
}

@Composable
private fun DebtRow(debt: Debt, onEdit: () -> Unit, onDelete: () -> Unit, onPay: () -> Unit, payModifier: Modifier = Modifier) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(FinaiColors.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(debt.rank.toString(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(debt.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                Text(debt.meta, fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 1.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(debt.amount, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(debt.rate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = debt.rateColor, modifier = Modifier.padding(top = 2.dp))
            }
            OverflowMenu(
                listOf(OverflowAction("Editar", onClick = onEdit), OverflowAction("Excluir", destructive = true, onClick = onDelete)),
                contentDescription = "Mais ações para ${debt.name}",
            )
        }
        ProgressTrack(
            progress = debt.progressPct, fillColor = debt.barColor, height = 6.dp,
            modifier = Modifier.padding(start = 38.dp, end = 14.dp, top = 6.dp),
        )
        if (debt.hasParcelaFixa) {
            ActionButton("Paguei a parcela", onPay, modifier = Modifier.padding(start = 38.dp, top = 8.dp).then(payModifier))
        }
    }
}
