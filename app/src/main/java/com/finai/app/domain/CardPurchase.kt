package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.util.UUID

data class CardPayment(val card: String, val firstDueDate: LocalDate, val installments: Int = 1)

object CardPurchase {
    fun schedule(entry: TransacaoEntity, payment: CardPayment): List<Pair<LocalDate, TransacaoEntity>> {
        require(entry.id == 0L && entry.tipo == TransactionType.Gasto.name && !entry.recorrente)
        require(payment.card.isNotBlank() && payment.installments in 1..120)
        require(entry.valorCentavos >= payment.installments)
        require(!payment.firstDueDate.isBefore(entry.data.toLocalDate()))
        val purchaseId = UUID.randomUUID().toString()
        val base = entry.valorCentavos / payment.installments
        val remainder = entry.valorCentavos % payment.installments
        return (0 until payment.installments).map { index ->
            payment.firstDueDate.plusMonths(index.toLong()) to entry.copy(
                contaOrigem = payment.card.trim(),
                valorCentavos = base + if (index < remainder) 1 else 0,
                compraId = purchaseId, parcelaNumero = index + 1, parcelasTotal = payment.installments,
                descricao = entry.descricao + if (payment.installments > 1) " · parcela ${index + 1}/${payment.installments}" else "",
                recorrente = false,
            )
        }
    }
}
