package com.finai.app.ui.screens.simulator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.MONTH_ABBREV_PT
import com.finai.app.domain.scenario.PurchaseOption
import com.finai.app.domain.scenario.PurchaseSimulation
import com.finai.app.domain.scenario.PurchaseVerdictV2
import com.finai.app.domain.scenario.PurchaseWay
import com.finai.app.ui.components.*
import com.finai.app.ui.screens.home.V2Panel
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0

private fun brl(cents: Long) = formatBrl0(cents / 100.0)

@Composable
private fun verdictColor(v: PurchaseVerdictV2): Color = when (v) {
    PurchaseVerdictV2.Cabe -> FinaiColors.Emerald
    PurchaseVerdictV2.Apertado -> FinaiColors.Amber
    PurchaseVerdictV2.NaoCabe -> FinaiColors.Rose
}

/**
 * "Posso comprar?" (planning-v2, fase 8): nome e valor, desconto à vista, cinco formas de pagar
 * com o veredito de cada uma, o gráfico do menor saldo com e sem a compra e a linha da margem.
 * "Lançar" abre o formulário do "+" preenchido; nada é gravado aqui.
 */
@Composable
fun SimulatorScreen(
    cards: List<String>,
    nextIncomeLabel: String?,
    simulate: (valueCents: Long, discountPercent: Double, card: String?) -> PurchaseSimulation?,
    onLaunch: (name: String, option: PurchaseOption, card: String?) -> Unit,
    onRemindLater: (name: String, valueCents: Long) -> Unit,
    onAsk: (name: String, simulation: PurchaseSimulation) -> Unit,
    onClose: () -> Unit,
    initialValueCents: Long = 0,
    initialName: String = "",
) {
    BackHandler(onBack = onClose)
    var name by rememberSaveable { mutableStateOf(initialName) }
    var valueText by rememberSaveable { mutableStateOf(if (initialValueCents > 0) (initialValueCents / 100).toString() else "") }
    var discount by rememberSaveable { mutableIntStateOf(0) }
    var wayIndex by rememberSaveable { mutableIntStateOf(-1) }
    var card by rememberSaveable { mutableStateOf(cards.firstOrNull().orEmpty()) }
    val valueCents = (valueText.toLongOrNull() ?: 0) * 100
    val simulation = remember(valueCents, discount, card) { if (valueCents > 0) simulate(valueCents, discount.toDouble(), card.ifBlank { null }) else null }
    val selected: PurchaseOption? = simulation?.let { s ->
        s.options.getOrNull(wayIndex) ?: s.best?.let(s::option) ?: s.options.first()
    }

    Column(Modifier.fillMaxSize().background(FinaiColors.Background)) {
        FinaiSettingsTopBar(title = "Posso comprar?", onNavigateBack = onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            V2Panel {
                Text("O QUE VOCÊ QUER COMPRAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                Box(Modifier.padding(top = 10.dp)) {
                    if (name.isEmpty()) Text("Nome (opcional)", fontSize = 15.sp, color = FinaiColors.TextMuted)
                    BasicTextField(name, { name = it.take(30) }, singleLine = true, cursorBrush = SolidColor(FinaiColors.EmeraldDark),
                        textStyle = TextStyle(fontSize = 15.sp, color = FinaiColors.TextPrimary), modifier = Modifier.fillMaxWidth().testTag("sim-name"))
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("R$ ", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextMuted)
                    Box(Modifier.weight(1f)) {
                        if (valueText.isEmpty()) Text("0", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextDisabled)
                        BasicTextField(valueText, { v -> valueText = v.filter { it.isDigit() }.trimStart('0').take(7); wayIndex = -1 }, singleLine = true,
                            cursorBrush = SolidColor(FinaiColors.EmeraldDark), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary), modifier = Modifier.fillMaxWidth().testTag("sim-value"))
                    }
                }
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10).forEach { d -> SelectChip(if (d == 0) "Sem desconto" else "$d% à vista", discount == d) { discount = d } }
                }
                if (cards.size > 1) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { c -> SelectChip(c, card == c) { card = c } }
                }
            }
            if (simulation == null || selected == null) {
                Text("Digite o valor para ver se cabe e qual o melhor jeito de pagar. Nada é lançado aqui.", fontSize = 14.sp, color = FinaiColors.TextSecondary)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    simulation.options.forEachIndexed { i, o ->
                        val isSelected = o.way == selected.way
                        Column(
                            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) FinaiColors.InkStrong else FinaiColors.Surface)
                                .border(1.dp, if (isSelected) FinaiColors.InkStrong else FinaiColors.BorderSubtle, RoundedCornerShape(14.dp))
                                .clickable(role = Role.Tab) { wayIndex = i }.semantics { this.selected = isSelected }.padding(vertical = 8.dp).testTag("sim-way-${o.way.installments}"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(o.way.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) FinaiColors.OnInkStrong else FinaiColors.TextPrimary)
                            Dot(verdictColor(o.verdict), 8.dp)
                        }
                    }
                }
                V2Panel {
                    val color = verdictColor(selected.verdict)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillTag(selected.verdict.label, color.copy(alpha = 0.18f), when (selected.verdict) {
                            PurchaseVerdictV2.Cabe -> FinaiColors.EmeraldDark; PurchaseVerdictV2.Apertado -> FinaiColors.AmberDark; PurchaseVerdictV2.NaoCabe -> FinaiColors.RoseDark
                        }, modifier = Modifier.testTag("sim-verdict"))
                        if (simulation.best == selected.way) Text("melhor forma", fontSize = 12.sp, color = FinaiColors.TextMuted)
                    }
                    Text(
                        when {
                            selected.way.isPix -> "No Pix, hoje: ${brl(selected.totalCents)}" + if (selected.totalCents < simulation.valueCents) " (com desconto)" else ""
                            selected.way.installments == 1 -> "1× no cartão: ${brl(selected.totalCents)} em %02d/%02d".format(selected.firstDue.dayOfMonth, selected.firstDue.monthValue)
                            else -> "${selected.way.installments}× de ${brl(selected.installmentCents)}, a 1ª em %02d/%02d".format(selected.firstDue.dayOfMonth, selected.firstDue.monthValue)
                        },
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 10.dp))
                    Text(simulation.sentence(selected), fontSize = 13.5.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    V2BarChart(
                        selected.months.map { m ->
                            ChartColumn(MONTH_ABBREV_PT[m.month.monthValue - 1].lowercase(), listOf(m.withCents to if (m.month == selected.worst.month) color else FinaiColors.TextDisabled),
                                highlighted = m.month == selected.worst.month)
                        },
                        description = "Menor saldo de cada mês com a compra: " + selected.months.joinToString { "${MONTH_ABBREV_PT[it.month.monthValue - 1]} ${brl(it.withCents)}, sem a compra ${brl(it.withoutCents)}" },
                        modifier = Modifier.padding(top = 16.dp), guideCents = simulation.marginCents.takeIf { it > 0 },
                    )
                    Text("Menor saldo previsto em cada mês, com a compra. Tracejado: sua margem de ${brl(simulation.marginCents)}. Sem a compra, o pior mês ficaria em ${brl(selected.worst.withoutCents)}.",
                        fontSize = 12.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
                }
                simulation.goalDelayMonths?.let { n ->
                    Text("Nas metas: cerca de $n ${if (n == 1) "mês" else "meses"} a mais para chegar lá, igual em qualquer forma de pagar.", fontSize = 13.sp, color = FinaiColors.TextSecondary)
                }
                ActionButton(
                    when {
                        selected.way.isPix -> "Lançar no Pix"
                        selected.way.installments == 1 -> "Lançar no ${card.ifBlank { "cartão" }} em 1×"
                        else -> "Lançar no ${card.ifBlank { "cartão" }} em ${selected.way.installments}×"
                    },
                    { onLaunch(name.trim(), selected, card.ifBlank { null }) }, Modifier.fillMaxWidth().testTag("sim-launch"), primary = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nextIncomeLabel != null) ActionButton("Lembrar depois do $nextIncomeLabel", { onRemindLater(name.trim(), simulation.valueCents) }, Modifier.weight(1f))
                    ActionButton("Perguntar", { onAsk(name.trim(), simulation) }, Modifier.weight(if (nextIncomeLabel != null) 0.6f else 1f))
                }
                Text("Conta com o que já está lançado para os próximos 6 meses. Aportes planejados das metas não entram como saída.", fontSize = 12.sp, color = FinaiColors.TextMuted)
            }
        }
    }
}
