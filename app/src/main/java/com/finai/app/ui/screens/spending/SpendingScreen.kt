package com.finai.app.ui.screens.spending

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Categorias
import com.finai.app.domain.spending.SubscriptionLine
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.spending.CategorySpend
import com.finai.app.domain.spending.LimitProposal
import com.finai.app.domain.spending.LimitSuggestion
import com.finai.app.domain.spending.SpendTone
import com.finai.app.domain.spending.SpendingAnalysis
import com.finai.app.domain.spending.SpendingPeriod
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.*
import com.finai.app.ui.screens.home.V2Panel
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

/**
 * Gastos (planning-v2, fase 6): busca, período, total com uma frase de comparação, seis meses,
 * categorias com limite e ritmo, estabelecimentos e assinaturas. Absorve a antiga tela de Limites.
 */
@Composable
fun SpendingScreen(
    listState: LazyListState,
    transacoes: List<TransacaoEntity>,
    limits: Map<String, Long>,
    subscriptions: List<SubscriptionLine>,
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    onSetLimit: (String, Long) -> Unit,
    onApplyLimits: (List<LimitProposal>) -> Unit,
    onSubscriptionAction: (SubscriptionLine) -> Unit,
    onOpenEntry: (TransacaoEntity) -> Unit,
    onNewEntry: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val current = YearMonth.from(today)
    val periods = remember(current) {
        listOf(current.minusMonths(1).let { SpendingPeriod(it.year, it.monthValue) }, SpendingPeriod(current.year, current.monthValue), SpendingPeriod(current.year))
    }
    var periodIndex by rememberSaveable { mutableIntStateOf(1) }
    var query by rememberSaveable { mutableStateOf("") }
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var limitTarget by remember { mutableStateOf<CategorySpend?>(null) }
    var showSuggestions by remember { mutableStateOf(false) }
    val period = periods[periodIndex]
    val analysis = remember(transacoes, limits, period, today) { SpendingAnalysis.of(transacoes, limits, period, today) }
    val results = remember(transacoes, query) { SpendingAnalysis.search(transacoes, query) }
    val proposals = remember(transacoes, limits, today) { LimitSuggestion.suggest(transacoes, limits, today) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) { runCatching { focus.requestFocus() }; onSearchFocused() } }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("title") { PageTitle("Gastos", listState, subtitle = "Para onde o dinheiro foi") }
        item("search") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(FinaiColors.Surface)
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar lançamento, lugar ou categoria", fontSize = 14.sp, color = FinaiColors.TextMuted)
                    BasicTextField(
                        query, { query = it.take(40) }, singleLine = true, cursorBrush = SolidColor(FinaiColors.EmeraldDark),
                        textStyle = TextStyle(fontSize = 14.sp, color = FinaiColors.TextPrimary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("spending-search").semantics { contentDescription = "Buscar nos lançamentos" },
                    )
                }
                if (query.isNotEmpty()) TextAction("Limpar", { query = "" })
            }
        }
        if (query.trim().length >= 2) {
            item("results-head") { SectionHeader("${results.size} ${if (results.size == 1) "resultado" else "resultados"}", subtitle = "Total ${formatBrl(results.filter { it.tipo == "Gasto" }.sumOf { it.valorCentavos } / 100.0)} em gastos") }
            items(results, key = { "r-${it.id}" }) { t -> EntryLine(t, onOpenEntry) }
            if (results.isEmpty()) item("no-results") { Text("Nada encontrado para \"${query.trim()}\".", fontSize = 14.sp, color = FinaiColors.TextSecondary) }
        } else {
            item("periods") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periods.forEachIndexed { i, p -> SelectChip(p.label, i == periodIndex) { periodIndex = i; openCategory = null } }
                }
            }
            item("total") {
                V2Panel {
                    Text(if (period.month == null) "GASTO EM ${period.year}" else if (period.yearMonth == current) "GASTO ATÉ HOJE" else "GASTO EM ${period.label.uppercase()}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                    Text(formatBrl0(analysis.totalCents / 100.0), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
                        modifier = Modifier.padding(top = 10.dp).testTag("spending-total"))
                    if (analysis.comparison.isNotBlank()) Text(analysis.comparison, fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    if (analysis.trend.any { it.second > 0 }) {
                        V2BarChart(
                            analysis.trend.map { (m, cents) -> ChartColumn(MONTH_ABBREV_PT[m.monthValue - 1].lowercase(), listOf(cents to FinaiColors.Emerald), highlighted = period.yearMonth == m) },
                            description = "Gasto por mês: " + analysis.trend.joinToString { (m, c) -> "${MONTH_ABBREV_PT[m.monthValue - 1]} ${formatBrl0(c / 100.0)}" },
                            modifier = Modifier.padding(top = 16.dp), height = 96.dp,
                        )
                    }
                }
            }
            if (analysis.totalCents == 0L && analysis.categories.isEmpty()) item("empty") {
                EmptyStateCard("Nenhum gasto lançado neste período. Lance pelo \"+\" ou importe uma fatura para ver para onde o dinheiro foi.", "Lançar gasto", onNewEntry)
            }
            if (analysis.categories.isNotEmpty()) {
                item("cat-head") {
                    SectionHeader("Por categoria", subtitle = if (period.month != null) "Toque para ver os lançamentos e ajustar o limite" else null,
                        action = if (proposals.isNotEmpty() && period.yearMonth == current) "Sugerir limites" else null, onAction = { showSuggestions = true })
                }
                items(analysis.categories, key = { "c-${it.categoria}" }) { cat ->
                    CategoryCard(cat, analysis.totalCents, open = openCategory == cat.categoria, canLimit = period.yearMonth == current,
                        entries = analysis.entries.filter { it.categoria == cat.categoria && !it.data.toLocalDate().isAfter(today) },
                        onToggle = { openCategory = if (openCategory == cat.categoria) null else cat.categoria },
                        onLimit = { limitTarget = cat }, onOpenEntry = onOpenEntry)
                }
            }
            if (analysis.merchants.isNotEmpty()) item("merchants") {
                V2Panel {
                    SectionHeader("Onde você mais gastou")
                    analysis.merchants.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontSize = 14.sp, color = FinaiColors.TextPrimary, maxLines = 1)
                                Text("${m.count} ${if (m.count == 1) "vez" else "vezes"}", fontSize = 12.sp, color = FinaiColors.TextMuted)
                            }
                            Text(formatBrl(m.cents / 100.0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                        }
                    }
                }
            }
            val active = subscriptions.filter { it.active }
            if (subscriptions.isNotEmpty()) item("subs") {
                V2Panel {
                    val monthly = active.sumOf { it.cents }
                    SectionHeader("Assinaturas", subtitle = if (active.isEmpty()) "Nenhuma ativa" else "${formatBrl0(monthly / 100.0)} por mês · ${formatBrl0(monthly * 12 / 100.0)} por ano")
                    subscriptions.forEach { sub ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.name, fontSize = 14.sp, color = FinaiColors.TextPrimary)
                                Text(sub.note, fontSize = 12.sp, color = FinaiColors.TextMuted)
                            }
                            Text(formatBrl(sub.cents / 100.0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sub.active) FinaiColors.TextPrimary else FinaiColors.TextDisabled)
                            TextAction(if (sub.active) "Cancelei" else "Reativar", { onSubscriptionAction(sub) }, color = if (sub.active) FinaiColors.RoseDark else FinaiColors.EmeraldDark)
                        }
                    }
                }
            }
            item("footer") {
                Text("Aqui é consumo: a compra no cartão conta no dia da compra, não no vencimento da fatura. O caixa (o que sai da conta e quando) fica na Agenda.",
                    fontSize = 12.sp, color = FinaiColors.TextMuted)
            }
        }
    }

    limitTarget?.let { cat -> LimitDialog(cat, onDismiss = { limitTarget = null }) { cents -> onSetLimit(cat.categoria, cents); limitTarget = null } }
    if (showSuggestions) {
        AlertDialog(
            onDismissRequest = { showSuggestions = false },
            title = { Text("Limites sugeridos", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pela média dos últimos três meses, com 5% a menos onde o limite estourou.", fontSize = 13.sp, color = FinaiColors.TextSecondary)
                    proposals.forEach { p ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(p.categoria, Modifier.weight(1f), fontSize = 14.sp, color = FinaiColors.TextPrimary)
                            Text((p.currentCents?.let { formatBrl0(it / 100.0) + " → " } ?: "") + formatBrl0(p.suggestedCents / 100.0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ onApplyLimits(proposals); showSuggestions = false }) { Text("Aplicar") } },
            dismissButton = { TextButton({ showSuggestions = false }) { Text("Agora não") } },
        )
    }
}

@Composable
private fun CategoryCard(
    cat: CategorySpend, total: Long, open: Boolean, canLimit: Boolean, entries: List<TransacaoEntity>,
    onToggle: () -> Unit, onLimit: () -> Unit, onOpenEntry: (TransacaoEntity) -> Unit,
) {
    val color = when (cat.tone) { SpendTone.Over -> FinaiColors.Rose; SpendTone.Warn -> FinaiColors.Amber; SpendTone.Ok -> FinaiColors.Emerald }
    val noteColor = when (cat.tone) { SpendTone.Over -> FinaiColors.RoseDark; SpendTone.Warn -> FinaiColors.AmberDark; SpendTone.Ok -> FinaiColors.TextMuted }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FinaiColors.Surface).border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "recolher" else "ver lançamentos", onClick = onToggle).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cat.categoria, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(formatBrl0(cat.cents / 100.0) + (cat.limitCents?.let { " de ${formatBrl0(it / 100.0)}" } ?: ""), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        }
        val progress = cat.limitCents?.let { (cat.cents.toFloat() / it).coerceIn(0f, 1f) } ?: if (total > 0) cat.cents.toFloat() / total else 0f
        ProgressTrack(progress, fillColor = if (cat.limitCents == null) FinaiColors.TextDisabled else color, modifier = Modifier.padding(top = 10.dp))
        Text(cat.note.ifBlank { if (total > 0) "${cat.cents * 100 / total}% do total · ${cat.count} ${if (cat.count == 1) "lançamento" else "lançamentos"}" else "Sem gasto ainda" },
            fontSize = 12.5.sp, color = noteColor, modifier = Modifier.padding(top = 6.dp))
        if (open) {
            entries.take(12).forEach { EntryLine(it, onOpenEntry) }
            if (entries.size > 12) Text("e mais ${entries.size - 12}. Use a busca para ver todos.", fontSize = 12.sp, color = FinaiColors.TextMuted)
            if (canLimit && cat.categoria in Categorias.all) TextAction(if (cat.limitCents == null) "Definir limite" else "Ajustar limite", onLimit)
        }
    }
}

@Composable
private fun EntryLine(t: TransacaoEntity, onOpen: (TransacaoEntity) -> Unit) {
    val d = t.data.toLocalDate()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpen(t) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.descricao.ifBlank { t.categoria }, fontSize = 14.sp, color = FinaiColors.TextPrimary, maxLines = 1)
            Text("%02d/%02d · %s".format(d.dayOfMonth, d.monthValue, t.categoria) + (if (t.faturaId != null) " · cartão" else ""), fontSize = 12.sp, color = FinaiColors.TextMuted)
        }
        Text((if (t.tipo == "Receita") "+ " else "") + formatBrl(t.valorCentavos / 100.0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = if (t.tipo == "Receita") FinaiColors.EmeraldDark else FinaiColors.TextPrimary)
    }
}

@Composable
private fun LimitDialog(cat: CategorySpend, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(cat.limitCents?.let { (it / 100).toString() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limite de ${cat.categoria}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quanto você quer gastar por mês nesta categoria? Deixe vazio para tirar o limite.", fontSize = 13.sp, color = FinaiColors.TextSecondary)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("R$ ", fontSize = 18.sp, color = FinaiColors.TextMuted)
                    BasicTextField(text, { v -> text = v.filter { it.isDigit() }.take(7) }, singleLine = true, cursorBrush = SolidColor(FinaiColors.EmeraldDark),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary), modifier = Modifier.fillMaxWidth().testTag("limit-field"))
                }
            }
        },
        confirmButton = { TextButton({ onSave((text.toLongOrNull() ?: 0) * 100) }) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
