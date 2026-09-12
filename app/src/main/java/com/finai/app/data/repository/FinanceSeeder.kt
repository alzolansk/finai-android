package com.finai.app.data.repository

import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.monthKey
import com.finai.app.domain.toEpochMillis
import java.time.LocalDate

/**
 * Inserts one realistic starter dataset the first time the app runs, so a
 * fresh install isn't a wall of empty screens the prototype never designed
 * for. Every screen still reads exclusively from Room afterwards — this is
 * just the equivalent of a spreadsheet import, standing in until Fase 4
 * (importação de fatura) exists. Everything the user does next (add, edit,
 * delete) replaces this data through the normal manual-entry flows.
 */
object FinanceSeeder {
    suspend fun seedOnce(repository: FinanceRepository, alreadySeeded: Boolean) {
        if (alreadySeeded) return
        val today = LocalDate.now()

        val salario = ContaEntity(
            nome = "Salário", valorCentavos = 850_000, vencimento = today.withDayOfMonth(5.coerceAtMost(today.lengthOfMonth())).toEpochMillis(),
            status = "pendente", tipo = "a_receber", recorrente = true,
        )
        val aluguel = ContaEntity(
            nome = "Aluguel Apartamento", valorCentavos = 180_000, vencimento = dayOrLast(today, 8).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = true,
        )
        val condominio = ContaEntity(
            nome = "Condomínio", valorCentavos = 45_000, vencimento = dayOrLast(today, 8).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = true,
        )
        val internet = ContaEntity(
            nome = "Internet Vivo Fibra", valorCentavos = 11_999, vencimento = dayOrLast(today, 6).toEpochMillis(),
            status = "pago", tipo = "a_pagar", recorrente = true,
        )
        val faturaCartao = ContaEntity(
            nome = "Fatura Nubank", valorCentavos = 438_720, vencimento = dayOrLast(today, 11).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = false,
        )
        listOf(salario, aluguel, condominio, internet, faturaCartao).forEach { repository.salvarConta(it) }

        val objetivos = listOf(
            ObjetivoEntity(
                tipo = "Viagem", nome = "Portugal — 10 dias",
                valorAlvoCentavos = 1_500_000, valorGuardadoCentavos = 550_000,
                prazo = today.plusMonths(7).toEpochMillis(), prioridade = 2,
            ),
            ObjetivoEntity(
                tipo = "Compra", nome = "iPhone 15 Pro Max",
                valorAlvoCentavos = 949_900, valorGuardadoCentavos = 320_000,
                prazo = today.plusMonths(4).toEpochMillis(), prioridade = 3,
            ),
            ObjetivoEntity(
                tipo = "Reserva", nome = "Reserva de emergência",
                valorAlvoCentavos = 2_550_000, valorGuardadoCentavos = 1_240_000,
                prazo = today.plusMonths(14).toEpochMillis(), prioridade = 1,
            ),
        )
        objetivos.forEach { repository.salvarObjetivo(it) }

        val dividas = listOf(
            DividaEntity(
                nome = "Rotativo Nubank", valorOriginalCentavos = 438_720, valorAbertoCentavos = 438_720,
                taxaJurosMensalBasisPoints = 1390, parcelasRestantes = 0, valorParcelaCentavos = 0,
            ),
            DividaEntity(
                nome = "Empréstimo consignado", valorOriginalCentavos = 1_482_600, valorAbertoCentavos = 741_300,
                taxaJurosMensalBasisPoints = 190, parcelasRestantes = 18, valorParcelaCentavos = 41_200,
            ),
            DividaEntity(
                nome = "Parcelamento notebook", valorOriginalCentavos = 310_000, valorAbertoCentavos = 184_000,
                taxaJurosMensalBasisPoints = 0, parcelasRestantes = 6, valorParcelaCentavos = 31_000,
            ),
        )
        dividas.forEach { repository.salvarDivida(it) }

        val mes = monthKey(today)
        val limites = mapOf(
            "Alimentação" to 120_000L, "Transporte" to 60_000L, "Lazer" to 80_000L,
            "Assinaturas" to 20_000L, "Saúde" to 70_000L,
        )
        limites.forEach { (categoria, limite) ->
            repository.salvarLimiteOrcamento(OrcamentoCategoriaEntity(categoria, limite, mes))
        }

        val transacoes = listOf(
            TransacaoEntity(data = today.minusDays(2).toEpochMillis(), descricao = "Supermercado", valorCentavos = 42_000, categoria = "Alimentação", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(4).toEpochMillis(), descricao = "Restaurante", valorCentavos = 18_500, categoria = "Alimentação", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(1).toEpochMillis(), descricao = "iFood", valorCentavos = 6_400, categoria = "Alimentação", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(6).toEpochMillis(), descricao = "Uber", valorCentavos = 8_900, categoria = "Transporte", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(10).toEpochMillis(), descricao = "Combustível", valorCentavos = 22_400, categoria = "Transporte", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(3).toEpochMillis(), descricao = "Cinema", valorCentavos = 8_000, categoria = "Lazer", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(9).toEpochMillis(), descricao = "Show", valorCentavos = 45_000, categoria = "Lazer", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(1).toEpochMillis(), descricao = "Farmácia", valorCentavos = 12_000, categoria = "Saúde", contaOrigem = "Nubank", recorrente = false, origem = "manual"),
            TransacaoEntity(data = today.minusDays(15).toEpochMillis(), descricao = "Plano de saúde", valorCentavos = 41_600, categoria = "Saúde", contaOrigem = "Nubank", recorrente = true, origem = "manual"),
            TransacaoEntity(data = today.minusDays(20).toEpochMillis(), descricao = "ChatGPT Plus", valorCentavos = 9_700, categoria = "Assinaturas", contaOrigem = "Nubank", recorrente = true, origem = "manual"),
        )
        transacoes.forEach { repository.salvarTransacao(it) }

        val assinaturas = listOf(
            AssinaturaEntity(nome = "Amazon Prime", valorCentavos = 1_490, ultimoUso = today.minusDays(74).toEpochMillis(), status = "ativa"),
            AssinaturaEntity(nome = "Alura", valorCentavos = 10_900, ultimoUso = today.minusDays(90).toEpochMillis(), status = "ativa"),
            AssinaturaEntity(nome = "ChatGPT Plus", valorCentavos = 9_700, ultimoUso = today.minusDays(1).toEpochMillis(), status = "ativa"),
        )
        assinaturas.forEach { repository.salvarAssinatura(it) }
    }

    private fun dayOrLast(reference: LocalDate, day: Int): LocalDate =
        reference.withDayOfMonth(day.coerceAtMost(reference.lengthOfMonth()))
}
