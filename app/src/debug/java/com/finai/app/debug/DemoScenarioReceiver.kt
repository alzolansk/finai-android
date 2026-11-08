package com.finai.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.finai.app.data.local.FinaiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Só no build debug: apaga os dados e grava o cenário do protótipo v2 ([DemoScenarioV2]).
 * `adb shell am broadcast -a com.finai.app.DEBUG_LOAD_DEMO -n com.finai.app/.debug.DemoScenarioReceiver`
 * Depois, mude a data do emulador para 08/11/2026 (os cálculos usam o dia de hoje do aparelho).
 */
class DemoScenarioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val db = FinaiDatabase.get(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.withTransaction {
                    db.transacaoDao().deleteAll()
                    db.contaDao().deleteAll()
                    db.dividaDao().deleteAll()
                    db.objetivoDao().deleteAll()
                    DemoScenarioV2.transacoes.forEach { db.transacaoDao().upsert(it) }
                    DemoScenarioV2.contas.forEach { db.contaDao().upsert(it) }
                    DemoScenarioV2.dividas.forEach { db.dividaDao().upsert(it) }
                    DemoScenarioV2.objetivos.forEach { db.objetivoDao().upsert(it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
