package com.finai.app.data.repository

import com.finai.app.data.local.FinaiDatabase
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
 */
class ChatRepository(db: FinaiDatabase) {
    private val dao = db.mensagemChatDao()

    val mensagens: Flow<List<ChatMessage>> = dao.observeAll().map { list ->
        list.map { ChatMessage(if (it.papel == PAPEL_IA) ChatRole.Ai else ChatRole.Me, it.texto) }
    }

    suspend fun registrar(role: ChatRole, texto: String) {
        dao.insert(
            MensagemChatEntity(
                papel = if (role == ChatRole.Ai) PAPEL_IA else PAPEL_USUARIO,
                texto = texto,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val PAPEL_IA = "ia"
        const val PAPEL_USUARIO = "usuario"
    }
}
