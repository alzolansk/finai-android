package com.finai.app.domain.closing

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.MONTH_NAMES_PT
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.importer.MerchantClassifier
import com.finai.app.domain.spending.LimitProposal
import com.finai.app.domain.spending.LimitSuggestion
import com.finai.app.domain.spending.SpendingAnalysis
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth

data class CategoryDiff(val categoria: String, val cents: Long, val averageCents: Long) {
    val diffCents: Long get() = cents - averageCents
}

/** Cobrança que veio nos meses anteriores e não apareceu no mês fechado ("luz não veio"). */
data class MissingCharge(val name: String, val cents: Long, val categoria: String)

enum class ClosingStepId(val key: String) { Missing("faltou"), Limits("limites"), Surplus("sobra"), Pattern("padrao") }

data class ClosingStep(
    val id: ClosingStepId,
    val title: String,
    val detail: String,
    /** Rótulo do botão que faz a ação de verdade; nulo quando o passo não tem o que fazer. */
    val action: String?,
    val skip: String,
    /** Resposta já dada (não se pergunta de novo). */
    val answer: String? = null,
) {
    val done: Boolean get() = answer != null
}

/**
 * Fechamento do mês (planning-v2, fase 10): o que entrou, saiu e sobrou no mês que acabou, as
 * maiores diferenças contra a média dos três meses anteriores e quatro passos com ação real.
 * Tudo calculado aqui; a IA só reescreve o parágrafo de resumo.
 */
data class MonthClosing(
    val month: YearMonth,
    val inCents: Long,
    val outCents: Long,
    val leftCents: Long,
    /** Sobra média dos três meses anteriores; nula sem histórico. */
    val averageLeftCents: Long?,
    val diffs: List<CategoryDiff>,
    val missing: List<MissingCharge>,
    val limits: List<LimitProposal>,
    val surplusGoal: Pair<Long, String>?,
    val surplusCents: Long,
    val pattern: CategoryDiff?,
    val steps: List<ClosingStep>,
) {
    val monthName: String get() = MONTH_NAMES_PT[month.monthValue - 1]
    val key: String get() = month.toString()
    val doneCount: Int get() = steps.count { it.done }
    val pending: Boolean get() = steps.any { !it.done }
    /** Não há o que fechar: mês sem nenhum movimento. */
    val empty: Boolean get() = inCents == 0L && outCents == 0L

    /** Resumo sem IA; também é o que a IA recebe como fatos. */
    fun summary(): String = buildString {
        append("Em ${monthName.lowercase()} entraram ${brl(inCents)} e saíram ${brl(outCents)}: ")
        append(if (leftCents >= 0) "sobraram ${brl(leftCents)}." else "faltaram ${brl(-leftCents)}.")
        averageLeftCents?.let { avg ->
            val d = leftCents - avg
            if (d != 0L) append(" Isso é ${brl(kotlin.math.abs(d))} ${if (d > 0) "a mais" else "a menos"} que a média dos três meses anteriores.")
        }
        diffs.firstOrNull()?.let { d ->
            append(" A maior diferença foi em ${d.categoria}: ${brl(d.cents)}, contra uma média de ${brl(d.averageCents)}.")
        }
    }

    fun factsBlock(): String = buildString {
        appendLine(summary())
        diffs.forEach { appendLine("- ${it.categoria}: ${brl(it.cents)} (média ${brl(it.averageCents)}).") }
        missing.forEach { appendLine("- Não apareceu no mês: ${it.name}, que costuma ser ${brl(it.cents)}.") }
    }

    companion object {
        const val VISIBLE_UNTIL_DAY = 7
        private fun brl(c: Long) = formatBrl0(c / 100.0)

        /** O mês que se fecha em [today]: o anterior. */
        fun monthFor(today: LocalDate): YearMonth = YearMonth.from(today).minusMonths(1)

        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            limits: Map<String, Long>,
            /** Meta que recebe a sobra (a recomendada pelo plano, ou a primeira em andamento). */
            surplusGoal: Pair<Long, String>?,
            /** Livre de hoje: a sobra sugerida nunca passa dele. */
            freeCents: Long,
            answers: Map<String, String>,
            today: LocalDate,
        ): MonthClosing {
            val month = monthFor(today)
            val flow = MonthCashFlow.of(contas, transacoes, dividas, month, today)
            val previous = (1L..3L).map { month.minusMonths(it) }
            val prevFlows = previous.map { MonthCashFlow.of(contas, transacoes, dividas, it, today) }.filter { it.entraCents != 0L || it.saiCents != 0L }
            val avgLeft = prevFlows.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.fechamentoCents } / list.size }

            val now = SpendingAnalysis.monthEntries(transacoes, month)
            val before = previous.map { SpendingAnalysis.monthEntries(transacoes, it) }
            val monthsWithData = before.count { it.isNotEmpty() }
            val diffs = if (monthsWithData == 0) emptyList() else {
                val names = (now.map { it.categoria } + before.flatten().map { it.categoria }).distinct()
                names.map { name ->
                    CategoryDiff(name, now.filter { it.categoria == name }.sumOf { it.valorCentavos },
                        before.flatten().filter { it.categoria == name }.sumOf { it.valorCentavos } / monthsWithData)
                }.filter { kotlin.math.abs(it.diffCents) >= 2_000 }.sortedByDescending { kotlin.math.abs(it.diffCents) }.take(3)
            }

            // Recorrente esperado sem ocorrência: mesma cobrança em pelo menos dois dos três meses anteriores.
            fun key(t: TransacaoEntity) = MerchantClassifier.normalizeMerchant(t.descricao)
            val seenNow = now.map(::key).toSet()
            val missing = before.flatMap { list -> list.distinctBy(::key) }.groupBy(::key)
                .filter { (k, list) -> k.length >= 3 && list.size >= 2 && k !in seenNow }
                .map { (_, list) -> list.maxByOrNull { it.data }!! }
                .filter { last -> last.valorCentavos >= 1_000 }
                .sortedByDescending { it.valorCentavos }.take(2)
                .map { MissingCharge(it.descricao.trim().take(28), it.valorCentavos, it.categoria) }

            val proposals = LimitSuggestion.suggest(transacoes, limits, today).take(4)
            val surplus = (minOf(flow.fechamentoCents, freeCents).coerceAtLeast(0) / 1_000) * 1_000
            val pattern = diffs.firstOrNull { it.diffCents > 0 }

            val steps = listOf(
                ClosingStep(
                    ClosingStepId.Missing,
                    if (missing.isEmpty()) "Nada ficou de fora" else "${missing.first().name} não apareceu",
                    if (missing.isEmpty()) "Todas as cobranças que se repetem apareceram em ${MONTH_NAMES_PT[month.monthValue - 1].lowercase()}."
                    else "Costuma ser ${brl(missing.first().cents)} por mês e não foi lançada. Se você pagou, vale lançar para o mês fechar certo.",
                    missing.firstOrNull()?.let { "Lançar ${brl(it.cents)}" }, if (missing.isEmpty()) "Ok" else "Não teve",
                ),
                ClosingStep(
                    ClosingStepId.Limits,
                    if (proposals.isEmpty()) "Limites em dia" else "Limites para este mês",
                    if (proposals.isEmpty()) "Seus limites já acompanham o que você gasta."
                    else proposals.joinToString(" · ") { "${it.categoria} ${brl(it.suggestedCents)}" } + ". Pela média dos últimos três meses.",
                    "Aplicar".takeIf { proposals.isNotEmpty() }, if (proposals.isEmpty()) "Ok" else "Manter os atuais",
                ),
                ClosingStep(
                    ClosingStepId.Surplus,
                    if (surplus > 0 && surplusGoal != null) "Destino da sobra" else "Sem sobra para guardar",
                    when {
                        surplusGoal == null -> "Crie uma meta em Planos para dar um destino à sobra dos próximos meses."
                        surplus > 0 -> "Dá para guardar ${brl(surplus)} em ${surplusGoal.second} sem mexer na sua margem."
                        else -> "O mês fechou sem sobra livre. Nada a guardar agora."
                    },
                    "Guardar ${brl(surplus)}".takeIf { surplus > 0 && surplusGoal != null }, if (surplus > 0 && surplusGoal != null) "Agora não" else "Ok",
                ),
                ClosingStep(
                    ClosingStepId.Pattern,
                    pattern?.let { "${it.categoria} subiu" } ?: "Nenhum padrão novo",
                    pattern?.let { "Foram ${brl(it.cents)}, contra uma média de ${brl(it.averageCents)}. Posso avisar se passar de ${brl(it.averageCents)} neste mês." }
                        ?: "Nenhuma categoria fugiu da sua média.",
                    "Quero o aviso".takeIf { pattern != null }, if (pattern != null) "Já sei" else "Ok",
                ),
            ).map { it.copy(answer = answers[it.id.key]) }

            return MonthClosing(month, flow.entraCents, flow.saiCents, flow.fechamentoCents, avgLeft, diffs, missing, proposals,
                surplusGoal, surplus, pattern, steps)
        }

        /** O cartão aparece do dia 1 ao 7, ou enquanto faltar passo de um fechamento já começado. */
        fun showCard(closing: MonthClosing, today: LocalDate): Boolean =
            !closing.empty && closing.pending && (today.dayOfMonth <= VISIBLE_UNTIL_DAY || closing.doneCount > 0)
    }
}
