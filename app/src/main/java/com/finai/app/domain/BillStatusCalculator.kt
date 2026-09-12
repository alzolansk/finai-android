package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import java.time.LocalDate

/** Status efetivo de uma conta — planning.md §3.2 (pago, pendente, atrasado, vence hoje, previsto). */
enum class EffectiveBillStatus { PAGO, ATRASADO, VENCE_HOJE, PENDENTE, PREVISTO }

/**
 * Deriva o status da conta da data de vencimento em vez de confiar no que
 * está gravado em [ContaEntity.status] (planning.md §6: status de conta é
 * cálculo determinístico local, não dado fixo). O campo do banco guarda
 * apenas o fato que o usuário informa e que a data não revela — se a conta
 * já foi paga; "atrasado"/"vence_hoje" mudam sozinhos com o passar do dia e
 * por isso nunca são persistidos: eram valores mortos no schema, e a Agenda
 * mostrava "Pendente" numa conta vencida há meses.
 */
object BillStatusCalculator {
    fun of(conta: ContaEntity, today: LocalDate = LocalDate.now()): EffectiveBillStatus {
        if (conta.status == "pago") return EffectiveBillStatus.PAGO
        if (conta.tipo == "a_receber") return EffectiveBillStatus.PREVISTO
        val vencimento = conta.vencimento.toLocalDate()
        return when {
            vencimento.isBefore(today) -> EffectiveBillStatus.ATRASADO
            vencimento.isEqual(today) -> EffectiveBillStatus.VENCE_HOJE
            else -> EffectiveBillStatus.PENDENTE
        }
    }
}
