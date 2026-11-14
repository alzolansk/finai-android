package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import java.time.LocalDate

/**
 * Dia em que o dinheiro da conta saiu (ou entrou): o dia gravado ao marcar paga ([ContaEntity.pagoEm]).
 * Conta paga antes da v18 não tem o dia: vale o vencimento, ou hoje se ele ainda não chegou (o
 * dinheiro já saiu, só não se sabe quando). Nulo para conta em aberto.
 */
fun ContaEntity.paidOn(today: LocalDate): LocalDate? {
    if (status != "pago") return null
    val due = vencimento.toLocalDate()
    return pagoEm?.toLocalDate()?.let { minOf(it, today) } ?: minOf(due, today)
}
