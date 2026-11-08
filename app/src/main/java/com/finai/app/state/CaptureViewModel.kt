package com.finai.app.state

import android.app.Application
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finai.app.data.capture.CaptureLinks
import com.finai.app.data.capture.CaptureNotifications
import com.finai.app.data.capture.CaptureProcessor
import com.finai.app.data.capture.CaptureRepository
import com.finai.app.data.capture.TestNotificationWorker
import com.finai.app.data.capture.TestStatus
import com.finai.app.data.capture.isTest
import com.finai.app.data.capture.state
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.data.local.entity.TesteNotificacaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.CardPayment
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.EntryReceipt
import com.finai.app.domain.capture.CaptureSamples
import com.finai.app.domain.capture.CapturedNotification
import com.finai.app.domain.capture.MissingField
import com.finai.app.domain.capture.NOTIFICATION_ORIGIN
import com.finai.app.domain.capture.ParseOutcome
import com.finai.app.domain.capture.SuggestionDraft
import com.finai.app.domain.capture.SuggestionOrigin
import com.finai.app.domain.capture.SuggestionRules
import com.finai.app.domain.capture.SuggestionState
import com.finai.app.util.FinaiLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Um app que pode ser acompanhado. [suggested] = app financeiro conhecido. */
data class CaptureApp(val packageName: String, val label: String, val suggested: Boolean)

data class CaptureUiState(
    val enabled: Boolean = false,
    val packages: Set<String> = emptySet(),
    val suggestions: List<SugestaoLancamentoEntity> = emptyList(),
    val tests: List<TesteNotificacaoEntity> = emptyList(),
) {
    /** "Para confirmar": a mais antiga primeiro (é a ordem em que aparecem, uma por vez). */
    val pending: List<SugestaoLancamentoEntity> get() = suggestions.filter { it.state == SuggestionState.Pending }.sortedBy { it.capturadoEm }
    /** Ainda não mostrada no app: é a que abre o quadro sozinha quando dá. */
    val nextUnseen: SugestaoLancamentoEntity? get() = pending.firstOrNull { it.avisadaEm == null }
    val resolved: List<SugestaoLancamentoEntity> get() = suggestions.filter { it.state != SuggestionState.Pending }
}

/** O que a simulação ("Simular a leitura") entendeu de cada etapa. */
data class SimulationStep(val label: String, val outcome: String, val recognized: List<String>, val missing: List<String>)
data class SimulationReport(val sample: Int, val steps: List<SimulationStep>)

/**
 * Estado e ações do lançamento por notificações: preferências da captura, sugestões, confirmação
 * e área de testes. A gravação financeira é sempre a do [CaptureRepository], na mesma transação
 * que marca a sugestão.
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FinaiDatabase.get(application)
    private val finance = FinanceRepository(db)
    private val repository = CaptureRepository(db, finance)
    private val prefs = FinaiPreferences(application)

    val uiState: StateFlow<CaptureUiState> = combine(
        prefs.captureEnabled, prefs.capturePackages, repository.suggestions, repository.testRuns,
    ) { enabled, packages, suggestions, tests -> CaptureUiState(enabled, packages, suggestions, tests) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CaptureUiState())

    private val _apps = MutableStateFlow<List<CaptureApp>?>(null)
    val apps: StateFlow<List<CaptureApp>?> = _apps.asStateFlow()

    private val _simulation = MutableStateFlow<SimulationReport?>(null)
    val simulation: StateFlow<SimulationReport?> = _simulation.asStateFlow()

    val listenerConnected: StateFlow<Boolean> = CaptureLinks.listenerConnected

    init {
        viewModelScope.launch { runCatching { repository.purge() } }
    }

    /** Acesso a notificações liberado para o FinAI nos ajustes do Android (não confundir com POST_NOTIFICATIONS). */
    fun hasListenerAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(getApplication()).contains(getApplication<Application>().packageName)

    /** Abre a tela do Android para liberar o acesso; na API 30+, direto no FinAI. */
    fun listenerSettingsIntent(): Intent {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, CaptureNotifications.listenerComponent(app).flattenToString())
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun fallbackListenerSettingsIntent(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCaptureEnabled(enabled)
        if (enabled && hasListenerAccess()) {
            runCatching { NotificationListenerService.requestRebind(CaptureNotifications.listenerComponent(getApplication())) }
        }
    }

    fun togglePackage(pkg: String) = viewModelScope.launch {
        val now = prefs.capturePackages.first()
        prefs.setCapturePackages(if (pkg in now) now - pkg else now + pkg)
    }

    /** Apps com ícone no launcher; os financeiros conhecidos primeiro. O próprio FinAI não entra. */
    fun loadApps() = viewModelScope.launch {
        val app = getApplication<Application>()
        _apps.value = withContext(Dispatchers.IO) {
            val pm = app.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != app.packageName }
                .distinctBy { it.first }
                .map { (pkg, label) -> CaptureApp(pkg, label, isFinanceApp(pkg, label)) }
                .sortedWith(compareByDescending<CaptureApp> { it.suggested }.thenBy { it.label.lowercase() })
        }
    }

    // ── sugestões ────────────────────────────────────────────────────

    fun markShown(id: Long) = viewModelScope.launch {
        repository.markShown(id)
        CaptureNotifications.cancelSuggestion(getApplication(), id)
    }

    suspend fun get(id: Long): SugestaoLancamentoEntity? = repository.get(id)

    /** "Salvar lançamento" no quadro. Erro de regra/estado volta como exceção com mensagem para a tela. */
    suspend fun confirm(id: Long, draft: SuggestionDraft, cardDue: EntryOptions.CardDue?): EntryReceipt =
        repository.confirm(id, draft, cardDue).also { CaptureNotifications.cancelSuggestion(getApplication(), id) }

    /** "Editar" → formulário do "+": as três gravações dele, presas à sugestão. */
    suspend fun confirmWithEntry(id: Long, entry: TransacaoEntity): EntryReceipt =
        repository.confirmWith(id) { finance.salvarLancamento(entry.copy(origem = NOTIFICATION_ORIGIN)) }
            .also { CaptureNotifications.cancelSuggestion(getApplication(), id) }

    suspend fun confirmWithCard(id: Long, entry: TransacaoEntity, payment: CardPayment): EntryReceipt =
        repository.confirmWith(id) { finance.salvarCompraCartao(entry.copy(origem = NOTIFICATION_ORIGIN), payment) }
            .also { CaptureNotifications.cancelSuggestion(getApplication(), id) }

    suspend fun confirmWithContribution(id: Long, entry: TransacaoEntity): EntryReceipt =
        repository.confirmWith(id) { finance.registrarAporte(entry.copy(origem = NOTIFICATION_ORIGIN)) }
            .also { CaptureNotifications.cancelSuggestion(getApplication(), id) }

    suspend fun undo(id: Long, receipt: EntryReceipt) = repository.undo(id, receipt)

    suspend fun ignore(id: Long) {
        repository.ignore(id)
        CaptureNotifications.cancelSuggestion(getApplication(), id)
    }

    fun restore(id: Long) = viewModelScope.launch { runCatching { repository.restore(id) } }

    // ── testes ───────────────────────────────────────────────────────

    /**
     * "Simular a leitura": o mesmo parser e o mesmo [CaptureRepository.register] da captura
     * real, mas o texto vem da tela, não do Android. Não comprova acesso nem listener.
     */
    fun simulate(sampleNumber: Int, title: String, text: String) = viewModelScope.launch {
        val sample = CaptureSamples.byNumber(sampleNumber)
        val processor = CaptureProcessor(getApplication())
        val run = System.currentTimeMillis()
        val steps = sample.stepsFor(title, text).map { (step, content) ->
            val now = System.currentTimeMillis()
            val testId = repository.insertTest(
                TesteNotificacaoEntity(exemplo = sample.number, etapa = step.label, modo = "simulacao", titulo = content.first,
                    texto = content.second, notificacaoId = 0, status = TestStatus.Received.name, detalhe = "Simulação: sem passar pelo Android",
                    sugestaoId = null, criadoEm = now, atualizadoEm = now),
            )
            val n = CapturedNotification(
                packageName = SIMULATION_PACKAGE, appLabel = "Simulação", systemKey = "sim|$run|${step.slot}",
                postedAt = now, title = content.first, text = content.second,
            )
            val report = processor.process(n, SuggestionOrigin.Simulation, testId)
            stepReport(step.label, report)
        }
        _simulation.value = SimulationReport(sample.number, steps)
    }

    fun clearSimulation() { _simulation.value = null }

    /**
     * "Enviar notificação de teste": cada etapa vira uma notificação real "Teste do FinAI",
     * publicada depois de [delayMillis] (e 4 s entre etapas). Etapas do mesmo slot usam o mesmo id
     * no Android — é isso que faz o reenvio e a atualização serem a mesma notificação.
     */
    fun sendTest(sampleNumber: Int, title: String, text: String, delayMillis: Long) = viewModelScope.launch {
        val sample = CaptureSamples.byNumber(sampleNumber)
        val base = TEST_NOTIFICATION_BASE_ID + repository.nextTestNotificationBase() % 10_000
        sample.stepsFor(title, text).forEachIndexed { index, (step, content) ->
            val now = System.currentTimeMillis()
            val testId = repository.insertTest(
                TesteNotificacaoEntity(exemplo = sample.number, etapa = step.label, modo = "notificacao", titulo = content.first,
                    texto = content.second, notificacaoId = base + step.slot, status = TestStatus.Waiting.name, detalhe = "",
                    sugestaoId = null, criadoEm = now + index, atualizadoEm = now),
            )
            TestNotificationWorker.enqueue(getApplication(), testId, delayMillis + index * STEP_GAP_MILLIS)
        }
    }

    /** "Limpar testes": envios, sugestões de teste/simulação e as notificações deles. Nada real. */
    fun clearTests() = viewModelScope.launch {
        val app = getApplication<Application>()
        val state = uiState.value
        TestNotificationWorker.cancelAll(app)
        CaptureNotifications.cancelTests(app, state.tests.map { it.notificacaoId }.filter { it > 0 })
        state.suggestions.filter { it.isTest }.forEach { CaptureNotifications.cancelSuggestion(app, it.id) }
        try { repository.clearTests() } catch (t: Throwable) { FinaiLog.w(TAG, "Falha ao limpar os testes", t) }
        _simulation.value = null
    }

    private suspend fun stepReport(label: String, report: CaptureProcessor.Report): SimulationStep {
        val outcome = report.outcomes.firstOrNull()
        val registered = report.registered.firstOrNull()
        if (outcome is ParseOutcome.Ignored || outcome == null) {
            return SimulationStep(label, "Não reconhecida: ${(outcome as? ParseOutcome.Ignored)?.reason?.label ?: "sem movimentação"}", emptyList(), emptyList())
        }
        val m = (outcome as ParseOutcome.Movement).movement
        val recognized = buildList {
            add(m.kind.label)
            m.amountCents?.let { add("Valor ${com.finai.app.domain.brl2(it)}") }
            if (m.ambiguous) add("Valores encontrados: ${m.candidatesCents.joinToString(" e ") { com.finai.app.domain.brl2(it) }}")
            m.installments?.let { n -> add("$n parcelas" + (m.installmentCents?.let { " de ${com.finai.app.domain.brl2(it)}" } ?: "")) }
            if (m.counterpart.isNotBlank()) add(m.counterpart)
            add(if (m.dateExplicit) "Data da mensagem: ${EntryOptions.dayMonth(m.date)}" else "Data: dia da notificação (${EntryOptions.dayMonth(m.date)})")
            m.category?.let { add("Categoria sugerida: $it") }
            m.cardLastDigits?.let { add("Cartão final $it") }
        }
        val missing = SuggestionRules.missing(SuggestionRules.draftOf(m), null)
            .map { if (it == MissingField.Review) "Precisa revisar: ${m.kind.label.lowercase()} não vira gasto nem receita sozinho" else it.label }
        val what = when (registered) {
            is CaptureRepository.Registered.Created -> if (registered.possibleRepeat) "Sugestão criada (pode ser repetida)" else "Sugestão criada"
            is CaptureRepository.Registered.Updated -> "Sugestão existente atualizada"
            is CaptureRepository.Registered.Repeated -> "Repetição ignorada"
            null -> "Reconhecida"
        }
        return SimulationStep(label, what, recognized, missing)
    }

    private fun isFinanceApp(pkg: String, label: String): Boolean {
        val p = pkg.lowercase()
        val l = label.lowercase()
        return KNOWN_FINANCE_PACKAGES.any { p.startsWith(it) } ||
            listOf("banco", "bank", "nubank", "itaú", "itau", "bradesco", "caixa", "santander", "inter", "picpay", "mercado pago",
                "pagbank", "c6", "neon", "next", "sicoob", "sicredi", "btg", "xp", "carteira", "wallet", "cartão", "cartao").any { l.contains(it) }
    }

    private companion object {
        const val TAG = "CaptureViewModel"
        const val SIMULATION_PACKAGE = "com.finai.app.simulacao"
        const val TEST_NOTIFICATION_BASE_ID = 60_000
        const val STEP_GAP_MILLIS = 4_000L
        val KNOWN_FINANCE_PACKAGES = listOf(
            "com.nu.production", "com.itau", "br.com.itau", "com.bradesco", "br.com.bradesco", "br.com.bb.android",
            "br.com.gabba.caixa", "br.gov.caixa", "com.santander", "br.com.santander", "br.com.intermedium", "com.picpay",
            "com.mercadopago", "br.com.uol.ps", "com.c6bank", "br.com.neon", "com.btg", "br.com.xp", "com.sicredi",
            "br.com.sicoob", "com.google.android.apps.walletnfcrel", "com.samsung.android.spay",
        )
    }
}
