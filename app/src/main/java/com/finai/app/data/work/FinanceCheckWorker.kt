package com.finai.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finai.app.data.ai.AiRouter
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.notifications.FinaiNotifier
import com.finai.app.data.notifications.NotificationContentBuilder
import com.finai.app.data.notifications.NotificationDedupeStore
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.AlertCalculator
import com.finai.app.domain.BehaviorCoach
import com.finai.app.domain.BudgetCalculator
import com.finai.app.domain.GoalCalculator
import com.finai.app.domain.SavingsCapacityCalculator
import com.finai.app.domain.SubscriptionCalculator
import com.finai.app.domain.monthKey
import com.finai.app.domain.monthRangeMillis
import com.finai.app.util.FinaiLog
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * A "rotina diária" da Fase 5 (planning.md §5/§9): recalcula tudo localmente
 * a partir do Room — os mesmos calculators de `domain/` que as telas usam,
 * nenhum novo — e só encosta na camada de IA (Fase 3, [AiRouter]) para
 * redigir o texto de uma notificação quando já existe um motivo real para
 * mandar uma. Roda com o app fechado (WorkManager sobrevive a processo morto
 * e a reboot, ver [schedule]) e nunca decide sozinha se algo é relevante —
 * essa decisão é 100% de [AlertCalculator]/[BehaviorCoach], em Kotlin puro.
 *
 * Camadas separadas de propósito (nenhuma regra de detecção mora aqui):
 * detecção → [AlertCalculator]/[BehaviorCoach]; conteúdo →
 * [NotificationContentBuilder]; entrega → [FinaiNotifier]; deduplicação →
 * [NotificationDedupeStore]. Este arquivo só orquestra as quatro, na ordem
 * certa, e decide o que é "novo o bastante para chamar IA" — nunca a IA.
 */
class FinanceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            runCheck(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            FinaiLog.e(TAG, "Falha ao rodar a checagem financeira proativa", t)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private suspend fun runCheck(context: Context) {
        val db = FinaiDatabase.get(context)
        val repository = FinanceRepository(db)
        val dedupe = NotificationDedupeStore(db.notificacaoEnviadaDao())
        val today = LocalDate.now()

        // ── 1. Recalcula tudo localmente — nenhuma chamada de IA até aqui ──
        val contas = repository.contas.first()
        val objetivos = repository.objetivos.first()
        val dividas = repository.dividas.first()
        val transacoes = repository.transacoes.first()
        val assinaturas = repository.assinaturas.first()
        val orcamentos = repository.orcamentosDoMes(monthKey(today)).first()

        val monthlyCapacityCents = SavingsCapacityCalculator.monthlyCapacityCents(contas, dividas)
        val goalPlans = GoalCalculator.plan(objetivos, monthlyCapacityCents, today)
        val transacoesDoMes = transacoes.filter { it.data in monthRangeMillis(today) }
        val budgetProgress = BudgetCalculator.forCategories(orcamentos, transacoesDoMes, today)
        val subscriptionInsights = SubscriptionCalculator.insights(assinaturas, today)

        // ── 2. Detecção de eventos — mesma regra determinística da tela ────
        val alerts = AlertCalculator.alerts(contas, budgetProgress, subscriptionInsights, goalPlans, today)
        val pattern = BehaviorCoach.detect(transacoes, today).firstOrNull()

        // ── 3. Deduplicação — só o que ainda não foi notificado hoje ───────
        val newAlerts = alerts.filter { dedupe.shouldSend(it.id, today) }
        val newPattern = pattern?.takeIf { dedupe.shouldSend(it.id, today) }

        if (newAlerts.isEmpty() && newPattern == null) {
            FinaiLog.i(TAG, "Nada de novo para notificar hoje — nenhuma chamada de IA, nenhuma notificação.")
        } else {
            // Router só é criado (e só é chamado) quando existe algo para redigir — planning.md §9's
            // "evita gerar notificação, e gastar cota, todo dia à toa". No máximo 2 chamadas por
            // execução: uma para o lote de alertas, uma para o coach — nunca uma por evento.
            val aiRouter = AiRouter(context)
            val contentBuilder = NotificationContentBuilder(aiRouter)
            val notifier = FinaiNotifier(context)

            if (newAlerts.isNotEmpty()) {
                val content = contentBuilder.forAlerts(newAlerts)
                notifier.notifyAlerts(content, FinaiNotifier.ALERTS_NOTIFICATION_ID)
                newAlerts.forEach { dedupe.markSent(it.id, today) }
            }

            if (newPattern != null) {
                val content = contentBuilder.forCoach(newPattern)
                notifier.notifyCoach(content, FinaiNotifier.COACH_NOTIFICATION_ID)
                dedupe.markSent(newPattern.id, today)
            }
        }

        // ── 4. Limpeza — some da lista o registro de evento resolvido ──────
        val activeKeys = alerts.map { it.id } + listOfNotNull(pattern?.id)
        dedupe.pruneExcept(activeKeys)
    }

    companion object {
        private const val TAG = "FinanceCheckWorker"
        private const val UNIQUE_WORK_NAME = "finai_finance_check"
        private const val MAX_RETRIES = 3

        /**
         * Enfileira a rotina periódica — chamado uma vez em [com.finai.app.FinaiApplication.onCreate],
         * então em toda subida de processo (inclusive após reboot, já que WorkManager persiste seu
         * próprio agendamento). [ExistingPeriodicWorkPolicy.KEEP] garante que reabrir o app não reinicia
         * a janela de 24h nem perde o histórico de retry.
         *
         * Sem restrição de rede: a detecção é 100% local (planning.md §4 — "funcionamento offline
         * parcial") e deve rodar mesmo sem internet; só o texto da IA depende de rede, e já degrada
         * graciosamente sozinho (ver [com.finai.app.data.notifications.NotificationContentBuilder]).
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FinanceCheckWorker>(24, TimeUnit.HOURS, 4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Roda a checagem uma vez, imediatamente — usado pelo botão "Testar agora" nas configurações de IA. */
        fun runOnce(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<FinanceCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
