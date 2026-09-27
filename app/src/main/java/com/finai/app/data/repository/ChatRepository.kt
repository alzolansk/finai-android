package com.finai.app.data.repository

import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ConversaResumo
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Histórico do chat do assistente, persistido em Room como planning.md §5/§8
 * já previam (`MensagemChat`). A tabela e o DAO existiam desde a Fase 0, mas
 * nada os usava: a conversa vivia só na memória do [com.finai.app.state.AppViewModel]
 * e era descartada a cada restart do processo, começando de novo com uma
 * mensagem fixa de boas-vindas. Agora o Room é a fonte de verdade também aqui.
 *
 * Desde a versão 8 do banco o histórico é dividido em conversas
 * (`conversaId`): antes era uma sequência única desde a instalação, e a IA
 * recebia no prompt mensagens antigas de outros assuntos — com números que já
 * não valiam mais.
 */
class ChatRepository(db: FinaiDatabase) {
    private val dao = db.mensagemChatDao()

    /** Mensagens de uma conversa e o contexto do card que a abriu, se houver. */
    data class Conversa(val id: Long, val mensagens: List<ChatMessage>, val contexto: String?)

    fun conversa(id: Long): Flow<Conversa> = dao.observeConversa(id).map { list ->
        Conversa(
            id = id,
            mensagens = list.map { ChatMessage(if (it.papel == PAPEL_IA) ChatRole.Ai else ChatRole.Me, it.texto) },
            contexto = list.firstNotNullOfOrNull { it.contexto },
        )
    }

    val conversas: Flow<List<ConversaResumo>> = dao.observeConversas()

    suspend fun registrar(conversaId: Long, role: ChatRole, texto: String, contexto: String? = null) {
        dao.insert(
            MensagemChatEntity(
                papel = if (role == ChatRole.Ai) PAPEL_IA else PAPEL_USUARIO,
                texto = texto,
                timestamp = System.currentTimeMillis(),
                conversaId = conversaId,
                contexto = contexto,
            ),
        )
    }

    suspend fun apagarConversa(conversaId: Long) = dao.deleteConversa(conversaId)

    private companion object {
        const val PAPEL_IA = "ia"
        const val PAPEL_USUARIO = "usuario"
    }
}
