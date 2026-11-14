package com.finai.app.domain.plans

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.SavingsProjection
import com.finai.app.domain.TransactionType
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

data class MonthCapacity(val month: YearMonth, val cents: Long)

/**
 * Regra 4: quanto cabe guardar por mês = média da sobra dos próximos 6 meses (entradas − contas,
 * parcelas e o gasto variável médio). Aporte planejado de meta **não** conta como saída
 * (decisão de 08/11): só sai do saldo quando é registrado.
 */
data class SavingsCapacity(val months: List<MonthCapacity>, val variableAverageCents: Long, val usesEstimatedSalary: Boolean) {
    val cabeGuardarCents: Long get() = if (months.isEmpty()) 0 else (months.sumOf { it.cents } / months.size).coerceAtLeast(0)

    companion object {
        const val HORIZON = 6

        /** Gasto que não se repete sozinho num mês: avulsos e contas não recorrentes (a fatura do cartão). */
        private fun variable(flow: MonthCashFlow): Long =
            flow.avulsas.filter { it.tipo == TransactionType.Gasto.name }.sumOf { it.valorCentavos } +
                flow.payable.filter { !it.recorrente }.sumOf { it.valorCentavos }

        fun of(contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, dividas: List<DividaEntity>, today: LocalDate): SavingsCapacity {
            val current = YearMonth.from(today)
            val past = (1L..3L).map { MonthCashFlow.of(contas, transacoes, dividas, current.minusMonths(it), today) }.map(::variable).filter { it > 0 }
            val average = if (past.isEmpty()) 0 else past.sum() / past.size
            val projection = SavingsProjection.of(contas, transacoes, dividas, current.plusMonths(HORIZON.toLong()), today)
            val months = (1..HORIZON).map { current.plusMonths(it.toLong()) }.map { m ->
                val flow = MonthCashFlow.of(contas, transacoes, dividas, m, today)
                val base = projection.months.firstOrNull { it.month == m }?.balanceCents ?: flow.fechamentoCents
                // O que já está lançado para o mês (uma fatura futura) faz parte da média, não soma a ela.
                MonthCapacity(m, base + flow.aportesCents - (average - variable(flow)).coerceAtLeast(0))
            }
            return SavingsCapacity(months, average, projection.usesEstimatedSalary)
        }
    }
}

enum class GoalSeal(val label: String) { EmDia("Em dia"), NaoCabe("Não cabe"), Continua("Contínua"), Concluida("Concluída") }

data class GoalAsk(val id: Long, val name: String, val monthlyCents: Long, val priority: Int, val missingCents: Long, val deadline: YearMonth?)

data class GoalConflict(
    val goalId: Long,
    val goalName: String,
    val asksCents: Long,
    val fitsCents: Long,
    val currentDeadline: YearMonth,
    /** Primeiro prazo em que todas cabem; nulo quando nem adiando esta meta cabe. */
    val newDeadline: YearMonth?,
    val newMonthlyCents: Long,
) {
    /** Chave da decisão "Manter" na Caixa: uma por meta. */
    val decisionId: String get() = "meta-conflito-$goalId"
}

/**
 * Regra 5: se as metas pedem mais do que cabe guardar, a de **menor prioridade** é a que cede.
 * Devolve um conflito por vez, com o primeiro prazo em que todas cabem.
 */
object GoalConflictResolver {
    fun asks(objetivos: List<ObjetivoEntity>, today: LocalDate): List<GoalAsk> = objetivos.filter { it.concluidoEm == null }.mapNotNull { o ->
        if (o.isOpenReserve) {
            if (o.aporteMensalCentavos > 0) GoalAsk(o.id, o.nome, o.aporteMensalCentavos, o.prioridade, 0, null) else null
        } else {
            val missing = (o.valorAlvoCentavos - o.valorGuardadoCentavos).coerceAtLeast(0)
            val deadline = YearMonth.from(o.prazo.toLocalDate())
            val months = ChronoUnit.MONTHS.between(YearMonth.from(today), deadline).coerceAtLeast(1)
            if (missing <= 0) null else GoalAsk(o.id, o.nome, ceil(missing.toDouble() / months).toLong(), o.prioridade, missing, deadline)
        }
    }

    fun resolve(objetivos: List<ObjetivoEntity>, fitsCents: Long, today: LocalDate): GoalConflict? {
        val asks = asks(objetivos, today)
        val total = asks.sumOf { it.monthlyCents }
        if (total <= fitsCents) return null
        // Quem cede: a meta com prazo de menor prioridade (número maior); empate, o maior aporte.
        val loser = asks.filter { it.deadline != null }.sortedWith(compareByDescending<GoalAsk> { it.priority }.thenByDescending { it.monthlyCents }).firstOrNull() ?: return null
        val available = fitsCents - (total - loser.monthlyCents)
        val current = YearMonth.from(today)
        if (available <= 0) return GoalConflict(loser.id, loser.name, total, fitsCents, loser.deadline!!, null, 0)
        val months = ceil(loser.missingCents.toDouble() / available).toLong().coerceAtLeast(1)
        return GoalConflict(loser.id, loser.name, total, fitsCents, loser.deadline!!, current.plusMonths(months), ceil(loser.missingCents.toDouble() / months).toLong())
    }

    fun seal(objetivo: ObjetivoEntity, conflict: GoalConflict?, today: LocalDate): GoalSeal = when {
        objetivo.isOpenReserve -> GoalSeal.Continua
        objetivo.concluidoEm != null || objetivo.valorGuardadoCentavos >= objetivo.valorAlvoCentavos -> GoalSeal.Concluida
        conflict?.goalId == objetivo.id -> GoalSeal.NaoCabe
        objetivo.prazo.toLocalDate().isBefore(today) -> GoalSeal.NaoCabe
        else -> GoalSeal.EmDia
    }
}
