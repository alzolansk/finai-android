package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.HomeSituation
import com.finai.app.domain.InitialSetup
import com.finai.app.domain.TransactionEntry
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class SetupStep { Income, Bills, Result }

private data class DraftBill(val nome: String, val cents: Long, val date: LocalDate, val categoria: String)

/**
 * Assistente inicial mínimo (Fase 7, item 6): pede só a **renda principal e a data do
 * próximo recebimento** e, opcional, **as contas desta semana**, e termina mostrando o
 * resultado real — "até o salário você tem R$ X, R$ Y por dia". Dívidas, metas e gastos
 * fixos deixaram de ser passos obrigatórios: viram convites no estado vazio de cada tela.
 *
 * Nada é inventado (planning.md §4): campo vazio não gera registro. Grava só ao tocar em
 * "Ver meu resultado"; "Pular", sempre visível, encerra sem gravar nada.
 */
@Composable
fun InitialSetupWizard(
    situation: HomeSituation?,
    onSave: suspend (List<TransacaoEntity>) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    var step by rememberSaveable { mutableStateOf(SetupStep.Income) }
    var descricao by rememberSaveable { mutableStateOf("Salário") }
    var valor by rememberSaveable { mutableStateOf("") }
    var nextPayString by rememberSaveable { mutableStateOf(InitialSetup.suggestedNextPay(today).toString()) }
    val bills = remember { mutableStateListOf<DraftBill>() }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val nextPay = LocalDate.parse(nextPayString)
    val incomeCents = InitialSetup.parseAmountCents(valor)

    BackHandler(enabled = step == SetupStep.Bills) { step = SetupStep.Income }

    fun saveAndShow() {
        saving = true; error = null
        val entries = buildList {
            incomeCents?.let { add(InitialSetup.mainIncome(descricao, it, nextPay, today)) }
            bills.forEach { add(InitialSetup.bill(it.nome, it.cents, it.date, it.categoria, recorrente = true)) }
        }
        scope.launch {
            try {
                onSave(entries)
                step = SetupStep.Result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Não foi possível salvar. Tente de novo."
            } finally {
                saving = false
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = FinaiColors.Background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            WizardHeader(step, onSkip = onFinish)
            Box(Modifier.weight(1f)) {
                when (step) {
                    SetupStep.Income -> StepScaffold(
                        title = "Quanto você recebe, e quando cai o próximo?",
                        subtitle = "Com isso o app calcula quanto está livre até o próximo pagamento. Não pedimos saldo de banco.",
                        primaryLabel = if (incomeCents != null) "Continuar" else "Pular esta etapa",
                        primaryEnabled = valor.isBlank() || incomeCents != null,
                        onPrimary = { step = SetupStep.Bills },
                    ) {
                        OutlinedTextField(
                            valor, { valor = it }, label = { Text("Valor que cai por mês (R$)") }, singleLine = true,
                            isError = valor.isNotBlank() && incomeCents == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            descricao, { descricao = it }, label = { Text("Nome (ex.: Salário, Pró-labore)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DateField("Próximo recebimento", nextPay, today) { nextPayString = it.toString() }
                        Text(
                            "Ela entra como sua renda principal, repetindo todo mês. Dá para mudar depois na Agenda.",
                            fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary,
                        )
                    }
                    SetupStep.Bills -> StepScaffold(
                        title = "Alguma conta vence nesta semana?",
                        subtitle = "Opcional. Aluguel, luz, cartão: o que sai até ${dm(today.plusDays(6))}. O resto você lança depois, pelo +.",
                        primaryLabel = if (saving) "Salvando…" else "Ver meu resultado",
                        primaryEnabled = !saving,
                        onPrimary = ::saveAndShow,
                        onBack = { step = SetupStep.Income },
                    ) {
                        bills.forEachIndexed { index, bill ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(FinaiColors.Surface)
                                    .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(14.dp))
                                    .padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(bill.nome, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                                    Text("${dm(bill.date)} · ${bill.categoria}", fontSize = 13.sp, color = FinaiColors.TextTertiary)
                                }
                                Text(formatBrl0(bill.cents / 100.0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                                TextAction("Remover", { bills.removeAt(index) }, color = FinaiColors.RoseDark)
                            }
                        }
                        NewBillForm(today) { bills.add(it) }
                        error?.let { Text(it, color = FinaiColors.RoseDark, fontSize = 14.sp) }
                    }
                    SetupStep.Result -> ResultStep(situation, incomeCents != null, onFinish)
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(step: SetupStep, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (step) { SetupStep.Income -> "1 de 2"; SetupStep.Bills -> "2 de 2"; SetupStep.Result -> "Pronto" },
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary, modifier = Modifier.weight(1f),
        )
        // 48 dp e fora de qualquer área rolável: no emulador o antigo "Pular tudo" (TextButton de
        // 40 dp dentro de um Row com padding) não respondia na primeira tentativa.
        if (step != SetupStep.Result) TextAction("Pular", onSkip, color = FinaiColors.TextSecondary)
    }
}

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text(subtitle, fontSize = 15.sp, lineHeight = 21.sp, color = FinaiColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) TextAction("Voltar", onBack, color = FinaiColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            Button(
                enabled = primaryEnabled,
                onClick = onPrimary,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
            ) { Text(primaryLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, FinaiColors.TextMuted, RoundedCornerShape(6.dp))
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 12.sp, color = FinaiColors.TextTertiary)
        Text(
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + if (date == today) " · hoje" else "",
            fontSize = 16.sp, color = FinaiColors.TextPrimary,
        )
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun NewBillForm(today: LocalDate, onAdd: (DraftBill) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var dayOffset by remember { mutableStateOf(0) }
    var categoria by remember { mutableStateOf("Moradia") }
    val cents = InitialSetup.parseAmountCents(valor)
    val days = InitialSetup.weekDays(today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(nome, { nome = it }, label = { Text("Conta (ex.: Aluguel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            valor, { valor = it }, label = { Text("Valor (R$)") }, singleLine = true,
            isError = valor.isNotBlank() && cents == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Vence em", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary)
        ChoiceRow(days.map { if (it == today) "Hoje" else dm(it) }, dayOffset) { dayOffset = it }
        Text("Categoria", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextSecondary)
        ChoiceRow(TransactionEntry.categories, TransactionEntry.categories.indexOf(categoria)) { categoria = TransactionEntry.categories[it] }
        ActionButton(
            "Adicionar conta",
            onClick = {
                if (nome.isNotBlank() && cents != null) {
                    onAdd(DraftBill(nome, cents, days[dayOffset], categoria))
                    nome = ""; valor = ""
                }
            },
        )
    }
}

/** Opções em linhas que quebram, com alvo de 48 dp cada. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) FinaiColors.Ink else FinaiColors.Surface)
                    .border(1.dp, if (on) FinaiColors.Ink else FinaiColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else FinaiColors.TextPrimary)
            }
        }
    }
}

/** O resultado real, lido do banco depois de gravar — o mesmo número do topo da Início. */
@Composable
private fun ResultStep(situation: HomeSituation?, informedIncome: Boolean, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (situation == null || !informedIncome) {
            Text("Tudo pronto", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Text(
                "Sem a renda principal, o app calcula só até o fim do mês. Quando quiser, use \"Informar renda\" na Início.",
                fontSize = 15.sp, lineHeight = 21.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Text(situation.headline, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
            Text(
                situation.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                (if (situation.shortfall) "Faltam " else "") + formatBrl0(situation.mainCents / 100.0),
                fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                color = if (situation.shortfall) FinaiColors.RoseDark else FinaiColors.EmeraldDark,
            )
            if (!situation.shortfall) {
                Text(
                    "Até o próximo pagamento você tem ${formatBrl0(situation.mainCents / 100.0)}: cerca de " +
                        "${formatBrl0(situation.perDayCents / 100.0)} por dia, por ${situation.days} dia(s).",
                    fontSize = 15.sp, lineHeight = 21.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                "Conta feita com o que você informou: o que já saiu desde o último pagamento o app ainda não sabe. " +
                    "Lance seus gastos pelo + e o número acompanha.",
                fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 14.dp),
            )
        }
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp).heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
        ) { Text("Começar a usar", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
