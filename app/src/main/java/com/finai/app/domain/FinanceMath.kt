package com.finai.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Small shared helpers behind every calculator in this package. Nothing here
 * calls out to a network or an AI provider — planning.md §6 keeps every
 * number that can be derived from the user's own data local and
 * deterministic; only the natural-language explanation of a number is IA's
 * job, starting Fase 2.
 */

private val zone: ZoneId = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun LocalDate.toEpochMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** [start, endExclusive) millis range covering the calendar month containing [date]. */
fun monthRangeMillis(date: LocalDate = LocalDate.now()): LongRange {
    val month = YearMonth.from(date)
    val start = month.atDay(1).toEpochMillis()
    val end = month.plusMonths(1).atDay(1).toEpochMillis()
    return start until end
}

fun monthKey(date: LocalDate = LocalDate.now()): String = YearMonth.from(date).toString() // "yyyy-MM"

val MONTH_NAMES_PT = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)

val MONTH_ABBREV_PT = listOf(
    "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez",
)

/** "Julho de 2026" — matches the prototype's goal-eta copy. */
fun formatMonthYearLong(date: LocalDate): String = "${MONTH_NAMES_PT[date.monthValue - 1]} de ${date.year}"

/** "Ago 2026" — matches the prototype's debt-free-by copy. */
fun formatMonthYearShort(date: LocalDate): String = "${MONTH_ABBREV_PT[date.monthValue - 1]} ${date.year}"

/** Whole months between "now" and [target], floored at 1 so a due-this-month goal doesn't divide by zero. */
fun monthsUntil(target: LocalDate, today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)).coerceAtLeast(1)

fun centsToReais(cents: Long): Double = cents / 100.0
