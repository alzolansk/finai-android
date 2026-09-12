package com.finai.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finai.app.data.model.Categorias
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Plain Material3 [AlertDialog] forms for the manual-entry flows Fase 1
 * requires (planning.md §9) — lançamentos, contas, objetivos, dívidas.
 * Deliberately unstyled against the rest of the app's bespoke bottom-sheet
 * look; the goal here is correct, real CRUD, not a pixel-perfect form.
 */

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text, dateFormatter)
} catch (e: DateTimeParseException) {
    null
}

private fun parseAmountOrNull(text: String): Double? = text.replace(",", ".").toDoubleOrNull()

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (descricao: String, valor: Double, categoria: String, contaOrigem: String, data: LocalDate, recorrente: Boolean) -> Unit,
) {
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf(Categorias.all.first()) }
    var contaOrigem by remember { mutableStateOf("") }
    var dataTexto by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    var recorrente by remember { mutableStateOf(false) }
    val valorOk = parseAmountOrNull(valor) != null
    val dataOk = parseDateOrNull(dataTexto) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lançar gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(valor, { valor = it }, label = { Text("Valor (R$)") }, singleLine = true, isError = valor.isNotEmpty() && !valorOk)
                OutlinedTextField(contaOrigem, { contaOrigem = it }, label = { Text("Conta/cartão de origem") }, singleLine = true)
                OutlinedTextField(dataTexto, { dataTexto = it }, label = { Text("Data (dd/mm/aaaa)") }, singleLine = true, isError = !dataOk)
                Text("Categoria")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Categorias.all) { cat ->
                        FilterChip(selected = cat == categoria, onClick = { categoria = cat }, label = { Text(cat) })
                    }
                }
                Row {
                    Checkbox(checked = recorrente, onCheckedChange = { recorrente = it })
                    Text("Recorrente", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = descricao.isNotBlank() && valorOk && dataOk,
                onClick = {
                    onConfirm(descricao.trim(), parseAmountOrNull(valor)!!, categoria, contaOrigem.trim(), parseDateOrNull(dataTexto)!!, recorrente)
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (nome: String, tipo: String, valorAlvo: Double, valorGuardado: Double, prazo: LocalDate, prioridade: Int) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Compra") }
    var valorAlvo by remember { mutableStateOf("") }
    var valorGuardado by remember { mutableStateOf("0") }
    var prazoTexto by remember { mutableStateOf(LocalDate.now().plusMonths(6).format(dateFormatter)) }
    var prioridade by remember { mutableStateOf("2") }
    val alvoOk = parseAmountOrNull(valorAlvo) != null
    val guardadoOk = parseAmountOrNull(valorGuardado) != null
    val prazoOk = parseDateOrNull(prazoTexto) != null
    val prioridadeOk = prioridade.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo objetivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Viagem", "Compra", "Reserva").forEach { t ->
                        FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t) })
                    }
                }
                OutlinedTextField(valorAlvo, { valorAlvo = it }, label = { Text("Valor alvo (R$)") }, singleLine = true, isError = valorAlvo.isNotEmpty() && !alvoOk)
                OutlinedTextField(valorGuardado, { valorGuardado = it }, label = { Text("Já guardado (R$)") }, singleLine = true, isError = !guardadoOk)
                OutlinedTextField(prazoTexto, { prazoTexto = it }, label = { Text("Prazo (dd/mm/aaaa)") }, singleLine = true, isError = !prazoOk)
                OutlinedTextField(prioridade, { prioridade = it }, label = { Text("Prioridade (1 = mais alta)") }, singleLine = true, isError = !prioridadeOk)
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank() && alvoOk && guardadoOk && prazoOk && prioridadeOk,
                onClick = {
                    onConfirm(nome.trim(), tipo, parseAmountOrNull(valorAlvo)!!, parseAmountOrNull(valorGuardado)!!, parseDateOrNull(prazoTexto)!!, prioridade.toInt())
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ContributionDialog(goalName: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var valor by remember { mutableStateOf("") }
    val ok = parseAmountOrNull(valor) != null && (parseAmountOrNull(valor) ?: 0.0) > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar aporte") },
        text = {
            Column {
                Text("Quanto você guardou para \"$goalName\"?")
                OutlinedTextField(valor, { valor = it }, label = { Text("Valor (R$)") }, singleLine = true, isError = valor.isNotEmpty() && !ok)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(parseAmountOrNull(valor)!!) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun AddContaDialog(
    onDismiss: () -> Unit,
    onConfirm: (nome: String, valor: Double, vencimento: LocalDate, tipo: String, recorrente: Boolean) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var vencimentoTexto by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    var tipo by remember { mutableStateOf("a_pagar") }
    var recorrente by remember { mutableStateOf(false) }
    val valorOk = parseAmountOrNull(valor) != null
    val dataOk = parseDateOrNull(vencimentoTexto) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova conta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(valor, { valor = it }, label = { Text("Valor (R$)") }, singleLine = true, isError = valor.isNotEmpty() && !valorOk)
                OutlinedTextField(vencimentoTexto, { vencimentoTexto = it }, label = { Text("Vencimento (dd/mm/aaaa)") }, singleLine = true, isError = !dataOk)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tipo == "a_pagar", onClick = { tipo = "a_pagar" }, label = { Text("A pagar") })
                    FilterChip(selected = tipo == "a_receber", onClick = { tipo = "a_receber" }, label = { Text("A receber") })
                }
                Row {
                    Checkbox(checked = recorrente, onCheckedChange = { recorrente = it })
                    Text("Recorrente (todo mês)")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank() && valorOk && dataOk,
                onClick = { onConfirm(nome.trim(), parseAmountOrNull(valor)!!, parseDateOrNull(vencimentoTexto)!!, tipo, recorrente) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun AddDividaDialog(
    onDismiss: () -> Unit,
    onConfirm: (nome: String, valorOriginal: Double, valorAberto: Double, taxaPercentual: Double, parcelasRestantes: Int, valorParcela: Double) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var valorOriginal by remember { mutableStateOf("") }
    var valorAberto by remember { mutableStateOf("") }
    var taxa by remember { mutableStateOf("") }
    var parcelasRestantes by remember { mutableStateOf("0") }
    var valorParcela by remember { mutableStateOf("0") }
    val originalOk = parseAmountOrNull(valorOriginal) != null
    val abertoOk = parseAmountOrNull(valorAberto) != null
    val taxaOk = parseAmountOrNull(taxa) != null
    val parcelasOk = parcelasRestantes.toIntOrNull() != null
    val parcelaOk = parseAmountOrNull(valorParcela) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova dívida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(valorOriginal, { valorOriginal = it }, label = { Text("Valor original (R$)") }, singleLine = true, isError = valorOriginal.isNotEmpty() && !originalOk)
                OutlinedTextField(valorAberto, { valorAberto = it }, label = { Text("Valor em aberto hoje (R$)") }, singleLine = true, isError = valorAberto.isNotEmpty() && !abertoOk)
                OutlinedTextField(taxa, { taxa = it }, label = { Text("Taxa de juros mensal (% a.m.)") }, singleLine = true, isError = taxa.isNotEmpty() && !taxaOk)
                OutlinedTextField(parcelasRestantes, { parcelasRestantes = it }, label = { Text("Parcelas restantes (0 se não houver)") }, singleLine = true, isError = !parcelasOk)
                OutlinedTextField(valorParcela, { valorParcela = it }, label = { Text("Valor da parcela (R$, 0 se não houver)") }, singleLine = true, isError = !parcelaOk)
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank() && originalOk && abertoOk && taxaOk && parcelasOk && parcelaOk,
                onClick = {
                    onConfirm(
                        nome.trim(), parseAmountOrNull(valorOriginal)!!, parseAmountOrNull(valorAberto)!!,
                        parseAmountOrNull(taxa)!!, parcelasRestantes.toInt(), parseAmountOrNull(valorParcela)!!,
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun BudgetLimitDialog(categoria: String, currentLimitReais: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var valor by remember { mutableStateOf(if (currentLimitReais > 0) currentLimitReais.toString() else "") }
    val ok = parseAmountOrNull(valor) != null && (parseAmountOrNull(valor) ?: 0.0) > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limite de $categoria") },
        text = { OutlinedTextField(valor, { valor = it }, label = { Text("Limite mensal (R$)") }, singleLine = true, isError = valor.isNotEmpty() && !ok) },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(parseAmountOrNull(valor)!!) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
