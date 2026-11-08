package com.finai.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.AgendaBalance
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MonthEnd
import com.finai.app.domain.ReconciliationNotice
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.text.NumberFormat
import java.util.Locale

private fun brl(cents: Long) = (if (cents < 0) "− " else "") + formatBrl0(kotlin.math.abs(cents) / 100.0)

/** "3,2k" / "12k" / "850": o valor curto em cima de cada barra. */
internal fun compactCents(cents: Long): String {
    val reais = cents / 100.0
    val abs = kotlin.math.abs(reais)
    val text = if (abs >= 1000) {
        NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }.format(abs / 1000) + "k"
    } else Math.round(abs).toString()
    return (if (cents < 0) "−" else "") + text
}

/**
 * Saldo projetado contínuo (ref. 3a): o saldo de hoje, o menor saldo previsto e o saldo no último dia
 * de cada mês (o atual e os três seguintes). O saldo de um mês é o inicial do seguinte: nenhum mês
 * recomeça do zero. Tudo vem de [AgendaBalance], a mesma projeção da Agenda.
 */
@Composable
fun ProjectedBalanceCard(balance: AgendaBalance?, notice: ReconciliationNotice?, onReconcile: () -> Unit, modifier: Modifier = Modifier) {
    if (balance == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BalanceBarsCard(balance)
        when (notice) {
            is ReconciliationNotice.Stale -> StaleNotice(notice.days, onReconcile)
            is ReconciliationNotice.DoneToday -> DoneNotice(notice.adjustmentCents)
            null -> Unit
        }
    }
}

@Composable
private fun BalanceBarsCard(balance: AgendaBalance) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(FinaiColors.InkStrong)
            .background(Brush.radialGradient(listOf(FinaiColors.Emerald.copy(alpha = 0.22f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), radius = 700f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("SALDO EM CONTA HOJE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.OnInkStrong.copy(alpha = 0.6f))
            Text(
                brl(balance.todayCents), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp,
                color = if (balance.todayCents < 0) FinaiColors.Rose else FinaiColors.OnInkStrong, modifier = Modifier.padding(top = 6.dp),
            )
            val low = balance.lowest
            Text(
                "Menor saldo previsto: ${brl(low.cents)} em ${"%02d/%02d".format(low.date.dayOfMonth, low.date.monthValue)}. Nenhum mês recomeça do zero.",
                fontSize = 12.5.sp, lineHeight = 18.sp, color = FinaiColors.OnInkStrong.copy(alpha = 0.7f), modifier = Modifier.padding(top = 7.dp),
            )
            Bars(balance.monthEnds, Modifier.padding(top = 18.dp))
            Text(
                "Saldo previsto no último dia de cada mês", fontSize = 12.sp,
                color = FinaiColors.OnInkStrong.copy(alpha = 0.55f), modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Bars(ends: List<MonthEnd>, modifier: Modifier = Modifier) {
    val max = ends.maxOfOrNull { it.cents }?.coerceAtLeast(1L) ?: 1L
    val description = ends.joinToString("; ") { "${MONTH_ABBREV_PT[it.month.monthValue - 1]}: ${brl(it.cents)}" }
    Row(
        modifier.fillMaxWidth().height(118.dp).semantics { contentDescription = "Saldo previsto no último dia de cada mês. $description" },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
    ) {
        ends.forEachIndexed { index, end ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                Text(compactCents(end.cents), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.OnInkStrong.copy(alpha = 0.85f), maxLines = 1)
                val h = if (end.cents <= 0) 8 else maxOf(8, Math.round(end.cents.toDouble() / max * 64).toInt())
                val color = when {
                    end.cents < 0 -> FinaiColors.Rose.copy(alpha = 0.6f)
                    // O mês atual em verde cheio; os demais, translúcidos.
                    index == 0 -> FinaiColors.Emerald
                    else -> FinaiColors.Emerald.copy(alpha = 0.35f)
                }
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(8.dp)).background(color))
                Text(MONTH_ABBREV_PT[end.month.monthValue - 1].uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.OnInkStrong.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun StaleNotice(days: Int?, onReconcile: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(FinaiColors.AmberSoftBg).border(1.dp, FinaiColors.Amber.copy(alpha = 0.35f), shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(FinaiColors.Surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Timeline, contentDescription = null, tint = FinaiColors.AmberDark, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Confira com o banco", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.AmberDeep)
            val ago = if (days == null) "Você ainda não conciliou." else "Última conciliação há $days ${if (days == 1) "dia" else "dias"}."
            Text(
                "$ago Informe o saldo do app do banco para corrigir diferenças.",
                fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.AmberDark, modifier = Modifier.padding(top = 3.dp),
            )
            Box(
                Modifier.padding(top = 10.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(FinaiColors.AmberDark)
                    .clickable(role = Role.Button, onClick = onReconcile).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Conciliar agora", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.Surface)
            }
        }
    }
}

@Composable
private fun DoneNotice(adjustmentCents: Long) {
    val shape = RoundedCornerShape(18.dp)
    val ajuste = if (adjustmentCents == 0L) "Nenhum ajuste foi necessário" else "Ajuste de ${(if (adjustmentCents > 0) "+" else "−")} ${formatBrl(kotlin.math.abs(adjustmentCents) / 100.0)} lançado"
    Text(
        "Conciliado hoje. $ajuste e todas as projeções foram recalculadas.",
        fontSize = 13.sp, lineHeight = 19.sp, color = FinaiColors.EmeraldDark,
        modifier = Modifier.fillMaxWidth().clip(shape).background(FinaiColors.EmeraldSoftBg).border(1.dp, FinaiColors.EmeraldSoftStroke, shape).padding(14.dp),
    )
}
