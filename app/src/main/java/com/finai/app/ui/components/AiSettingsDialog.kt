package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.ai.ProviderId
import com.finai.app.ui.theme.FinaiColors

/**
 * Full-screen API-key configuration. Keys are encrypted at rest by [AiKeyStore]
 * and a provider is considered only when it has a configured key.
 */
@Composable
fun ApiKeySettingsScreen(
    configuredProviders: Set<ProviderId>,
    onSave: (ProviderId, String) -> Unit,
    onClear: (ProviderId) -> Unit,
    notificationsEnabled: Boolean,
    onTestNotifications: () -> Unit,
    onRestartTour: () -> Unit,
    onEraseAllData: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(ScreenContentPadding),
    ) {
        Text("Conecte sua IA", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Escolha os provedores que deseja usar. O FinAI alterna entre as chaves configuradas quando necessário.",
            color = FinaiColors.TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        SecurityNotice()
        Spacer(Modifier.height(20.dp))
        Text("Provedores", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        ProviderId.entries.forEach { provider ->
            ProviderKeyCard(
                provider = provider,
                hasKey = provider in configuredProviders,
                onSave = { key -> onSave(provider, key) },
                onClear = { onClear(provider) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(14.dp))
        NotificationsDiagnostics(notificationsEnabled, onTestNotifications)
        Spacer(Modifier.height(20.dp))
        Text("Dados e privacidade", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        DataPrivacySection(onRestartTour = onRestartTour, onEraseAllData = onEraseAllData)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * "Rever tour guiado" reabre o onboarding sem apagar nenhum dado —
 * [com.finai.app.state.AppViewModel.restartOnboarding]. "Apagar todos os
 * dados" exige uma confirmação explícita porque é irreversível: não há
 * backup/export automático (planning.md §11), então o Room apagado aqui não
 * volta.
 */
@Composable
private fun DataPrivacySection(onRestartTour: () -> Unit, onEraseAllData: () -> Unit) {
    var showEraseConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinaiColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, tint = FinaiColors.TextTertiary)
            Column {
                Text("Tour guiado", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text("Reveja a explicação rápida das principais telas do FinAI.", color = FinaiColors.TextSecondary, fontSize = 12.sp)
            }
        }
        OutlinedButton(
            onClick = onRestartTour,
            modifier = Modifier.padding(top = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FinaiColors.TextPrimary),
        ) { Text("Rever tour guiado") }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = FinaiColors.RoseDark)
            Column {
                Text("Apagar todos os dados", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(
                    "Remove lançamentos, contas, objetivos, dívidas, orçamentos, assinaturas, chat e notificações. " +
                        "Suas chaves de IA não são afetadas.",
                    color = FinaiColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Button(
            onClick = { showEraseConfirm = true },
            modifier = Modifier.padding(top = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.RoseSoftBg, contentColor = FinaiColors.RoseDark),
        ) { Text("Apagar todos os dados") }
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = FinaiColors.RoseDark) },
            title = { Text("Apagar todos os dados?") },
            text = {
                Text(
                    "Esta ação é irreversível. Todos os seus lançamentos, contas, objetivos, dívidas, orçamentos, " +
                        "assinaturas, o histórico do chat e as notificações serão apagados permanentemente, e o app " +
                        "voltará ao estado de uma instalação nova. Não há como desfazer.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseConfirm = false
                        onEraseAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.RoseDark, contentColor = Color.White),
                ) { Text("Apagar tudo") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Diagnóstico das notificações proativas da Fase 5 (planning.md §9) — não é
 * necessário para o app funcionar, mas é o que torna a rotina do
 * `FinanceCheckWorker` testável sem esperar a janela de 24h: dispara uma
 * execução imediata via `WorkManager.enqueue` (não muda o agendamento
 * periódico, só roda uma vez a mais).
 */
@Composable
private fun NotificationsDiagnostics(notificationsEnabled: Boolean, onTest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinaiColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(
                Icons.Filled.Notifications, contentDescription = null,
                tint = if (notificationsEnabled) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
            )
            Column {
                Text("Notificações proativas", fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(
                    if (notificationsEnabled) "Permitidas neste aparelho" else "Sem permissão do sistema — ative em Ajustes",
                    color = if (notificationsEnabled) FinaiColors.EmeraldDark else FinaiColors.RoseDark,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            "Uma rotina em segundo plano recalcula seus dados a cada 24h e só notifica quando há algo " +
                "relevante (conta vencendo/atrasada, orçamento perto do limite, objetivo que precisa de atenção " +
                "ou um padrão de gasto identificado). Use o botão abaixo para rodar essa checagem agora, sem esperar.",
            color = FinaiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = onTest,
            modifier = Modifier.padding(top = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FinaiColors.TextPrimary),
        ) { Text("Testar agora") }
    }
}

@Composable
private fun SecurityNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinaiColors.EmeraldSoftBg, RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.EmeraldSoftBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = FinaiColors.EmeraldDark, modifier = Modifier.padding(top = 1.dp))
        Column {
            Text("Suas chaves ficam protegidas", fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDeep, fontSize = 13.sp)
            Text(
                "Elas são criptografadas neste aparelho e usadas somente para falar com cada provedor.",
                color = FinaiColors.EmeraldDeep,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ProviderKeyCard(
    provider: ProviderId,
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var key by remember(provider) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinaiColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(
                imageVector = if (hasKey) Icons.Filled.CheckCircle else Icons.Filled.Key,
                contentDescription = null,
                tint = if (hasKey) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
            )
            Column {
                Text(provider.displayName, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                Text(
                    if (hasKey) "Pronto para uso" else "Adicione uma chave para ativar",
                    color = if (hasKey) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(if (hasKey) "Chave salva" else "Chave da API") },
            placeholder = {
                Text(
                    if (hasKey) "•••• •••• •••• ••••" else "Cole sua chave aqui",
                    color = FinaiColors.TextMuted,
                )
            },
            supportingText = {
                if (hasKey && key.isBlank()) {
                    Text("Digite uma nova chave apenas para substituí-la.")
                }
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = key.isNotBlank(),
                onClick = { onSave(key.trim()); key = "" },
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
            ) { Text("Salvar") }
            if (hasKey) {
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.RoseSoftBg, contentColor = FinaiColors.RoseDark),
                ) { Text("Remover") }
            }
        }
    }
}
