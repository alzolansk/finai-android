package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompletionTest {

    private val goal = ObjetivoEntity(id = 1, tipo = "Viagem", nome = "Italia", valorAlvoCentavos = 100_000, valorGuardadoCentavos = 90_000, prazo = 0, prioridade = 1)

    @Test
    fun `aporte que alcanca o alvo conclui e comemora uma vez`() {
        val first = Completion.onGoalSaved(goal, goal.copy(valorGuardadoCentavos = 100_000), now = 42)
        assertTrue(first.celebrate)
        assertEquals(42L, first.objetivo.concluidoEm)
        // Aporte depois de concluída: continua concluída, mesma data, sem nova festa.
        val second = Completion.onGoalSaved(first.objetivo, first.objetivo.copy(valorGuardadoCentavos = 130_000), now = 99)
        assertFalse(second.celebrate)
        assertEquals(42L, second.objetivo.concluidoEm)
    }

    @Test
    fun `subir o alvo reabre a meta`() {
        val done = goal.copy(valorGuardadoCentavos = 100_000, concluidoEm = 42)
        val reopened = Completion.onGoalSaved(done, done.copy(valorAlvoCentavos = 200_000), now = 99)
        assertNull(reopened.objetivo.concluidoEm)
        assertFalse(Completion.isDone(reopened.objetivo))
    }

    @Test
    fun `ultima parcela quita a divida e zera o saldo`() {
        val divida = DividaEntity(
            id = 7, nome = "Tablet", valorOriginalCentavos = 264_000, valorAbertoCentavos = 22_050,
            taxaJurosMensalBasisPoints = 0, parcelasRestantes = 1, valorParcelaCentavos = 22_000, parcelasTotais = 12,
            proximoVencimento = LocalDate.of(2026, 10, 10).toEpochMillis(),
        )
        val update = Completion.onDebtSaved(divida, DebtSchedule.afterPayment(divida, LocalDate.of(2026, 10, 10)), now = 5)
        assertTrue(update.celebrate)
        assertEquals(0L, update.divida.valorAbertoCentavos)
        assertEquals(5L, update.divida.quitadaEm)
    }

    @Test
    fun `excluir o gasto do pagamento desfaz a parcela`() {
        val divida = DividaEntity(
            id = 7, nome = "Tablet", valorOriginalCentavos = 264_000, valorAbertoCentavos = 88_000,
            taxaJurosMensalBasisPoints = 0, parcelasRestantes = 4, valorParcelaCentavos = 22_000, parcelasTotais = 12,
            proximoVencimento = LocalDate.of(2026, 10, 27).toEpochMillis(),
        )
        val paid = DebtSchedule.afterPayment(divida, LocalDate.of(2026, 9, 27))
        val payment = TransacaoEntity(
            data = LocalDate.of(2026, 9, 27).toEpochMillis(), descricao = "Tablet · parcela 9 de 12", valorCentavos = 22_000,
            categoria = DebtSchedule.PAYMENT_CATEGORY, contaOrigem = "", recorrente = false, origem = DebtSchedule.PAYMENT_ORIGIN,
        )
        assertEquals("Tablet", DebtSchedule.debtNameOf(payment))
        val undone = DebtSchedule.undoPayment(paid, payment)
        assertEquals(divida.parcelasRestantes, undone.parcelasRestantes)
        assertEquals(divida.valorAbertoCentavos, undone.valorAbertoCentavos)
        assertEquals(divida.proximoVencimento, undone.proximoVencimento)
    }

    @Test
    fun `viagem ganha aviao, o resto confete`() {
        assertEquals(CelebrationStyle.Travel, celebrationStyleFor("Viagem"))
        assertEquals(CelebrationStyle.Confetti, celebrationStyleFor("Compra"))
    }
}
