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
import androidx.compose.foundation.layout.heightIn
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.local.entity.ConversaResumo
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
    conversations: List<ConversaResumo>,
    currentConversationId: Long,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestion: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pendingPurchase: com.finai.app.domain.scenario.PurchaseScenario? = null,
    onRegisterPurchase: (com.finai.app.domain.scenario.PurchaseScenario) -> Unit = {},
    onDiscardPurchase: () -> Unit = {},
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    // Registrado depois do BackHandler central de FinaiApp, então tem prioridade:
    // com a lista aberta, voltar fecha só a lista.
    BackHandler(enabled = showHistory) { showHistory = false }
    val listState = rememberLazyListState()
    LaunchedEffect(currentConversationId, messages.size, thinking, pendingPurchase) {
        val last = messages.size + (if (thinking) 1 else 0) + (if (!thinking && pendingPurchase != null) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
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
                Text(
                    if (showHistory) "Conversas anteriores" else "FinAI",
                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary,
                )
                Text(
                    // O que a IA realmente recebe é o resumo agregado de FinanceUiState
                    // (planning.md §4: minimização do que sai do aparelho) — não o extrato.
                    when {
                        showHistory -> "${conversations.size} " + if (conversations.size == 1) "conversa" else "conversas"
                        thinking -> "analisando seus números..."
                        else -> "lê o resumo dos seus números"
                    },
                    fontSize = 12.sp, color = FinaiColors.TextTertiary,
                )
            }
            HeaderIcon(
                icon = if (showHistory) Icons.AutoMirrored.Filled.ArrowBack else Icons.Outlined.History,
                description = if (showHistory) "Voltar para a conversa" else "Conversas anteriores",
                onClick = { showHistory = !showHistory },
            )
            HeaderIcon(
                icon = Icons.Outlined.AddComment,
                description = "Nova conversa",
                onClick = { onNewConversation(); showHistory = false },
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FinaiColors.TextPrimary.copy(alpha = 0.06f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = FinaiColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        if (showHistory) {
            ConversationHistory(
                conversations = conversations,
                currentConversationId = currentConversationId,
                onOpen = { id -> onOpenConversation(id); showHistory = false },
                onDelete = onDeleteConversation,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty() && !thinking) {
                item { EmptyChatIntro() }
            }
            items(messages) { message -> ChatBubble(message) }
            if (thinking) item { ThinkingBubble() }
            if (!thinking && pendingPurchase != null) {
                item { SimulatedPurchaseCard(pendingPurchase, onRegisterPurchase, onDiscardPurchase) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FinaiColors.Surface.copy(alpha = 0.8f))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            LazyRow(modifier = Modifier.tipTarget("chat.suggestions"), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(FinaiFixtures.chatSuggestions) { suggestion ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
                            .background(FinaiColors.Surface)
                            .clickable { onSuggestion(suggestion) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(suggestion, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextBody)
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
                    placeholder = { Text("Pergunte sobre suas finanças...", fontSize = 12.5.sp, color = FinaiColors.TextMuted) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = FinaiColors.TextPrimary),
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

/**
 * A compra que o chat simulou. Simular não gravou nada; registrar é esta ação separada, com
 * confirmação que lista exatamente o que vai para a Agenda.
 */
@Composable
private fun SimulatedPurchaseCard(
    scenario: com.finai.app.domain.scenario.PurchaseScenario,
    onRegister: (com.finai.app.domain.scenario.PurchaseScenario) -> Unit,
    onDiscard: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FinaiColors.Surface)
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("COMPRA SIMULADA · NADA FOI LANÇADO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextTertiary)
        Text(scenario.label, fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextPrimary, modifier = Modifier.padding(top = 6.dp))
        Row(modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Descartar", onClick = onDiscard, modifier = Modifier.weight(1f))
            ActionButton("Registrar compra", onClick = { confirming = true }, modifier = Modifier.weight(1f), primary = true)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Registrar esta compra?") },
            text = {
                Text(
                    buildString {
                        append(if (scenario.count > 1) "Vão para a Agenda ${scenario.count} gastos, um por parcela:\n" else "Vai para a Agenda um gasto:\n")
                        scenario.toRecords().zip(scenario.payments).take(6).forEach { (r, p) ->
                            append("\n• ${r.descricao}: ${com.finai.app.util.formatBrl0(p.cents / 100.0)} em ${"%02d/%02d/%d".format(p.date.dayOfMonth, p.date.monthValue, p.date.year)}")
                        }
                        if (scenario.count > 6) append("\n• … e mais ${scenario.count - 6}")
                        append("\n\nDá para excluir qualquer um deles na Agenda depois.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; onRegister(scenario) }) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancelar") } },
        )
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
                Text(message.text, fontSize = 12.5.sp, lineHeight = 18.sp, color = Color.White)
            } else {
                AiRichText(message.text, fontSize = 12.5.sp, lineHeight = 18.sp, showHighlights = true)
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = FinaiColors.TextSecondary, modifier = Modifier.size(19.dp))
    }
}

/** Lista de conversas guardadas, a mais recente primeiro. Título = primeira pergunta. */
@Composable
private fun ConversationHistory(
    conversations: List<ConversaResumo>,
    currentConversationId: Long,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ConversaResumo?>(null) }
    if (conversations.isEmpty()) {
        Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                "Nenhuma conversa ainda. As conversas ficam guardadas aqui para você voltar a elas.",
                fontSize = 12.5.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary, textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(conversations, key = { it.conversaId }) { conversa ->
            val current = conversa.conversaId == currentConversationId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FinaiColors.Surface)
                    .border(
                        1.dp,
                        if (current) FinaiColors.EmeraldSoftBorder else FinaiColors.BorderHairline,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onOpen(conversa.conversaId) }
                    .padding(start = 14.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        conversa.titulo ?: "Conversa sem pergunta",
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (if (current) "Aberta · " else "") + conversationDateLabel(conversa.ultima) +
                            " · ${conversa.total} " + if (conversa.total == 1) "mensagem" else "mensagens",
                        fontSize = 12.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                HeaderIcon(Icons.Outlined.DeleteOutline, "Apagar conversa") { pendingDelete = conversa }
            }
        }
    }
    pendingDelete?.let { conversa ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Apagar conversa?") },
            text = { Text("\"${conversa.titulo ?: "Conversa"}\" e todas as mensagens dela serão apagadas.") },
            confirmButton = {
                TextButton(onClick = { onDelete(conversa.conversaId); pendingDelete = null }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

private fun conversationDateLabel(millis: Long): String {
    val dateTime = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val days = java.time.temporal.ChronoUnit.DAYS.between(dateTime.toLocalDate(), java.time.LocalDate.now())
    val time = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    return when (days) {
        0L -> "hoje, $time"
        1L -> "ontem, $time"
        else -> dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
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
        Text("Assistente FinAI", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Text(
            FinaiFixtures.chatPitch, fontSize = 12.sp, lineHeight = 17.sp, color = FinaiColors.TextTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
