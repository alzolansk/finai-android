package com.finai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finai.app.data.capture.TestStatus
import com.finai.app.data.local.entity.TesteNotificacaoEntity
import com.finai.app.domain.capture.CaptureSamples
import com.finai.app.state.CaptureApp
import com.finai.app.state.CaptureUiState
import com.finai.app.state.SimulationReport
import com.finai.app.ui.theme.FinaiColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tudo que as páginas de "Lançamentos por notificações" leem e fazem, ligado no `FinaiApp`. */
class CaptureSettingsBindings(
    val state: CaptureUiState,
    val apps: List<CaptureApp>?,
    val simulation: SimulationReport?,
    /** Acesso a notificações do Android (leitura) — relido ao voltar para o app. */
    val listenerAccess: Boolean,
    val listenerConnected: Boolean,
    /** POST_NOTIFICATIONS: o FinAI pode mostrar as próprias notificações. Outra coisa. */
    val ownNotificationsAllowed: Boolean,
    val onOpenAccessSettings: () -> Unit,
    val onOpenOwnNotificationSettings: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onTogglePackage: (String) -> Unit,
    val onLoadApps: () -> Unit,
    val onOpenPending: () -> Unit,
    val onOpenSuggestion: (Long) -> Unit,
    val onSimulate: (Int, String, String) -> Unit,
    val onSendTest: (Int, String, String, Long) -> Unit,
    val onClearTests: () -> Unit,
    val onClearSimulation: () -> Unit,
)

/** Linha da página principal de Configurações. */
@Composable
internal fun CaptureMainRow(b: CaptureSettingsBindings, onOpen: () -> Unit) {
    val status = when {
        !b.state.enabled -> StatusLine("Pausada", StatusTone.Neutral)
        !b.listenerAccess -> StatusLine("Ativa, mas sem acesso às notificações", StatusTone.Warning)
        b.state.packages.isEmpty() -> StatusLine("Ativa, nenhum app escolhido", StatusTone.Warning)
        else -> StatusLine("Ativa · ${appsLabel(b.state.packages.size)}" + pendingSuffix(b.state.pending.size), StatusTone.Ok)
    }
    SettingsRow(icon = Icons.Filled.NotificationsActive, title = "Lançamentos por notificações", status = status, onClick = onOpen)
}

private fun appsLabel(n: Int) = if (n == 1) "1 app" else "$n apps"
private fun pendingSuffix(n: Int) = when (n) { 0 -> ""; 1 -> " · 1 para confirmar"; else -> " · $n para confirmar" }

@Composable
internal fun CapturePage(b: CaptureSettingsBindings, onOpenApps: () -> Unit, onOpenTests: () -> Unit) {
    Text("Lance compras e Pix a partir dos avisos do banco", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
        color = FinaiColors.TextPrimary, modifier = Modifier.semantics { heading() })
    Text(
        "Quando um app que você escolher avisar uma compra ou um Pix, o FinAI prepara o lançamento e pergunta se " +
            "está certo. Nada entra nas suas contas antes de você tocar em Salvar. O texto é lido só neste aparelho: " +
            "não vai para IA nem para a internet.",
        fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp),
    )

    GroupLabel("Autorizações do Android")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.Lock,
            title = "Ler notificações (acesso a notificações)",
            status = if (b.listenerAccess) StatusLine("Permitido", StatusTone.Ok)
            else StatusLine("Não permitido · toque para abrir os ajustes do Android", StatusTone.Warning),
            onClick = b.onOpenAccessSettings,
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Notifications,
            title = "Mostrar avisos do FinAI",
            status = if (b.ownNotificationsAllowed) StatusLine("Permitido", StatusTone.Ok)
            else StatusLine("Bloqueado · as sugestões aparecem só dentro do app", StatusTone.Warning),
            onClick = b.onOpenOwnNotificationSettings,
        )
    }
    FootNote(
        null,
        "São duas permissões diferentes. A primeira deixa o FinAI ler notificações; o Android a dá para todos os " +
            "apps de uma vez, mas o FinAI descarta, antes de ler o conteúdo, tudo que não vem dos apps escolhidos abaixo. " +
            "A segunda só serve para o FinAI avisar \"Movimentação encontrada\" quando você está em outro app.",
    )

    GroupLabel("Captura")
    SettingsGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = b.state.enabled, role = Role.Switch, onValueChange = b.onSetEnabled)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ler notificações dos apps escolhidos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
                Text(
                    when {
                        !b.state.enabled -> "Pausada"
                        !b.listenerAccess -> "Ativa, mas o Android ainda não liberou o acesso"
                        else -> "Ativa"
                    },
                    fontSize = 12.sp, color = if (b.state.enabled && b.listenerAccess) FinaiColors.EmeraldDark else FinaiColors.TextTertiary,
                )
            }
            Switch(
                checked = b.state.enabled, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = FinaiColors.EmeraldButton),
            )
        }
        RowDivider()
        val chosen = b.state.packages
        SettingsRow(
            icon = Icons.Filled.Apps,
            title = "Aplicativos acompanhados",
            status = if (chosen.isEmpty()) StatusLine("Nenhum escolhido", StatusTone.Warning)
            else StatusLine(appsLabel(chosen.size) + chosenNames(chosen, b.apps), StatusTone.Neutral),
            onClick = onOpenApps,
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Inbox,
            title = "Para confirmar",
            status = StatusLine(
                when (val n = b.state.pending.size) { 0 -> "Nada esperando"; 1 -> "1 movimentação"; else -> "$n movimentações" },
                if (b.state.pending.isEmpty()) StatusTone.Neutral else StatusTone.Warning,
            ),
            onClick = b.onOpenPending,
        )
    }

    GroupLabel("Testes")
    SettingsGroup {
        SettingsRow(
            icon = Icons.Filled.Science,
            title = "Testar com notificações",
            status = StatusLine("Exemplos de compra, Pix, estorno e propaganda, sem compra real", StatusTone.Neutral),
            onClick = onOpenTests,
        )
    }
    FootNote(
        null,
        "Formatos reconhecidos: compra aprovada no crédito, no débito ou sem dizer a forma; compra parcelada com o número " +
            "de parcelas; Pix enviado e recebido. Estorno, cancelamento, pagamento de fatura e transferência aparecem como " +
            "\"Precisa revisar\". Propaganda, oferta de crédito, aviso de limite e compra recusada são ignorados. Cada banco " +
            "escreve de um jeito: o que não for reconhecido é descartado, nunca adivinhado.",
    )
}

private fun chosenNames(chosen: Set<String>, apps: List<CaptureApp>?): String {
    val labels = apps?.filter { it.packageName in chosen }?.map { it.label }.orEmpty()
    return if (labels.isEmpty()) "" else ": " + labels.take(3).joinToString(", ") + if (labels.size > 3) "…" else ""
}

@Composable
internal fun CaptureAppsPage(b: CaptureSettingsBindings) {
    LaunchedEffect(Unit) { b.onLoadApps() }
    Text(
        "Escolha os apps do banco, do cartão ou da carteira digital. O FinAI só olha as notificações deles; " +
            "as dos outros apps são descartadas sem leitura. O app escolhido não define a conta: você confirma em cada lançamento.",
        fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary,
    )
    val apps = b.apps
    if (apps == null) {
        Text("Carregando os aplicativos…", fontSize = 13.sp, color = FinaiColors.TextTertiary, modifier = Modifier.padding(top = 16.dp))
    } else {
        AppsList(b, apps)
    }
}

@Composable
private fun AppsList(b: CaptureSettingsBindings, apps: List<CaptureApp>) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Buscar app") },
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    )
    val filtered = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    val (suggested, others) = filtered.partition { it.suggested || it.packageName in b.state.packages }
    if (suggested.isNotEmpty()) {
        GroupLabel("Financeiros e escolhidos")
        SettingsGroup { suggested.forEachIndexed { i, app -> if (i > 0) RowDivider(); AppRow(app, app.packageName in b.state.packages, b.onTogglePackage) } }
    }
    if (others.isNotEmpty()) {
        GroupLabel("Outros aplicativos")
        SettingsGroup { others.forEachIndexed { i, app -> if (i > 0) RowDivider(); AppRow(app, app.packageName in b.state.packages, b.onTogglePackage) } }
    }
    FootNote(null, "Evite escolher apps de mensagens ou e-mail: o FinAI passaria a olhar todas as conversas deles.")
}

@Composable
private fun AppRow(app: CaptureApp, checked: Boolean, onToggle: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(app.packageName) })
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).background(FinaiColors.SurfaceMuted, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Text(app.packageName, fontSize = 11.sp, color = FinaiColors.TextMuted, maxLines = 1)
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

// ── testes ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaptureTestsPage(b: CaptureSettingsBindings) {
    var number by rememberSaveable { mutableIntStateOf(1) }
    val sample = CaptureSamples.byNumber(number)
    // Textos editados por exemplo; "Restaurar exemplo" volta ao original.
    var edits by rememberSaveable { mutableStateOf(mapOf<Int, List<String>>()) }
    val title = edits[number]?.get(0) ?: sample.title
    val text = edits[number]?.get(1) ?: sample.text
    var delay by rememberSaveable { mutableLongStateOf(0L) }
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Text("Testar sem compra real", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FinaiColors.TextPrimary,
        modifier = Modifier.semantics { heading() })
    Text(
        "Tudo que nasce aqui fica marcado \"Teste do FinAI\". Confirmar uma sugestão de teste mostra o fluxo inteiro, " +
            "mas não grava nada nas suas finanças.",
        fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp),
    )

    GroupLabel("Exemplo")
    SettingsGroup {
        Box {
            SettingsRow(title = "${sample.number} · ${sample.name}", status = StatusLine(sample.expectation, StatusTone.Neutral), onClick = { pickerOpen = true })
            DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                CaptureSamples.all.forEach { s ->
                    DropdownMenuItem(text = { Text("${s.number} · ${s.name}", fontSize = 14.sp) }, onClick = { number = s.number; pickerOpen = false })
                }
            }
        }
    }
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = title, onValueChange = { edits = edits + (number to listOf(it, text)) }, label = { Text("Título") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = text, onValueChange = { edits = edits + (number to listOf(title, it)) }, label = { Text("Mensagem") },
            minLines = 2, modifier = Modifier.fillMaxWidth())
        if (sample.steps.size > 1) {
            Text(
                "Este exemplo manda ${sample.steps.size} vezes: " + sample.steps.joinToString(", ") { it.label.lowercase() } +
                    ". Só o primeiro texto é editável; os outros derivam dele.",
                fontSize = 12.sp, color = FinaiColors.TextTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (edits.containsKey(number)) TextAction("Restaurar exemplo", { edits = edits - number })
            if (edits.isNotEmpty()) TextAction("Restaurar todos", { edits = emptyMap() }, color = FinaiColors.TextSecondary)
        }
    }

    GroupLabel("A · Simular a leitura")
    Text(
        "Manda o texto acima direto para o mesmo leitor e o mesmo fluxo de sugestões. Mostra o que foi reconhecido e o " +
            "que falta. Não comprova a autorização do Android nem o funcionamento do listener.",
        fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary,
    )
    ActionButton("Simular a leitura", { b.onSimulate(number, title, text) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    b.simulation?.takeIf { it.sample == number }?.let { report ->
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp).background(FinaiColors.Surface, RoundedCornerShape(14.dp))
                .padding(14.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            report.steps.forEach { step ->
                Text("${step.label}: ${step.outcome}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinaiColors.TextPrimary)
                if (step.recognized.isNotEmpty()) Text("Reconhecido: " + step.recognized.joinToString(" · "), fontSize = 13.sp, color = FinaiColors.TextSecondary)
                if (step.missing.isNotEmpty()) Text("Falta: " + step.missing.joinToString(" · "), fontSize = 13.sp, color = FinaiColors.AmberDark)
            }
            TextAction("Limpar resultado", b.onClearSimulation, color = FinaiColors.TextSecondary)
        }
    }

    GroupLabel("B · Enviar notificação de teste")
    Text(
        "Publica uma notificação real do FinAI, identificada como \"Teste do FinAI\". Ela passa pelo leitor de " +
            "notificações do Android, como a de um banco. Escolha um tempo, toque em Enviar e saia do app para ver " +
            "o aviso \"Movimentação encontrada\".",
        fontSize = 13.sp, lineHeight = 18.sp, color = FinaiColors.TextSecondary,
    )
    if (!b.listenerAccess) WarningLine("O acesso a notificações não está liberado: a notificação será publicada, mas o listener não vai recebê-la.", b.onOpenAccessSettings)
    if (!b.ownNotificationsAllowed) WarningLine("As notificações do FinAI estão bloqueadas: não dá para publicar o teste.", b.onOpenOwnNotificationSettings)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0L to "Agora", 5_000L to "Em 5 s", 15_000L to "Em 15 s").forEach { (ms, label) ->
            SelectChip(label, delay == ms) { delay = ms }
        }
    }
    Button(
        onClick = { b.onSendTest(number, title, text, delay) }, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FinaiColors.InkStrong, contentColor = FinaiColors.OnInkStrong),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
    ) { Text("Enviar notificação de teste", fontWeight = FontWeight.Bold) }

    GroupLabel("Envios recentes")
    if (b.state.tests.isEmpty()) {
        Text("Nenhum teste ainda.", fontSize = 13.sp, color = FinaiColors.TextTertiary)
    } else {
        SettingsGroup {
            b.state.tests.take(30).forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                TestRow(t, onOpenSuggestion = t.sugestaoId?.let { id -> { b.onOpenSuggestion(id) } })
            }
        }
    }
    ActionButton("Limpar testes", { confirmClear = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    FootNote(null, "Limpar testes apaga só os envios de teste, as sugestões marcadas \"Teste do FinAI\" e as notificações deles. Nada real é tocado.")

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar testes?") },
            text = { Text("Apaga os envios de teste e as sugestões de teste. Seus lançamentos e sugestões reais continuam.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; b.onClearTests() }) { Text("Limpar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun WarningLine(text: String, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(FinaiColors.AmberSoftBg, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, color = FinaiColors.AmberDeep, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        TextAction("Abrir ajustes", onFix, color = FinaiColors.AmberDark)
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
private fun TestRow(t: TesteNotificacaoEntity, onOpenSuggestion: (() -> Unit)?) {
    val status = runCatching { TestStatus.valueOf(t.status) }.getOrDefault(TestStatus.Waiting)
    val tone = when (status) {
        TestStatus.SuggestionCreated, TestStatus.SuggestionUpdated -> StatusTone.Ok
        TestStatus.NotPublished -> StatusTone.Error
        TestStatus.Repeated, TestStatus.NotRecognized -> StatusTone.Neutral
        else -> StatusTone.Warning
    }
    val time = Instant.ofEpochMilli(t.atualizadoEm).atZone(ZoneId.systemDefault()).format(TIME)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${t.exemplo} · ${t.etapa} · ${if (t.modo == "simulacao") "Simulação" else "Notificação"}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FinaiColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(tone.color, CircleShape))
                Text("${status.label} · $time", fontSize = 12.sp, color = tone.color)
            }
            if (t.detalhe.isNotBlank()) Text(t.detalhe, fontSize = 12.sp, color = FinaiColors.TextTertiary)
        }
        if (onOpenSuggestion != null) TextAction("Ver sugestão", onOpenSuggestion)
    }
}
