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
import com.finai.app.data.notifications.NotificationDedupeStore
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.AlertCalculator
import com.finai.app.domain.BehaviorCoach
import com.finai.app.domain.BudgetCalculator
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
 * [com.finai.app.domain.notify.NotificationPlanner] (modelos fixos, sem IA); entrega → [com.finai.app.data.notifications.RichNotifier]; deduplicação →
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

        // O mesmo plano central das telas: o aviso do sino e a notificação não fazem conta própria.
        val plan = com.finai.app.domain.FinancialPlan.build(
            contas, transacoes, dividas, objetivos, today,
            com.finai.app.data.prefs.FinaiPreferences(applicationContext).safetyMarginCents.first(),
        )
        val goalPlans = plan.goals
        val transacoesDoMes = transacoes.filter { it.data in monthRangeMillis(today) }
        val budgetProgress = BudgetCalculator.forCategories(orcamentos, transacoesDoMes, today)
        val subscriptionInsights = SubscriptionCalculator.insights(assinaturas, today)

        // ── 2. Detecção de eventos — mesma regra determinística da tela ────
        val prefs = com.finai.app.data.prefs.FinaiPreferences(context)
        val dismissed = prefs.dismissedInsights.first()
        val pattern = com.finai.app.domain.HomeCoach.detect(transacoes, today, dismissed)
        val hasIncome = transacoes.any { it.tipo == "Receita" && it.valorCentavos > 0 } || contas.any { it.tipo == "a_receber" }
        val overview = com.finai.app.domain.GoalPlanning.overview(plan, hasIncome, today)
        val insights = com.finai.app.domain.GoalPlanning.insights(plan, overview, dividas, hasIncome, today)
        val inbox = com.finai.app.data.repository.InboxRepository(db)
        val items = com.finai.app.domain.inbox.InboxCatalog.build(contas, transacoes, dividas, plan,
            budgetProgress, insights, db.sugestaoLancamentoDao().observeAll().first(), today, pattern,
            AlertCalculator.alerts(contas, budgetProgress, subscriptionInsights, goalPlans, today, plan.shortfall))
        inbox.synchronize(items, dismissed, today)

        // ── 3. Deduplicação — só o que ainda não foi notificado hoje ───────
        val visibleItems = com.finai.app.domain.inbox.InboxRules.visible(items, inbox.states.first(), today)
        val specs = visibleItems.filter { dedupe.shouldSend(it.id, today) }
            .mapNotNull { com.finai.app.domain.notify.NotificationPlanner.forInbox(it, today) }
        val rich = com.finai.app.data.notifications.RichNotifier(context)
        if (specs.isEmpty()) {
            FinaiLog.i(TAG, "Nada de novo para notificar hoje.")
        } else {
            // Uma notificação por evento, com os botões dele, até MAX_PER_RUN; o resto fica na Caixa.
            // Sem chamada de IA: o texto vem do planner, e o número, do item da Caixa.
            specs.take(com.finai.app.domain.notify.NotificationPlanner.MAX_PER_RUN).forEach { spec ->
                val channel = if (spec.look == com.finai.app.domain.notify.NotifyLook.Coach) com.finai.app.data.notifications.FinaiNotificationChannels.COACH_CHANNEL_ID
                else com.finai.app.data.notifications.FinaiNotificationChannels.ALERTS_CHANNEL_ID
                // A dispensa pode ter acontecido enquanto a rotina rodava: conferir o Room de novo.
                val still = com.finai.app.domain.inbox.InboxRules.visible(items, inbox.states.first(), today).any { it.id == spec.key }
                if (still && rich.post(spec, channel)) dedupe.markSent(spec.key, today)
            }
        }

        // Fechamento do mês: um convite por mês, do dia 1 ao 7, enquanto houver passo pendente.
        val limits = orcamentos.associate { it.categoria to it.limiteMensalCentavos }
        val answers = db.fechamentoDao().getAll().filter { it.mes == com.finai.app.domain.closing.MonthClosing.monthFor(today).toString() }.associate { it.passo to it.resposta }
        val closing = com.finai.app.domain.closing.MonthClosing.of(contas, transacoes, dividas, limits, null, plan.livreCents, answers, today)
        val closingKey = "fechamento-${closing.key}"
        if (!closing.empty && closing.pending && closing.doneCount == 0 && today.dayOfMonth <= com.finai.app.domain.closing.MonthClosing.VISIBLE_UNTIL_DAY &&
            db.notificacaoEnviadaDao().find(closingKey) == null) {
            if (rich.post(com.finai.app.domain.notify.NotificationPlanner.closing(closing.monthName, closing.leftCents, closing.key),
                    com.finai.app.data.notifications.FinaiNotificationChannels.ALERTS_CHANNEL_ID)) dedupe.markSent(closingKey, today)
        }

        // "Lembrar depois da próxima entrada" do simulador de compra.
        prefs.purchaseReminder.first()?.split('|')?.takeIf { it.size == 3 }?.let { (date, name, cents) ->
            val due = runCatching { LocalDate.parse(date) }.getOrNull()
            if (due != null && !today.isBefore(due)) {
                val value = cents.toLongOrNull() ?: 0
                rich.post(com.finai.app.domain.notify.NotifySpec(
                    key = "compra-lembrete", look = com.finai.app.domain.notify.NotifyLook.Goal, eyebrow = "VOCÊ PEDIU PARA LEMBRAR",
                    title = "${name.ifBlank { "Aquela compra" }}: ainda quer?", body = "A entrada que você esperava já passou. Simule de novo com o saldo de hoje.",
                    amount = com.finai.app.util.formatBrl0(value / 100.0),
                    buttons = listOf(com.finai.app.domain.notify.NotifyButton("Abrir o FinAI", com.finai.app.domain.notify.NotifyAction.OpenInbox, primary = true)),
                ), com.finai.app.data.notifications.FinaiNotificationChannels.ALERTS_CHANNEL_ID)
                prefs.setPurchaseReminder(null)
            }
        }
        val alerts = visibleItems.mapNotNull { it.alert }

        // ── 4. Limpeza — some da lista o registro de evento resolvido ──────
        val activeKeys = alerts.map { it.id } + listOfNotNull(pattern?.id) + closingKey
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
         * parcial") e deve rodar mesmo sem internet; o texto das notificações vem de modelos fixos
         * ([com.finai.app.domain.notify.NotificationPlanner]), sem chamada de IA.
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
