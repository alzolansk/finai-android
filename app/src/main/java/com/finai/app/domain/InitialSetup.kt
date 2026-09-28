package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Regras do assistente inicial mínimo (Fase 7, item 6): renda principal + próximo
 * recebimento e, opcional, as contas desta semana. Kotlin puro para ser testável.
 */
object InitialSetup {

    /** "3.500", "3500,00", "R$ 3.500,50" → centavos. Nulo se não der para ler um valor positivo. */
    fun parseAmountCents(text: String): Long? {
        val clean = text.replace("R$", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null
        val normalized = when {
            ',' in clean -> clean.replace(".", "").replace(',', '.')
            // "3.500" é três mil e quinhentos, não três e meio.
            Regex("""\d{1,3}(\.\d{3})+""").matches(clean) -> clean.replace(".", "")
            else -> clean
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        val cents = value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return cents.takeIf { it > 0 }
    }

    /**
     * Último pagamento antes de [nextPay]: é o dinheiro que precisa durar até lá. A renda é
     * gravada como recorrente a partir dele, para o ciclo já nascer com início e fim —
     * gravar a partir do próximo deixaria o ciclo sem nenhuma entrada e tudo que vence até
     * lá apareceria como falta.
     */
    fun lastPayday(nextPay: LocalDate, today: LocalDate): LocalDate =
        if (!nextPay.isAfter(today)) nextPay else nextPay.minusMonths(1)

    /**
     * Data em que a série recorrente começa. Normalmente é [lastPayday]; mas um recorrente que
     * começa no último dia do mês segue o fim de mês ([monthlyOccurrence]), então "dia 30"
     * gravado em 30/09 viraria 31/10. Nesse caso a série começa num mês anterior em que o dia
     * não é o último, e as ocorrências caem todas no dia certo.
     */
    fun seriesStart(nextPay: LocalDate, today: LocalDate): LocalDate {
        val last = lastPayday(nextPay, today)
        val target = YearMonth.from(nextPay)
        return (0L..12L).map { last.minusMonths(it) }
            .firstOrNull { monthlyOccurrence(it, target) == nextPay && monthlyOccurrence(it, YearMonth.from(last)) == last }
            ?: last
    }

    fun mainIncome(descricao: String, cents: Long, nextPay: LocalDate, today: LocalDate): TransacaoEntity =
        TransacaoEntity(
            data = seriesStart(nextPay, today).toEpochMillis(),
            descricao = descricao.trim().ifEmpty { "Salário" },
            valorCentavos = cents,
            categoria = "Outros",
            contaOrigem = "",
            recorrente = true,
            tipo = TransactionType.Receita.name,
            origem = "manual",
            rendaPrincipal = true,
        )

    /** Conta da semana: um gasto com a data do vencimento (futuro), que entra no ciclo. */
    fun bill(nome: String, cents: Long, date: LocalDate, categoria: String, recorrente: Boolean): TransacaoEntity =
        TransacaoEntity(
            data = date.toEpochMillis(),
            descricao = nome.trim(),
            valorCentavos = cents,
            categoria = categoria,
            contaOrigem = "",
            recorrente = recorrente,
            tipo = TransactionType.Gasto.name,
            origem = "manual",
        )

    /** Dias que o assistente oferece para "contas desta semana": hoje e os próximos 6. */
    fun weekDays(today: LocalDate): List<LocalDate> = (0L..6L).map(today::plusDays)

    /** Sugestão para o próximo recebimento: dia 5 do mês seguinte (ou deste, se ainda não passou). */
    fun suggestedNextPay(today: LocalDate): LocalDate {
        val thisMonth = YearMonth.from(today).atDay(5)
        return if (thisMonth.isAfter(today)) thisMonth else thisMonth.plusMonths(1)
    }
}
