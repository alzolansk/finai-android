package com.finai.app.data.assistant

import androidx.room.withTransaction
import com.finai.app.data.backup.BackupCodec
import com.finai.app.data.backup.FinaiBackup
import com.finai.app.data.local.FINAI_DB_VERSION
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AcaoAssistenteEntity
import com.finai.app.data.local.entity.AcompanhamentoEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.MemoriaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.PlanoEntity
import com.finai.app.data.local.entity.SessaoAssistenteEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.assistant.ActionExecutor
import com.finai.app.domain.assistant.ActionInfo
import com.finai.app.domain.assistant.ActionPlan
import com.finai.app.domain.assistant.ActionRequest
import com.finai.app.domain.assistant.ActionStatus
import com.finai.app.domain.assistant.AnswerKind
import com.finai.app.domain.assistant.AnswerMetric
import com.finai.app.domain.assistant.AnswerOption
import com.finai.app.domain.assistant.AssistantAnswer
import com.finai.app.domain.assistant.AssistantSession
import com.finai.app.domain.assistant.AssistantSnapshot
import com.finai.app.domain.assistant.EntityRef
import com.finai.app.domain.assistant.ExecResult
import com.finai.app.domain.assistant.PendingAction
import com.finai.app.domain.assistant.PendingOption
import com.finai.app.domain.assistant.RowChange
import com.finai.app.domain.assistant.SessionStore
import com.finai.app.domain.assistant.Tone
import com.finai.app.domain.assistant.ToolCall
import com.finai.app.domain.assistant.WatchOutcome
import com.finai.app.domain.assistant.WatchRules
import com.finai.app.util.FinaiLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Retrato financeiro consistente (§5.1): todas as tabelas lidas numa transação só, com a margem
 * e o dia daquele instante. É a única entrada das consultas e das ações do assistente.
 */
class FinancialSnapshotRepository(private val db: FinaiDatabase, private val prefs: FinaiPreferences) {
    suspend fun read(today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis()): AssistantSnapshot {
        val margin = prefs.safetyMarginCents.first()
        val known = prefs.knownAccounts.first()
        return db.withTransaction {
            AssistantSnapshot(
                contas = db.contaDao().getAll(), transacoes = db.transacaoDao().getAll(), dividas = db.dividaDao().getAll(),
                marginCents = margin, today = today, objetivos = db.objetivoDao().getAll(), orcamentos = db.orcamentoCategoriaDao().getAll(),
                faturas = db.faturaCartaoDao().getAll(), memoria = db.memoriaDao().getAll(),
                regras = db.regraCategoriaDao().getAll().associate { it.estabelecimentoNormalizado to it.categoria },
                acompanhamentos = db.acompanhamentoDao().getAll(), planos = db.planoDao().getAll(), nowMillis = now, extraAccounts = known,
            )
        }
    }
}

/** As linhas de um recibo, por tabela. O formato em disco é o do backup, que já sabe ler versões antigas. */
private data class Rows(
    val tx: List<TransacaoEntity> = emptyList(), val bills: List<ContaEntity> = emptyList(), val goals: List<ObjetivoEntity> = emptyList(),
    val debts: List<DividaEntity> = emptyList(), val budgets: List<OrcamentoCategoriaEntity> = emptyList(), val watches: List<AcompanhamentoEntity> = emptyList(),
    val memory: List<MemoriaEntity> = emptyList(), val plans: List<PlanoEntity> = emptyList(),
) {
    fun encode(): String = BackupCodec.encode(FinaiBackup(
        schemaVersion = FINAI_DB_VERSION, exportedAt = 0, transacoes = tx, faturas = emptyList(), contas = bills, objetivos = goals, dividas = debts,
        orcamentos = budgets, assinaturas = emptyList(), mensagens = emptyList(), memoria = memory, acompanhamentos = watches, planos = plans,
    ))

    /** Como as linhas voltam do disco: a comparação de "mudou depois?" é feita nessa mesma forma. */
    fun normalized(): Rows = decode(encode())

    companion object {
        fun decode(text: String): Rows = BackupCodec.decode(text, FINAI_DB_VERSION).let {
            Rows(it.transacoes, it.contas, it.objetivos, it.dividas, it.orcamentos, it.acompanhamentos, it.memoria, it.planos)
        }
    }
}

/**
 * Grava o que [ActionPlan] calculou (§5.10, "execução controlada pelo app"):
 *
 * - **idempotência**: a chave da operação autorizada é única; repetir (retentativa, retomada do
 *   processo, resposta duplicada do provedor) devolve `already_applied` sem gravar de novo;
 * - **revalidação**: cada linha só é alterada se ainda estiver como no retrato usado para planejar;
 *   se mudou, nada é gravado (`conflict`) — o alvo nunca é trocado por outro em silêncio;
 * - **atomicidade**: todas as linhas da ação entram numa transação, com o recibo;
 * - **desfazer**: pelo recibo, e só se nada do que a ação gravou foi alterado depois.
 *
 * Conta/fatura passa por [FinanceRepository.salvarConta], o mesmo caminho da Agenda (avisos da Caixa).
 */
class AssistantActionExecutor(private val db: FinaiDatabase, private val repository: FinanceRepository) : ActionExecutor {
    private val actions = db.acaoAssistenteDao()

    private suspend fun current(): Rows = Rows(
        db.transacaoDao().getAll(), db.contaDao().getAll(), db.objetivoDao().getAll(), db.dividaDao().getAll(), db.orcamentoCategoriaDao().getAll(),
        db.acompanhamentoDao().getAll(), db.memoriaDao().getAll(), db.planoDao().getAll(),
    )

    private fun stillCurrent(c: RowChange, now: Rows): Boolean = when (c) {
        is RowChange.Tx -> c.before == null || now.tx.firstOrNull { it.id == c.before!!.id } == c.before
        is RowChange.Bill -> c.before == null || now.bills.firstOrNull { it.id == c.before!!.id } == c.before
        is RowChange.Goal -> c.before == null || now.goals.firstOrNull { it.id == c.before!!.id } == c.before
        is RowChange.Debt -> c.before == null || now.debts.firstOrNull { it.id == c.before!!.id } == c.before
        is RowChange.Budget -> (c.after ?: c.before)?.let { key -> now.budgets.firstOrNull { it.categoria == key.categoria && it.mesReferencia == key.mesReferencia } } == c.before
        is RowChange.Watch -> c.before == null || now.watches.firstOrNull { it.id == c.before!!.id } == c.before
        is RowChange.Memory -> c.before == null || now.memory.firstOrNull { it.id == c.before!!.id } == c.before
        is RowChange.Plan -> c.before == null || now.plans.firstOrNull { it.id == c.before!!.id } == c.before
    }

    override suspend fun apply(plan: ActionPlan, key: String, conversationId: Long): ExecResult = try {
        db.withTransaction {
            actions.byKey(key)?.let { return@withTransaction ExecResult(ActionStatus.AlreadyApplied, it.id, summary = it.resumo) }
            if (plan.changes.isEmpty()) return@withTransaction ExecResult(ActionStatus.Failed, message = "Não havia nada a gravar.")
            val now = current()
            if (plan.changes.any { !stillCurrent(it, now) }) {
                return@withTransaction ExecResult(ActionStatus.Conflict, message = "Um dos registros mudou enquanto eu preparava a alteração.")
            }
            var before = Rows(); var after = Rows()
            fun newId(given: Long, generated: Long) = if (given == 0L) generated else given
            plan.changes.forEach { c ->
                when (c) {
                    is RowChange.Tx -> {
                        c.before?.let { before = before.copy(tx = before.tx + it) }
                        val a = c.after
                        if (a == null) db.transacaoDao().delete(c.before!!) else after = after.copy(tx = after.tx + a.copy(id = newId(a.id, db.transacaoDao().upsert(a))))
                    }
                    is RowChange.Bill -> {
                        c.before?.let { before = before.copy(bills = before.bills + it) }
                        val a = c.after
                        if (a == null) db.contaDao().delete(c.before!!) else after = after.copy(bills = after.bills + a.copy(id = newId(a.id, repository.salvarConta(a))))
                    }
                    is RowChange.Goal -> {
                        c.before?.let { before = before.copy(goals = before.goals + it) }
                        val a = c.after
                        if (a == null) db.objetivoDao().delete(c.before!!) else after = after.copy(goals = after.goals + a.copy(id = newId(a.id, db.objetivoDao().upsert(a))))
                    }
                    is RowChange.Debt -> {
                        c.before?.let { before = before.copy(debts = before.debts + it) }
                        val a = c.after
                        if (a == null) db.dividaDao().delete(c.before!!) else after = after.copy(debts = after.debts + a.copy(id = newId(a.id, db.dividaDao().upsert(a))))
                    }
                    is RowChange.Budget -> {
                        c.before?.let { before = before.copy(budgets = before.budgets + it) }
                        val a = c.after
                        if (a == null) db.orcamentoCategoriaDao().delete(c.before!!.categoria, c.before!!.mesReferencia)
                        else { db.orcamentoCategoriaDao().upsert(a); after = after.copy(budgets = after.budgets + a) }
                    }
                    is RowChange.Watch -> {
                        c.before?.let { before = before.copy(watches = before.watches + it) }
                        val a = c.after
                        if (a == null) db.acompanhamentoDao().delete(c.before!!.id) else after = after.copy(watches = after.watches + a.copy(id = newId(a.id, db.acompanhamentoDao().upsert(a))))
                    }
                    is RowChange.Memory -> {
                        c.before?.let { before = before.copy(memory = before.memory + it) }
                        val a = c.after
                        if (a == null) db.memoriaDao().delete(c.before!!.id) else after = after.copy(memory = after.memory + a.copy(id = newId(a.id, db.memoriaDao().insert(a))))
                    }
                    is RowChange.Plan -> {
                        c.before?.let { before = before.copy(plans = before.plans + it) }
                        val a = c.after
                        if (a == null) db.planoDao().delete(c.before!!.id) else after = after.copy(plans = after.plans + a.copy(id = newId(a.id, db.planoDao().upsert(a))))
                    }
                }
            }
            val receipt = JSONObject().put("before", before.encode()).put("after", after.encode()).toString()
            val id = actions.insert(AcaoAssistenteEntity(chave = key, conversaId = conversationId, tipo = plan.request.name, resumo = plan.summary,
                recibo = receipt, estado = APPLIED, criadoEm = System.currentTimeMillis()))
            actions.prune(KEEP)
            ExecResult(ActionStatus.Applied, id, summary = plan.summary)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        // A transação inteira voltou atrás: nada ficou pela metade.
        FinaiLog.e(TAG, "Falha ao aplicar a ação do assistente", t)
        ExecResult(ActionStatus.Failed, message = t.message?.takeIf { it.isNotBlank() && t is IllegalArgumentException } ?: "Não consegui gravar essa alteração.")
    }

    override suspend fun undo(conversationId: Long, actionId: Long?): ExecResult = try {
        db.withTransaction {
            val action = (if (actionId != null) actions.byId(actionId) else actions.lastApplied(conversationId))
                ?: return@withTransaction ExecResult(ActionStatus.Unsupported, message = "Não há alteração minha para desfazer nesta conversa.")
            if (action.estado != APPLIED) return@withTransaction ExecResult(ActionStatus.Unsupported, action.id, "Essa alteração já foi desfeita.")
            val json = JSONObject(action.recibo)
            val before = Rows.decode(json.getString("before"))
            val after = Rows.decode(json.getString("after"))
            val now = current().normalized()
            // Só desfaz se o que a ação gravou continua igual: o que foi mexido depois não é apagado por cima.
            val untouched = after.tx.all { a -> now.tx.firstOrNull { it.id == a.id } == a } && after.bills.all { a -> now.bills.firstOrNull { it.id == a.id } == a } &&
                after.goals.all { a -> now.goals.firstOrNull { it.id == a.id } == a } && after.debts.all { a -> now.debts.firstOrNull { it.id == a.id } == a } &&
                after.budgets.all { a -> now.budgets.firstOrNull { it.categoria == a.categoria && it.mesReferencia == a.mesReferencia } == a } &&
                after.plans.all { a -> now.plans.firstOrNull { it.id == a.id } == a } &&
                // Linha que a ação excluiu não pode ter voltado com outro conteúdo.
                before.tx.filter { b -> after.tx.none { it.id == b.id } }.all { b -> now.tx.none { it.id == b.id } }
            if (!untouched) return@withTransaction ExecResult(ActionStatus.Conflict, action.id, "esse registro foi alterado depois, então desfazer agora apagaria a mudança mais nova. Ajuste pela Agenda ou por Planos.")
            after.tx.filter { a -> before.tx.none { it.id == a.id } }.forEach { db.transacaoDao().delete(it) }
            before.tx.forEach { db.transacaoDao().upsert(it) }
            after.bills.filter { a -> before.bills.none { it.id == a.id } }.forEach { db.contaDao().delete(it) }
            before.bills.forEach { repository.salvarConta(it) }
            after.goals.filter { a -> before.goals.none { it.id == a.id } }.forEach { db.objetivoDao().delete(it) }
            before.goals.forEach { db.objetivoDao().upsert(it) }
            after.debts.filter { a -> before.debts.none { it.id == a.id } }.forEach { db.dividaDao().delete(it) }
            before.debts.forEach { db.dividaDao().upsert(it) }
            after.budgets.filter { a -> before.budgets.none { it.categoria == a.categoria && it.mesReferencia == a.mesReferencia } }.forEach { db.orcamentoCategoriaDao().delete(it.categoria, it.mesReferencia) }
            before.budgets.forEach { db.orcamentoCategoriaDao().upsert(it) }
            after.watches.filter { a -> before.watches.none { it.id == a.id } }.forEach { db.acompanhamentoDao().delete(it.id) }
            before.watches.forEach { db.acompanhamentoDao().upsert(it) }
            after.memory.filter { a -> before.memory.none { it.id == a.id } }.forEach { db.memoriaDao().delete(it.id) }
            before.memory.forEach { db.memoriaDao().insert(it) }
            after.plans.filter { a -> before.plans.none { it.id == a.id } }.forEach { db.planoDao().delete(it.id) }
            before.plans.forEach { db.planoDao().upsert(it) }
            actions.update(action.copy(estado = UNDONE))
            ExecResult(ActionStatus.Undone, action.id, summary = action.resumo)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        FinaiLog.e(TAG, "Falha ao desfazer a ação do assistente", t)
        ExecResult(ActionStatus.Conflict, actionId, "não consegui restaurar os registros.")
    }

    /** A ação ainda pode ser desfeita? Usado para o botão "Desfazer" do cartão. */
    suspend fun undoable(actionId: Long): Boolean = actions.byId(actionId)?.estado == APPLIED

    companion object {
        private const val TAG = "AssistantExecutor"
        const val APPLIED = "applied"
        const val UNDONE = "undone"
        private const val KEEP = 80
    }
}

/** Resposta estruturada ↔ JSON de `mensagens_chat.estrutura`. Campo ausente = padrão, para ler versões antigas. */
object AssistantAnswerCodec {
    fun encode(a: AssistantAnswer): String = JSONObject().apply {
        put("kind", a.kind.name); put("title", a.title); put("headline", a.headline)
        put("metrics", JSONArray(a.metrics.map { JSONObject().put("l", it.label).put("v", it.value).put("t", it.tone.name) }))
        put("evidence", JSONArray(a.evidence)); put("notes", JSONArray(a.notes))
        put("options", JSONArray(a.options.map { JSONObject().put("l", it.label).put("s", it.send) }))
        a.action?.let { put("action", JSONObject().put("status", it.status.key).putOpt("id", it.actionId).put("undo", it.canUndo).putOpt("date", it.agendaDate?.toString())) }
    }.toString()

    fun decode(text: String?): AssistantAnswer? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(text)
            fun strings(key: String) = o.optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty()
            fun objects(key: String) = o.optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }.orEmpty()
            AssistantAnswer(
                kind = runCatching { AnswerKind.valueOf(o.optString("kind")) }.getOrDefault(AnswerKind.Query),
                title = o.optString("title"), headline = o.optString("headline"),
                metrics = objects("metrics").map { AnswerMetric(it.optString("l"), it.optString("v"), runCatching { Tone.valueOf(it.optString("t")) }.getOrDefault(Tone.Neutral)) },
                evidence = strings("evidence"), notes = strings("notes"),
                options = objects("options").map { AnswerOption(it.optString("l"), it.optString("s").ifBlank { it.optString("l") }) },
                action = o.optJSONObject("action")?.let { a ->
                    ActionInfo(ActionStatus.of(a.optString("status")) ?: ActionStatus.Failed, if (a.has("id")) a.getLong("id") else null, a.optBoolean("undo"),
                        a.optString("date").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
                },
            )
        }.getOrNull()
    }
}

/** Estado da conversa em `sessoes_assistente`: sobrevive ao processo; some com a conversa. */
class RoomSessionStore(private val db: FinaiDatabase) : SessionStore {
    private fun map(o: JSONObject?): Map<String, String> = o?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } }.orEmpty()

    override suspend fun load(conversationId: Long): AssistantSession {
        val row = db.sessaoAssistenteDao().get(conversationId) ?: return AssistantSession()
        return runCatching {
            val o = JSONObject(row.estado)
            AssistantSession(
                pending = o.optJSONObject("pending")?.let { p ->
                    PendingAction(
                        request = ActionRequest(p.getString("name"), map(p.optJSONObject("args"))), kind = p.optString("kind", PendingAction.CONFIRM),
                        options = p.optJSONArray("options")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.map { PendingOption(it.optString("label"), map(it.optJSONObject("args"))) } }.orEmpty(),
                        messageId = p.optLong("messageId"), snapshotId = p.optString("snapshotId"),
                    )
                },
                focus = o.optJSONArray("focus")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.map { EntityRef(it.optString("type"), it.optLong("id"), it.optString("label")) } }.orEmpty(),
                lastActionId = if (o.has("lastActionId")) o.getLong("lastActionId") else null,
                lastScenario = o.optJSONObject("lastScenario")?.let { ToolCall(it.getString("name"), map(it.optJSONObject("args"))) },
            )
        }.getOrDefault(AssistantSession())
    }

    override suspend fun save(conversationId: Long, session: AssistantSession) {
        val o = JSONObject()
        session.pending?.let { p ->
            o.put("pending", JSONObject().put("name", p.request.name).put("args", JSONObject(p.request.args)).put("kind", p.kind)
                .put("options", JSONArray(p.options.map { JSONObject().put("label", it.label).put("args", JSONObject(it.args)) }))
                .put("messageId", p.messageId).put("snapshotId", p.snapshotId))
        }
        o.put("focus", JSONArray(session.focus.map { JSONObject().put("type", it.type).put("id", it.id).put("label", it.label) }))
        session.lastActionId?.let { o.put("lastActionId", it) }
        session.lastScenario?.let { o.put("lastScenario", JSONObject().put("name", it.name).put("args", JSONObject(it.args))) }
        db.sessaoAssistenteDao().upsert(SessaoAssistenteEntity(conversationId, o.toString(), System.currentTimeMillis()))
    }

    suspend fun delete(conversationId: Long) = db.sessaoAssistenteDao().delete(conversationId)
}

/**
 * Reavalia os acompanhamentos (§5.9) contra o retrato atual e guarda o resultado. Devolve só os
 * que passaram a valer agora: é isso que vira aviso — a mesma condição não avisa de novo.
 */
class AssistantWatcher(private val db: FinaiDatabase, private val snapshots: FinancialSnapshotRepository) {
    suspend fun evaluate(today: LocalDate = LocalDate.now()): List<WatchOutcome> {
        val s = snapshots.read(today)
        val active = s.acompanhamentos.filter { it.estado == WatchRules.ACTIVE }
        if (active.isEmpty()) return emptyList()
        val outcomes = active.map { WatchRules.evaluate(it, s) }
        val fresh = outcomes.filter { it.met && it.rule.ultimoResultado != WatchRules.resultKey(it, today) }
        outcomes.forEach { o ->
            val key = WatchRules.resultKey(o, today)
            if (o.rule.ultimoResultado != key || o.rule.ultimaAvaliacao != today.toString()) {
                db.acompanhamentoDao().upsert(o.rule.copy(ultimaAvaliacao = today.toString(), ultimoResultado = key))
            }
        }
        return fresh
    }
}
