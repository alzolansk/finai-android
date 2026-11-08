package com.finai.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A última conciliação com o saldo do banco (guardada em [com.finai.app.data.prefs.FinaiPreferences]). */
data class ReconciliationState(
    val date: LocalDate,
    /** Saldo do banco informado na ocasião. */
    val balanceCents: Long,
    /** Ajuste lançado (com sinal); zero quando o FinAI já batia com o banco. */
    val adjustmentCents: Long,
)

/** O aviso da Início: pede a conciliação (âmbar) ou confirma que ela foi feita hoje (verde). */
sealed interface ReconciliationNotice {
    /** [days] nulo = nunca conciliou. */
    data class Stale(val days: Int?) : ReconciliationNotice
    data class DoneToday(val adjustmentCents: Long) : ReconciliationNotice
}

/** De quanto em quanto tempo o app pede para conferir com o banco. */
const val RECONCILIATION_STALE_DAYS = 30

object Reconciliation {
    fun notice(last: ReconciliationState?, today: LocalDate): ReconciliationNotice? {
        if (last == null) return ReconciliationNotice.Stale(null)
        if (last.date == today) return ReconciliationNotice.DoneToday(last.adjustmentCents)
        val days = ChronoUnit.DAYS.between(last.date, today).toInt()
        return if (days > RECONCILIATION_STALE_DAYS) ReconciliationNotice.Stale(days) else null
    }

    /** Diferença entre o banco e o FinAI: positivo = o banco tem mais. Nulo se o valor digitado não vale. */
    fun difference(bankCents: Long?, registeredCents: Long): Long? = bankCents?.minus(registeredCents)

    /** Máscara pt-BR enquanto digita: só dígitos viram centavos ("123456" → "1.234,56"); "-" no começo vale. */
    fun mask(raw: String): String {
        val negative = raw.trim().startsWith("-")
        val digits = raw.filter { it.isDigit() }.trimStart('0').take(11)
        if (digits.isEmpty()) return if (negative) "-" else ""
        val padded = digits.padStart(3, '0')
        val int = padded.dropLast(2).reversed().chunked(3).joinToString(".").reversed()
        return (if (negative) "-" else "") + int + "," + padded.takeLast(2)
    }

    /** O valor da máscara em centavos; nulo se vazio ou só o sinal. */
    fun parse(masked: String): Long? {
        val digits = masked.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val cents = digits.toLong()
        return if (masked.trim().startsWith("-")) -cents else cents
    }

    /** O valor em centavos como a máscara o mostra (para pré-preencher). */
    fun format(cents: Long): String = mask((if (cents < 0) "-" else "") + kotlin.math.abs(cents).toString())
}

/** A caixa "Diferença" da conciliação: verde com zero ou valor inválido, vinho se o banco tem menos, azul se tem mais. */
enum class DiffTone { Neutral, BankLess, BankMore }

fun diffTone(diffCents: Long?): DiffTone = when {
    diffCents == null || diffCents == 0L -> DiffTone.Neutral
    diffCents < 0 -> DiffTone.BankLess
    else -> DiffTone.BankMore
}

fun diffNote(diffCents: Long?): String = when {
    diffCents == null -> "Digite o saldo do banco."
    diffCents == 0L -> "Tudo certo. Saldo do FinAI bate com o banco."
    diffCents < 0 -> "O banco tem menos do que o FinAI registrou. Pode ser um gasto não lançado ou uma tarifa."
    else -> "O banco tem mais do que o FinAI registrou. Pode ser uma entrada não lançada."
}
