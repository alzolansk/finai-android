package com.finai.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.finai.app.data.ai.CerebrasAiProvider
import com.finai.app.data.ai.GeminiAiProvider
import com.finai.app.data.ai.GroqAiProvider
import com.finai.app.data.ai.MistralAiProvider
import com.finai.app.data.ai.OpenRouterAiProvider
import com.finai.app.data.ai.ProviderId
import com.finai.app.state.ConnectionTest
import com.finai.app.ui.theme.FinaiColors

/**
 * Central de configurações. A página principal só mostra *o que está
 * configurado e em que estado* — uma linha por área, com um subtítulo de
 * status e um chevron. Formulário (chave de API, nova conta, apagar dados) só
 * aparece depois que o usuário entra na área que quer mudar.
 *
 * As subpáginas vivem aqui, num estado salvável, e não como rotas do
 * NavHost: são uma pilha rasa (no máximo principal → IA → provedor) que só
 * faz sentido dentro desta tela, e é esta tela que desenha a própria topbar
 * para o título e o "voltar" acompanharem a subpágina.
 */
@Composable
fun SettingsScreen(
    onExit: () -> Unit,
    configuredProviders: Set<ProviderId>,
    exhaustedProviders: Set<ProviderId>,
    connectionTests: Map<ProviderId, ConnectionTest>,
    onSaveKey: (ProviderId, String) -> Unit,
    onClearKey: (ProviderId) -> Unit,
    onTestProvider: (ProviderId) -> Unit,
    notificationsEnabled: Boolean,
    onRunNotificationCheck: () -> Unit,
    knownAccounts: Set<String>,
    onAddAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onRestartTour: () -> Unit,
    onEraseAllData: () -> Unit,
    onExportData: (android.net.Uri) -> Unit = {},
    onPickRestore: (android.net.Uri) -> Unit = {},
    pendingRestoreSummary: String? = null,
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    backupStatus: String? = null,
    onDismissBackupStatus: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var providerName by rememberSaveable { mutableStateOf<String?>(null) }
    val provider = providerName?.let { ProviderId.valueOf(it) }

    fun back() {
        page = when (page) {
            SettingsPage.MAIN -> { onExit(); SettingsPage.MAIN }
            SettingsPage.PROVIDER -> SettingsPage.AI
            else -> SettingsPage.MAIN
        }
    }
    BackHandler(enabled = page != SettingsPage.MAIN) { back() }

    val activeProvider = ProviderId.entries.firstOrNull { it in configuredProviders && it !in exhaustedProviders }

    Column(modifier = Modifier.fillMaxSize()) {
        FinaiSettingsTopBar(
            title = if (page == SettingsPage.PROVIDER && provider != null) provider.displayName else page.title,
            onNavigateBack = ::back,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        ) {
            when (page) {
                SettingsPage.MAIN -> MainPage(
                    configuredProviders = configuredProviders,
                    activeProvider = activeProvider,
                    notificationsEnabled = notificationsEnabled,
                    accountCount = knownAccounts.size,
                    onOpen = { page = it },
                    onRestartTour = onRestartTour,
                )
                SettingsPage.AI -> AiPage(
                    configuredProviders = configuredProviders,
                    exhaustedProviders = exhaustedProviders,
                    activeProvider = activeProvider,
                    onOpenProvider = {
                        providerName = it.name
                        page = SettingsPage.PROVIDER
                    },
                )
                SettingsPage.PROVIDER -> if (provider != null) {
                    ProviderPage(
                        provider = provider,
                        connected = provider in configuredProviders,
                        exhausted = provider in exhaustedProviders,
                        active = provider == activeProvider,
                        test = connectionTests[provider],
                        onSave = { key ->
                            onSaveKey(provider, key)
                            onTestProvider(provider)
                        },
                        onClear = { onClearKey(provider) },
                        onTest = { onTestProvider(provider) },
                    )
                }
                SettingsPage.NOTIFICATIONS -> NotificationsPage(notificationsEnabled, onRunNotificationCheck)
                SettingsPage.ACCOUNTS -> AccountsPage(knownAccounts, onAddAccount, onRemoveAccount)
                SettingsPage.PRIVACY -> PrivacyPage(
                    onEraseAllData, onExportData, onPickRestore, pendingRestoreSummary, onConfirmRestore, onCancelRestore,
                    backupStatus, onDismissBackupStatus,
                )
            }
        }
    }
}

private enum class SettingsPage(val title: String) {
    MAIN("Configurações"),
    AI("IA e assistente"),
    PROVIDER("Provedor"),
    NOTIFICATIONS("Notificações"),
    ACCOUNTS("Contas e cartões"),
    PRIVACY("Privacidade e dados"),
}

// ── principal ───────────────────────────────────────────────────────────

@Composable
private fun MainPage(
    configuredProviders: Set<ProviderId>,
    activeProvider: ProviderId?,
    notificationsEnabled: Boolean,
    accountCount: Int,
    onOpen: (SettingsPage) -> Unit,
    onRestartTour: () -> Unit,
) {
    val aiStatus = when {
        configuredProviders.isEmpty() -> StatusLine("Nenhum provedor conectado", StatusTone.Warning)
        activeProvider == null -> StatusLine("Todos sem cota hoje", StatusTone.Warning)
        else -> StatusLine(
            "${activeProvider.displayName} em uso · ${configuredProviders.size} de ${ProviderId.entries.size} conectados",
            StatusTone.Ok,
        )
    }
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.AutoAwesome,
            title = "IA e assistente",
            status = aiStatus,
            onClick = { onOpen(SettingsPage.AI) },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Notifications,
            title = "Notificações",
            status = if (notificationsEnabled) StatusLine("Ativas", StatusTone.Ok)
            else StatusLine("Sem permissão do sistema", StatusTone.Warning),
            onClick = { onOpen(SettingsPage.NOTIFICATIONS) },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.CreditCard,
            title = "Contas e cartões",
            status = StatusLine(
                when (accountCount) {
                    0 -> "Nenhuma cadastrada"
                    1 -> "1 cadastrada"
                    else -> "$accountCount cadastradas"
                },
                StatusTone.Neutral,
            ),
            onClick = { onOpen(SettingsPage.ACCOUNTS) },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Shield,
            title = "Privacidade e dados",
            status = StatusLine("Seus dados ficam neste aparelho", StatusTone.Neutral),
            onClick = { onOpen(SettingsPage.PRIVACY) },
        )
    }

    GroupLabel("Ajuda")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.PlayCircleOutline,
            title = "Rever tour guiado",
            status = StatusLine("Explicação rápida das telas principais", StatusTone.Neutral),
            onClick = onRestartTour,
            showChevron = false,
        )
    }
}

// ── IA ──────────────────────────────────────────────────────────────────

@Composable
private fun AiPage(
    configuredProviders: Set<ProviderId>,
    exhaustedProviders: Set<ProviderId>,
    activeProvider: ProviderId?,
    onOpenProvider: (ProviderId) -> Unit,
) {
    val (headline, detail) = when {
        configuredProviders.isEmpty() ->
            "Nenhum provedor conectado" to
                "Conecte ao menos um para ativar o chat e os textos explicativos. Os números do app funcionam sem IA."
        activeProvider == null ->
            "Sem cota disponível hoje" to
                "Todos os provedores conectados atingiram o limite gratuito do dia. A cota renova à meia-noite."
        else ->
            "${activeProvider.displayName} está em uso" to
                "Se ele falhar ou esgotar a cota do dia, o FinAI passa sozinho para o próximo conectado, na ordem abaixo."
    }
    Text(headline, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary)
    Text(detail, fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))

    GroupLabel("Provedores · ordem de uso")
    SettingsGroup {
        ProviderId.entries.forEachIndexed { index, id ->
            if (index > 0) RowDivider()
            val connected = id in configuredProviders
            SettingsRow(
                leading = { OrderBadge(index + 1, connected) },
                title = id.displayName,
                status = if (connected) StatusLine("Conectado · chave ••••••••", StatusTone.Neutral)
                else StatusLine("Não configurado", StatusTone.Muted),
                trailing = { ProviderStatePill(connected, id in exhaustedProviders, id == activeProvider) },
                onClick = { onOpenProvider(id) },
            )
        }
    }
    FootNote(Icons.Filled.Lock, "As chaves são criptografadas neste aparelho e enviadas só ao próprio provedor.")
}

@Composable
private fun ProviderStatePill(connected: Boolean, exhausted: Boolean, active: Boolean) {
    when {
        !connected -> Unit
        exhausted -> PillTag("Sem cota hoje", FinaiColors.AmberSoftBg, FinaiColors.AmberDark)
        active -> PillTag("Em uso", FinaiColors.EmeraldSoftBg, FinaiColors.EmeraldDark)
        else -> PillTag("Reserva", FinaiColors.SurfaceMuted, FinaiColors.TextTertiary)
    }
}

@Composable
private fun OrderBadge(position: Int, connected: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(if (connected) FinaiColors.EmeraldSoftBg else FinaiColors.SurfaceMuted, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$position", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (connected) FinaiColors.EmeraldDark else FinaiColors.TextMuted,
        )
    }
}

/**
 * Detalhe de um provedor. Conectado, não mostra campo nenhum — só o estado,
 * "Testar conexão", "Trocar chave" e "Desconectar". O campo aparece quando
 * ainda não há chave ou quando o usuário pede para trocá-la. Salvar já testa
 * a chave, para "salvei" e "funciona" não ficarem ambíguos.
 */
@Composable
private fun ProviderPage(
    provider: ProviderId,
    connected: Boolean,
    exhausted: Boolean,
    active: Boolean,
    test: ConnectionTest?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
) {
    var replacing by rememberSaveable(provider) { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val showForm = !connected || replacing

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (connected) "Conectado" else "Não configurado",
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
        )
        ProviderStatePill(connected, exhausted, active)
    }
    Text(
        when {
            !connected -> "Cole uma chave gratuita para o FinAI poder usar o ${provider.displayName}."
            exhausted -> "Atingiu o limite gratuito hoje. O FinAI volta a usá-lo depois da meia-noite."
            active -> "É o provedor que o FinAI está usando agora."
            else -> "Fica de reserva: é usado se os provedores acima dele falharem ou ficarem sem cota."
        },
        fontSize = 13.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp),
    )

    GroupLabel("Chave de API")
    if (showForm) {
        KeyForm(
            provider = provider,
            replacing = replacing,
            onSave = { key ->
                onSave(key)
                replacing = false
            },
            onCancel = if (replacing) ({ replacing = false }) else null,
        )
    } else {
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.Key,
                title = "Chave configurada",
                status = StatusLine("••••••••••••", StatusTone.Neutral),
                trailing = { TextAction("Trocar") { replacing = true } },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Filled.AutoAwesome,
                title = "Testar conexão",
                status = testStatus(test),
                trailing = {
                    if (test == ConnectionTest.Running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FinaiColors.TextTertiary)
                    }
                },
                onClick = if (test == ConnectionTest.Running) null else onTest,
                showChevron = false,
            )
        }
        SettingsGroup(modifier = Modifier.padding(top = 12.dp)) {
            SettingsRow(
                icon = Icons.Filled.Close,
                title = "Desconectar",
                titleColor = FinaiColors.RoseDark,
                iconTint = FinaiColors.RoseDark,
                status = StatusLine("Remove a chave deste aparelho", StatusTone.Neutral),
                onClick = { confirmDisconnect = true },
                showChevron = false,
            )
        }
    }

    FootNote(null, "Modelo gratuito usado: ${provider.freeModel}")

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar ${provider.displayName}?") },
            text = { Text("A chave será apagada deste aparelho. Para usar o ${provider.displayName} de novo, será preciso colá-la outra vez.") },
            confirmButton = {
                Button(
                    onClick = { confirmDisconnect = false; onClear() },
                    colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.RoseDark, contentColor = Color.White),
                ) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun KeyForm(provider: ProviderId, replacing: Boolean, onSave: (String) -> Unit, onCancel: (() -> Unit)?) {
    var key by rememberSaveable(provider) { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    SettingsGroup {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(if (replacing) "Nova chave" else "Chave da API") },
                placeholder = { Text("Cole sua chave aqui", color = FinaiColors.TextMuted) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = key.isNotBlank(),
                    onClick = { onSave(key.trim()); key = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
                ) { Text(if (replacing) "Salvar e testar" else "Conectar") }
                if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancelar", color = FinaiColors.TextSecondary) }
            }
        }
    }
    Text(
        "Onde conseguir uma chave gratuita: ${provider.keyUrl}",
        fontSize = 12.sp,
        color = FinaiColors.EmeraldDark,
        modifier = Modifier
            .padding(top = 10.dp, start = 4.dp)
            .clickable { runCatching { uriHandler.openUri("https://${provider.keyUrl}") } },
    )
}

private fun testStatus(test: ConnectionTest?): StatusLine = when (test) {
    null -> StatusLine("Confere se a chave está respondendo", StatusTone.Neutral)
    ConnectionTest.Running -> StatusLine("Testando…", StatusTone.Neutral)
    ConnectionTest.Ok -> StatusLine("Funcionando — o provedor respondeu", StatusTone.Ok)
    is ConnectionTest.Failed -> StatusLine(test.reason, StatusTone.Error)
}

/** Espelha o `DEFAULT_MODEL` de cada adaptador — a fonte da verdade continua lá. */
private val ProviderId.freeModel: String
    get() = when (this) {
        ProviderId.GEMINI -> GeminiAiProvider.DEFAULT_MODEL
        ProviderId.GROQ -> GroqAiProvider.DEFAULT_MODEL
        ProviderId.OPENROUTER -> OpenRouterAiProvider.DEFAULT_MODEL
        ProviderId.MISTRAL -> MistralAiProvider.DEFAULT_MODEL
        ProviderId.CEREBRAS -> CerebrasAiProvider.DEFAULT_MODEL
    }

private val ProviderId.keyUrl: String
    get() = when (this) {
        ProviderId.GEMINI -> "aistudio.google.com/apikey"
        ProviderId.GROQ -> "console.groq.com/keys"
        ProviderId.OPENROUTER -> "openrouter.ai/keys"
        ProviderId.MISTRAL -> "console.mistral.ai/api-keys"
        ProviderId.CEREBRAS -> "cloud.cerebras.ai"
    }

// ── notificações ────────────────────────────────────────────────────────

/**
 * Estado da permissão + "Verificar agora", que dispara uma execução avulsa do
 * `FinanceCheckWorker` (não mexe no agendamento de 24h) — é o que torna a
 * rotina proativa conferível sem esperar a janela diária.
 */
@Composable
private fun NotificationsPage(notificationsEnabled: Boolean, onRunCheck: () -> Unit) {
    val context = LocalContext.current
    var checkStarted by remember { mutableStateOf(false) }

    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.Notifications,
            title = "Permissão do sistema",
            status = if (notificationsEnabled) StatusLine("Permitidas neste aparelho", StatusTone.Ok)
            else StatusLine("Bloqueadas — toque para abrir os ajustes", StatusTone.Warning),
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.PlayCircleOutline,
            title = "Verificar agora",
            status = if (checkStarted) StatusLine("Verificação iniciada — se houver algo relevante, a notificação chega em instantes", StatusTone.Ok)
            else StatusLine("Roda a checagem diária sem esperar", StatusTone.Neutral),
            onClick = { onRunCheck(); checkStarted = true },
            showChevron = false,
        )
    }
    FootNote(
        null,
        "Uma vez por dia o FinAI recalcula seus dados e só avisa quando há algo relevante: conta vencendo ou " +
            "atrasada, limite de categoria perto de estourar, objetivo que precisa de atenção ou um padrão de gasto. " +
            "O mesmo aviso não se repete no mesmo dia.",
    )
}

// ── contas e cartões ────────────────────────────────────────────────────

/**
 * Nomes sugeridos no seletor de "conta/cartão de origem" do lançamento
 * manual. Remover aqui não apaga nenhum lançamento gravado com esse nome, só
 * tira a sugestão — contas já usadas em algum lançamento continuam
 * aparecendo no seletor de qualquer forma.
 */
@Composable
private fun AccountsPage(accounts: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }

    if (accounts.isNotEmpty()) {
        SettingsGroup {
            accounts.sorted().forEachIndexed { index, name ->
                if (index > 0) RowDivider()
                SettingsRow(
                    icon = Icons.Filled.CreditCard,
                    title = name,
                    trailing = {
                        Icon(
                            Icons.Filled.Close, contentDescription = "Remover $name", tint = FinaiColors.TextTertiary,
                            modifier = Modifier.size(20.dp).clickable { pendingRemoval = name },
                        )
                    },
                )
            }
        }
    } else {
        Text("Nenhuma conta ou cartão cadastrado ainda.", fontSize = 13.sp, color = FinaiColors.TextSecondary)
    }

    GroupLabel("Adicionar")
    SettingsGroup {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, label = { Text("Ex.: Nubank, Carteira") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Button(
                enabled = input.isNotBlank(),
                onClick = { onAdd(input.trim()); input = "" },
                colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
            ) { Text("Adicionar") }
        }
    }
    FootNote(
        null,
        "Aparecem como sugestão ao lançar um gasto. Remover uma daqui não apaga lançamentos já feitos com ela.",
    )

    pendingRemoval?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remover \"$name\"?") },
            text = { Text("Ela deixa de ser sugerida. Lançamentos já gravados com esse nome não mudam.") },
            confirmButton = { TextButton(onClick = { pendingRemoval = null; onRemove(name) }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancelar") } },
        )
    }
}

// ── privacidade e dados ─────────────────────────────────────────────────

/**
 * O que fica e o que sai do aparelho (resumo de PRIVACY.md), exportar/restaurar (Fase 7,
 * item 12 — o backup do Android está desligado, então é o jeito de trocar de aparelho sem
 * perder os dados) e "Apagar todos os dados", que exige confirmação porque é irreversível.
 */
@Composable
private fun PrivacyPage(
    onEraseAllData: () -> Unit,
    onExportData: (android.net.Uri) -> Unit,
    onPickRestore: (android.net.Uri) -> Unit,
    pendingRestoreSummary: String?,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    backupStatus: String?,
    onDismissBackupStatus: () -> Unit,
) {
    var showEraseConfirm by remember { mutableStateOf(false) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExportData) }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPickRestore) }

    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.PhoneAndroid,
            title = "Dados financeiros",
            status = StatusLine("Ficam só neste aparelho, sem backup na nuvem", StatusTone.Neutral),
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.AutoAwesome,
            title = "O que vai para a IA",
            status = StatusLine("Só totais e rótulos — nunca o extrato, o arquivo importado ou seu nome", StatusTone.Neutral),
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Lock,
            title = "Chaves de IA",
            status = StatusLine("Criptografadas com o Android Keystore", StatusTone.Neutral),
        )
    }

    GroupLabel("Backup")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.Upload,
            title = "Exportar dados",
            status = StatusLine("Salva um arquivo com lançamentos, contas, objetivos, dívidas, limites e chat", StatusTone.Neutral),
            onClick = { exportLauncher.launch("finai-backup-${java.time.LocalDate.now()}.json") },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Download,
            title = "Restaurar de um arquivo",
            status = StatusLine("Substitui todos os dados deste aparelho pelos do arquivo", StatusTone.Neutral),
            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        )
    }
    FootNote(
        null,
        "O arquivo exportado sai do controle do app: fica sem criptografia onde você salvar. Chaves de IA nunca vão nele.",
    )
    backupStatus?.let { msg ->
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .background(FinaiColors.SurfaceMuted, RoundedCornerShape(12.dp))
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(msg, fontSize = 14.sp, lineHeight = 20.sp, color = FinaiColors.TextBody, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = onDismissBackupStatus, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK") }
        }
    }

    GroupLabel("Zona de risco")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.DeleteForever,
            iconTint = FinaiColors.RoseDark,
            title = "Apagar todos os dados",
            titleColor = FinaiColors.RoseDark,
            status = StatusLine("Lançamentos, contas, objetivos, dívidas, limites, chat e notificações", StatusTone.Neutral),
            onClick = { showEraseConfirm = true },
            showChevron = false,
        )
    }
    FootNote(null, "Suas chaves de IA não são apagadas.")

    pendingRestoreSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = FinaiColors.AmberDark) },
            title = { Text("Substituir todos os dados?") },
            text = {
                Text(
                    "O arquivo traz $summary. Tudo o que está neste aparelho hoje será apagado e trocado pelo " +
                        "conteúdo do arquivo. Suas chaves de IA continuam como estão.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.Ink, contentColor = Color.White),
                ) { Text("Substituir tudo") }
            },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancelar") } },
        )
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

// ── peças de lista ──────────────────────────────────────────────────────

private enum class StatusTone { Ok, Warning, Error, Neutral, Muted }

private data class StatusLine(val text: String, val tone: StatusTone)

private val StatusTone.color: Color
    get() = when (this) {
        StatusTone.Ok -> FinaiColors.EmeraldDark
        StatusTone.Warning -> FinaiColors.AmberDark
        StatusTone.Error -> FinaiColors.RoseDark
        StatusTone.Neutral -> FinaiColors.TextTertiary
        StatusTone.Muted -> FinaiColors.TextMuted
    }

/** Um bloco de linhas: uma só borda, divisórias finas entre as linhas — nada de card dentro de card. */
@Composable
private fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FinaiColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, FinaiColors.BorderSubtle, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = FinaiColors.TextTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = FinaiColors.BorderFaint, modifier = Modifier.padding(start = 58.dp))
}

@Composable
private fun FootNote(icon: ImageVector?, text: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = FinaiColors.TextMuted, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Text(text, fontSize = 12.sp, color = FinaiColors.TextTertiary)
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinaiColors.EmeraldDark,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    iconTint: Color = FinaiColors.TextSecondary,
    titleColor: Color = FinaiColors.TextPrimary,
    status: StatusLine? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> Box(
                modifier = Modifier.size(30.dp).background(FinaiColors.SurfaceMuted, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                Text(status.text, fontSize = 12.sp, color = status.tone.color, modifier = Modifier.padding(top = 1.dp))
            }
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = FinaiColors.TextMuted, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Permissão de notificação relida a cada ON_RESUME — o usuário costuma sair
 * para os ajustes do sistema, ligar e voltar; sem isso a tela continuaria
 * dizendo "bloqueadas".
 */
@Composable
fun rememberOnResume(read: () -> Boolean): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var value by remember { mutableStateOf(read()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) value = read() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return value
}
