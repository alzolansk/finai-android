package com.finai.app.ui.screens.agenda

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Bill
import com.finai.app.data.model.BillStatus
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.ScreenContentPadding
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

@Composable
fun AgendaScreen(
    monthIndex: Int,
    year: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    bills: List<Bill>,
    toPayCents: Long,
    toPayCount: Int,
    toGetCents: Long,
    toGetCount: Int,
    transactions: List<TransacaoEntity>,
    onNewConta: () -> Unit,
    onToggleContaPaga: (id: Long, pago: Boolean) -> Unit,
    onDeleteTransaction: (TransacaoEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ScreenContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Agenda", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Text(
                        "Pagamentos e recebimentos do mês", fontSize = 13.sp, color = FinaiColors.TextTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FinaiColors.Ink)
                        .clickable(onClick = onNewConta)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nova conta", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                    .background(FinaiColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ChevronLeft, contentDescription = "Mês anterior", tint = FinaiColors.TextMuted,
                    modifier = Modifier.clickable(onClick = onPrevMonth),
                )
                Text(
                    "${MONTH_NAMES_PT[monthIndex]} de $year", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = FinaiColors.TextPrimary,
                )
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = "Próximo mês", tint = FinaiColors.TextMuted,
                    modifier = Modifier.clickable(onClick = onNextMonth),
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile(
                    label = "Contas a pagar", value = formatBrl0(toPayCents / 100.0),
                    note = "$toPayCount conta(s)", valueColor = FinaiColors.TextPrimary, modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    label = "Recebimentos", value = "+ " + formatBrl0(toGetCents / 100.0),
                    note = "$toGetCount entrada(s)", valueColor = FinaiColors.EmeraldDark, modifier = Modifier.weight(1f),
                )
            }
        }

        if (bills.isEmpty()) {
            item {
                Text(
                    "Nenhuma conta cadastrada para este mês.", fontSize = 13.sp, color = FinaiColors.TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        items(bills, key = { "conta-" + it.id }) { bill -> BillRow(bill, onToggleContaPaga) }

        item {
            Text(
                "Lançamentos deste mês", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    "Nenhum gasto lançado ou importado este mês.", fontSize = 13.sp, color = FinaiColors.TextMuted,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        } else {
            items(transactions, key = { "transacao-" + it.id }) { transacao -> TransacaoRow(transacao, onDeleteTransaction) }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, note: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(13.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, modifier = Modifier.padding(top = 5.dp))
        Text(note, fontSize = 10.5.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Tocar numa conta a pagar alterna paga/pendente — a única ação que existia
 * antes era criar/excluir contas; sem isto uma conta atrasada nunca saía dos
 * avisos e a Fase 5 não tinha como ser testada de verdade (planning.md §9/§10).
 */
@Composable
private fun BillRow(bill: Bill, onTogglePaga: (id: Long, pago: Boolean) -> Unit) {
    val estaPaga = bill.status == BillStatus.Paid
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .clickable { onTogglePaga(bill.id, !estaPaga) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bill.tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(bill.initials, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = bill.ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(bill.meta, fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(bill.amount, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(bill.status.bg)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(bill.status.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = bill.status.fg)
            }
            Text(
                if (estaPaga) "Toque para reabrir" else "Toque para marcar como paga",
                fontSize = 9.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * Gasto já realizado (manual ou importado) — diferente de [BillRow], que é um
 * compromisso futuro (planning.md §8: `Transacao` vs `Conta` são entidades
 * distintas). Não tem toggle de pago porque já é dinheiro gasto; só exclusão,
 * espelhando a ação que já existe na tela Limites.
 */
@Composable
private fun TransacaoRow(transacao: TransacaoEntity, onDelete: (TransacaoEntity) -> Unit) {
    val d = transacao.data.toLocalDate()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderFaint, RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(transacao.descricao, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(
                ("${if (transacao.tipo == "Transferencia") "Transferência" else transacao.tipo} · ${transacao.categoria}" +
                    (if (transacao.recorrente) " · recorrente" else "") + " · %02d/%02d").format(d.dayOfMonth, d.monthValue) +
                    if (transacao.origem == "importado") " · importado" else "",
                fontSize = 11.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text(com.finai.app.util.formatBrl(transacao.valorCentavos / 100.0), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when (transacao.tipo) {
                "Receita" -> Color(0xFF059669)
                "Transferencia" -> Color(0xFF4F46E5)
                else -> FinaiColors.TextPrimary
            })
        Text(
            "Excluir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48),
            modifier = Modifier.clickable { onDelete(transacao) },
        )
    }
}
