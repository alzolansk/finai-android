package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Regras novas da Fase 7 (itens 3, 5 e 6): situação da Início, renda principal, dia da dívida, assistente, dicas. */
class Fase7ClarezaTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun receita(date: LocalDate, cents: Long, desc: String, recorrente: Boolean = true, principal: Boolean = false) = TransacaoEntity(
        data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
        contaOrigem = "", recorrente = recorrente, origem = "manual", tipo = TransactionType.Receita.name, rendaPrincipal = principal,
    )

    private fun gasto(date: LocalDate, cents: Long, desc: String = "Conta") = TransacaoEntity(
        data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Moradia",
        contaOrigem = "", recorrente = false, origem = "manual", tipo = TransactionType.Gasto.name,
    )

    // ── Renda principal (item 5) ──────────────────────────────────

    @Test fun markedMainIncomeBeatsTheNameAndTheLargestRecurring() {
        val lancamentos = listOf(
            receita(LocalDate.of(2026, 9, 30), 900_000, "Aluguel recebido"),
            receita(LocalDate.of(2026, 9, 26), 300_000, "Salário"),
            receita(LocalDate.of(2026, 9, 20), 450_000, "Pró-labore", principal = true),
        )
        val c = PayCycle.of(emptyList(), lancamentos, emptyList(), today)!!
        assertEquals("Pró-labore", c.salarioNome)
        assertEquals(LocalDate.of(2026, 9, 20), c.inicio)
        assertEquals(LocalDate.of(2026, 10, 20), c.proximo)
        assertTrue(c.salarioDefinido)
    }

    @Test fun deducedIncomeIsFlaggedAsNotDefined() {
        val c = PayCycle.of(emptyList(), listOf(receita(LocalDate.of(2026, 9, 10), 400_000, "Renda mensal")), emptyList(), today)!!
        assertFalse(c.salarioDefinido)
        val e = SpendExplanation.of(SafeToSpendCalculator.fromCycle(c, 0), c, today)
        assertTrue(e.missing.first().contains("Marcar como renda principal"))
    }

    @Test fun extraIsNeverMainIncomeEvenIfMarked() {
        val bonus = receita(LocalDate.of(2026, 9, 10), 400_000, "Bônus", principal = true).copy(extra = true)
        assertFalse(PayCycle.isMainIncome(bonus))
    }

    // ── Dia combinado da dívida (item 5) ──────────────────────────

    private fun loan(dia: Int?, due: LocalDate) = DividaEntity(
        id = 1, nome = "Empréstimo", valorOriginalCentavos = 600_000, valorAbertoCentavos = 200_000,
        taxaJurosMensalBasisPoints = 0, parcelasRestantes = 4, valorParcelaCentavos = 50_000,
        parcelasTotais = 12, proximoVencimento = due.toEpochMillis(), diaVencimento = dia,
    )

    @Test fun day29ContractReturnsTo29AfterFebruary() {
        val d = loan(29, LocalDate.of(2027, 1, 29))
        assertEquals(LocalDate.of(2027, 2, 28), DebtSchedule.installmentInMonth(d, YearMonth.of(2027, 2), today)!!.vencimento)
        assertEquals(LocalDate.of(2027, 3, 29), DebtSchedule.installmentInMonth(d, YearMonth.of(2027, 3), today)!!.vencimento)
        val afterJan = DebtSchedule.afterPayment(d, today)
        assertEquals(LocalDate.of(2027, 2, 28), afterJan.proximoVencimento!!.toLocalDate())
        val afterFeb = DebtSchedule.afterPayment(afterJan, today)
        // Antes: 31/03 (o app achava que era "fim de mês"). Agora volta ao dia combinado.
        assertEquals(LocalDate.of(2027, 3, 29), afterFeb.proximoVencimento!!.toLocalDate())
        assertEquals(29, afterFeb.diaVencimento)
    }

    @Test fun undoingFebruaryPaymentGoesBackTo29January() {
        val afterJan = DebtSchedule.afterPayment(loan(29, LocalDate.of(2027, 1, 29)), today)
        val payment = gasto(LocalDate.of(2027, 1, 29), 50_000, "Empréstimo · parcela 9 de 12").copy(origem = DebtSchedule.PAYMENT_ORIGIN)
        assertEquals(LocalDate.of(2027, 1, 29), DebtSchedule.undoPayment(afterJan, payment).proximoVencimento!!.toLocalDate())
    }

    @Test fun oldDebtWithoutContractDayKeepsTheMonthEndRule() {
        val d = loan(null, LocalDate.of(2026, 9, 30))
        assertEquals(LocalDate.of(2026, 10, 31), DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 10), today)!!.vencimento)
    }

    @Test fun lastDayOfMonthCountsAsDay31() {
        assertEquals(31, DebtSchedule.contractDayOf(LocalDate.of(2026, 9, 30)))
        assertEquals(29, DebtSchedule.contractDayOf(LocalDate.of(2026, 10, 29)))
    }

    @Test fun debtEntryKeepsTheContractDayWhenTheDateDidNotChange() {
        val afterJan = DebtSchedule.afterPayment(loan(29, LocalDate.of(2027, 1, 29)), today)
        val edited = DebtEntry.build(
            initial = afterJan, nome = "Empréstimo renomeado", parcelada = true, valorCents = 50_000, total = 12, pagas = 9,
            proximoVencimento = LocalDate.of(2027, 2, 28), rateText = "", saldoCentsOverride = null,
        )
        assertEquals(29, edited.diaVencimento)
        val moved = DebtEntry.build(
            initial = afterJan, nome = "Empréstimo", parcelada = true, valorCents = 50_000, total = 12, pagas = 9,
            proximoVencimento = LocalDate.of(2027, 2, 10), rateText = "", saldoCentsOverride = null,
        )
        assertEquals(10, moved.diaVencimento)
    }

    // ── Situação da Início (item 3) ───────────────────────────────

    @Test fun situationShowsFreeUntilPaydayAsTheMainNumber() {
        val c = PayCycle.of(
            emptyList(),
            listOf(receita(LocalDate.of(2026, 9, 25), 500_000, "Salário", principal = true), gasto(LocalDate.of(2026, 10, 10), 100_000)),
            emptyList(), today,
        )!!
        val safe = SafeToSpendCalculator.fromCycle(c, 50_000)
        val s = HomeSituation.of(safe, c)
        assertEquals("LIVRE ATÉ O SALÁRIO · 25/10", s.label)
        assertEquals(safe.slackThisMonthCents, s.mainCents)
        // O mesmo número da linha "Livre até o salário" da folha "Entenda este valor".
        val explain = SpendExplanation.of(safe, c, today)
        assertEquals(s.mainCents, explain.steps.last { it.label == "Livre até o salário" }.cents)
        assertEquals(SituationAction.CanIBuy, s.action)
        assertFalse(s.shortfall)
    }

    @Test fun shortfallTurnsTheNumberIntoWhatIsMissing() {
        val c = PayCycle.of(
            emptyList(),
            listOf(receita(LocalDate.of(2026, 9, 30), 100_000, "Salário", principal = true), gasto(LocalDate.of(2026, 10, 10), 300_000)),
            emptyList(), today,
        )!!
        val s = HomeSituation.of(SafeToSpendCalculator.fromCycle(c, 0), c)
        assertTrue(s.shortfall)
        assertEquals(200_000L, s.mainCents)
        assertEquals(SituationAction.SeeCommitments, s.action)
        assertEquals("Falta dinheiro antes do próximo salário", s.headline)
    }

    @Test fun withoutIncomeTheActionIsToInformIt() {
        val safe = SafeToSpendCalculator.calculate(emptyList(), emptyList(), 0, today)
        val s = HomeSituation.of(safe, null)
        assertEquals(SituationAction.AddIncome, s.action)
        assertEquals("LIVRE ATÉ O FIM DO MÊS", s.label)
    }

    // ── Assistente inicial (item 6) ───────────────────────────────

    @Test fun parsesBrazilianAmounts() {
        assertEquals(350_000L, InitialSetup.parseAmountCents("3.500"))
        assertEquals(350_050L, InitialSetup.parseAmountCents("R$ 3.500,50"))
        assertEquals(350_000L, InitialSetup.parseAmountCents("3500,00"))
        assertNull(InitialSetup.parseAmountCents("abc"))
        assertNull(InitialSetup.parseAmountCents("0"))
    }

    @Test fun wizardIncomeStartsTheCycleAtTheLastPayday() {
        val income = InitialSetup.mainIncome("", 500_000, LocalDate.of(2026, 10, 30), today)
        assertTrue(income.rendaPrincipal && income.recorrente)
        assertEquals("Salário", income.descricao)
        val c = PayCycle.of(emptyList(), listOf(income), emptyList(), today)!!
        assertEquals(LocalDate.of(2026, 9, 30), c.inicio)
        assertEquals(LocalDate.of(2026, 10, 30), c.proximo)
        assertEquals(500_000L, c.entradasCents)
    }

    @Test fun day31PaydayStillFollowsTheMonthEnd() {
        val c = PayCycle.of(emptyList(), listOf(InitialSetup.mainIncome("Salário", 500_000, LocalDate.of(2026, 10, 31), today)), emptyList(), today)!!
        assertEquals(LocalDate.of(2026, 9, 30), c.inicio)
        assertEquals(LocalDate.of(2026, 10, 31), c.proximo)
    }

    @Test fun paydayTodayIsTheStartOfTheCycle() {
        assertEquals(today, InitialSetup.lastPayday(today, today))
    }

    // ── Dicas por tela (item 6) ───────────────────────────────────

    @Test fun tipsSkipMissingTargetsAndSeenScreens() {
        val home = TipScript.visibleTips(TipScript.HOME, setOf("home.situation", "nav.add"), emptySet())
        assertEquals(listOf("home.situation", "nav.add"), home.map { it.targetId })
        assertTrue(TipScript.visibleTips(TipScript.HOME, setOf("home.situation"), setOf(TipScript.HOME)).isEmpty())
        assertTrue(TipScript.visibleTips(TipScript.AGENDA, emptySet(), emptySet()).isEmpty())
    }

    @Test fun everyScreenHasAScript() {
        TipScript.screens.forEach { assertTrue(it, TipScript.forScreen(it).isNotEmpty()) }
    }
}
