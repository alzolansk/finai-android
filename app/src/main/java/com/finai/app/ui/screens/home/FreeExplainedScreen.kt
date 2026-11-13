package com.finai.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.Reconciliation
import com.finai.app.domain.ReconciliationState
import com.finai.app.domain.balance.DayBalance
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.ui.components.FinaiSettingsTopBar
import com.finai.app.ui.theme.FinaiColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Tela inteira; todos os valores vêm do mesmo motor do cartão de Hoje. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreeExplainedScreen(
    free: FreeToSpend?, accountCents: Long, marginCents: Long, reconciliation: ReconciliationState?,
    accounts: List<String>, onBack: () -> Unit, onMargin: (Long) -> Unit,
    onReconcile: suspend (String, Long) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    BackHandler(onBack = onBack)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customAmount by rememberSaveable { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize(), color = FinaiColors.Background) {
    Column(Modifier.fillMaxSize()) {
        FinaiSettingsTopBar("De onde vem o livre", onNavigateBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (free != null) {
                Text("Seu saldo dia a dia até ${free.nextIncome.noun}", fontSize = 13.sp, color = FinaiColors.TextMuted)
                DailyBalanceChart(free)
                V2Panel {
                    EquationLine("Em conta hoje", free.accountCents)
                    HorizontalDivider(color = FinaiColors.BorderFaint)
                    val netLabel = if (free.movements.any { it.cents > 0 && !it.date.isAfter(free.lowest.date) }) "− Saídas menos entradas até ${dm(free.lowest.date)}" else "− Vai sair até ${dm(free.windowEnd)}"
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.semantics { stateDescription = if (expanded) "expandido" else "recolhido" }) {
                        EquationLine("$netLabel · ${if (expanded) "ocultar" else "ver ${free.movements.size}"}", free.goingOutCents)
                    }
                    if (expanded) {
                        free.movements.forEach { HomeMoneyRow(it) }
                        Text("O valor descontado é a queda líquida até o menor saldo; inclui entradas previstas nesse caminho. Movimentos posteriores ao menor saldo não reduzem o livre novamente.", color = FinaiColors.TextMuted, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = FinaiColors.BorderFaint)
                    EquationLine("= Menor saldo (${dm(free.lowest.date)})", free.lowest.cents, accent = true)
                    HorizontalDivider(color = FinaiColors.BorderFaint)
                    EquationLine("− Margem de segurança", free.marginCents)
                    HorizontalDivider(color = FinaiColors.BorderFaint)
                    EquationLine(if (free.shortfall != null) "= Falta prevista" else "= Livre", free.shortfall?.cents ?: free.freeCents, total = true, warning = free.shortfall != null)
                    if (free.shortfall != null || free.insideMargin) Text(
                        if (free.shortfall != null) "O saldo fica negativo em ${dm(free.shortfall.date)}. A falta é o valor para cobrir o saldo, sem somar a margem."
                        else "O menor saldo está dentro da margem. O livre fica em zero; a diferença para a margem não é chamada de falta.",
                        color = FinaiColors.TextMuted, fontSize = 12.sp,
                    )
                }
            } else Text("Sem renda recorrente, a previsão ainda usa o mês inteiro. Você pode conferir o saldo e definir a margem que será usada quando cadastrar a renda.", color = FinaiColors.TextSecondary)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Margem de segurança", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text("Um valor que o app nunca chama de livre, para imprevistos e gastos que você esquece de lançar. Começa em R$ 300 fixos; você escolhe e o app não muda sozinho.", fontSize = 13.sp, color = FinaiColors.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L, 15_000L, 30_000L, 50_000L).forEach { value ->
                        val selected = marginCents == value
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) FinaiColors.InkStrong else FinaiColors.SurfaceMuted).clickable(role = Role.RadioButton) { onMargin(value); custom = false }.heightIn(min = 48.dp).padding(horizontal = 14.dp).semantics { this.selected = selected }, contentAlignment = Alignment.Center) {
                            Text(if (value == 0L) "Sem" else money(value), fontSize = 13.sp, color = if (selected) FinaiColors.OnInkStrong else FinaiColors.TextPrimary)
                        }
                    }
                    V2Button("Personalizado", { custom = !custom })
                }
                if (custom) {
                    OutlinedTextField(customAmount, { customAmount = Reconciliation.mask(it).removePrefix("-") }, label = { Text("Margem personalizada em reais") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    V2Button("Aplicar margem", { Reconciliation.parse(customAmount)?.let(onMargin) }, enabled = Reconciliation.parse(customAmount) != null)
                }
                if (marginCents !in listOf(0L, 15_000L, 30_000L, 50_000L)) Text("Margem atual: ${money(marginCents)}", color = FinaiColors.TextSecondary)
            }
            BankReconciliation(accountCents, accounts, reconciliation, today, onReconcile)
            Text(
                if (free == null) "A sobra não some: o saldo continua entre meses e salários."
                else "A sobra não some. Em ${dm(free.nextIncome.date)} entram ${money(free.nextIncome.cents)}${if (free.nextIncome.estimated) " (estimativa)" else ""}. O livre passa a ser calculado até a próxima renda, a partir do que estiver na conta.",
                color = FinaiColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
    }
}

@Composable
private fun EquationLine(label: String, cents: Long, accent: Boolean = false, total: Boolean = false, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val color = when { warning -> FinaiColors.RoseDark; total -> FinaiColors.EmeraldDark; accent -> FinaiColors.AmberDark; else -> FinaiColors.TextPrimary }
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(money(cents), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DailyBalanceChart(f: FreeToSpend) {
    val wideLabels = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    // Inclui a próxima entrada como referência, fora da janela usada para calcular o livre.
    val bars = f.series + DayBalance(f.nextIncome.date, f.endCents + f.nextIncome.cents)
    val max = bars.maxOf { kotlin.math.abs(it.cents) }.coerceAtLeast(1)
    val description = "Saldo previsto: " + bars.joinToString { "${dm(it.date)}: ${money(it.cents)}" } + ". Menor saldo ${money(f.lowest.cents)} em ${dm(f.lowest.date)}."
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = description }.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            bars.forEach { day ->
                val color = when { day.cents < 0 -> FinaiColors.RoseDark; day.date == f.nextIncome.date -> FinaiColors.Emerald; day.date == f.lowest.date -> FinaiColors.Amber; day.date == f.today -> FinaiColors.TextPrimary; else -> FinaiColors.TextDisabled }
                Column(Modifier.width(if (wideLabels) 28.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(120.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(20.dp).height((kotlin.math.abs(day.cents).toDouble() / max * 112).toFloat().coerceAtLeast(4f).dp).clip(RoundedCornerShape(5.dp)).background(color))
                    }
                    Text(day.date.dayOfMonth.toString(), color = FinaiColors.TextMuted, fontSize = 12.sp)
                }
            }
        }
        Text("■ Menor saldo: ${money(f.lowest.cents)} em ${dm(f.lowest.date)}", color = FinaiColors.AmberDark, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("■ ${f.nextIncome.noun.replaceFirstChar { it.titlecase() }} em ${dm(f.nextIncome.date)}", color = FinaiColors.EmeraldDark, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankReconciliation(registeredCents: Long, accounts: List<String>, last: ReconciliationState?, today: LocalDate, onConfirm: suspend (String, Long) -> Unit) {
    var bank by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull() ?: "Carteira") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cents = Reconciliation.parse(bank)
    val diff = Reconciliation.difference(cents, registeredCents)
    V2Panel {
        Text("Conferir com o banco", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Text("${reconciliationLabel(last, today).replaceFirstChar { it.titlecase() }}. Se o banco mostrar outro valor, a diferença vira um ajuste de saldo, fora dos gastos.", fontSize = 13.sp, color = FinaiColors.TextMuted, modifier = Modifier.padding(vertical = 10.dp))
        if (accounts.size > 1) Text("Compare a soma dos saldos das suas contas. A conta abaixo recebe o ajuste; o saldo do FinAI é geral.", color = FinaiColors.TextMuted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            accounts.ifEmpty { listOf("Carteira") }.forEach { name -> FilterChip(selected = name == account, onClick = { account = name }, enabled = !saving, label = { Text(name) }) }
        }
        OutlinedTextField(bank, {
            val masked = Reconciliation.mask(it)
            // Zero digitado é um saldo válido; vazio continua sendo ausência de valor.
            bank = if (masked.isBlank() && it.any(Char::isDigit)) "0,00" else masked
            message = null
        }, label = { Text("Saldo no banco agora") }, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(if (diff == null) "Digite o saldo do banco." else "Diferença: ${money(diff)}. ${if (diff == 0L) "Apenas a data será registrada." else "O ajuste será lançado ao conferir."}", color = FinaiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        V2Button(if (saving) "Salvando…" else "Conferir", {
            if (cents != null && !saving) {
                saving = true
                scope.launch {
                    try { onConfirm(account, cents); bank = ""; message = "Saldo conferido." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = "Não foi possível conferir. Tente novamente." }
                    finally { saving = false }
                }
            }
        }, primary = true, enabled = cents != null && !saving)
        message?.let { Text(it, color = FinaiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}
