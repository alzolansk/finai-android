package com.finai.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finai.app.data.local.entity.*
import com.finai.app.domain.toLocalDate
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

@Composable
fun InstallmentsOverview(entries: List<TransacaoEntity>, invoices: List<FaturaCartaoEntity>, accounts: List<ContaEntity>) {
    val purchases = entries.filter { it.compraId != null && (it.parcelasTotal ?: 0) > 1 }.groupBy { it.compraId }
    if (purchases.isEmpty()) return
    val invoiceById = invoices.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    val money = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    fun amount(cents: Long) = money.format(cents / 100.0)
    fun pending(item: TransacaoEntity): Boolean = invoiceById[item.faturaId]?.let { accountById[it.contaId]?.status != "pago" } == true
    val remaining = purchases.values.flatten().filter(::pending)
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Parcelamentos", style = MaterialTheme.typography.titleMedium)
            Text("${remaining.size} parcelas em aberto · ${amount(remaining.sumOf { it.valorCentavos })}")
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher" else "Ver compras e próximos meses") }
            if (expanded) {
                purchases.values.forEach { items ->
                    val first = items.minBy { it.parcelaNumero ?: 0 }
                    val unpaid = items.filter(::pending)
                    Text(first.descricao.substringBeforeLast(" · parcela "), style = MaterialTheme.typography.titleSmall)
                    Text("${first.contaOrigem} · total ${amount(items.sumOf { it.valorCentavos })}\n" +
                        "${unpaid.size} de ${first.parcelasTotal} parcelas em aberto · ${amount(unpaid.sumOf { it.valorCentavos })}")
                }
                HorizontalDivider()
                Text("Comprometido por mês", style = MaterialTheme.typography.titleSmall)
                remaining.groupBy { YearMonth.from(requireNotNull(invoiceById[it.faturaId]).vencimento.toLocalDate()) }
                    .toSortedMap().forEach { (month, items) ->
                        Text("${month.monthValue.toString().padStart(2, '0')}/${month.year} · ${amount(items.sumOf { it.valorCentavos })}")
                    }
            }
        }
    }
}
