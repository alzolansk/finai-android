package com.finai.app.domain.capture

import com.finai.app.domain.importer.MerchantClassifier

/**
 * Problema A: a **mesma notificação** chegando mais de uma vez (reenvio, atualização do texto,
 * notificação agrupada redesenhada). Não é a mesma coisa que duas compras iguais: isso é o
 * problema B ([SuggestionMatcher]), que compara com lançamentos já gravados.
 *
 * O Android dá a cada notificação uma chave (pacote + id + tag) que se mantém em reenvios e
 * atualizações; o parser dá uma assinatura do conteúdo. Regras:
 * 1. mesma chave e mesmo conteúdo → repetição, ignorada;
 * 2. mesma chave, conteúdo diferente, mesma movimentação (valor e quem) → atualização: a
 *    sugestão pendente é atualizada; uma já confirmada ou ignorada **não** reabre;
 * 3. mesma chave com outra movimentação → alguns apps reaproveitam o id: é uma sugestão nova;
 * 4. outra chave com o mesmo conteúdo, do mesmo app, pouco depois → sugestão nova marcada como
 *    "pode ser repetida" (o usuário decide; duas compras iguais seguidas existem);
 * 5. o resto é sugestão nova.
 */
object CaptureDedupe {
    /** Janela da regra 4. */
    const val REPEAT_WINDOW_MILLIS = 10 * 60 * 1000L

    /** O mínimo de uma sugestão já gravada que a decisão precisa. */
    data class Known(
        val id: Long,
        val packageName: String,
        val systemKey: String,
        val signature: String,
        val state: SuggestionState,
        val capturedAt: Long,
        val kind: MovementKind,
        val amountCents: Long?,
        val counterpart: String,
    )

    sealed interface Decision {
        data class Create(val possibleRepeat: Boolean) : Decision
        data class Repeat(val id: Long) : Decision
        data class UpdatePending(val id: Long) : Decision
        /** Atualização de uma sugestão já resolvida: nada muda. */
        data class KeepResolved(val id: Long) : Decision
    }

    fun decide(
        packageName: String,
        systemKey: String,
        signature: String,
        movement: ParsedMovement,
        now: Long,
        known: List<Known>,
    ): Decision {
        val sameKey = known.filter { it.systemKey == systemKey }.sortedByDescending { it.capturedAt }
        sameKey.firstOrNull { it.signature == signature }?.let { return Decision.Repeat(it.id) }
        sameKey.firstOrNull { sameMovement(it, movement) }?.let {
            return if (it.state == SuggestionState.Pending) Decision.UpdatePending(it.id) else Decision.KeepResolved(it.id)
        }
        val repeated = known.any {
            it.packageName == packageName && it.signature == signature && now - it.capturedAt in 0..REPEAT_WINDOW_MILLIS
        }
        return Decision.Create(possibleRepeat = repeated)
    }

    private fun sameMovement(k: Known, m: ParsedMovement): Boolean =
        k.amountCents == m.amountCents && kindFamily(k.kind) == kindFamily(m.kind) &&
            MerchantClassifier.normalizeMerchant(k.counterpart) == MerchantClassifier.normalizeMerchant(m.counterpart)

    /** Uma atualização pode trocar "Compra aprovada" por "Compra no crédito aprovada": é a mesma compra. */
    private fun kindFamily(k: MovementKind): String = when (k) {
        MovementKind.Purchase, MovementKind.CreditPurchase, MovementKind.DebitPurchase, MovementKind.InstallmentPurchase -> "compra"
        else -> k.name
    }
}
