package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import java.time.LocalDate

/**
 * Regras do formulário de dívida (`DebtEntryScreen`), fora do Composable para serem
 * testáveis. O usuário descreve a dívida como aparece no contrato — "24x de R$ 550, já
 * paguei 5, vence dia 10" — e daqui sai a [DividaEntity] com restantes/saldo derivados.
 */
object DebtEntry {
    val suggestions = listOf("Empréstimo", "Financiamento", "Cartão de crédito", "Cheque especial", "Consignado")

    /** "2,5" / "2.5" → 250 bp. Vazio é 0% (juros é opcional). Nulo se inválido. */
    fun parseRateBp(text: String): Int? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return 0
        val v = t.toDoubleOrNull() ?: return null
        if (v < 0 || v > 100) return null
        return Math.round(v * 100).toInt()
    }

    fun rateText(bp: Int): String = if (bp == 0) "" else
        (bp / 100.0).toString().removeSuffix(".0").replace('.', ',')

    fun restantes(total: Int, pagas: Int): Int = (total - pagas).coerceAtLeast(0)

    /** Saldo devedor quando o usuário não informa: parcelas restantes × valor da parcela. */
    fun saldoEstimadoCents(parcelaCents: Long, total: Int, pagas: Int): Long = parcelaCents * restantes(total, pagas)

    fun canSave(
        nome: String, parcelada: Boolean, valorCents: Long, total: Int, pagas: Int,
        proximoVencimento: LocalDate?, rateText: String,
    ): Boolean {
        if (nome.isBlank() || valorCents <= 0 || parseRateBp(rateText) == null) return false
        if (!parcelada) return true
        return total in 1..600 && pagas in 0 until total && proximoVencimento != null
    }

    /**
     * [valorCents] é o valor da parcela (parcelada) ou o saldo em aberto (sem parcelas).
     * [saldoCentsOverride] é o saldo devedor informado à mão numa dívida parcelada (juros
     * embutidos, quitação antecipada...); nulo usa [saldoEstimadoCents].
     */
    fun build(
        initial: DividaEntity?,
        nome: String,
        parcelada: Boolean,
        valorCents: Long,
        total: Int,
        pagas: Int,
        proximoVencimento: LocalDate?,
        rateText: String,
        saldoCentsOverride: Long?,
    ): DividaEntity {
        val rateBp = parseRateBp(rateText) ?: 0
        val base = initial ?: DividaEntity(
            nome = "", valorOriginalCentavos = 0, valorAbertoCentavos = 0,
            taxaJurosMensalBasisPoints = 0, parcelasRestantes = 0, valorParcelaCentavos = 0,
        )
        return if (parcelada) {
            val saldo = saldoCentsOverride?.takeIf { it > 0 } ?: saldoEstimadoCents(valorCents, total, pagas)
            base.copy(
                nome = nome.trim(),
                // Editar não reescreve o valor original que o usuário já tinha — é o que a barra
                // de progresso da tela Dívidas usa como referência.
                valorOriginalCentavos = base.valorOriginalCentavos.takeIf { initial != null && it > 0 }
                    ?: (valorCents * total).coerceAtLeast(saldo),
                valorAbertoCentavos = saldo,
                taxaJurosMensalBasisPoints = rateBp,
                parcelasRestantes = restantes(total, pagas),
                parcelasTotais = total,
                valorParcelaCentavos = valorCents,
                proximoVencimento = proximoVencimento?.toEpochMillis(),
                // Mesma data de antes (só editou outro campo): mantém o dia combinado, que pode
                // ser 29–31 mesmo com o próximo vencimento caindo em 28/02.
                diaVencimento = proximoVencimento?.let { date ->
                    base.diaVencimento.takeIf { initial?.proximoVencimento == date.toEpochMillis() }
                        ?: date.dayOfMonth
                },
            )
        } else {
            base.copy(
                nome = nome.trim(),
                valorOriginalCentavos = base.valorOriginalCentavos.takeIf { initial != null && it >= valorCents }
                    ?: valorCents,
                valorAbertoCentavos = valorCents,
                taxaJurosMensalBasisPoints = rateBp,
                parcelasRestantes = 0,
                parcelasTotais = 0,
                valorParcelaCentavos = 0,
                proximoVencimento = null,
                diaVencimento = null,
            )
        }
    }
}
