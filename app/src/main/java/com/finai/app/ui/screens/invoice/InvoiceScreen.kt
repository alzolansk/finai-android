package com.finai.app.ui.screens.invoice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.BillStatus
import com.finai.app.domain.InvoiceSummary
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.toUiBill
import com.finai.app.ui.components.EntryGlyph
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.util.formatBrl
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val CardShape = RoundedCornerShape(22.dp)

/** Cor fixa por categoria — a mesma categoria tem a mesma cor em todos os blocos da página. */
private fun categoryColor(categoria: String): Color = when (categoria) {
    "Alimentação" -> Color(0xFFF59E0B)
    "Transporte" -> Color(0xFF6366F1)
    "Moradia" -> Color(0xFF0EA5E9)
    "Saúde" -> Color(0xFFF43F5E)
    "Lazer" -> Color(0xFF8B5CF6)
    "Educação" -> Color(0xFF14B8A6)
    "Compras" -> Color(0xFFEC4899)
    "Serviços" -> Color(0xFF64748B)
    else -> Color(0xFFA1A1AA)
}

private enum class ItemFilter(val label: String) { Todos("Todos"), Compras("Compras"), Estornos("Estornos"), Parcelados("Parcelados") }

/**
 * Página da fatura do cartão — substitui o `AlertDialog` que só listava texto corrido.
 * Mostra o que a fatura é (total, vencimento, status com ação de pagar) e o que ela
 * contém (para onde foi por categoria, onde mais usou, parcelamentos, estornos e todos os
 * itens por dia). Os números vêm de [InvoiceSummary]; nada aqui chama IA.
 */
@Composable
fun InvoiceScreen(
    visible: Boolean,
    conta: ContaEntity?,
    fatura: FaturaCartaoEntity?,
    items: List<TransacaoEntity>,
    onTogglePaid: (contaId: Long, pago: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(visible) { onClose() }
    AnimatedVisibility(
        visible = visible && conta != null,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { it / 6 },
    ) {
        // Mantém o último conteúdo durante a animação de saída, quando conta já virou nula.
        val lastConta = remember { mutableStateOf(conta) }
        if (conta != null) lastConta.value = conta
        val shown = lastConta.value ?: return@AnimatedVisibility
        InvoiceContent(shown, fatura, items, onTogglePaid, onClose)
    }
}

@Composable
private fun InvoiceContent(
    conta: ContaEntity,
    fatura: FaturaCartaoEntity?,
    items: List<TransacaoEntity>,
    onTogglePaid: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val summary = remember(items) { InvoiceSummary.of(items) }
    val status = remember(conta) { conta.toUiBill(LocalDate.now()).status }
    var filter by remember { mutableStateOf(ItemFilter.Todos) }
    val installmentIds = remember(summary) { summary.parcelas.map { it.item.id }.toSet() }

    Column(
        Modifier.fillMaxSize().background(FinaiColors.SurfaceSunken)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Camada própria: toque em área vazia não pode vazar para a Agenda atrás.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(Modifier.fillMaxWidth().height(58.dp).background(FinaiColors.Surface)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { EntryGlyph("back", FinaiColors.TextPrimary, "Voltar") }
            Text("Fatura do cartão", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(FinaiColors.BorderHairline))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeroCard(conta, fatura, status, summary, onTogglePaid) }
            if (summary.categorias.isNotEmpty()) item { CategoriesCard(summary) }
            if (summary.merchants.size > 1) item { MerchantsCard(summary) }
            if (summary.parcelas.isNotEmpty()) item { InstallmentsCard(summary) }

            item {
                Column {
                    Text("Todos os lançamentos", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
                    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemFilter.values().forEach { f ->
                            val count = when (f) {
                                ItemFilter.Todos -> items.size
                                ItemFilter.Compras -> summary.comprasCount
                                ItemFilter.Estornos -> summary.estornosCount
                                ItemFilter.Parcelados -> summary.parcelas.size
                            }
                            if (count > 0 || f == ItemFilter.Todos) FilterChip("${f.label} · $count", filter == f) { filter = f }
                        }
                    }
                }
            }
            val filtered = summary.porDia.mapNotNull { (day, list) ->
                val kept = list.filter {
                    when (filter) {
                        ItemFilter.Todos -> true
                        ItemFilter.Compras -> it.valorCentavos > 0
                        ItemFilter.Estornos -> it.valorCentavos < 0
                        ItemFilter.Parcelados -> it.id in installmentIds
                    }
                }
                if (kept.isEmpty()) null else day to kept
            }
            filtered.forEach { (day, list) ->
                item(key = "day-$day-$filter") { DayGroup(day, list) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeroCard(conta: ContaEntity, fatura: FaturaCartaoEntity?, status: BillStatus, summary: InvoiceSummary, onTogglePaid: (Long, Boolean) -> Unit) {
    val vencimento = (fatura?.vencimento ?: conta.vencimento).toLocalDate()
    val fechamento = fatura?.fechamento?.toLocalDate()
    val paga = conta.status == "pago"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(FinaiColors.Ink, FinaiColors.InkBorder)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                EntryGlyph("card", Color.White, size = 18)
            }
            Text(
                (fatura?.referencia ?: conta.nome).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = .75f), letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Text(
                status.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = status.fg,
                modifier = Modifier.clip(CircleShape).background(status.bg).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(formatBrl(conta.valorCentavos / 100.0), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 18.dp))
        Text(
            "Vence em ${vencimento.dayOfMonth} de ${MONTH_NAMES_PT[vencimento.monthValue - 1].lowercase()}" +
                (fechamento?.let { " · fechou ${it.dayOfMonth} ${MONTH_ABBREV_PT[it.monthValue - 1].lowercase()}" } ?: ""),
            fontSize = 12.5.sp, color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat("Compras", formatBrl(summary.comprasCents / 100.0), "${summary.comprasCount} ite${if (summary.comprasCount == 1) "m" else "ns"}", Modifier.weight(1f))
            HeroStat(
                "Estornos",
                if (summary.estornosCents > 0) "− " + formatBrl(summary.estornosCents / 100.0) else "—",
                if (summary.estornosCount > 0) "${summary.estornosCount} devolvido${if (summary.estornosCount == 1) "" else "s"}" else "nenhum",
                Modifier.weight(1f), valueColor = if (summary.estornosCents > 0) Color(0xFF6EE7B7) else Color.White,
            )
        }
        val bg by animateColorAsState(if (paga) Color.White.copy(alpha = .08f) else FinaiColors.Emerald, finaiTween(FinaiMotion.Quick), label = "payBg")
        Box(
            Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                .border(1.dp, if (paga) Color.White.copy(alpha = .16f) else Color.Transparent, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { onTogglePaid(conta.id, !paga) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (paga) EntryGlyph("check", Color(0xFF6EE7B7), size = 18)
                Text(
                    if (paga) "Fatura paga · toque para desfazer" else "Marcar fatura como paga",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = if (paga) 8.dp else 0.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, note: String, modifier: Modifier, valueColor: Color = Color.White) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp)) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextOnDarkMuted, letterSpacing = .5.sp)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        Text(note, fontSize = 12.sp, color = FinaiColors.TextOnDarkMuted, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).border(1.dp, FinaiColors.BorderHairline, CardShape)
            .background(FinaiColors.Surface).padding(16.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun CategoriesCard(summary: InvoiceSummary) {
    // A barra "enche" ao abrir a página — dá leitura de proporção antes dos números.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val grow by animateFloatAsState(if (appeared) 1f else 0f, tween(700), label = "catGrow")
    SectionCard("Para onde foi", "Compras por categoria") {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(FinaiColors.SurfaceSunken)) {
            summary.categorias.forEach { c ->
                if (c.fraction * grow > 0f) Box(Modifier.weight(c.fraction * grow).fillMaxHeight().background(categoryColor(c.categoria)))
            }
            if (grow < 1f) Spacer(Modifier.weight(1f - grow + 0.0001f))
        }
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            summary.categorias.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(categoryColor(c.categoria).copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                        EntryGlyph(c.categoria, categoryColor(c.categoria), size = 17)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.categoria, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                        Text("${c.count} compra${if (c.count == 1) "" else "s"} · ${Math.round(c.fraction * 100)}%", fontSize = 12.sp, color = FinaiColors.TextMuted)
                    }
                    Text(formatBrl(c.cents / 100.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun MerchantsCard(summary: InvoiceSummary) {
    val top = summary.merchants.first().cents.coerceAtLeast(1)
    SectionCard("Onde você mais usou", "Somando compras repetidas no mesmo lugar") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            summary.merchants.forEachIndexed { index, m ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted, modifier = Modifier.width(20.dp))
                        Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (m.count > 1) Text("${m.count}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp).clip(CircleShape).background(FinaiColors.SurfaceMuted).padding(horizontal = 7.dp, vertical = 2.dp))
                        Text(formatBrl(m.cents / 100.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    }
                    Box(Modifier.padding(start = 20.dp, top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(FinaiColors.SurfaceSunken)) {
                        Box(Modifier.fillMaxWidth(m.cents.toFloat() / top).fillMaxHeight().clip(CircleShape).background(FinaiColors.TextPrimary.copy(alpha = .75f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallmentsCard(summary: InvoiceSummary) {
    val porMes = summary.parcelas.filter { it.restantes > 0 }.sumOf { it.item.valorCentavos }
    SectionCard(
        "Parcelamentos",
        if (porMes > 0) "${formatBrl(porMes / 100.0)} já comprometidos nas próximas faturas" else "Todas terminam nesta fatura",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            summary.parcelas.forEach { p ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.nome, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Parcela ${p.atual} de ${p.total}" + if (p.restantes == 0) " · última" else " · faltam ${p.restantes}",
                                fontSize = 12.sp, color = if (p.restantes == 0) FinaiColors.EmeraldDark else FinaiColors.TextMuted,
                            )
                        }
                        Text(formatBrl(p.item.valorCentavos / 100.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                    }
                    // Um segmento por parcela: preenchido = já cobrada.
                    Row(Modifier.padding(top = 7.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(p.total.coerceAtMost(24)) { i ->
                            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape)
                                .background(if (i < p.atual) FinaiColors.Emerald else FinaiColors.SurfaceSunken))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) =
    com.finai.app.ui.components.SelectChip(label, selected, onClick)

@Composable
private fun DayGroup(day: LocalDate, list: List<TransacaoEntity>) {
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR")).trimEnd('.')
    Column {
        Row(Modifier.padding(bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$weekday, ${day.dayOfMonth} ${MONTH_ABBREV_PT[day.monthValue - 1].lowercase()}".uppercase(),
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextMuted, letterSpacing = .5.sp, modifier = Modifier.weight(1f))
            val dayTotal = list.sumOf { it.valorCentavos }
            Text(formatBrl(dayTotal / 100.0), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
        }
        Column(Modifier.fillMaxWidth().clip(CardShape).border(1.dp, FinaiColors.BorderHairline, CardShape).background(FinaiColors.Surface)) {
            list.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(FinaiColors.BorderHairline))
                ItemRow(item)
            }
        }
    }
}

@Composable
private fun ItemRow(item: TransacaoEntity) {
    val estorno = item.valorCentavos < 0
    val parcela = InvoiceSummary.installmentOf(item.descricao)
    val color = if (estorno) FinaiColors.EmeraldDark else categoryColor(item.categoria)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            EntryGlyph(if (estorno) "back" else item.categoria, color, size = 17)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(InvoiceSummary.cleanName(item.descricao), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(if (estorno) "Estorno" else item.categoria, fontSize = 12.sp, color = FinaiColors.TextMuted)
                if (parcela != null) Text(
                    "${parcela.first}/${parcela.second}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary,
                    modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(FinaiColors.SurfaceMuted).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            (if (estorno) "− " else "") + formatBrl(kotlin.math.abs(item.valorCentavos) / 100.0),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (estorno) FinaiColors.EmeraldDark else FinaiColors.TextPrimary,
        )
    }
}
