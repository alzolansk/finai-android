package com.finai.app.data.fixtures

import androidx.compose.ui.graphics.Color
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.model.NotificationItem
import com.finai.app.data.model.QuickAction

/**
 * What's left of the Phase 0 prototype data after Fase 1 (planning.md §9)
 * replaced everything backed by real numbers with Room + `domain/`
 * calculators (see [com.finai.app.state.FinanceViewModel]), and Fase 2
 * replaced the chat's canned replies with real Gemini calls (see
 * [com.finai.app.state.AppViewModel]). Only later-phase placeholders
 * (notificações proativas — Fase 5) remain
 * fixture-driven here, plus [offlineReply] — the local fallback used only
 * when the AI call itself is unavailable — and a couple of plain UI menu
 * labels that were never "data" to begin with.
 */
object FinaiFixtures {

    const val notifCount = 1

    const val chatPitch = "Pergunte sobre suas finanças — a IA lê seus lançamentos, contas, objetivos e dívidas reais para responder."
    val chatSuggestions = listOf("Como está minha folga este mês?", "Qual dívida devo priorizar?", "Onde economizar?")

    val simPresets = listOf(200.0, 500.0, 1200.0, 3000.0)
    const val defaultSimAmount = 500.0

    val initialMessages = listOf(
        ChatMessage(
            ChatRole.Ai,
            "Olá! Sou o assistente do FinAI. Posso responder com base nos seus números reais — contas, objetivos, dívidas e limites.",
        ),
    )

    /**
     * Local fallback shown only when [com.finai.app.data.ai.AiProvider] itself is unavailable
     * (no key configured, quota esgotada, sem rede) — [reason] already explains why; this adds
     * a still-useful pointer to the real, non-AI numbers already on screen (planning.md §4's
     * resiliência requirement: the app stays useful even with the AI layer down).
     */
    fun offlineReply(question: String, reason: String): String {
        val t = question.lowercase()
        val tip = when {
            t.contains("folga") || t.contains("gastar") ->
                "Enquanto isso, veja o cartão \"Pode gastar hoje\" na tela Início — já é calculado a partir das suas contas e lançamentos reais."
            t.contains("dívida") || t.contains("divida") ->
                "Enquanto isso, veja a tela Dívidas — a ordem de ataque já é calculada pelo custo do juro de cada uma."
            t.contains("economizar") || t.contains("assinatura") ->
                "Enquanto isso, veja a tela Limites — assinaturas sem uso aparecem sinalizadas."
            else ->
                "Os números das telas Início, Agenda, Objetivos, Dívidas e Limites continuam reais mesmo sem a IA."
        }
        return "$reason $tip"
    }

    val quickActions = listOf(
        QuickAction("R$", Color(0xFFECFDF5), Color(0xFF059669), "Lançar gasto", "Manual"),
        QuickAction("↑", Color(0xFFEEF2FF), Color(0xFF4F46E5), "Importar fatura", "PDF, planilha ou foto"),
        QuickAction("?", Color(0xFFFFFBEB), Color(0xFFB45309), "Posso comprar?", "Simular antes de decidir"),
        QuickAction("◎", Color(0xFFFFF1F2), Color(0xFFE11D48), "Novo objetivo", "Compra, viagem ou reserva"),
    )

    val notifications = listOf(
        NotificationItem(Color(0xFF10B981), "Notificações proativas chegam na Fase 5", "Vencimentos e limites já são calculados nas telas Agenda e Limites."),
    )
}
