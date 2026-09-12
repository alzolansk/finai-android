package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BillStatusCalculatorTest {

    private val today = LocalDate.of(2026, 3, 21)

    private fun conta(
        dia: Int,
        status: String = "pendente",
        tipo: String = "a_pagar",
    ) = ContaEntity(
        nome = "Conta", valorCentavos = 10_000,
        vencimento = today.withDayOfMonth(dia).toEpochMillis(),
        status = status, tipo = tipo, recorrente = false,
    )

    @Test
    fun `conta vencida e nao paga fica atrasada`() {
        assertEquals(EffectiveBillStatus.ATRASADO, BillStatusCalculator.of(conta(dia = 10), today))
    }

    @Test
    fun `conta que vence hoje tem status proprio`() {
        assertEquals(EffectiveBillStatus.VENCE_HOJE, BillStatusCalculator.of(conta(dia = 21), today))
    }

    @Test
    fun `conta futura fica pendente`() {
        assertEquals(EffectiveBillStatus.PENDENTE, BillStatusCalculator.of(conta(dia = 28), today))
    }

    @Test
    fun `conta paga continua paga mesmo depois do vencimento`() {
        assertEquals(EffectiveBillStatus.PAGO, BillStatusCalculator.of(conta(dia = 10, status = "pago"), today))
    }

    @Test
    fun `conta a receber e sempre prevista, nunca atrasada`() {
        assertEquals(
            EffectiveBillStatus.PREVISTO,
            BillStatusCalculator.of(conta(dia = 5, tipo = "a_receber"), today),
        )
    }
}
