package com.finai.app.data.notifications

import com.finai.app.data.local.dao.NotificacaoEnviadaDao
import com.finai.app.data.local.entity.NotificacaoEnviadaEntity
import java.time.LocalDate

/**
 * "Evite notificações repetidas sobre o mesmo evento" (planning.md §9/§10):
 * cada evento ([com.finai.app.domain.FinanceAlert.id] /
 * [com.finai.app.domain.BehaviorPattern.id]) só gera uma notificação por dia
 * — a granularidade exata do critério de aceite da Fase 5 ("no máximo as
 * notificações relevantes daquele dia, não uma por hora"). Uma conta que
 * segue atrasada, ou um orçamento que segue estourado, volta a notificar no
 * dia seguinte (o id continua "existindo" enquanto o problema não é
 * resolvido); uma vez resolvido (conta paga, orçamento normalizado), o id
 * some da lista que [AlertCalculator] devolve e nunca mais notifica sozinho.
 *
 * Backing store é Room (`notificacoes_enviadas`), não DataStore: é uma tabela
 * de fato (uma linha por evento), consistente com o resto do app usar Room
 * como fonte de verdade (planning.md §5).
 */
class NotificationDedupeStore(private val dao: NotificacaoEnviadaDao) {

    suspend fun shouldSend(chave: String, today: LocalDate = LocalDate.now()): Boolean {
        val registro = dao.find(chave) ?: return true
        return registro.ultimoEnvio != today.toString()
    }

    suspend fun markSent(chave: String, today: LocalDate = LocalDate.now()) {
        dao.upsert(NotificacaoEnviadaEntity(chave, today.toString()))
    }

    /** Descarta registros de eventos que não estão mais ativos — evita a tabela crescer para sempre. */
    suspend fun pruneExcept(activeKeys: List<String>) {
        dao.removerExceto(activeKeys)
    }
}
