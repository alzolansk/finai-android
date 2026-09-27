package com.finai.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.domain.toEpochMillis
import com.finai.app.ui.theme.FinaiColors
import com.finai.app.util.formatBrl0
import java.time.LocalDate

private enum class SetupStep(val label: String) {
    Welcome("Bem-vindo"),
    Income("Renda"),
    Accounts("Contas e cartões"),
    RecurringBills("Gastos fixos"),
    Debts("Dívidas"),
    Goals("Objetivos"),
    Done("Pronto"),
}

/**
 * Assistente opcional de configuração inicial, mostrado uma única vez logo
 * depois do [OnboardingTour] (nunca antes dele terminar/ser pulado — ver
 * [com.finai.app.FinaiApp]). Junta os dados mínimos que o app precisa para
 * "Pode gastar hoje", capacidade de poupança e a Agenda já nascerem com
 * números reais, sem inventar nada: cada campo vazio simplesmente não gera
 * registro nenhum (planning.md §4 — nada de dado fictício automático).
 *
 * Cada etapa persiste no Room assim que o usuário toca em "Continuar" —
 * fechar o app no meio do assistente mantém o que já foi confirmado até ali
 * (o cenário de "onboarding parcialmente preenchido"). "Pular tudo", sempre
 * visível no topo, encerra o assistente imediatamente sem descartar o que já
 * foi salvo em etapas anteriores. Nenhum dado aqui é obrigatório: tudo pode
 * ser criado, editado ou apagado depois nas telas de Agenda/Objetivos/
 * Dívidas/Limites, exatamente como uma entrada manual comum.
 */
@Composable
fun InitialSetupWizard(
    onAddConta: (ContaEntity) -> Unit,
    onAddDivida: (DividaEntity) -> Unit,
    onAddObjetivo: (ObjetivoEntity) -> Unit,
    onAddAccounts: (Set<String>) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.Welcome) }
    var incomeAdded by rememberSaveable { mutableStateOf(false) }
    var accountsAdded by rememberSaveable { mutableStateOf(0) }
    var billsAdded by rememberSaveable { mutableStateOf(0) }
    var debtsAdded by rememberSaveable { mutableStateOf(0) }
    var goalsAdded by rememberSaveable { mutableStateOf(0) }

    fun goTo(next: SetupStep) { step = next }

    // Voltar do sistema = o "Voltar" da própria etapa. Na tela de boas-vindas
    // segue o padrão do Android.
    BackHandler(enabled = step != SetupStep.Welcome) { step = SetupStep.entries[step.ordinal - 1] }

    Surface(modifier = modifier.fillMaxSize(), color = FinaiColors.Background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            WizardHeader(step = step, onSkipAll = onFinish)
            Box(Modifier.weight(1f)) {
                when (step) {
                    SetupStep.Welcome -> WelcomeStep(
                        onStart = { goTo(SetupStep.Income) },
                        onSkipAll = onFinish,
                    )
                    SetupStep.Income -> IncomeStep(
                        onContinue = { conta ->
                            conta?.let { onAddConta(it); incomeAdded = true }
                            goTo(SetupStep.Accounts)
                        },
                        onBack = { goTo(SetupStep.Welcome) },
                    )
                    SetupStep.Accounts -> AccountsStep(
                        onContinue = { names ->
                            if (names.isNotEmpty()) { onAddAccounts(names); accountsAdded = names.size }
                            goTo(SetupStep.RecurringBills)
                        },
                        onBack = { goTo(SetupStep.Income) },
                    )
                    SetupStep.RecurringBills -> RecurringBillsStep(
                        onContinue = { contas ->
                            contas.forEach(onAddConta)
                            billsAdded = contas.size
                            goTo(SetupStep.Debts)
                        },
                        onBack = { goTo(SetupStep.Accounts) },
                    )
                    SetupStep.Debts -> DebtsStep(
                        onContinue = { dividas ->
                            dividas.forEach(onAddDivida)
                            debtsAdded = dividas.size
                            goTo(SetupStep.Goals)
                        },
                        onBack = { goTo(SetupStep.RecurringBills) },
                    )
                    SetupStep.Goals -> GoalsStep(
                        onContinue = { objetivos ->
                            objetivos.forEach(onAddObjetivo)
                            goalsAdded = objetivos.size
                            goTo(SetupStep.Done)
                        },
                        onBack = { goTo(SetupStep.Debts) },
                    )
                    SetupStep.Done -> DoneStep(
                        incomeAdded = incomeAdded,
                        accountsAdded = accountsAdded,
                        billsAdded = billsAdded,
                        debtsAdded = debtsAdded,
                        goalsAdded = goalsAdded,
                        onFinish = onFinish,
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(step: SetupStep, onSkipAll: () -> Unit) {
    val steps = SetupStep.entries.filter { it != SetupStep.Done }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Configuração inicial", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary)
            if (step != SetupStep.Done) {
                TextButton(onClick = onSkipAll) {
                    Text("Pular tudo", color = FinaiColors.TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
        if (step != SetupStep.Done) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                steps.forEach { s ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (s.ordinal <= step.ordinal) FinaiColors.Emerald else FinaiColors.BorderSubtle),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    continueEnabled: Boolean = true,
    continueLabel: String = "Continuar",
    onContinue: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, lineHeight = 19.sp, color = FinaiColors.TextSecondary)
            Spacer(Modifier.height(18.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("Voltar", color = FinaiColors.TextTertiary, fontWeight = FontWeight.SemiBold) }
            } else {
                Spacer(Modifier.height(1.dp))
            }
            Button(
                enabled = continueEnabled,
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
            ) { Text(continueLabel) }
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit, onSkipAll: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Vamos configurar seus dados?", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Em menos de 2 minutos você pode cadastrar sua renda, contas, gastos fixos, dívidas e objetivos — " +
                "tudo o que o FinAI usa para calcular quanto você pode gastar hoje. Cada etapa é opcional: pule " +
                "qualquer uma delas, ou o assistente inteiro, e cadastre tudo depois pelas telas normais do app.",
            fontSize = 14.sp, lineHeight = 21.sp, color = FinaiColors.TextSecondary,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
        ) { Text("Começar", modifier = Modifier.padding(vertical = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSkipAll, modifier = Modifier.fillMaxWidth()) {
            Text("Prefiro começar com o app vazio")
        }
    }
}

// ── Renda ────────────────────────────────────────────────────────
@Composable
private fun IncomeStep(onContinue: (ContaEntity?) -> Unit, onBack: () -> Unit) {
    var nome by remember { mutableStateOf("Renda mensal") }
    var valor by remember { mutableStateOf("") }
    var dataTexto by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    val valorInformado = valor.isNotBlank()
    val valorOk = !valorInformado || (parseAmountOrNull(valor) ?: 0.0) > 0
    val dataOk = parseDateOrNull(dataTexto) != null

    StepScaffold(
        title = "Qual é a sua renda mensal?",
        subtitle = "Salário, pró-labore ou outra entrada fixa que você recebe todo mês. Deixe em branco se preferir não informar agora.",
        onBack = onBack,
        continueEnabled = !valorInformado || (valorOk && dataOk),
        continueLabel = if (valorInformado) "Salvar e continuar" else "Pular esta etapa",
        onContinue = {
            if (valorInformado && valorOk && dataOk) {
                onContinue(
                    ContaEntity(
                        nome = nome.ifBlank { "Renda mensal" },
                        valorCentavos = Math.round(parseAmountOrNull(valor)!! * 100),
                        vencimento = parseDateOrNull(dataTexto)!!.toEpochMillis(),
                        status = "pendente",
                        tipo = "a_receber",
                        recorrente = true,
                    ),
                )
            } else {
                onContinue(null)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nome, { nome = it }, label = { Text("Nome (ex.: Salário)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                valor, { valor = it }, label = { Text("Valor mensal (R$)") }, singleLine = true,
                isError = valorInformado && !valorOk, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                dataTexto, { dataTexto = it }, label = { Text("Próximo recebimento (dd/mm/aaaa)") }, singleLine = true,
                isError = !dataOk, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Contas e cartões ────────────────────────────────────────────
@Composable
private fun AccountsStep(onContinue: (Set<String>) -> Unit, onBack: () -> Unit) {
    val names = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }

    StepScaffold(
        title = "Quais contas e cartões você usa?",
        subtitle = "Ex.: Carteira, Nubank, Itaú, Cartão XP. Isso só define as opções do seletor de conta ao lançar um gasto — nenhum saldo ou número de conta é armazenado.",
        onBack = onBack,
        continueLabel = if (names.isEmpty()) "Pular esta etapa" else "Salvar e continuar",
        onContinue = { onContinue(names.toSet()) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                input, { input = it }, label = { Text("Nome da conta/cartão") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = input.isNotBlank(),
                onClick = { names.add(input.trim()); input = "" },
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
            ) { Text("Adicionar") }
        }
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name ->
                    FilterChip(
                        selected = true,
                        onClick = { names.remove(name) },
                        label = { Text(name) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remover $name", modifier = Modifier.padding(2.dp)) },
                    )
                }
            }
        }
    }
}

// ── Gastos fixos e contas recorrentes ──────────────────────────
private data class DraftConta(val nome: String, val valor: Double, val vencimento: LocalDate)

@Composable
private fun RecurringBillsStep(onContinue: (List<ContaEntity>) -> Unit, onBack: () -> Unit) {
    val drafts = remember { mutableStateListOf<DraftConta>() }
    var nome by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var dataTexto by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    val valorOk = parseAmountOrNull(valor) != null
    val dataOk = parseDateOrNull(dataTexto) != null
    val podeAdicionar = nome.isNotBlank() && valorOk && dataOk

    StepScaffold(
        title = "Gastos fixos e contas recorrentes",
        subtitle = "Aluguel, condomínio, assinaturas, internet, cartão de crédito — tudo que se repete todo mês. " +
            "Cada um entra como uma conta recorrente na Agenda, com vencimento no dia que você informar.",
        onBack = onBack,
        continueLabel = if (drafts.isEmpty()) "Pular esta etapa" else "Salvar e continuar (${drafts.size})",
        onContinue = {
            onContinue(
                drafts.map {
                    ContaEntity(
                        nome = it.nome, valorCentavos = Math.round(it.valor * 100),
                        vencimento = it.vencimento.toEpochMillis(), status = "pendente",
                        tipo = "a_pagar", recorrente = true,
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nome, { nome = it }, label = { Text("Nome (ex.: Aluguel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(valor, { valor = it }, label = { Text("Valor (R$)") }, singleLine = true, isError = valor.isNotEmpty() && !valorOk, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dataTexto, { dataTexto = it }, label = { Text("Vencimento (dd/mm/aaaa)") }, singleLine = true, isError = !dataOk, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                enabled = podeAdicionar,
                onClick = {
                    drafts.add(DraftConta(nome.trim(), parseAmountOrNull(valor)!!, parseDateOrNull(dataTexto)!!))
                    nome = ""; valor = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Adicionar à lista") }
        }
        DraftList(drafts.map { "${it.nome} — ${formatBrlLabel(it.valor)}" }) { index -> drafts.removeAt(index) }
    }
}

// ── Dívidas ─────────────────────────────────────────────────────
// Sem campo de "valor original" separado — o formulário completo de
// DebtEntryScreen (tela Dívidas) já cobre esse caso para quem quiser refinar depois; aqui o
// objetivo é a dívida entrar rápido no cálculo de juros/ordem de quitação, o
// que só depende do valor em aberto hoje e da taxa. Ambos os campos de
// DividaEntity recebem o mesmo valor.
private data class DraftDivida(
    val nome: String, val valorAberto: Double,
    val taxa: Double, val parcelasRestantes: Int, val valorParcela: Double,
)

@Composable
private fun DebtsStep(onContinue: (List<DividaEntity>) -> Unit, onBack: () -> Unit) {
    val drafts = remember { mutableStateListOf<DraftDivida>() }
    var nome by remember { mutableStateOf("") }
    var valorAberto by remember { mutableStateOf("") }
    var taxa by remember { mutableStateOf("") }
    var parcelasRestantes by remember { mutableStateOf("0") }
    var valorParcela by remember { mutableStateOf("0") }
    val abertoOk = parseAmountOrNull(valorAberto) != null
    val taxaOk = parseAmountOrNull(taxa) != null
    val parcelasOk = parcelasRestantes.toIntOrNull() != null
    val parcelaOk = parseAmountOrNull(valorParcela) != null
    val podeAdicionar = nome.isNotBlank() && abertoOk && taxaOk && parcelasOk && parcelaOk

    StepScaffold(
        title = "Alguma dívida em aberto?",
        subtitle = "Cartão parcelado, empréstimo, financiamento. O FinAI usa a taxa de juros para sugerir a ordem de quitação.",
        onBack = onBack,
        continueLabel = if (drafts.isEmpty()) "Pular esta etapa" else "Salvar e continuar (${drafts.size})",
        onContinue = {
            onContinue(
                drafts.map {
                    DividaEntity(
                        nome = it.nome,
                        valorOriginalCentavos = Math.round(it.valorAberto * 100),
                        valorAbertoCentavos = Math.round(it.valorAberto * 100),
                        taxaJurosMensalBasisPoints = Math.round(it.taxa * 100).toInt(),
                        parcelasRestantes = it.parcelasRestantes,
                        valorParcelaCentavos = Math.round(it.valorParcela * 100),
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nome, { nome = it }, label = { Text("Nome (ex.: Cartão Nubank)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(valorAberto, { valorAberto = it }, label = { Text("Valor em aberto hoje (R$)") }, singleLine = true, isError = valorAberto.isNotEmpty() && !abertoOk, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(taxa, { taxa = it }, label = { Text("Taxa de juros mensal (% a.m.)") }, singleLine = true, isError = taxa.isNotEmpty() && !taxaOk, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(parcelasRestantes, { parcelasRestantes = it }, label = { Text("Parcelas restantes (0 se não houver)") }, singleLine = true, isError = !parcelasOk, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(valorParcela, { valorParcela = it }, label = { Text("Valor da parcela (R$, 0 se não houver)") }, singleLine = true, isError = !parcelaOk, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                enabled = podeAdicionar,
                onClick = {
                    drafts.add(
                        DraftDivida(
                            nome.trim(), parseAmountOrNull(valorAberto)!!,
                            parseAmountOrNull(taxa)!!, parcelasRestantes.toInt(), parseAmountOrNull(valorParcela)!!,
                        ),
                    )
                    nome = ""; valorAberto = ""; taxa = ""; parcelasRestantes = "0"; valorParcela = "0"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Adicionar à lista") }
        }
        DraftList(drafts.map { "${it.nome} — ${formatBrlLabel(it.valorAberto)} em aberto" }) { index -> drafts.removeAt(index) }
    }
}

// ── Objetivos ───────────────────────────────────────────────────
private data class DraftObjetivo(
    val nome: String, val tipo: String, val valorAlvo: Double,
    val valorGuardado: Double, val prazo: LocalDate, val prioridade: Int,
)

@Composable
private fun GoalsStep(onContinue: (List<ObjetivoEntity>) -> Unit, onBack: () -> Unit) {
    val drafts = remember { mutableStateListOf<DraftObjetivo>() }
    var nome by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Compra") }
    var valorAlvo by remember { mutableStateOf("") }
    var valorGuardado by remember { mutableStateOf("0") }
    var prazoTexto by remember { mutableStateOf(LocalDate.now().plusMonths(6).format(dateFormatter)) }
    val alvoOk = parseAmountOrNull(valorAlvo) != null
    val guardadoOk = parseAmountOrNull(valorGuardado) != null
    val prazoOk = parseDateOrNull(prazoTexto) != null
    val podeAdicionar = nome.isNotBlank() && alvoOk && guardadoOk && prazoOk

    StepScaffold(
        title = "Algum objetivo em mente?",
        subtitle = "Uma viagem, uma compra ou uma reserva de emergência. Você pode ajustar o valor guardado a qualquer momento na tela de Objetivos.",
        onBack = onBack,
        continueLabel = if (drafts.isEmpty()) "Pular esta etapa" else "Salvar e continuar (${drafts.size})",
        onContinue = {
            onContinue(
                drafts.map {
                    ObjetivoEntity(
                        tipo = it.tipo, nome = it.nome,
                        valorAlvoCentavos = Math.round(it.valorAlvo * 100),
                        valorGuardadoCentavos = Math.round(it.valorGuardado * 100),
                        prazo = it.prazo.toEpochMillis(), prioridade = it.prioridade,
                    )
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(nome, { nome = it }, label = { Text("Nome (ex.: Viagem para o Nordeste)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Viagem", "Compra", "Reserva").forEach { t ->
                    FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t) })
                }
            }
            OutlinedTextField(valorAlvo, { valorAlvo = it }, label = { Text("Valor alvo (R$)") }, singleLine = true, isError = valorAlvo.isNotEmpty() && !alvoOk, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(valorGuardado, { valorGuardado = it }, label = { Text("Já guardado (R$)") }, singleLine = true, isError = !guardadoOk, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(prazoTexto, { prazoTexto = it }, label = { Text("Prazo (dd/mm/aaaa)") }, singleLine = true, isError = !prazoOk, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                enabled = podeAdicionar,
                onClick = {
                    drafts.add(DraftObjetivo(nome.trim(), tipo, parseAmountOrNull(valorAlvo)!!, parseAmountOrNull(valorGuardado)!!, parseDateOrNull(prazoTexto)!!, drafts.size + 1))
                    nome = ""; valorAlvo = ""; valorGuardado = "0"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Adicionar à lista") }
        }
        DraftList(drafts.map { "${it.nome} — ${formatBrlLabel(it.valorAlvo)}" }) { index -> drafts.removeAt(index) }
    }
}

@Composable
private fun DraftList(items: List<String>, onRemove: (Int) -> Unit) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 13.sp, color = FinaiColors.TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remover", tint = FinaiColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun DoneStep(
    incomeAdded: Boolean,
    accountsAdded: Int,
    billsAdded: Int,
    debtsAdded: Int,
    goalsAdded: Int,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Tudo pronto!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Você já pode cadastrar mais coisas ou editar o que acabou de cadastrar a qualquer momento, pelas telas do app.",
            fontSize = 14.sp, lineHeight = 20.sp, color = FinaiColors.TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FinaiColors.Surface)
                .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SummaryLine("Renda mensal", if (incomeAdded) "cadastrada" else "não cadastrada")
            SummaryLine("Contas e cartões", if (accountsAdded > 0) "$accountsAdded cadastrado(s)" else "nenhuma")
            SummaryLine("Gastos fixos e contas recorrentes", if (billsAdded > 0) "$billsAdded cadastrado(s)" else "nenhum")
            SummaryLine("Dívidas", if (debtsAdded > 0) "$debtsAdded cadastrada(s)" else "nenhuma")
            SummaryLine("Objetivos", if (goalsAdded > 0) "$goalsAdded cadastrado(s)" else "nenhum")
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
        ) { Text("Ir para o Início", modifier = Modifier.padding(vertical = 4.dp)) }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = FinaiColors.TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
    }
}

private fun formatBrlLabel(valor: Double): String = formatBrl0(valor)
