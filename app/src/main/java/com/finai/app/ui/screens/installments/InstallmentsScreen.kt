package com.finai.app.ui.screens.installments

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.InstallmentMonth
import com.finai.app.domain.InstallmentPurchase
import com.finai.app.domain.InstallmentsSummary
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.ui.components.EmptyStateCard
import com.finai.app.ui.components.FinaiSettingsTopBar
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.rememberReducedMotion
import com.finai.app.util.formatBrl
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun brl(cents: Long) = formatBrl(cents / 100.0)

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

/** "mar/2027" */
private fun monthYear(month: YearMonth) = "${MONTH_ABBREV_PT[month.monthValue - 1].lowercase()}/${month.year}"

/** "jan" no ano corrente, "jan/27" em outro ano. */
private fun shortMonth(month: YearMonth, today: LocalDate): String {
    val name = MONTH_ABBREV_PT[month.monthValue - 1].lowercase()
    return if (month.year == today.year) name else "$name/${month.year % 100}"
}

/** Rótulo curto da barra, em reais inteiros: "660", "1.250", "12,5 mil". */
private fun compactReais(cents: Long): String {
    val reais = Math.round(cents / 100.0)
    val fmt = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }
    return if (reais >= 10_000) fmt.format(reais / 1000.0) + " mil" else fmt.format(reais)
}

/**
 * Cartão da Agenda (ref. 2a): o que as compras parceladas pesam no mês mostrado, com os
 * próximos meses em miniatura. Toca e abre a tela de Parcelamentos.
 */
@Composable
fun InstallmentsAgendaCard(summary: InstallmentsSummary, month: YearMonth, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val monthCents = summary.monthCents(month)
    val purchases = summary.monthPurchases(month)
    val line = if (monthCents > 0) "${brl(monthCents)} neste mês · ${plural(purchases, "compra", "compras")}"
    else "Nenhuma parcela neste mês · ${plural(summary.active.size, "compra ativa", "compras ativas")}"
    // Este mês e até quatro seguintes, os mesmos totais da tela de Parcelamentos. Meses vazios no fim saem.
    val upcoming = (0L until 5L).map { month.plusMonths(it) to summary.monthCents(month.plusMonths(it)) }
        .dropLastWhile { it.second == 0L }
    val max = upcoming.maxOfOrNull { it.second } ?: 0L
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .clickable(role = Role.Button, onClickLabel = "Abrir parcelamentos", onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FinaiColors.EmeraldSoftBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Parcelamentos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(line, fontSize = 12.5.sp, color = FinaiColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (max > 0) {
            val description = upcoming.joinToString("; ") { (m, cents) ->
                "${MONTH_NAMES_PT[m.monthValue - 1]}: ${if (cents > 0) brl(cents) else "nenhuma parcela"}"
            }
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "Parcelas por mês. $description" },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                upcoming.forEachIndexed { index, (m, cents) ->
                    Column(Modifier.width(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(28.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .width(10.dp)
                                    // Mês sem parcela vira um traço rente à base, não uma barra.
                                    .then(if (cents > 0) Modifier.fillMaxHeight(cents.toFloat() / max) else Modifier.height(2.dp))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (index == 0) FinaiColors.Emerald else FinaiColors.TextMuted.copy(alpha = 0.45f)),
                            )
                        }
                        Text(MONTH_ABBREV_PT[m.monthValue - 1].take(1), fontSize = 12.sp, maxLines = 1,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (index == 0) FinaiColors.TextPrimary else FinaiColors.TextMuted,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FinaiColors.TextMuted, modifier = Modifier.size(20.dp))
    }
}

/**
 * Tela de Parcelamentos (ref. 2b): total em aberto, comprometido por mês e as compras. Abre como camada
 * por cima de tudo, entrando da direita: como rota do NavHost, a topbar do app sumia antes da troca e a
 * Agenda pulava para cima durante o fade.
 */
@Composable
fun InstallmentsScreen(visible: Boolean, summary: InstallmentsSummary, onBack: () -> Unit, today: LocalDate = LocalDate.now()) {
    BackHandler(visible) { onBack() }
    val reducedMotion = rememberReducedMotion()
    val inMillis = if (reducedMotion) 0 else 300
    val outMillis = if (reducedMotion) 0 else 240
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(inMillis, easing = FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(outMillis, easing = FastOutSlowInEasing)) { it },
    ) {
        InstallmentsContent(summary, onBack, today)
    }
}

@Composable
private fun InstallmentsContent(summary: InstallmentsSummary, onBack: () -> Unit, today: LocalDate) {
    val months = summary.openByMonth
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = months.firstOrNull { it.month.toString() == selectedKey }?.month
        ?: InstallmentsSummary.defaultMonth(months, today)

    Column(Modifier.fillMaxSize().background(FinaiColors.Background)) {
        FinaiSettingsTopBar(
            title = "Parcelamentos",
            subtitle = plural(summary.active.size, "compra ativa", "compras ativas"),
            onNavigateBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OpenTotal(summary, today) }
            if (months.isNotEmpty()) {
                item {
                    MonthlyChart(months, selected, today, onSelect = { selectedKey = it.toString() },
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (summary.active.isEmpty()) {
                item {
                    EmptyStateCard(
                        "Nenhuma compra parcelada em aberto. Compras parceladas lançadas no app ou vindas de fatura importada " +
                            "aparecem aqui até a última parcela ser paga.",
                        actionLabel = null, onAction = null,
                    )
                }
            } else {
                item {
                    Text("Compras", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                        modifier = Modifier.padding(top = 10.dp))
                }
                itemsIndexed(summary.active, key = { _, it -> it.compraId }) { index, purchase ->
                    PurchaseCard(purchase, purchaseTone(index), today)
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun OpenTotal(summary: InstallmentsSummary, today: LocalDate) {
    Column {
        Text("Total em aberto", fontSize = 13.sp, color = FinaiColors.TextTertiary)
        Text(brl(summary.openCents), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
            modifier = Modifier.padding(top = 2.dp))
        val freeFrom = summary.freeFrom
        if (summary.openCount > 0 && freeFrom != null) {
            Text(
                buildAnnotatedString {
                    append(plural(summary.openCount, "parcela restante", "parcelas restantes"))
                    append(" · livre a partir de ")
                    withStyle(SpanStyle(color = FinaiColors.EmeraldDark, fontWeight = FontWeight.Bold)) { append(monthYear(freeFrom)) }
                },
                fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthlyChart(
    months: List<InstallmentMonth>,
    selected: YearMonth?,
    today: LocalDate,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = months.maxOf { it.cents }.coerceAtLeast(1)
    // Até seis meses dividem a largura, como na referência; mais que isso rolam de lado.
    val fits = months.size <= 6
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(20.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comprometido por mês", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                modifier = Modifier.weight(1f))
            Text("toque num mês", fontSize = 12.sp, color = FinaiColors.TextMuted)
        }
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState())),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEach { m ->
                val isSelected = m.month == selected
                val label = MONTH_ABBREV_PT[m.month.monthValue - 1]
                Column(
                    modifier = (if (fits) Modifier.weight(1f) else Modifier.width(52.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Tab) { onSelect(m.month) }
                        .semantics {
                            this.selected = isSelected
                            contentDescription = "${MONTH_NAMES_PT[m.month.monthValue - 1]} de ${m.month.year}: ${brl(m.cents)}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(compactReais(m.cents), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        color = if (isSelected) FinaiColors.EmeraldDark else FinaiColors.TextMuted)
                    Box(Modifier.padding(top = 6.dp).height(80.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((m.cents.toFloat() / max).coerceAtLeast(0.06f))
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) FinaiColors.Emerald else FinaiColors.SurfaceMuted),
                        )
                    }
                    Text(
                        if (m.month.year == today.year || m.month.monthValue != 1) label else "$label/${m.month.year % 100}",
                        fontSize = 12.sp, maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) FinaiColors.TextPrimary else FinaiColors.TextMuted,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
            }
        }
        val current = months.firstOrNull { it.month == selected }
        if (current != null) {
            HorizontalDivider(color = FinaiColors.BorderFaint, thickness = 1.dp, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val name = MONTH_NAMES_PT[current.month.monthValue - 1] +
                    if (current.month.year != today.year) " de ${current.month.year}" else ""
                val estimate = if (current.estimatedCount > 0) " · estimado" else ""
                Text("$name · ${plural(current.count, "parcela", "parcelas")}$estimate", fontSize = 13.sp,
                    color = FinaiColors.TextMuted, modifier = Modifier.weight(1f))
                Text(brl(current.cents), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            }
        }
    }
}

/** Fundo e letra do selo da compra, e a cor das parcelas pagas na barra. */
private data class PurchaseTone(val badge: Color, val letter: Color, val bar: Color)

@Composable
private fun purchaseTone(index: Int): PurchaseTone {
    val dark = FinaiColors.isDark
    return when (index % 4) {
        0 -> if (dark) PurchaseTone(Color(0xFF1C2850), Color(0xFF93A9F5), Color(0xFF4A6BB5))
        else PurchaseTone(Color(0xFFE6ECFB), Color(0xFF2F4E9E), Color(0xFF5B7BC8))
        1 -> if (dark) PurchaseTone(Color(0xFF3A2E0E), Color(0xFFE8BC4A), Color(0xFFB08A2A))
        else PurchaseTone(Color(0xFFFBF2DC), Color(0xFF8A6512), Color(0xFFC9A03A))
        2 -> if (dark) PurchaseTone(Color(0xFF431A2E), Color(0xFFF2A2C4), Color(0xFFA24C74))
        else PurchaseTone(Color(0xFFFBE6EF), Color(0xFF9C2F5E), Color(0xFFC9668F))
        else -> PurchaseTone(FinaiColors.EmeraldSoftBg, FinaiColors.EmeraldDark, FinaiColors.Emerald)
    }
}

@Composable
private fun PurchaseCard(purchase: InstallmentPurchase, tone: PurchaseTone, today: LocalDate) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tone.badge), contentAlignment = Alignment.Center) {
                Text(purchase.name.trim().take(1).uppercase().ifBlank { "•" }, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = tone.letter)
            }
            Column(Modifier.weight(1f)) {
                Text(purchase.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(purchase.card.takeIf { it.isNotBlank() }, "${purchase.totalInstallments}x",
                        "total ${brl(purchase.totalCents)}").joinToString(" · "),
                    fontSize = 12.5.sp, color = FinaiColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(brl(purchase.monthlyCents), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text("por mês", fontSize = 12.sp, color = FinaiColors.TextMuted)
            }
        }
        InstallmentProgress(purchase, tone, Modifier.padding(top = 14.dp))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Compra de fatura importada: as próximas parcelas ainda não estão em fatura nenhuma.
            val end = purchase.lastDue?.let { " · termina em ${shortMonth(it, today)}" }.orEmpty()
            Text("${purchase.paidCount} de ${purchase.totalInstallments} pagas$end", fontSize = 12.5.sp,
                color = FinaiColors.TextMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${brl(purchase.openCents)} em aberto", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = FinaiColors.TextPrimary, maxLines = 1)
        }
        if (purchase.estimated) {
            Text("Da fatura importada: as próximas parcelas são estimadas com o mesmo valor.", fontSize = 12.sp,
                color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Um traço por parcela: pagas na cor da compra, a próxima em destaque, as demais apagadas. */
@Composable
private fun InstallmentProgress(purchase: InstallmentPurchase, tone: PurchaseTone, modifier: Modifier = Modifier) {
    val total = purchase.totalInstallments.coerceAtLeast(1)
    val description = "${purchase.paidCount} de $total parcelas pagas"
    if (total > 24) {
        // Muitas parcelas viram traços finos demais para ler; uma barra contínua diz o mesmo.
        Box(
            modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(FinaiColors.SurfaceMuted)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Box(Modifier.fillMaxWidth(purchase.paidCount.toFloat() / total).height(5.dp).background(tone.bar))
        }
        return
    }
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..total).forEach { n ->
            val color = when (n) {
                in purchase.paidNumbers -> tone.bar
                purchase.nextNumber -> FinaiColors.TextPrimary
                else -> FinaiColors.SurfaceMuted
            }
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}
