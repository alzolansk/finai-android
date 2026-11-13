package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.util.formatBrl0
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Mesmos movimentos da Agenda. Contas pagas permanecem na semana para poder reabrir. */
object HomeWeek {
    fun movements(contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, dividas: List<DividaEntity>, today: LocalDate): List<TimelineMovement> =
        CashFlowTimeline.monthMovements(contas, transacoes, dividas, today, 2)
            .filterNot { (it.source as? CycleSource.Lancamento)?.transacao?.isAdjustment == true }
            .map { m ->
                val due = (m.source as? CycleSource.Conta)?.conta?.vencimento?.toLocalDate()
                if (due != null) m.copy(date = due) else m
            }
            .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(7)) }
            .sortedWith(compareBy<TimelineMovement> { it.date }.thenByDescending { it.cents })
}

/** Regra 9: só novidade contra os três meses completos anteriores, sem IA. */
object HomeCoach {
    fun detect(transacoes: List<TransacaoEntity>, today: LocalDate, dismissed: Set<String> = emptySet()): BehaviorPattern? {
        val month = YearMonth.from(today)
        fun expenses(m: YearMonth) = transacoes.transactionsInMonth(m.atDay(1)).filter {
            it.tipo == "Gasto" && it.origem != DebtSchedule.PAYMENT_ORIGIN &&
                it.categoria !in com.finai.app.data.model.Categorias.system &&
                !it.data.toLocalDate().isAfter(today) && it.valorCentavos > 0
        }
        val current = expenses(month)
        val history = (1L..3L).flatMap { expenses(month.minusMonths(it)) }
        fun candidates(merchant: Boolean): List<Pair<Double, BehaviorPattern>> {
            fun key(t: TransacaoEntity) = if (merchant) t.descricao.trim().lowercase(Locale.ROOT) else t.categoria
            val totals = current.groupBy(::key)
            val past = history.groupBy(::key)
            return totals.mapNotNull { (key, entries) ->
                if (key.isBlank()) return@mapNotNull null
                val total = entries.sumOf { it.valorCentavos }
                val baseline = past[key]?.sumOf { it.valorCentavos } ?: return@mapNotNull null
                // Comparação exata contra 1,6 × (soma/3), sem arredondar a média.
                if (baseline <= 0 || total.toDouble() * 15 <= baseline.toDouble() * 8) return@mapNotNull null
                val id = "coach:v2:${if (merchant) "estabelecimento" else "categoria"}:$key"
                if (id in dismissed) return@mapNotNull null
                val name = if (merchant) entries.first().descricao.trim() else key
                val average = baseline / 3
                val money = { cents: Long -> formatBrl0(cents / 100.0) }
                (total.toDouble() / baseline) to BehaviorPattern(
                    id, if (merchant) BehaviorPatternKind.FREQUENT_MERCHANT else BehaviorPatternKind.CATEGORY_GROWTH,
                    "$name: ${money(total)} em ${today.dayOfMonth} dias.",
                    "Nos últimos 3 meses, a média era ${money(average)} por mês. " +
                        "No ritmo atual, este mês chega a cerca de ${money(total / today.dayOfMonth * month.lengthOfMonth())}.",
                )
            }
        }
        return (candidates(true) + candidates(false)).sortedByDescending { it.first }.firstOrNull()?.second
    }
}
