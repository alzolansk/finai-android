package com.finai.app.domain.spending

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Categorias
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.TransactionType
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.quickentry.QuickEntryParser
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.transactionsInMonth
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

/** Um mês, ou o ano inteiro quando [month] é nulo. */
data class SpendingPeriod(val year: Int, val month: Int? = null) {
    val yearMonth: YearMonth? get() = month?.let { YearMonth.of(year, it) }
    val label: String get() = month?.let { MONTH_NAMES_PT[it - 1] } ?: year.toString()
}

enum class SpendTone { Ok, Warn, Over }

data class CategorySpend(
    val categoria: String,
    val cents: Long,
    /** Limite mensal; nulo sem limite (ou no período do ano). */
    val limitCents: Long?,
    val note: String,
    val tone: SpendTone,
    val count: Int,
)

data class MerchantSpend(val name: String, val cents: Long, val count: Int)

/**
 * Análise de consumo (planning-v2, fase 6). Só `tipo = Gasto` nas categorias do usuário
 * ([Categorias.all]); categorias de sistema e ajuste ficam fora. Compras do cartão contam pela
 * **data da compra**, não pelo vencimento da fatura: aqui é consumo, não caixa.
 */
data class SpendingAnalysis(
    val period: SpendingPeriod,
    val totalCents: Long,
    /** Uma frase de comparação; vazia sem base para comparar. */
    val comparison: String,
    /** Seis meses terminando no período (ou os meses do ano). */
    val trend: List<Pair<YearMonth, Long>>,
    val categories: List<CategorySpend>,
    val merchants: List<MerchantSpend>,
    /** Os gastos do período, do mais recente para o mais antigo. */
    val entries: List<TransacaoEntity>,
) {
    companion object {
        fun isConsumption(t: TransacaoEntity) = t.tipo == TransactionType.Gasto.name && !Categorias.isSystem(t.categoria)

        fun monthEntries(transacoes: List<TransacaoEntity>, month: YearMonth): List<TransacaoEntity> =
            transacoes.transactionsInMonth(month.atDay(1)).filter(::isConsumption)

        private fun monthTotal(transacoes: List<TransacaoEntity>, month: YearMonth, untilDay: Int? = null): Long =
            monthEntries(transacoes, month).filter { untilDay == null || it.data.toLocalDate().dayOfMonth <= untilDay }.sumOf { it.valorCentavos }

        fun of(
            transacoes: List<TransacaoEntity>,
            limits: Map<String, Long>,
            period: SpendingPeriod,
            today: LocalDate,
        ): SpendingAnalysis {
            val current = YearMonth.from(today)
            val month = period.yearMonth
            val entries = if (month != null) monthEntries(transacoes, month)
            else (1..12).map { YearMonth.of(period.year, it) }.filter { it <= current }.flatMap { monthEntries(transacoes, it) }
            val counted = entries.filter { !it.data.toLocalDate().isAfter(today) }
            val total = counted.sumOf { it.valorCentavos }

            val comparison = when {
                month == null -> {
                    val months = (1..12).map { YearMonth.of(period.year, it) }.count { it <= current }.coerceAtLeast(1)
                    if (total > 0) "Média de ${formatBrl0(total / months / 100.0)} por mês em ${period.year}." else ""
                }
                month == current -> {
                    val before = monthTotal(transacoes, month.minusMonths(1), today.dayOfMonth)
                    diffSentence(total, before, "até o dia ${today.dayOfMonth} de ${MONTH_NAMES_PT[month.minusMonths(1).monthValue - 1].lowercase()}")
                }
                else -> diffSentence(total, monthTotal(transacoes, month.minusMonths(1)), "em ${MONTH_NAMES_PT[month.minusMonths(1).monthValue - 1].lowercase()}")
            }

            val trend = if (month != null) (5 downTo 0).map { month.minusMonths(it.toLong()) }.map { m ->
                m to monthEntries(transacoes, m).filter { !it.data.toLocalDate().isAfter(today) }.sumOf { it.valorCentavos }
            } else (1..12).map { YearMonth.of(period.year, it) }.filter { it <= current }.map { m -> m to monthTotal(transacoes, m) }

            val byCategory = counted.groupBy { it.categoria }
            val names = (byCategory.keys + if (month == current) limits.keys else emptySet()).distinct()
            val categories = names.map { name ->
                val list = byCategory[name].orEmpty()
                val spent = list.sumOf { it.valorCentavos }
                val limit = limits[name]?.takeIf { it > 0 && month != null }
                val (note, tone) = paceNote(spent, limit, month, today)
                CategorySpend(name, spent, limit, note, tone, list.size)
            }.sortedByDescending { it.cents }

            val merchants = counted.groupBy { MerchantClassifier.normalizeMerchant(it.descricao).ifBlank { it.categoria.lowercase() } }
                .map { (_, list) -> MerchantSpend(list.maxByOrNull { it.data }!!.descricao.ifBlank { list.first().categoria }.take(28), list.sumOf { it.valorCentavos }, list.size) }
                .sortedByDescending { it.cents }.take(5)

            return SpendingAnalysis(period, total, comparison, trend, categories, merchants, entries.sortedByDescending { it.data })
        }

        private fun diffSentence(now: Long, before: Long, reference: String): String {
            if (before <= 0L) return ""
            val diff = now - before
            return when {
                diff == 0L -> "Igual ao que você tinha gasto $reference."
                diff > 0 -> "${formatBrl0(diff / 100.0)} a mais do que $reference."
                else -> "${formatBrl0(-diff / 100.0)} a menos do que $reference."
            }
        }

        /** Ritmo: "passa do limite em N dias" = gasto ÷ dias corridos, levado até o limite. */
        fun paceNote(spent: Long, limit: Long?, month: YearMonth?, today: LocalDate): Pair<String, SpendTone> {
            if (limit == null || month == null) return "" to SpendTone.Ok
            if (spent >= limit) return (if (spent == limit) "No limite" else "Passou ${formatBrl0((spent - limit) / 100.0)} do limite") to SpendTone.Over
            val remaining = limit - spent
            if (month != YearMonth.from(today)) return "Sobrou ${formatBrl0(remaining / 100.0)} do limite" to SpendTone.Ok
            val perDay = spent.toDouble() / today.dayOfMonth
            val daysLeft = month.lengthOfMonth() - today.dayOfMonth
            val daysToLimit = if (perDay <= 0) Int.MAX_VALUE else ceil(remaining / perDay).toInt()
            return if (daysToLimit <= daysLeft) "Nesse ritmo passa do limite em $daysToLimit ${if (daysToLimit == 1) "dia" else "dias"}" to SpendTone.Warn
            else "Restam ${formatBrl0(remaining / 100.0)} até o fim do mês" to SpendTone.Ok
        }

        /** Busca sem acento e sem caixa em descrição, categoria, conta e origem. */
        fun search(transacoes: List<TransacaoEntity>, query: String, period: SpendingPeriod? = null): List<TransacaoEntity> {
            val q = QuickEntryParser.fold(query).trim()
            if (q.length < 2) return emptyList()
            return transacoes.asSequence()
                .filter { it.tipo != com.finai.app.domain.ADJUSTMENT_TYPE }
                .filter { t ->
                    val d = t.data.toLocalDate()
                    period == null || (d.year == period.year && (period.month == null || d.monthValue == period.month))
                }
                .filter { t -> listOf(t.descricao, t.categoria, t.contaOrigem, t.origem).any { QuickEntryParser.fold(it).contains(q) } }
                .sortedByDescending { it.data }.take(60).toList()
        }
    }
}

data class LimitProposal(val categoria: String, val suggestedCents: Long, val currentCents: Long?, val averageCents: Long, val overBefore: Boolean)

/** Regra 10: média de 3 meses fechados, −5% onde estourou, arredondado a R$ 10. */
object LimitSuggestion {
    fun suggest(transacoes: List<TransacaoEntity>, limits: Map<String, Long>, today: LocalDate): List<LimitProposal> {
        val months = (1L..3L).map { YearMonth.from(today).minusMonths(it) }
        val perMonth = months.map { m -> SpendingAnalysis.monthEntries(transacoes, m).groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.valorCentavos } } }
        val withData = perMonth.count { it.isNotEmpty() }
        if (withData == 0) return emptyList()
        return Categorias.all.mapNotNull { name ->
            val values = perMonth.filter { it.isNotEmpty() }.map { it[name] ?: 0L }
            val avg = values.sum() / values.size
            if (avg <= 0) return@mapNotNull null
            val current = limits[name]?.takeIf { it > 0 }
            val over = current != null && values.any { it > current }
            val raw = if (over) avg * 95 / 100 else avg
            val rounded = ((raw + 500) / 1000 * 1000).coerceAtLeast(1000)
            LimitProposal(name, rounded, current, avg, over)
        }.filter { it.currentCents != it.suggestedCents }
    }
}

/** Assinatura na tela de Gastos. O app não sabe se o serviço é usado: só há quantos meses é cobrado. */
data class SubscriptionLine(val name: String, val cents: Long, val monthsCharged: Int, val active: Boolean) {
    val note: String get() = when {
        !active -> "Cancelada"
        monthsCharged >= 2 -> "Cobrada há $monthsCharged meses seguidos"
        monthsCharged == 1 -> "Cobrada neste mês"
        else -> "Sem cobrança lançada"
    }
}

object SubscriptionLines {
    fun of(assinaturas: List<com.finai.app.data.local.entity.AssinaturaEntity>, transacoes: List<TransacaoEntity>, today: LocalDate): List<SubscriptionLine> {
        val current = YearMonth.from(today)
        return assinaturas.map { sub ->
            val key = MerchantClassifier.normalizeMerchant(sub.nome)
            fun charged(m: YearMonth) = key.isNotBlank() && SpendingAnalysis.monthEntries(transacoes, m).any { MerchantClassifier.normalizeMerchant(it.descricao).contains(key) }
            // O mês corrente pode ainda não ter a cobrança: a sequência pode começar no anterior.
            val start = if (charged(current)) current else current.minusMonths(1)
            val months = generateSequence(start) { it.minusMonths(1) }.take(24).takeWhile(::charged).count()
            SubscriptionLine(sub.nome, sub.valorCentavos, months, sub.status == "ativa")
        }.sortedWith(compareByDescending<SubscriptionLine> { it.active }.thenByDescending { it.cents })
    }
}
