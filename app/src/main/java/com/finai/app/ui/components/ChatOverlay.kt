package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.ui.theme.FinaiColors

/**
 * Chat do assistente em tela cheia — conversa livre sobre os números reais do
 * usuário, com o histórico vindo do Room ([com.finai.app.data.repository.ChatRepository])
 * e a resposta vindo do [com.finai.app.data.ai.AiRouter].
 *
 * Insets tratados explicitamente (corrigido na Fase 6): a Activity roda em
 * edge-to-edge (`enableEdgeToEdge()` em `MainActivity`), e este overlay cobre a
 * tela inteira sem passar por [FinaiTopBar], que é quem aplica
 * `statusBarsPadding()` nas telas normais. Sem isto, o cabeçalho ficava por
 * baixo da barra de status — o botão de fechar caía embaixo do relógio do
 * sistema e não respondia ao toque. `imePadding` sobe a barra de digitação com
 * o teclado, e `navigationBarsPadding` a mantém acima da barra de gestos.
 */
@Composable
fun ChatOverlay(
    messages: List<ChatMessage>,
    thinking: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestion: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FinaiColors.EmeraldSoftBg, FinaiColors.Background)))
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FinaiColors.Surface.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FinaiColors.Surface)
                    .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("FinAI", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(
                    // O que a IA realmente recebe é o resumo agregado de FinanceUiState
                    // (planning.md §4: minimização do que sai do aparelho) — não o extrato.
                    if (thinking) "analisando seus números..." else "online · lê o resumo dos seus números",
                    fontSize = 11.sp, color = FinaiColors.TextTertiary,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.05f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = FinaiColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty() && !thinking) {
                item { EmptyChatIntro() }
            }
            items(messages) { message -> ChatBubble(message) }
            if (thinking) item { ThinkingBubble() }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FinaiColors.Surface.copy(alpha = 0.8f))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(FinaiFixtures.chatSuggestions) { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                            .background(FinaiColors.Surface)
                            .clickable { onSuggestion(suggestion) }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Text(suggestion, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextBody)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 9.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                    .background(FinaiColors.Surface)
                    .padding(start = 16.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                androidx.compose.material3.TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pergunte sobre suas finanças...", fontSize = 13.sp, color = FinaiColors.TextMuted) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = FinaiColors.TextPrimary),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FinaiColors.Emerald)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val fromMe = message.role == ChatRole.Me
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (fromMe) 18.dp else 5.dp,
                        bottomEnd = if (fromMe) 5.dp else 18.dp,
                    ),
                )
                .background(if (fromMe) FinaiColors.Ink else FinaiColors.Surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (fromMe) {
                Text(message.text, fontSize = 13.sp, lineHeight = 20.sp, color = Color.White)
            } else {
                AiRichText(message.text, fontSize = 13.sp, lineHeight = 20.sp, showHighlights = true)
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp))
            .background(FinaiColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { Dot(FinaiColors.Emerald, size = 7.dp) }
    }
}

/**
 * Estado inicial da conversa. É texto de apresentação da tela, não uma
 * mensagem: o histórico real vem do Room ([com.finai.app.data.repository.ChatRepository])
 * e começa vazio, em vez de já conter uma "resposta" da IA que nunca foi gerada.
 */
@Composable
private fun EmptyChatIntro() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, FinaiColors.BorderHairline, RoundedCornerShape(18.dp))
            .background(FinaiColors.Surface)
            .padding(16.dp),
    ) {
        Text("Assistente FinAI", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Text(
            FinaiFixtures.chatPitch, fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
