package com.finai.app.ui.components

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.domain.quickentry.QuickEntryContext
import com.finai.app.ui.theme.FinaiColors

/**
 * Jeitos novos de lançar (planning-v2, fase 5), **ao lado** do formulário: nada aqui grava.
 * "Escrever" e "Falar" leem a frase com o [com.finai.app.domain.quickentry.QuickEntryParser] e
 * preenchem o formulário, que o usuário confere e salva pelo botão de sempre.
 */
class QuickEntryHooks(
    val context: QuickEntryContext,
    /** Aberto pelo "Falar ou escrever" do menu do "+": o campo de frase já abre ativo. */
    val startWithPhrase: Boolean = false,
    val onPhraseStarted: () -> Unit = {},
    val pendingCaptures: Int = 0,
    val onOpenPendingCaptures: (() -> Unit)? = null,
    val onOpenImport: (() -> Unit)? = null,
)

@Composable
internal fun QuickEntryStrip(
    hooks: QuickEntryHooks,
    /** Resultado da última frase ("Preenchido…" ou "Faltou…"); nulo antes de qualquer frase. */
    feedback: String?,
    onPhrase: (String) -> Unit,
) {
    val context = LocalContext.current
    var writing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // Sem reconhecedor de voz no aparelho, o botão Falar não aparece (risco da fase 5).
    val canSpeak = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) { text = heard; writing = true; onPhrase(heard) }
    }
    LaunchedEffect(hooks.startWithPhrase) {
        if (hooks.startWithPhrase) { writing = true; hooks.onPhraseStarted() }
    }
    LaunchedEffect(writing) { if (writing) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canSpeak) StripChip("Falar", "quick-speak") {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o lançamento, ex.: uber 23 ontem")
                runCatching { speech.launch(intent) }
            }
            StripChip("Escrever", "quick-write", selected = writing) { writing = !writing }
            if (hooks.pendingCaptures > 0 && hooks.onOpenPendingCaptures != null) {
                StripChip("Notificações (${hooks.pendingCaptures})", "quick-captures", onClick = hooks.onOpenPendingCaptures)
            }
            hooks.onOpenImport?.let { StripChip("Importar fatura", "quick-import", onClick = it) }
        }
        if (writing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus).testTag("quick-phrase"),
                singleLine = true,
                placeholder = { Text("uber 23 ontem · recebi 300 do freela", color = FinaiColors.TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onPhrase(text) }),
                trailingIcon = {
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, enabled = text.isNotBlank()) { onPhrase(text) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Preencher", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (text.isNotBlank()) FinaiColors.EmeraldDark else FinaiColors.TextDisabled) }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FinaiColors.Emerald, unfocusedBorderColor = FinaiColors.BorderSubtle,
                    focusedTextColor = FinaiColors.TextPrimary, unfocusedTextColor = FinaiColors.TextPrimary,
                ),
            )
            feedback?.let {
                Text(it, fontSize = 12.5.sp, color = if (it.startsWith("Faltou")) FinaiColors.AmberDark else FinaiColors.EmeraldDark,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp).testTag("quick-feedback"))
            }
        }
    }
}

@Composable
private fun StripChip(label: String, tag: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(99.dp))
            .background(if (selected) FinaiColors.InkStrong else FinaiColors.Surface)
            .border(1.dp, if (selected) FinaiColors.InkStrong else FinaiColors.BorderSubtle, RoundedCornerShape(99.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) FinaiColors.OnInkStrong else FinaiColors.TextPrimary)
    }
}
