package com.finai.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Plain Material3 [AlertDialog] forms for the manual-entry flows Fase 1
 * requires (planning.md §9) — contas e objetivos (dívidas têm tela própria,
 * [DebtEntryScreen]). The same dialog serves create and edit: pass
 * [AddContaDialog]'s/[AddGoalDialog]'s `initial` to pre-fill the fields from an existing
 * entity — `onConfirm` still returns plain field values (never the id), so
 * the caller decides whether to insert a new row or `.copy()` the one being
 * edited (preserving its id and whatever fields the dialog doesn't expose,
 * like a conta's paid/pending status).
 * Deliberately unstyled against the rest of the app's bespoke bottom-sheet
 * look; the goal here is correct, real CRUD, not a pixel-perfect form.
 */

// internal (não private): reaproveitados por InitialSetupWizard.kt, que segue a
// mesma convenção de formulário simples definida aqui.
internal val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

internal fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text, dateFormatter)
} catch (e: DateTimeParseException) {
    null
}

internal fun parseAmountOrNull(text: String): Double? = text.replace(",", ".").toDoubleOrNull()

@Composable
fun AddGoalDialog(
    initial: ObjetivoEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (nome: String, tipo: String, valorAlvo: Double, valorGuardado: Double, prazo: LocalDate, prioridade: Int) -> Unit,
) {
    var nome by remember { mutableStateOf(initial?.nome ?: "") }
    var tipo by remember { mutableStateOf(initial?.tipo ?: "Compra") }
    var valorAlvo by remember { mutableStateOf(initial?.let { (it.valorAlvoCentavos / 100.0).toString() } ?: "") }
    var valorGuardado by remember { mutableStateOf(initial?.let { (it.valorGuardadoCentavos / 100.0).toString() } ?: "0") }
    var prazoTexto by remember { mutableStateOf((initial?.prazo?.toLocalDate() ?: LocalDate.now().plusMonths(6)).format(dateFormatter)) }
    var prioridade by remember { mutableStateOf((initial?.prioridade ?: 2).toString()) }
    val alvoOk = parseAmountOrNull(valorAlvo) != null
    val guardadoOk = parseAmountOrNull(valorGuardado) != null
    val prazoOk = parseDateOrNull(prazoTexto) != null
    val prioridadeOk = prioridade.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Editar objetivo" else "Novo objetivo") },
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
    initial: ContaEntity? = null,
    defaultTipo: String = "a_pagar",
    onDismiss: () -> Unit,
    onConfirm: (nome: String, valor: Double, vencimento: LocalDate, tipo: String, recorrente: Boolean) -> Unit,
) {
    var nome by remember { mutableStateOf(initial?.nome ?: "") }
    var valor by remember { mutableStateOf(initial?.let { (it.valorCentavos / 100.0).toString() } ?: "") }
    var vencimentoTexto by remember { mutableStateOf((initial?.vencimento?.toLocalDate() ?: LocalDate.now()).format(dateFormatter)) }
    var tipo by remember { mutableStateOf(initial?.tipo ?: defaultTipo) }
    var recorrente by remember { mutableStateOf(initial?.recorrente ?: false) }
    val valorOk = parseAmountOrNull(valor) != null
    val dataOk = parseDateOrNull(vencimentoTexto) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Editar conta" else "Nova conta") },
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
