package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity

/**
 * Quando uma meta está concluída e uma dívida está quitada — e a transição
 * entre os estados, que é o que dispara a comemoração e grava a data.
 *
 * A data ([ObjetivoEntity.concluidoEm]/[DividaEntity.quitadaEm]) é o que a
 * tela mostra no histórico; o estado em si vem dos valores, então editar o
 * alvo para cima reabre a meta sem precisar de flag.
 */
object Completion {

    fun isDone(objetivo: ObjetivoEntity): Boolean =
        objetivo.valorAlvoCentavos > 0 && objetivo.valorGuardadoCentavos >= objetivo.valorAlvoCentavos

    /**
     * Com contrato de parcelas, quitada = nenhuma parcela restante (o saldo pode
     * sobrar alguns centavos de juros estimados). Sem contrato, saldo zerado.
     */
    fun isPaid(divida: DividaEntity): Boolean =
        divida.valorAbertoCentavos <= 0 || (divida.parcelasTotais > 0 && divida.parcelasRestantes <= 0)

    /** Aplica a data de conclusão a uma meta salva; [celebrate] = acabou de cruzar o alvo. */
    data class GoalUpdate(val objetivo: ObjetivoEntity, val celebrate: Boolean)

    fun onGoalSaved(before: ObjetivoEntity?, after: ObjetivoEntity, now: Long): GoalUpdate = when {
        !isDone(after) -> GoalUpdate(after.copy(concluidoEm = null), celebrate = false)
        before != null && isDone(before) -> GoalUpdate(after.copy(concluidoEm = before.concluidoEm ?: now), celebrate = false)
        // Meta criada já cheia não é conquista nova: marca como concluída sem festa.
        else -> GoalUpdate(after.copy(concluidoEm = now), celebrate = before != null)
    }

    data class DebtUpdate(val divida: DividaEntity, val celebrate: Boolean)

    fun onDebtSaved(before: DividaEntity?, after: DividaEntity, now: Long): DebtUpdate = when {
        !isPaid(after) -> DebtUpdate(after.copy(quitadaEm = null), celebrate = false)
        before != null && isPaid(before) -> DebtUpdate(after.copy(quitadaEm = before.quitadaEm ?: now), celebrate = false)
        else -> DebtUpdate(
            // Quitada de vez: nada em aberto, nenhuma parcela a projetar na Agenda.
            after.copy(valorAbertoCentavos = 0, parcelasRestantes = 0, proximoVencimento = null, quitadaEm = now),
            celebrate = before != null,
        )
    }
}

/** Comemoração mostrada por cima de tudo ao concluir uma meta ou quitar uma dívida. */
data class Celebration(
    val style: CelebrationStyle,
    val title: String,
    val name: String,
    val detail: String,
)

enum class CelebrationStyle { Travel, Confetti, DebtFree }

fun celebrationStyleFor(goalKind: String): CelebrationStyle =
    if (goalKind.trim().equals("Viagem", ignoreCase = true)) CelebrationStyle.Travel else CelebrationStyle.Confetti
