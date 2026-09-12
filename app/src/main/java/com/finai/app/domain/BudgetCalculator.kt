package com.finai.app.domain

import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

enum class BudgetTone { Ok, Warn, Over }

data class BudgetProgress(
    val categoria: String,
    val spentCents: Long,
    val limitCents: Long,
    val tone: BudgetTone,
    /** Projected end-of-month spend at the current daily pace; null once the limit is already blown. */
    val projectedEndOfMonthCents: Long?,
) {
    val progress: Float get() = if (limitCents <= 0) 0f else (spentCents.toFloat() / limitCents).coerceIn(0f, 1f)
}

/** Progresso de orçamento por categoria — planning.md §3.5/§6. */
object BudgetCalculator {
    fun forCategories(
        categorias: List<OrcamentoCategoriaEntity>,
        transacoes: List<TransacaoEntity>,
        today: LocalDate = LocalDate.now(),
    ): List<BudgetProgress> {
        val month = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth
        val lastDay = month.lengthOfMonth()
        val spentByCategory = transacoes.groupBy { it.categoria }.mapValues { (_, list) -> list.sumOf { it.valorCentavos } }

        return categorias.map { budget ->
            val spent = spentByCategory[budget.categoria] ?: 0L
            val projected = if (spent >= budget.limiteMensalCentavos) null
            else (spent.toDouble() / dayOfMonth * lastDay).roundToLong()
            val tone = when {
                budget.limiteMensalCentavos <= 0 -> BudgetTone.Ok
                spent >= budget.limiteMensalCentavos -> BudgetTone.Over
                projected != null && projected > budget.limiteMensalCentavos -> BudgetTone.Warn
                else -> BudgetTone.Ok
            }
            BudgetProgress(
                categoria = budget.categoria,
                spentCents = spent,
                limitCents = budget.limiteMensalCentavos,
                tone = tone,
                projectedEndOfMonthCents = projected,
            )
        }
    }
}
