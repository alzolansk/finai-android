package com.finai.app.util

import java.text.NumberFormat
import java.util.Locale

private val ptBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()

/** "R$ 74,30" — two decimals, pt-BR grouping/decimal separators. */
fun formatBrl(value: Double): String {
    val fmt = NumberFormat.getNumberInstance(ptBr).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "R$ " + fmt.format(value)
}

/** "R$ 8.500" — no decimals, matches the prototype's BRL0() helper. */
fun formatBrl0(value: Number): String {
    val fmt = NumberFormat.getNumberInstance(ptBr).apply {
        maximumFractionDigits = 0
    }
    return "R$ " + fmt.format(value)
}
