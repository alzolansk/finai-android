package com.finai.app.data.fixtures

import androidx.compose.ui.graphics.Color
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.model.ImportStepItem
import com.finai.app.data.model.NotificationItem
import com.finai.app.data.model.QuickAction

/**
 * What's left of the Phase 0 prototype data after Fase 1 (planning.md §9)
 * replaced everything backed by real numbers with Room + `domain/`
 * calculators (see [com.finai.app.state.FinanceViewModel]). Only genuinely
 * IA-dependent copy (chat) or later-phase placeholders (importação — Fase 4,
 * notificações proativas — Fase 5) remain fixture-driven here, plus a couple
 * of plain UI menu labels that were never "data" to begin with.
 */
object FinaiFixtures {

    const val notifCount = 1

    const val chatPitch = "Pergunte sobre suas finanças — leio seus lançamentos, contas, objetivos e dívidas reais."
    val chatSuggestions = listOf("Como está minha folga este mês?", "Qual dívida devo priorizar?", "Onde economizar?")

    val simPresets = listOf(200.0, 500.0, 1200.0, 3000.0)
    const val defaultSimAmount = 500.0

    val initialMessages = listOf(
        ChatMessage(
            ChatRole.Ai,
            "Olá! Sou o assistente do FinAI. A conversa com IA de verdade chega na Fase 2 — por enquanto respondo com dicas gerais sobre como usar o app.",
        ),
    )

    /** Canned, non-AI fallback — the real assistant (reading the user's own numbers) is Fase 2 scope. */
    fun canned(question: String): String {
        val t = question.lowercase()
        return when {
            t.contains("folga") || t.contains("gastar") ->
                "Veja o cartão \"Pode gastar hoje\" na tela Início — ele já é calculado a partir das suas contas e lançamentos reais."
            t.contains("dívida") || t.contains("divida") ->
                "Na tela Dívidas, a ordem de ataque já é calculada pelo custo do juro de cada uma que você cadastrou."
            t.contains("economizar") || t.contains("assinatura") ->
                "Veja a tela Limites — assinaturas sem uso há 45 dias ou mais aparecem sinalizadas."
            else ->
                "Ainda não tenho um modelo de IA conectado (chega na Fase 2). Enquanto isso, os números das telas Início, Agenda, Objetivos, Dívidas e Limites já são reais, calculados a partir do que você cadastrar."
        }
    }

    fun importSteps(stage: Int): List<ImportStepItem> {
        val texts = listOf(
            "Extrai os lançamentos sem enviar o PDF para fora do aparelho",
            "Classifica cada compra em uma categoria",
            "Detecta assinaturas recorrentes e parcelas",
            "Separa duplicados para você confirmar",
        )
        return texts.mapIndexed { i, text ->
            val done = stage == 2 || (stage == 1 && i == 0)
            ImportStepItem(text, done)
        }
    }

    fun importTitle(stage: Int) = when (stage) {
        0 -> "Solte a fatura aqui"
        1 -> "Lendo o documento..."
        else -> "Importação simulada — Fase 4"
    }

    fun importSubtitle(stage: Int) = when (stage) {
        0 -> "OCR on-device e classificação chegam na Fase 4. Este fluxo é uma prévia visual."
        1 -> "Identificando estabelecimentos, datas e parcelas."
        else -> "Nenhum lançamento real foi criado — a extração de verdade é Fase 4."
    }

    fun importCta(stage: Int) = if (stage == 2) "Entendi" else "Escolher arquivo"

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
