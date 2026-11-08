package com.finai.app.data.capture

import android.content.Context
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.capture.CapturedNotification
import com.finai.app.domain.capture.NotificationParser
import com.finai.app.domain.capture.ParseOutcome
import com.finai.app.domain.capture.SuggestionOrigin
import com.finai.app.util.FinaiLog
import kotlinx.coroutines.flow.first

/**
 * O fluxo único de uma notificação lida, igual para a captura real, a notificação de teste e a
 * simulação: interpretar ([NotificationParser], local) → gravar ou reconhecer repetição
 * ([CaptureRepository.register]) → avisar fora do app só se ele não estiver na tela. Nada do
 * texto vai para log, IA ou rede.
 */
class CaptureProcessor(private val context: Context) {
    private val db = FinaiDatabase.get(context)
    private val repository = CaptureRepository(db)

    data class Report(val outcomes: List<ParseOutcome>, val registered: List<CaptureRepository.Registered>)

    suspend fun process(n: CapturedNotification, origin: SuggestionOrigin, testId: Long?): Report {
        val outcomes = NotificationParser.parse(n, knownAccounts())
        val movements = outcomes.filterIsInstance<ParseOutcome.Movement>()
        val registered = if (movements.isEmpty()) emptyList() else repository.register(n, movements, origin, testId)
        if (testId != null) recordTest(testId, outcomes, registered)
        // Só conta, nunca conteúdo (FinaiLog).
        FinaiLog.d(TAG, "notificação processada: ${movements.size} movimentação(ões), ${registered.size} gravada(s)")
        if (origin != SuggestionOrigin.Simulation && !AppVisibility.isVisible) {
            registered.forEach { r ->
                val id = when (r) {
                    is CaptureRepository.Registered.Created -> r.id
                    is CaptureRepository.Registered.Updated -> r.id
                    is CaptureRepository.Registered.Repeated -> null
                } ?: return@forEach
                repository.get(id)?.takeIf { it.state == com.finai.app.domain.capture.SuggestionState.Pending }
                    ?.let { CaptureNotifications.notifySuggestion(context, it) }
            }
        }
        return Report(outcomes, registered)
    }

    /** Contas e cartões já conhecidos, para reconhecer quando a mensagem cita um pelo nome. */
    private suspend fun knownAccounts(): List<String> {
        val transacoes = db.transacaoDao().getAll()
        val cards = EntryOptions.knownCards(db.faturaCartaoDao().getAll(), transacoes)
        val extra = runCatching { FinaiPreferences(context).knownAccounts.first() }.getOrDefault(emptySet())
        return (cards + EntryOptions.recentAccounts(transacoes, cards, extra))
            .filter { it != EntryOptions.FALLBACK_ACCOUNT }.distinctBy { it.lowercase() }
    }

    private suspend fun recordTest(testId: Long, outcomes: List<ParseOutcome>, registered: List<CaptureRepository.Registered>) {
        val first = registered.firstOrNull()
        when {
            first is CaptureRepository.Registered.Created -> repository.setTestStatus(
                testId, TestStatus.SuggestionCreated,
                if (first.possibleRepeat) "Marcada como \"pode ser repetida\": você decide se salva." else "",
                first.id,
            )
            first is CaptureRepository.Registered.Updated -> repository.setTestStatus(testId, TestStatus.SuggestionUpdated, "Nenhuma sugestão nova.", first.id)
            first is CaptureRepository.Registered.Repeated -> repository.setTestStatus(
                testId, TestStatus.Repeated,
                if (first.resolved) "A sugestão já tinha sido resolvida e não foi reaberta." else "Mesma notificação de novo: nenhuma sugestão nova.",
                first.id,
            )
            else -> repository.setTestStatus(
                testId, TestStatus.NotRecognized,
                (outcomes.firstOrNull() as? ParseOutcome.Ignored)?.reason?.label ?: "Sem movimentação identificável",
            )
        }
    }

    private companion object { const val TAG = "CaptureProcessor" }
}
