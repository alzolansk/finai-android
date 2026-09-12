package com.finai.app.data.fixtures

import androidx.compose.ui.graphics.Color
import com.finai.app.data.model.QuickAction

/**
 * What's left of the Phase 0 prototype data: **nenhum dado financeiro**.
 * Tudo que é número saiu daqui na Fase 1 (Room + calculators de `domain/`,
 * ver [com.finai.app.state.FinanceViewModel]); a conversa do chat virou
 * chamada real de IA + histórico no Room na Fase 2; e os avisos do sino
 * passaram a ser calculados por [com.finai.app.domain.AlertCalculator].
 *
 * Só sobrou configuração de UI sem dado pessoal (rótulos do menu do FAB,
 * presets e sugestões de pergunta) e [offlineReply], o texto local usado
 * quando a própria camada de IA está indisponível.
 */
object FinaiFixtures {

    const val chatPitch = "Pergunte sobre suas finanças — a IA lê seus lançamentos, contas, objetivos e dívidas reais para responder."
    val chatSuggestions = listOf("Como está minha folga este mês?", "Qual dívida devo priorizar?", "Onde economizar?")

    val simPresets = listOf(200.0, 500.0, 1200.0, 3000.0)
    const val defaultSimAmount = 500.0

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
        QuickAction("gasto", Color(0xFFD1FAE5), Color(0xFFECFDF5), Color(0xFF047857), "Lançar gasto", "Manual"),
        QuickAction("importar", Color(0xFFE0E7FF), Color(0xFFEEF2FF), Color(0xFF4338CA), "Importar fatura", "PDF, planilha ou foto"),
        QuickAction("simular", Color(0xFFFEF3C7), Color(0xFFFFFBEB), Color(0xFFB45309), "Posso comprar?", "Simular antes de decidir"),
        QuickAction("objetivo", Color(0xFFFFE4E6), Color(0xFFFFF1F2), Color(0xFFBE123C), "Novo objetivo", "Compra, viagem ou reserva"),
    )
}
