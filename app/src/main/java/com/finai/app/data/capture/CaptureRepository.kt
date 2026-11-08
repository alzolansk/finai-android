package com.finai.app.data.capture

import androidx.room.withTransaction
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.data.local.entity.TesteNotificacaoEntity
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.EntryReceipt
import com.finai.app.domain.capture.CaptureDedupe
import com.finai.app.domain.capture.CapturedNotification
import com.finai.app.domain.capture.MovementKind
import com.finai.app.domain.capture.ParseOutcome
import com.finai.app.domain.capture.ParsedMovement
import com.finai.app.domain.capture.SuggestionDraft
import com.finai.app.domain.capture.SuggestionOrigin
import com.finai.app.domain.capture.SuggestionRules
import com.finai.app.domain.capture.SuggestionState
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import kotlinx.coroutines.flow.Flow

/** A sugestão não está mais pendente: foi resolvida em outra tela/toque ([state]) ou apagada (nulo). */
class SuggestionUnavailableException(val state: SuggestionState?) : IllegalStateException(
    when (state) {
        SuggestionState.Confirmed -> "Essa movimentação já foi salva."
        SuggestionState.Ignored -> "Essa movimentação foi ignorada."
        else -> "Essa movimentação não existe mais."
    },
)

/**
 * Persistência do lançamento assistido por notificação. Toda gravação que mexe em dinheiro passa
 * por [FinanceRepository] (os mesmos `salvarLancamento`/`salvarCompraCartao`/`registrarAporte` do
 * "+") **dentro da mesma transação** que muda o estado da sugestão: ou o lançamento e a
 * confirmação entram juntos, ou nada entra. A checagem "ainda pendente" também está dentro da
 * transação, então dois toques ou duas telas nunca gravam duas vezes.
 */
class CaptureRepository(private val db: FinaiDatabase, private val finance: FinanceRepository = FinanceRepository(db)) {
    private val dao = db.sugestaoLancamentoDao()
    private val tests = db.testeNotificacaoDao()

    val suggestions: Flow<List<SugestaoLancamentoEntity>> = dao.observeAll()
    val testRuns: Flow<List<TesteNotificacaoEntity>> = tests.observeAll()

    fun observe(id: Long): Flow<SugestaoLancamentoEntity?> = dao.observe(id)
    suspend fun get(id: Long): SugestaoLancamentoEntity? = dao.get(id)

    // ── captura ──────────────────────────────────────────────────────

    sealed interface Registered {
        data class Created(val id: Long, val possibleRepeat: Boolean) : Registered
        data class Updated(val id: Long) : Registered
        /** Mesma notificação de novo, ou atualização de uma já resolvida: nada muda. */
        data class Repeated(val id: Long, val resolved: Boolean) : Registered
    }

    /** Grava as movimentações de uma notificação, decidindo repetição/atualização numa transação. */
    suspend fun register(
        n: CapturedNotification,
        movements: List<ParseOutcome.Movement>,
        origin: SuggestionOrigin,
        testId: Long?,
        now: Long = System.currentTimeMillis(),
    ): List<Registered> = db.withTransaction {
        purge(now)
        movements.map { outcome ->
            // Linha de notificação agrupada: cada linha é uma notificação à parte.
            val key = outcome.lineIndex?.let { "${n.systemKey}#${outcome.signature}" } ?: n.systemKey
            val known = dao.candidatesFor(key, n.packageName, now - KNOWN_WINDOW_MILLIS).map { it.toKnown() }
            when (val d = CaptureDedupe.decide(n.packageName, key, outcome.signature, outcome.movement, now, known)) {
                is CaptureDedupe.Decision.Create -> Registered.Created(
                    dao.insert(entityOf(n, key, outcome, origin, testId, d.possibleRepeat, now)), d.possibleRepeat,
                )
                is CaptureDedupe.Decision.UpdatePending -> {
                    val old = requireNotNull(dao.get(d.id))
                    val fresh = entityOf(n, key, outcome, origin, testId, old.possivelRepeticao, now)
                    dao.update(fresh.copy(id = old.id, capturadoEm = old.capturadoEm, avisadaEm = old.avisadaEm, testeId = old.testeId ?: testId))
                    Registered.Updated(d.id)
                }
                is CaptureDedupe.Decision.Repeat -> Registered.Repeated(d.id, resolved = false)
                is CaptureDedupe.Decision.KeepResolved -> Registered.Repeated(d.id, resolved = true)
            }
        }
    }

    private fun entityOf(
        n: CapturedNotification, key: String, outcome: ParseOutcome.Movement, origin: SuggestionOrigin,
        testId: Long?, possibleRepeat: Boolean, now: Long,
    ): SugestaoLancamentoEntity {
        val m = outcome.movement
        return SugestaoLancamentoEntity(
            origem = origin.key, pacote = n.packageName, appNome = n.appLabel, capturadoEm = now, atualizadoEm = now,
            chaveSistema = key, assinatura = outcome.signature, tipo = m.kind.name, valorCentavos = m.amountCents,
            valoresEncontrados = m.candidatesCents.joinToString(";"), descricao = m.counterpart, categoria = m.category,
            data = m.date.toEpochMillis(), dataExplicita = m.dateExplicit, parcelas = m.installments,
            valorParcelaCentavos = m.installmentCents, cartaoFinal = m.cardLastDigits, contaSugerida = m.accountHint,
            estado = SuggestionState.Pending.key, possivelRepeticao = possibleRepeat, avisadaEm = null, resolvidaEm = null,
            lancamentoIds = "", textoOriginal = n.body().take(MAX_TEXT_CHARS).ifBlank { null }, testeId = testId,
        )
    }

    // ── confirmação ──────────────────────────────────────────────────

    /**
     * "Salvar lançamento" no quadro: o mesmo caminho do "+" ([SuggestionRules.entryOf] →
     * `salvarLancamento`, ou `salvarCompraCartao` no cartão, com fatura e parcelas). Sugestão de
     * teste só muda de estado: nada é gravado nas finanças.
     */
    suspend fun confirm(id: Long, draft: SuggestionDraft, cardDue: EntryOptions.CardDue?, now: Long = System.currentTimeMillis()): EntryReceipt {
        require(SuggestionRules.missing(draft, cardDue).isEmpty()) { "Ainda falta preencher: confira os campos marcados." }
        return confirmWith(id, now) {
            val entry = SuggestionRules.entryOf(draft)
            if (draft.isCard) finance.salvarCompraCartao(entry, SuggestionRules.cardPaymentOf(draft, cardDue))
            else finance.salvarLancamento(entry)
        }
    }

    /**
     * Confirmação pelo formulário ("Editar"): [write] é a gravação do próprio formulário, já com
     * origem de notificação. Roda dentro da transação que confere e marca a sugestão.
     */
    suspend fun confirmWith(id: Long, now: Long = System.currentTimeMillis(), write: suspend () -> EntryReceipt): EntryReceipt = db.withTransaction {
        val s = dao.get(id) ?: throw SuggestionUnavailableException(null)
        val state = SuggestionState.of(s.estado)
        if (state != SuggestionState.Pending) throw SuggestionUnavailableException(state)
        val receipt = if (SuggestionOrigin.of(s.origem).isTest) {
            // Teste: demonstra o fluxo sem tocar nas finanças reais.
            EntryReceipt(if (MovementKind.valueOf(s.tipo).isIncome) com.finai.app.domain.TransactionType.Receita else com.finai.app.domain.TransactionType.Gasto,
                emptyList(), s.data.toLocalDate())
        } else write()
        dao.update(s.copy(estado = SuggestionState.Confirmed.key, resolvidaEm = now, atualizadoEm = now,
            lancamentoIds = receipt.transactionIds.joinToString(";"), textoOriginal = null))
        receipt
    }

    /** "Desfazer" depois de salvar: o lançamento sai (pelo mesmo `desfazer` do "+") e a sugestão volta a pendente. */
    suspend fun undo(id: Long, receipt: EntryReceipt, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val s = dao.get(id) ?: throw SuggestionUnavailableException(null)
        if (SuggestionState.of(s.estado) != SuggestionState.Confirmed) return@withTransaction
        if (receipt.transactionIds.isNotEmpty()) finance.desfazer(receipt, now)
        dao.update(s.copy(estado = SuggestionState.Pending.key, resolvidaEm = null, atualizadoEm = now, lancamentoIds = ""))
    }

    /** "Ignorar": fica guardada sem texto, para uma atualização da mesma notificação não a reabrir. */
    suspend fun ignore(id: Long, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val s = dao.get(id) ?: throw SuggestionUnavailableException(null)
        val state = SuggestionState.of(s.estado)
        if (state != SuggestionState.Pending) throw SuggestionUnavailableException(state)
        dao.update(s.copy(estado = SuggestionState.Ignored.key, resolvidaEm = now, atualizadoEm = now, textoOriginal = null))
    }

    /** Volta uma ignorada para "Para confirmar". */
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val s = dao.get(id) ?: return@withTransaction
        if (SuggestionState.of(s.estado) == SuggestionState.Ignored) {
            dao.update(s.copy(estado = SuggestionState.Pending.key, resolvidaEm = null, atualizadoEm = now))
        }
    }

    suspend fun markShown(id: Long, now: Long = System.currentTimeMillis()) = dao.markShown(id, now)

    /** Retenção: texto original só enquanto pendente e no máximo 30 dias; resolvidas somem em 90. */
    suspend fun purge(now: Long = System.currentTimeMillis()) {
        dao.clearOldText(now - TEXT_RETENTION_DAYS * DAY_MILLIS)
        dao.deleteResolvedBefore(now - RESOLVED_RETENTION_DAYS * DAY_MILLIS)
    }

    // ── área de testes ───────────────────────────────────────────────

    suspend fun insertTest(t: TesteNotificacaoEntity): Long = tests.insert(t)
    suspend fun testRun(id: Long): TesteNotificacaoEntity? = tests.get(id)
    suspend fun setTestStatus(id: Long, status: TestStatus, detail: String = "", suggestionId: Long? = null, now: Long = System.currentTimeMillis()) =
        tests.setStatus(id, status.name, detail, suggestionId, now)
    suspend fun setTestStatusIfWaiting(id: Long, status: TestStatus, detail: String, now: Long = System.currentTimeMillis()) =
        tests.setStatusIfWaiting(id, status.name, detail, now)
    suspend fun nextTestNotificationBase(): Int = tests.maxNotificationId() + 1

    /** "Limpar testes": só sugestões de teste/simulação e o histórico de envios. Nada real. */
    suspend fun clearTests() = db.withTransaction {
        dao.deleteTests()
        tests.deleteAll()
    }

    companion object {
        const val TEXT_RETENTION_DAYS = 30L
        const val RESOLVED_RETENTION_DAYS = 90L
        const val MAX_TEXT_CHARS = 600
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        /** Chave e app nas últimas 24 h bastam para decidir repetição ([CaptureDedupe]). */
        private const val KNOWN_WINDOW_MILLIS = DAY_MILLIS
    }
}

/** Etapas reais de um envio de teste. "Recebida" só quando o listener de fato leu. */
enum class TestStatus(val label: String) {
    Waiting("Aguardando envio"),
    Published("Notificação publicada"),
    NotPublished("Não publicada"),
    Received("Recebida pelo listener"),
    SuggestionCreated("Sugestão criada"),
    SuggestionUpdated("Sugestão existente atualizada"),
    Repeated("Repetição ignorada"),
    NotRecognized("Mensagem não reconhecida"),
}

internal fun SugestaoLancamentoEntity.toKnown() = CaptureDedupe.Known(
    id = id, packageName = pacote, systemKey = chaveSistema, signature = assinatura,
    state = SuggestionState.of(estado), capturedAt = capturadoEm, kind = MovementKind.valueOf(tipo),
    amountCents = valorCentavos, counterpart = descricao,
)

/** O que o parser leu, de volta da linha gravada (para montar o rascunho do quadro). */
fun SugestaoLancamentoEntity.toMovement(): ParsedMovement = ParsedMovement(
    kind = MovementKind.valueOf(tipo),
    amountCents = valorCentavos,
    candidatesCents = valoresEncontrados.split(';').mapNotNull { it.toLongOrNull() },
    counterpart = descricao,
    date = data.toLocalDate(),
    dateExplicit = dataExplicita,
    installments = parcelas,
    installmentCents = valorParcelaCentavos,
    cardLastDigits = cartaoFinal,
    accountHint = contaSugerida,
    category = categoria,
    ambiguous = valorCentavos == null && valoresEncontrados.isNotBlank(),
)

val SugestaoLancamentoEntity.state: SuggestionState get() = SuggestionState.of(estado)
val SugestaoLancamentoEntity.originKind: SuggestionOrigin get() = SuggestionOrigin.of(origem)
val SugestaoLancamentoEntity.isTest: Boolean get() = originKind.isTest
val SugestaoLancamentoEntity.savedIds: List<Long> get() = lancamentoIds.split(';').mapNotNull { it.toLongOrNull() }
