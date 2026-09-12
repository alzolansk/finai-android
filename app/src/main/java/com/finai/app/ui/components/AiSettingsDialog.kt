package com.finai.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * Where the Gemini API key gets configured (planning.md §7.4): pasted here,
 * stored via [com.finai.app.data.prefs.AiKeyStore] — EncryptedSharedPreferences
 * backed by the Android Keystore, never written to source code or plain
 * DataStore. Opened from the gear icon in [FinaiTopBar].
 */
@Composable
fun ApiKeySettingsDialog(
    hasKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chave da IA (Gemini)") },
        text = {
            Column {
                Text(
                    if (hasKey) {
                        "Uma chave já está configurada e guardada de forma criptografada neste aparelho. Cole uma nova " +
                            "abaixo para substituí-la, ou remova a atual."
                    } else {
                        "Cole sua chave da API do Gemini (Google AI Studio) para ativar o chat, a leitura de objetivos, o " +
                            "veredito do simulador, as decisões sugeridas e o roteiro de negociação de dívida. Ela fica " +
                            "guardada só neste aparelho, de forma criptografada — sai daqui apenas nas chamadas para a " +
                            "própria API do Gemini."
                    },
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Chave da API") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank(), onClick = { onSave(key.trim()); onDismiss() }) { Text("Salvar") }
        },
        dismissButton = {
            if (hasKey) TextButton(onClick = { onClear(); onDismiss() }) { Text("Remover chave") }
            else TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
