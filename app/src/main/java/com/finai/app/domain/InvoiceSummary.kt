package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate

data class InvoiceCategoryShare(val categoria: String, val cents: Long, val count: Int, val fraction: Float)
data class InvoiceMerchant(val name: String, val cents: Long, val count: Int)
data class InvoiceInstallment(val item: TransacaoEntity, val nome: String, val atual: Int, val total: Int) {
    val restantes: Int get() = total - atual
}

/** Números da página da fatura — só agrupamento dos itens já gravados, sem IA. */
data class InvoiceSummary(
    val totalCents: Long,
    val comprasCents: Long,
    val estornosCents: Long,
    val comprasCount: Int,
    val estornosCount: Int,
    val categorias: List<InvoiceCategoryShare>,
    val merchants: List<InvoiceMerchant>,
    val parcelas: List<InvoiceInstallment>,
    val porDia: List<Pair<LocalDate, List<TransacaoEntity>>>,
) {
    companion object {
        // "Jim.Com* Joao (01/02)", "LOJA PARC 3/10", "PARCELA 2 DE 6" — os formatos que o importador já reconhece.
        private val installmentPatterns = listOf(
            Regex("""\(\s*(\d{1,2})\s*/\s*(\d{1,2})\s*\)"""),
            Regex("""(?i)\bparc(?:ela)?\.?\s*(\d{1,2})\s*(?:/|de)\s*(\d{1,2})\b"""),
        )

        fun installmentOf(descricao: String): Pair<Int, Int>? {
            for (p in installmentPatterns) {
                val m = p.find(descricao) ?: continue
                val atual = m.groupValues[1].toInt()
                val total = m.groupValues[2].toInt()
                if (total in 2..99 && atual in 1..total) return atual to total
            }
            return null
        }

        /** Descrição sem o marcador de parcela — é o nome que o usuário reconhece. */
        fun cleanName(descricao: String): String =
            installmentPatterns.fold(descricao) { acc, p -> acc.replace(p, "") }.trim().trimEnd('-', '·').trim()

        fun of(items: List<TransacaoEntity>): InvoiceSummary {
            val compras = items.filter { it.valorCentavos > 0 }
            val estornos = items.filter { it.valorCentavos < 0 }
            val comprasCents = compras.sumOf { it.valorCentavos }
            val categorias = compras.groupBy { it.categoria }
                .map { (cat, list) ->
                    val cents = list.sumOf { it.valorCentavos }
                    InvoiceCategoryShare(cat, cents, list.size, if (comprasCents > 0) cents.toFloat() / comprasCents else 0f)
                }
                .sortedByDescending { it.cents }
            // Agrupa pelo nome sem parcela e sem caixa, pra "Dl*Uberrides" x4 virar uma linha só.
            val merchants = compras.groupBy { cleanName(it.descricao).lowercase() }
                .map { (_, list) -> InvoiceMerchant(cleanName(list.first().descricao), list.sumOf { it.valorCentavos }, list.size) }
                .sortedWith(compareByDescending<InvoiceMerchant> { it.cents }.thenByDescending { it.count })
                .take(5)
            val parcelas = compras.mapNotNull { item ->
                installmentOf(item.descricao)?.let { (atual, total) -> InvoiceInstallment(item, cleanName(item.descricao), atual, total) }
            }.sortedByDescending { it.item.valorCentavos }
            val porDia = items.groupBy { it.data.toLocalDate() }
                .toSortedMap(compareByDescending { it })
                .map { (d, list) -> d to list.sortedByDescending { it.valorCentavos } }
            return InvoiceSummary(
                totalCents = items.sumOf { it.valorCentavos },
                comprasCents = comprasCents,
                estornosCents = -estornos.sumOf { it.valorCentavos },
                comprasCount = compras.size,
                estornosCount = estornos.size,
                categorias = categorias,
                merchants = merchants,
                parcelas = parcelas,
                porDia = porDia,
            )
        }
    }
}
