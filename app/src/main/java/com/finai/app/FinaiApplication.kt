package com.finai.app

import android.app.Application
import com.finai.app.data.notifications.FinaiNotificationChannels
import com.finai.app.data.work.FinanceCheckWorker

/**
 * Ponto de entrada da Fase 5 (planning.md §9): cria os canais de notificação
 * e agenda a rotina periódica assim que o processo sobe — inclusive depois
 * de um boot do aparelho, já que o próprio WorkManager persiste o
 * agendamento e se reprograma sozinho (nenhum `BroadcastReceiver` de boot é
 * necessário). Nada mais mora aqui: dado é Room, estado de tela é os
 * ViewModels em `state/`.
 */
class FinaiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FinaiNotificationChannels.ensureCreated(this)
        FinanceCheckWorker.schedule(this)
    }
}
