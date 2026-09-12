package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.util.formatBrl0
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class BehaviorPatternKind { CATEGORY_GROWTH, FREQUENT_MERCHANT, WEEKEND_CONCENTRATION, WEEKDAY_CONCENTRATION }

/**
 * Um padrão de comportamento identificado localmente — planning.md §3.1's
 * "Coach de comportamento" ("padrão identificado no gasto, ex.: delivery em
 * dias de trabalho tarde"). [title]/[detail] já são texto pt-BR completo e
 * autossuficiente: a IA (Fase 5, [com.finai.app.state.AiViewModel.ensureCoachInsight])
 * só tem permissão de reescrever esse texto em linguagem mais natural, nunca
 * de decidir se o padrão existe — isso já foi decidido aqui, em Kotlin puro.
 */
data class BehaviorPattern(
    val id: String,
    val kind: BehaviorPatternKind,
    val title: String,
    val detail: String,
)

/**
 * Detecta padrões de gasto recorrentes a partir só do que já está no Room —
 * nenhuma chamada de IA (planning.md §6). Como [TransacaoEntity.data] não
 * guarda hora (só o dia, `LocalDate.toEpochMillis()` grava meia-noite), os
 * padrões aqui são por dia da semana e por repetição de categoria/
 * estabelecimento, não por horário do dia como o protótipo sugeria — a
 * granularidade real dos dados do app.
 *
 * Sempre devolve no máximo os padrões mais fortes de cada tipo, ordenados por
 * relevância; o chamador decide quantos mostrar (hoje, só o primeiro, no
 * cartão "Coach" da Início e na notificação da Fase 5).
 */
object BehaviorCoach {

    private const val MIN_TRANSACTIONS_FOR_PATTERN = 3
    private const val GROWTH_THRESHOLD = 0.3 // 30%
    private const val GROWTH_MIN_CENTS = 5_000L // R$ 50 — ignora ruído em categorias pequenas
    private const val WEEKEND_FRACTION_THRESHOLD = 0.6
    private const val WEEKDAY_FRACTION_THRESHOLD = 0.85 // dias úteis já são 5/7 (~71%) da semana
    private const val FREQUENT_MERCHANT_MIN_COUNT = 4

    fun detect(transacoes: List<TransacaoEntity>, today: LocalDate = LocalDate.now()): List<BehaviorPattern> {
        val thisMonthRange = monthRangeMillis(today)
        val lastMonthRange = monthRangeMillis(YearMonth.from(today).minusMonths(1).atDay(1))
        val thisMonth = transacoes.filter { it.tipo == "Gasto" && it.data in thisMonthRange }
        val lastMonth = transacoes.filter { it.tipo == "Gasto" && it.data in lastMonthRange }

        val patterns = mutableListOf<BehaviorPattern>()
        patterns += categoryGrowthPatterns(thisMonth, lastMonth)
        patterns += frequentMerchantPatterns(thisMonth)
        patterns += weekendConcentrationPatterns(thisMonth)
        patterns += weekdayConcentrationPatterns(thisMonth)
        return patterns.sortedByDescending { it.rank() }
    }

    /** Ordena o que mostrar primeiro quando há mais de um padrão — crescimento é o mais acionável. */
    private fun BehaviorPattern.rank(): Int = when (kind) {
        BehaviorPatternKind.CATEGORY_GROWTH -> 3
        BehaviorPatternKind.FREQUENT_MERCHANT -> 2
        BehaviorPatternKind.WEEKEND_CONCENTRATION -> 1
        BehaviorPatternKind.WEEKDAY_CONCENTRATION -> 0
    }

    private fun categoryGrowthPatterns(thisMonth: List<TransacaoEntity>, lastMonth: List<TransacaoEntity>): List<BehaviorPattern> {
        val thisByCategory = thisMonth.groupBy { it.categoria }.mapValues { (_, l) -> l.sumOf { it.valorCentavos } }
        val lastByCategory = lastMonth.groupBy { it.categoria }.mapValues { (_, l) -> l.sumOf { it.valorCentavos } }
        return thisByCategory.entries.mapNotNull { (categoria, total) ->
            val anterior = lastByCategory[categoria] ?: return@mapNotNull null
            if (anterior <= 0 || total < GROWTH_MIN_CENTS) return@mapNotNull null
            val growth = (total - anterior).toDouble() / anterior
            if (growth < GROWTH_THRESHOLD) return@mapNotNull null
            val pct = (growth * 100).toInt()
            pct to BehaviorPattern(
                id = "coach:crescimento:$categoria",
                kind = BehaviorPatternKind.CATEGORY_GROWTH,
                title = "Gasto com $categoria subiu $pct%",
                detail = "Você gastou ${formatBrl0(centsToReais(total))} em $categoria este mês, contra " +
                    "${formatBrl0(centsToReais(anterior))} no mês passado — um aumento de $pct%.",
            )
        }
            // Maior crescimento percentual primeiro — o desempate mais acionável entre várias categorias que subiram.
            .sortedByDescending { (pct, _) -> pct }
            .map { (_, pattern) -> pattern }
    }

    private fun frequentMerchantPatterns(thisMonth: List<TransacaoEntity>): List<BehaviorPattern> {
        return thisMonth
            .filter { !it.recorrente } // assinatura já aparece em Limites; aqui é hábito de compra avulsa repetida
            .groupBy { it.descricao.trim().lowercase() }
            .filterValues { it.size >= FREQUENT_MERCHANT_MIN_COUNT }
            .mapNotNull { (_, entries) ->
                val nome = entries.first().descricao.trim()
                val total = entries.sumOf { it.valorCentavos }
                BehaviorPattern(
                    id = "coach:habito:${nome.lowercase()}",
                    kind = BehaviorPatternKind.FREQUENT_MERCHANT,
                    title = "\"$nome\" virou hábito frequente",
                    detail = "Você lançou \"$nome\" ${entries.size} vezes este mês, somando ${formatBrl0(centsToReais(total))}.",
                )
            }
    }

    private fun weekendConcentrationPatterns(thisMonth: List<TransacaoEntity>): List<BehaviorPattern> =
        concentrationPatterns(
            thisMonth,
            kind = BehaviorPatternKind.WEEKEND_CONCENTRATION,
            idPrefix = "coach:fimdesemana",
            threshold = WEEKEND_FRACTION_THRESHOLD,
            isTargetDay = { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY },
            titleFor = { categoria -> "$categoria concentrado no fim de semana" },
            detailFor = { categoria, fracaoPct, total ->
                "$fracaoPct% do que você gastou em $categoria este mês (${formatBrl0(centsToReais(total))}) caiu no sábado ou domingo."
            },
        )

    private fun weekdayConcentrationPatterns(thisMonth: List<TransacaoEntity>): List<BehaviorPattern> =
        concentrationPatterns(
            thisMonth,
            kind = BehaviorPatternKind.WEEKDAY_CONCENTRATION,
            idPrefix = "coach:diautil",
            threshold = WEEKDAY_FRACTION_THRESHOLD,
            isTargetDay = { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY },
            titleFor = { categoria -> "$categoria é um hábito de dia de semana" },
            detailFor = { categoria, fracaoPct, total ->
                "$fracaoPct% do que você gastou em $categoria este mês (${formatBrl0(centsToReais(total))}) foi em dias úteis — parece rotina de trabalho, não exceção."
            },
        )

    private fun concentrationPatterns(
        thisMonth: List<TransacaoEntity>,
        kind: BehaviorPatternKind,
        idPrefix: String,
        threshold: Double,
        isTargetDay: (DayOfWeek) -> Boolean,
        titleFor: (String) -> String,
        detailFor: (categoria: String, fracaoPct: Int, total: Long) -> String,
    ): List<BehaviorPattern> {
        return thisMonth.groupBy { it.categoria }
            .filterValues { it.size >= MIN_TRANSACTIONS_FOR_PATTERN }
            .mapNotNull { (categoria, entries) ->
                val total = entries.sumOf { it.valorCentavos }
                if (total <= 0) return@mapNotNull null
                val targetTotal = entries.filter { isTargetDay(it.data.toLocalDate().dayOfWeek) }.sumOf { it.valorCentavos }
                val fracao = targetTotal.toDouble() / total
                if (fracao < threshold) return@mapNotNull null
                BehaviorPattern(
                    id = "$idPrefix:$categoria",
                    kind = kind,
                    title = titleFor(categoria),
                    detail = detailFor(categoria, (fracao * 100).toInt(), total),
                )
            }
    }
}
