package com.finai.app.debug

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.ADJUSTMENT_LABEL
import com.finai.app.domain.ADJUSTMENT_ORIGIN
import com.finai.app.domain.ADJUSTMENT_TYPE
import com.finai.app.domain.toEpochMillis
import java.time.LocalDate

/**
 * O cenário do protótipo v2 (planning-v2 §5), só no build debug: hoje é 08/11/2026, há R$ 3.480 em
 * conta, a fatura Nubank (R$ 1.320) vence em 10/11, o adiantamento de R$ 2.400 cai em 20/11 e o
 * Picpay vence em 05/12. Os números que a tela mostra têm que ser **iguais** aos do quadro:
 * R$ 3.480 em conta, menor saldo R$ 1.520 em 18/11, livre R$ 1.220 com margem de R$ 300, R$ 101/dia.
 *
 * O protótipo não estava acessível quando isto foi escrito (planning-v2 §11): os valores de
 * academia, condomínio e internet foram escolhidos para somar os R$ 1.960 que o quadro tira do
 * saldo até 18/11. Se o quadro tiver outros valores, ajuste aqui e em `DemoScenarioV2Test`.
 *
 * Carregar no aparelho: `adb shell am broadcast -a com.finai.app.DEBUG_LOAD_DEMO -n com.finai.app/.debug.DemoScenarioReceiver`
 */
object DemoScenarioV2 {
    val today: LocalDate = LocalDate.of(2026, 11, 8)

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day).toEpochMillis()

    /** R$ 3.480 em conta hoje: um ajuste de conciliação, para o saldo ser exato sem histórico. */
    val ajuste = TransacaoEntity(
        id = 1, data = d(11, 8), descricao = ADJUSTMENT_LABEL, valorCentavos = 348_000, categoria = ADJUSTMENT_LABEL,
        contaOrigem = "Nubank", recorrente = false, tipo = ADJUSTMENT_TYPE, origem = ADJUSTMENT_ORIGIN,
    )

    /** Adiantamento (dia 20): a renda principal e a que fecha o período. */
    val adiantamento = TransacaoEntity(
        id = 2, data = d(11, 20), descricao = "Salário · adiantamento", valorCentavos = 240_000, categoria = "Salário",
        contaOrigem = "Nubank", recorrente = true, tipo = "Receita", origem = "manual", rendaPrincipal = true,
        rendaGrupo = "demo", rendaParte = "adiantamento", rendaDivisao = "pct:40",
    )

    /** Saldo do salário (dia 5 do mês seguinte): a primeira ocorrência é em 05/12. */
    val saldoSalario = TransacaoEntity(
        id = 3, data = d(12, 5), descricao = "Salário · saldo", valorCentavos = 360_000, categoria = "Salário",
        contaOrigem = "Nubank", recorrente = true, tipo = "Receita", origem = "manual",
        rendaGrupo = "demo", rendaParte = "saldo", rendaDivisao = "pct:40", competenciaDeslocamento = -1,
    )

    val transacoes = listOf(ajuste, adiantamento, saldoSalario)

    private fun conta(id: Long, nome: String, cents: Long, month: Int, day: Int) = ContaEntity(
        id = id, nome = nome, valorCentavos = cents, vencimento = d(month, day), status = "pendente", tipo = "a_pagar", recorrente = false,
    )

    val contas = listOf(
        conta(1, "Fatura Nubank", 132_000, 11, 10),
        conta(2, "Academia", 18_000, 11, 12),
        conta(3, "Condomínio", 34_000, 11, 15),
        conta(4, "Internet", 12_000, 11, 18),
    )

    val dividas = listOf(
        DividaEntity(
            id = 1, nome = "Picpay", valorOriginalCentavos = 48_100 * 8, valorAbertoCentavos = 48_100 * 5,
            taxaJurosMensalBasisPoints = 0, parcelasRestantes = 5, valorParcelaCentavos = 48_100,
            parcelasTotais = 8, proximoVencimento = d(12, 5), diaVencimento = 5,
        ),
    )

    val objetivos = listOf(
        ObjetivoEntity(id = 1, tipo = "Viagem", nome = "Chile", valorAlvoCentavos = 800_000, valorGuardadoCentavos = 240_000, prazo = d(7, 28, 2027), prioridade = 1),
        ObjetivoEntity(id = 2, tipo = "Compra", nome = "Notebook", valorAlvoCentavos = 600_000, valorGuardadoCentavos = 120_000, prazo = d(3, 28, 2027), prioridade = 2),
        ObjetivoEntity(id = 3, tipo = "Reserva", nome = "Reserva", valorAlvoCentavos = 0, valorGuardadoCentavos = 300_000, prazo = d(12, 31, 2027), prioridade = 3, aporteMensalCentavos = 20_000),
    )
}
