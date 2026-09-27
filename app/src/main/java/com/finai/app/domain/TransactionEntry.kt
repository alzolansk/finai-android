package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

enum class TransactionType(val label: String, val confirmation: String) {
    Gasto("Gasto", "Gasto salvo!"), Receita("Receita", "Receita salva!"),
    Transferencia("Transferência", "Transferência salva!")
}

object TransactionEntry {
    val categories = listOf("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Compras", "Serviços", "Outros")
    fun key(cents: String, key: String): String {
        if (key == "erase") return cents.dropLast(1)
        if (key !in listOf("0", "00", "1", "2", "3", "4", "5", "6", "7", "8", "9")) return cents
        val next = (cents + key).trimStart('0')
        return if (next.length <= 9) next else cents
    }
    fun canSave(cents: String, category: String) = (cents.toLongOrNull() ?: 0) > 0 && category in categories
}

/**
 * Ocorrência mensal de uma série iniciada em [start]. Começar no último dia do mês
 * (30/09, 28/02) quer dizer "todo fim de mês": vira 31/10, 30/11, 31/12. Fora isso é o
 * mesmo dia, e 29–31 num mês mais curto cai no último dia dele.
 */
fun monthlyOccurrence(start: LocalDate, month: YearMonth): LocalDate =
    if (start.dayOfMonth == start.lengthOfMonth()) month.atEndOfMonth()
    else month.atDay(start.dayOfMonth.coerceAtMost(month.lengthOfMonth()))

/** Recurring entries retain their Room id: the monthly occurrence is a projection, not a duplicate insert. */
fun List<TransacaoEntity>.transactionsInMonth(date: LocalDate): List<TransacaoEntity> {
    val month = YearMonth.from(date)
    return mapNotNull { entry ->
        val start = entry.data.toLocalDate()
        when {
            YearMonth.from(start) == month -> entry
            entry.recorrente && YearMonth.from(start) < month -> entry.copy(
                data = monthlyOccurrence(start, month).toEpochMillis())
            else -> null
        }
    }
}
