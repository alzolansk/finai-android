package com.finai.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.notifications.FinaiNotificationChannels
import com.finai.app.data.notifications.RichNotifier
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.balance.FreeToSpend
import com.finai.app.domain.notify.NotificationPlanner
import com.finai.app.util.FinaiLog
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * O lembrete do dia ("Gastou alguma coisa hoje?"), com os botões **Lançar gasto** e **Nada hoje**.
 * Roda uma vez por dia perto da hora escolhida e só notifica se o dia ainda não tem registro:
 * quem já lançou algo (ou já respondeu "Nada hoje") não é incomodado. Sem IA e sem rede.
 *
 * O horário é aproximado: o WorkManager entrega dentro da janela que o Android permitir
 * (economia de bateria pode atrasar), nunca um alarme exato.
 */
class DailyReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext
        val prefs = FinaiPreferences(context)
        if (prefs.dailyReminder.first() && prefs.initialSetupComplete.first()) {
            val repository = FinanceRepository(FinaiDatabase.get(context))
            val today = LocalDate.now()
            val transacoes = repository.transacoes.first()
            val quiet = prefs.quietDays.first()
            if (!NotificationPlanner.loggedToday(transacoes, quiet, today)) {
                val free = runCatching {
                    FreeToSpend.of(repository.contas.first(), transacoes, repository.dividas.first(), today, prefs.safetyMarginCents.first())?.freeCents
                }.getOrNull()
                RichNotifier(context).post(NotificationPlanner.daily(today, NotificationPlanner.streak(transacoes, quiet, today), free),
                    FinaiNotificationChannels.REMINDER_CHANNEL_ID)
            }
        }
        Result.success()
    } catch (t: Throwable) {
        FinaiLog.e("DailyReminderWorker", "Falha no lembrete diário", t)
        Result.success()
    }

    companion object {
        private const val WORK = "finai_daily_reminder"

        /** (Re)agenda para a próxima ocorrência de [hour]; trocar a hora substitui o agendamento. */
        fun schedule(context: Context, hour: Int, replace: Boolean = false) {
            val now = LocalDateTime.now()
            var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setInitialDelay(Duration.between(now, next).toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }
}
