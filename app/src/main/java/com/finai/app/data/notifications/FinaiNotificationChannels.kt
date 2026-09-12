package com.finai.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Os dois canais de notificação da Fase 5 (planning.md §3.9/§9), separados
 * para que o usuário possa silenciar um sem o outro nas configurações do
 * sistema: avisos financeiros (conta atrasada, orçamento estourado) pedem
 * mais atenção que o comentário do coach de comportamento.
 *
 * Criar um canal já existente é inofensivo (a API ignora), então
 * [ensureCreated] pode ser chamado sempre que o app inicia sem custo.
 */
object FinaiNotificationChannels {
    const val ALERTS_CHANNEL_ID = "finai_alerts"
    const val COACH_CHANNEL_ID = "finai_coach"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Avisos financeiros",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Contas vencendo ou atrasadas, orçamento estourando, objetivos que precisam de atenção."
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                COACH_CHANNEL_ID,
                "Coach de comportamento",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Padrões de gasto identificados nos seus lançamentos."
            },
        )
    }
}
