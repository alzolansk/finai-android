package com.finai.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.finai.app.data.capture.CaptureRepository
import com.finai.app.data.capture.isTest
import com.finai.app.data.capture.toMovement
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AvisoEntity
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.capture.SuggestionRules
import com.finai.app.domain.notify.NotificationPlanner
import com.finai.app.domain.notify.NotifyAction
import com.finai.app.util.FinaiLog
import com.finai.app.util.formatBrl
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Executa os botões das notificações **sem abrir o app**: marcar conta como paga (e desfazer),
 * adiar para amanhã, dispensar, "Já sei", "Nada hoje", salvar ou ignorar uma movimentação lida.
 * Não exportado: só PendingIntents criados pelo próprio FinAI chegam aqui. Tudo passa pelos
 * mesmos repositórios que as telas usam; nenhuma regra nova mora aqui.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val action = runCatching { NotifyAction.valueOf(intent.getStringExtra(EXTRA_ACTION).orEmpty()) }.getOrNull() ?: return
        val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
        val target = intent.getLongExtra(EXTRA_TARGET, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                handle(app, action, key, target, title, notificationId)
            } catch (t: Throwable) {
                FinaiLog.e(TAG, "Falha ao executar a ação da notificação", t)
                NotificationManagerCompat.from(app).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: NotifyAction, key: String, target: Long, title: String, notificationId: Int) {
        val db = FinaiDatabase.get(context)
        val repository = FinanceRepository(db)
        val notifier = RichNotifier(context)
        val today = LocalDate.now()
        fun dismissNotification() = NotificationManagerCompat.from(context).cancel(notificationId)
        fun confirm(spec: com.finai.app.domain.notify.NotifySpec) = notifier.post(spec, FinaiNotificationChannels.ALERTS_CHANNEL_ID, timeoutMs = DONE_TIMEOUT_MS, silent = true)

        when (action) {
            NotifyAction.PayBill -> {
                val conta = db.contaDao().getAll().firstOrNull { it.id == target }
                if (conta == null || conta.status == "pago") { dismissNotification(); return }
                repository.salvarConta(conta.copy(status = "pago"))
                confirm(NotificationPlanner.done(key, "${conta.nome} paga", "${formatBrl(conta.valorCentavos / 100.0)} saiu do seu saldo. Bom trabalho.", undo = true, targetId = conta.id))
            }
            NotifyAction.UndoPay -> {
                db.contaDao().getAll().firstOrNull { it.id == target }?.let { repository.salvarConta(it.copy(status = "pendente")) }
                dismissNotification()
            }
            NotifyAction.Snooze -> {
                val previous = db.avisoDao().getAll().firstOrNull { it.id == key }
                db.avisoDao().upsert(AvisoEntity(key, previous?.tipo ?: "aviso:0", "adiado", previous?.valorRef ?: 0,
                    vistoEm = today.toString(), adiadoAte = today.plusDays(1).toString()))
                confirm(NotificationPlanner.done(key, "Combinado, lembro amanhã", title, undo = false, targetId = null))
            }
            NotifyAction.Dismiss -> {
                val previous = db.avisoDao().getAll().firstOrNull { it.id == key }
                if (previous != null || !key.startsWith("fechamento-")) {
                    db.avisoDao().upsert(AvisoEntity(key, previous?.tipo ?: "aviso:0", "dispensado", previous?.valorRef ?: 0,
                        vistoEm = previous?.vistoEm ?: today.toString(), dispensadoEm = today.toString()))
                }
                dismissNotification()
            }
            NotifyAction.AckCoach -> {
                FinaiPreferences(context).dismissInsight(key)
                repository.lembrar("Já sabe: $title", "padrao", key)
                confirm(NotificationPlanner.done(key, "Anotado", "Não aviso mais sobre isso.", undo = false, targetId = null))
            }
            NotifyAction.NothingToday -> {
                FinaiPreferences(context).markQuietDay(today)
                val prefs = FinaiPreferences(context)
                val streak = NotificationPlanner.streak(repository.transacoes.first(), prefs.quietDays.first(), today)
                confirm(NotificationPlanner.done(key, "Dia registrado", if (streak >= 2) "$streak dias seguidos em dia. Até amanhã." else "Sem gastos hoje. Até amanhã.", undo = false, targetId = null))
            }
            NotifyAction.CaptureSave -> {
                val capture = CaptureRepository(db, repository)
                val suggestion = db.sugestaoLancamentoDao().getAll().firstOrNull { it.id == target }
                if (suggestion == null || suggestion.estado != "pendente") { dismissNotification(); return }
                val draft = SuggestionRules.draftOf(suggestion.toMovement())
                val due = draft.card?.let { EntryOptions.resolveCardDue(it, draft.date, db.faturaCartaoDao().getAll(), db.contaDao().getAll()) }
                // A regra de "pode salvar direto" é conferida de novo na hora do toque: algo pode ter mudado.
                if (suggestion.possivelRepeticao || SuggestionRules.missing(draft, due).isNotEmpty()) {
                    notifier.post(NotificationPlanner.forCapture(suggestion.id, suggestion.descricao, suggestion.valorCentavos, suggestion.appNome, canSaveDirectly = false, test = suggestion.isTest),
                        FinaiNotificationChannels.CAPTURE_CHANNEL_ID, silent = true)
                    return
                }
                capture.confirm(suggestion.id, draft, due)
                confirm(NotificationPlanner.done(key, "Lançamento salvo", "${suggestion.descricao.ifBlank { "Movimentação" }} · ${suggestion.valorCentavos?.let { formatBrl(it / 100.0) }.orEmpty()}", undo = false, targetId = null))
            }
            NotifyAction.CaptureIgnore -> {
                runCatching { CaptureRepository(db, repository).ignore(target) }
                dismissNotification()
            }
            else -> dismissNotification()
        }
    }

    companion object {
        const val ACTION = "com.finai.app.NOTIFICATION_ACTION"
        const val EXTRA_ACTION = "com.finai.app.extra.ACTION"
        const val EXTRA_KEY = "com.finai.app.extra.KEY"
        const val EXTRA_TARGET = "com.finai.app.extra.TARGET"
        const val EXTRA_TITLE = "com.finai.app.extra.TITLE"
        const val EXTRA_NOTIFICATION_ID = "com.finai.app.extra.NOTIFICATION_ID"
        private const val DONE_TIMEOUT_MS = 12_000L
        private const val TAG = "NotificationAction"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
