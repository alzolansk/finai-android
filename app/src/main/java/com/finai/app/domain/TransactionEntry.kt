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
 * Aporte: transferência para um objetivo ou reserva. O dinheiro sai do livre (foi guardado),
 * mas não é gasto: fica fora de orçamento, coach e categorias.
 */
val TransacaoEntity.isGoalContribution: Boolean
    get() = tipo == TransactionType.Transferencia.name && objetivoId != null

/**
 * Efeito do lançamento no dinheiro livre: +1 entra, −1 sai, 0 não mexe. A única regra usada
 * por ciclo, balanço do mês, linha do tempo e projeções. Transferência entre contas é neutra
 * (o dinheiro continua disponível); o aporte sai uma vez, aqui, e o valor guardado no objetivo
 * nunca volta a ser contado como livre.
 */
val TransacaoEntity.freeCashSign: Int
    get() = when (tipo) {
        TransactionType.Receita.name -> 1
        TransactionType.Transferencia.name -> if (objetivoId != null) -1 else 0
        else -> -1
    }

/**
 * Ocorrência mensal de uma série iniciada em [start]. Começar no último dia do mês
 * (30/09, 28/02) quer dizer "todo fim de mês": vira 31/10, 30/11, 31/12. Fora isso é o
 * mesmo dia, e 29–31 num mês mais curto cai no último dia dele.
 */
fun monthlyOccurrence(start: LocalDate, month: YearMonth): LocalDate =
    if (start.dayOfMonth == start.lengthOfMonth()) month.atEndOfMonth()
    else month.atDay(start.dayOfMonth.coerceAtMost(month.lengthOfMonth()))

/**
 * Lançamentos do mês de [date]. Recorrente mantém o id do Room: a ocorrência do mês é uma
 * projeção ([occurrenceFor]), não uma cópia gravada. A projeção respeita o fim da série
 * ([TransacaoEntity.recorrenteAte]) e a exceção do mês ("Somente este mês"), que nunca é
 * sobrescrita pela regra geral.
 */
fun List<TransacaoEntity>.transactionsInMonth(date: LocalDate): List<TransacaoEntity> {
    val month = YearMonth.from(date)
    return mapNotNull { entry ->
        when {
            entry.isRecurringSeries -> entry.occurrenceFor(month)
            YearMonth.from(entry.data.toLocalDate()) == month -> entry
            else -> null
        }
    }
}
