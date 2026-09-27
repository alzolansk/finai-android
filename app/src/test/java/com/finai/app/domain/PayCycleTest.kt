package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PayCycleTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun tx(date: LocalDate, cents: Long, tipo: TransactionType, recorrente: Boolean = false, desc: String = "Teste", extra: Boolean = false) =
        TransacaoEntity(
            data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
            contaOrigem = "Carteira", recorrente = recorrente, origem = "manual", tipo = tipo.name, extra = extra)

    private fun salario(cents: Long = 600_000, desde: LocalDate = LocalDate.of(2026, 6, 30)) =
        tx(desde, cents, TransactionType.Receita, recorrente = true, desc = "Salário")

    private fun conta(nome: String, cents: Long, vence: LocalDate, status: String = "pendente") = ContaEntity(
        nome = nome, valorCentavos = cents, vencimento = vence.toEpochMillis(), status = status, tipo = "a_pagar", recorrente = false)

    private fun divida(parcela: Long, restantes: Int, vence: LocalDate) = DividaEntity(
        nome = "Picpay", valorOriginalCentavos = parcela * 8, valorAbertoCentavos = parcela * restantes,
        taxaJurosMensalBasisPoints = 0, parcelasRestantes = restantes, valorParcelaCentavos = parcela,
        parcelasTotais = 8, proximoVencimento = vence.toEpochMillis())

    private fun cycle(
        transacoes: List<TransacaoEntity>, contas: List<ContaEntity> = emptyList(),
        dividas: List<DividaEntity> = emptyList(), on: LocalDate = today,
    ) = PayCycle.of(contas, transacoes, dividas, on)!!

    @Test fun cycleRunsFromLastSalaryToTheNext() {
        val c = cycle(listOf(salario()))
        assertEquals(LocalDate.of(2026, 9, 30), c.inicio)
        assertEquals(LocalDate.of(2026, 10, 30), c.proximo)
        assertEquals(25, c.diasAteProximo)
    }

    /** O caso do colega: as contas de outubro saem do salário de 30/09, não do de 30/10. */
    @Test fun octoberBillsArePaidBySeptemberSalary() {
        val c = cycle(
            transacoes = listOf(salario(), tx(LocalDate.of(2026, 7, 5), 120_000, TransactionType.Gasto, recorrente = true)),
            contas = listOf(conta("Fatura", 200_000, LocalDate.of(2026, 10, 12))),
            dividas = listOf(divida(48_100, 4, LocalDate.of(2026, 10, 10))),
        )
        assertEquals(600_000L, c.entradasCents)
        assertEquals(120_000L, c.jaSaiuCents) // aluguel do dia 5 já passou
        assertEquals(248_100L, c.comprometidoCents)
        assertEquals(231_900L, c.livreCents)
        assertNull(c.shortfall)
    }

    @Test fun billOnNextPaydayBelongsToTheNextCycle() {
        val c = cycle(listOf(salario()), contas = listOf(conta("Fatura", 200_000, LocalDate.of(2026, 10, 30))))
        assertEquals(0L, c.comprometidoCents)
    }

    @Test fun salaryOnThe31stFallsOnLastDayOfShortMonths() {
        val c = cycle(listOf(salario(desde = LocalDate.of(2026, 10, 31))), on = LocalDate.of(2027, 2, 15))
        assertEquals(LocalDate.of(2027, 1, 31), c.inicio)
        assertEquals(LocalDate.of(2027, 2, 28), c.proximo)
    }

    @Test fun positiveTotalCanStillRunShortMidCycle() {
        val c = cycle(
            transacoes = listOf(salario(300_000), tx(LocalDate.of(2026, 10, 20), 200_000, TransactionType.Receita)),
            contas = listOf(conta("Fatura", 400_000, LocalDate.of(2026, 10, 12))),
        )
        assertEquals(100_000L, c.livreCents)
        assertEquals(CycleShortfall(LocalDate.of(2026, 10, 12), 100_000), c.shortfall)
    }

    @Test fun unpaidBillFromLastCycleIsStillOwed() {
        val c = cycle(listOf(salario()), contas = listOf(
            conta("Luz atrasada", 15_000, LocalDate.of(2026, 9, 20)),
            conta("Água paga", 9_000, LocalDate.of(2026, 9, 20), status = "pago"),
        ))
        assertEquals(15_000L, c.comprometidoCents)
    }

    @Test fun largestRecurringIncomeIsTheSalary() {
        val c = cycle(listOf(
            tx(LocalDate.of(2026, 6, 15), 80_000, TransactionType.Receita, recorrente = true, desc = "Aluguel recebido"),
            salario(),
            tx(LocalDate.of(2026, 9, 1), 900_000, TransactionType.Receita, desc = "Bônus", extra = true),
        ))
        assertEquals("Salário", c.salarioNome)
        // O aluguel recebido dia 15 cai no meio do ciclo e também entra.
        assertEquals(680_000L, c.entradasCents)
        assertEquals(80_000L, c.aReceberCents)
    }

    /** O caso real: último salário do estágio e o proporcional são avulsos; o fixo só começa em novembro. */
    @Test fun oneOffSalariesAreRecognizedByName() {
        val lancamentos = listOf(
            tx(LocalDate.of(2026, 9, 26), 255_000, TransactionType.Receita, desc = "Salario"),
            tx(LocalDate.of(2026, 10, 30), 180_000, TransactionType.Receita, desc = "Salário proporcional"),
            salario(desde = LocalDate.of(2026, 11, 30)),
        )
        val setembro = cycle(lancamentos, on = LocalDate.of(2026, 9, 26))
        assertEquals(LocalDate.of(2026, 9, 26), setembro.inicio)
        assertEquals(LocalDate.of(2026, 10, 30), setembro.proximo)
        assertEquals(255_000L, setembro.entradasCents) // o proporcional é do próximo ciclo

        val novembro = cycle(lancamentos, on = LocalDate.of(2026, 11, 5))
        assertEquals(LocalDate.of(2026, 10, 30), novembro.inicio)
        assertEquals(LocalDate.of(2026, 11, 30), novembro.proximo)
    }

    @Test fun thirteenthAndExtrasAreNotSalary() {
        val c = cycle(listOf(
            salario(),
            tx(LocalDate.of(2026, 10, 20), 300_000, TransactionType.Receita, desc = "13º salário"),
            tx(LocalDate.of(2026, 10, 22), 100_000, TransactionType.Receita, desc = "Salário atrasado", extra = true),
        ))
        // Nenhum dos dois corta o ciclo, mas os dois entram como dinheiro dele.
        assertEquals(LocalDate.of(2026, 10, 30), c.proximo)
        assertEquals(1_000_000L, c.entradasCents)
    }

    @Test fun withoutAFutureSalaryTheNextIsEstimated() {
        val c = cycle(listOf(tx(LocalDate.of(2026, 9, 26), 255_000, TransactionType.Receita, desc = "SALÁRIO")))
        assertEquals(LocalDate.of(2026, 10, 26), c.proximo)
        assertEquals(true, c.proximoEstimado)
    }

    @Test fun withoutTheWordTheLargestRecurringIncomeIsUsed() {
        val c = cycle(listOf(tx(LocalDate.of(2026, 6, 30), 600_000, TransactionType.Receita, recorrente = true, desc = "Empresa X")))
        assertEquals(LocalDate.of(2026, 9, 30), c.inicio)
    }

    @Test fun noRecurringIncomeMeansNoCycle() {
        assertNull(PayCycle.of(emptyList(), listOf(tx(today, 50_000, TransactionType.Receita)), emptyList(), today))
    }

    @Test fun beforeFirstSalaryEverythingIsUncovered() {
        val c = cycle(listOf(salario(desde = LocalDate.of(2026, 10, 30))), contas = listOf(conta("Fatura", 50_000, LocalDate.of(2026, 10, 12))))
        assertNull(c.inicio)
        assertEquals(-50_000L, c.livreCents)
    }

    @Test fun payingAnInstallmentKeepsWhatIsFree() {
        val picpay = divida(48_100, 4, LocalDate.of(2026, 10, 10))
        val antes = cycle(listOf(salario()), dividas = listOf(picpay))
        val depois = cycle(
            listOf(salario(), tx(today, 48_100, TransactionType.Gasto)),
            dividas = listOf(DebtSchedule.afterPayment(picpay, today)),
        )
        assertEquals(antes.livreCents, depois.livreCents)
    }
}
