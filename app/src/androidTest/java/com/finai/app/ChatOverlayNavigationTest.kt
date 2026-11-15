package com.finai.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.finai.app.data.local.entity.ConversaResumo
import com.finai.app.data.local.entity.MemoriaEntity
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.ui.components.ChatOverlay
import com.finai.app.ui.theme.FinaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatOverlayNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun memoryAndHistoryOpenAndReturnToTheChatWithExistingData() {
        var opened: Long? = null
        compose.setContent {
            FinaiTheme {
                ChatOverlay(
                    messages = listOf(ChatMessage(ChatRole.Me, "Mensagem da conversa atual")),
                    thinking = false,
                    draft = "Rascunho preservado",
                    conversations = listOf(ConversaResumo(7, 100, 200, 2, "Conversa guardada")),
                    currentConversationId = 7,
                    memory = listOf(MemoriaEntity(1, "Academia é prioridade", "decisao", 100, "chat")),
                    onDraftChange = {}, onSend = {}, onSuggestion = {}, onNewConversation = {},
                    onOpenConversation = { opened = it }, onDeleteConversation = {}, onClose = {},
                )
            }
        }
        repeat(3) {
            compose.onNode(hasText("Sei sobre você e acompanho", substring = true)).performClick()
            compose.onNodeWithText("ACOMPANHAMENTOS").assertExists()
            compose.onNodeWithText("Academia é prioridade").assertExists()
            compose.onNodeWithText("Mensagem da conversa atual").assertDoesNotExist()
            compose.onNode(hasText("Sei sobre você e acompanho", substring = true)).performClick()
            compose.onNodeWithText("Mensagem da conversa atual").assertExists()
            compose.onNodeWithText("Rascunho preservado").assertExists()

            compose.onNodeWithContentDescription("Conversas anteriores").performClick()
            compose.onNodeWithText("Conversa guardada").assertExists()
            compose.onNodeWithContentDescription("Voltar para a conversa").performClick()
            compose.onNodeWithText("Mensagem da conversa atual").assertExists()
        }
        compose.onNodeWithContentDescription("Conversas anteriores").performClick()
        compose.onNodeWithText("Conversa guardada").performClick()
        compose.runOnIdle { assertEquals(7L, opened) }
        compose.onNodeWithText("Mensagem da conversa atual").assertExists()
    }

    @Test fun emptyPanelsCanBeOpenedAndSwitchedRepeatedly() {
        compose.setContent {
            FinaiTheme {
                ChatOverlay(
                    messages = emptyList(), thinking = false, draft = "",
                    conversations = emptyList(), currentConversationId = 0,
                    onDraftChange = {}, onSend = {}, onSuggestion = {}, onNewConversation = {},
                    onOpenConversation = {}, onDeleteConversation = {}, onClose = {},
                )
            }
        }
        repeat(3) {
            compose.onNode(hasText("Sei sobre você e acompanho", substring = true)).performClick()
            compose.onNodeWithText("Nada guardado ainda.").assertExists()
            // Abrir o histórico enquanto a memória está aberta também recompõe o conteúdo.
            compose.onNodeWithContentDescription("Conversas anteriores").performClick()
            compose.onNode(hasText("Nenhuma conversa ainda.", substring = true)).assertExists()
            compose.onNodeWithContentDescription("Voltar para a conversa").performClick()
            compose.onNodeWithText("Nada guardado ainda.").assertExists()
            compose.onNode(hasText("Sei sobre você e acompanho", substring = true)).performClick()
            compose.onNodeWithContentDescription("Enviar").assertExists()
        }
    }
}
