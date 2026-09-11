package com.finai.app.data.fixtures

import androidx.compose.ui.graphics.Color
import com.finai.app.data.model.Bill
import com.finai.app.data.model.BillStatus
import com.finai.app.data.model.Budget
import com.finai.app.data.model.ChatMessage
import com.finai.app.data.model.ChatRole
import com.finai.app.data.model.Debt
import com.finai.app.data.model.Decision
import com.finai.app.data.model.Goal
import com.finai.app.data.model.GoalBadge
import com.finai.app.data.model.ImportStepItem
import com.finai.app.data.model.NegotiationStep
import com.finai.app.data.model.NotificationItem
import com.finai.app.data.model.QuickAction
import com.finai.app.data.model.SimEffect
import com.finai.app.data.model.StatusTone
import com.finai.app.data.model.Subscription
import com.finai.app.data.model.TimelineEntry
import com.finai.app.data.model.TimelineTone
import com.finai.app.data.model.WeekBill
import com.finai.app.util.formatBrl

/**
 * Example data lifted straight from the prototype's `renderVals()` (same
 * copy, same numbers) so Phase 0 screens look identical to the design handoff
 * before any real calculation or persistence exists. Replace call sites with
 * Room-backed repositories in Phase 1 — see planning.md §9.
 */
object FinaiFixtures {

    const val greeting = "Bom dia, João"
    const val subGreeting = "Faltam 9 dias para o 13º cair. Três decisões esperam você."
    const val notifCount = 3

    val goals = listOf(
        Goal(
            id = "g1", kind = "Viagem", name = "Portugal — 10 dias",
            saved = 5500.0, target = 15000.0, eta = "Julho de 2026",
            badge = GoalBadge.OnTrack, note = "R$ 1.583/mês mantém a data.",
            analysis = "Com o bônus de março direcionado para cá, a meta fecha em maio e as passagens saem 18% mais baratas fora da alta temporada.",
            action = "Direcionar o bônus",
        ),
        Goal(
            id = "g2", kind = "Compra", name = "iPhone 15 Pro Max",
            saved = 3200.0, target = 9499.0, eta = "Abril de 2026",
            badge = GoalBadge.Reassess, note = "Concorre com Portugal no mesmo mês.",
            analysis = "Os dois objetivos consomem R$ 3.433/mês e sua capacidade é R$ 1.847. Adiar o iPhone em 3 meses resolve o conflito sem tocar na viagem.",
            action = "Adiar 3 meses",
        ),
        Goal(
            id = "g3", kind = "Reserva", name = "Reserva de emergência",
            saved = 12400.0, target = 25500.0, eta = "Fevereiro de 2027",
            badge = GoalBadge.Priority, note = "2,1 meses de custo coberto.",
            analysis = "Três meses de custo fixo é o mínimo confortável para renda assalariada. Faltam R$ 3.560 para chegar lá.",
            action = "Aumentar aporte",
        ),
    )

    const val monthlyCapacity = 1847.0
    const val capacityNote = "Média dos últimos 90 dias, já descontando contas fixas, fatura e as parcelas em aberto."

    val safeToday = formatBrl(74.3)
    const val safeNote = "Sobram R$ 892 até o dia 31 depois de contas e aportes. Ontem você ficou R$ 18 abaixo."
    const val dayLeftLabel = "12"
    const val dayProgressFraction = 0.62f

    val decisions = listOf(
        Decision(
            id = "d1", tag = "13º salário · entra dia 20", dotColor = Color(0xFF10B981),
            title = "Dividir o 13º em três partes",
            why = "R$ 4.200 quitam o rotativo, R$ 2.500 vão para Portugal, R$ 1.800 ficam livres.",
            impact = "−R$ 611/mês de juros e Portugal 2 meses antes",
            cta = "Ver o plano", question = "Como devo dividir o 13º?",
        ),
        Decision(
            id = "d2", tag = "Assinaturas", dotColor = Color(0xFFF59E0B),
            title = "Cancelar Amazon Prime e Alura",
            why = "Nenhum acesso nos últimos 60 dias. Juntas custam R$ 123,90 por mês.",
            impact = "+R$ 1.486/ano para os objetivos",
            cta = "Cancelar as duas", question = "Quais assinaturas posso cortar?",
        ),
        Decision(
            id = "d3", tag = "Cartão Nubank", dotColor = Color(0xFFF43F5E),
            title = "Negociar o rotativo antes do dia 15",
            why = "A taxa de 13,9% a.m. pode cair para 3,2% em um parcelamento de 12x.",
            impact = "Economia estimada de R$ 3.840",
            cta = "Preparar negociação", question = "Como negocio a fatura do Nubank?",
        ),
    )

    const val chatPitch = "Encontrei três coisas que mudam seu dezembro. Escolha por onde começar e eu explico com os números."
    val chatSuggestions = listOf("Como divido o 13º?", "Posso viajar em maio?", "Onde economizar?", "Negociar o Nubank")

    val timeline = listOf(
        TimelineEntry("Dez", "+R$ 8.500", "13º salário", TimelineTone.Positive),
        TimelineEntry("Jan", "—", "IPVA e matrícula", TimelineTone.Neutral),
        TimelineEntry("Fev", "—", "Mês neutro", TimelineTone.Neutral),
        TimelineEntry("Mar", "+R$ 3.000", "Bônus anual (previsto)", TimelineTone.Positive),
        TimelineEntry("Abr", "—", "Mês neutro", TimelineTone.Neutral),
        TimelineEntry("Mai", "+R$ 1.240", "Restituição do IR", TimelineTone.Positive),
        TimelineEntry("Jul", "−R$ 15.000", "Portugal", TimelineTone.Negative),
    )
    const val timelineNote = "Somando as três entradas extras, você tem R$ 12.740 fora do salário em 2026. Hoje, R$ 9.100 disso ainda não têm destino definido."

    val week = listOf(
        WeekBill("11", "DEZ", "Fatura Nubank", "Cartão de crédito", formatBrl(4387.20), "Vence hoje", StatusTone.Due),
        WeekBill("12", "DEZ", "Aluguel", "Moradia", formatBrl(1800.0), "Agendado", StatusTone.Scheduled),
        WeekBill("15", "DEZ", "Conta de luz — Enel", "Contas", formatBrl(234.67), "Pendente", StatusTone.Pending),
        WeekBill("20", "DEZ", "13º salário", "Entrada", "+ " + formatBrl(8500.0), "Previsto", StatusTone.Positive),
    )

    const val showCoachDefault = true
    const val coachTitle = "Você pede delivery quando trabalha até depois das 20h"
    const val coachBody = "9 das 11 compras no iFood aconteceram nesses dias, somando R$ 612 no mês. Nas semanas em que você deixou algo pronto na segunda, o gasto caiu 64%."

    val months = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )
    const val defaultMonthIndex = 11 // Dezembro

    const val toPayTotalCents = 9021
    const val toPayCount = "9 contas · 1 atrasada"
    const val toGetTotalCents = 17000
    const val toGetCount = "2 entradas"
    const val agendaTip = "Se pagar a fatura do Nubank no dia 20, depois do 13º, você evita o rotativo sem atrasar nada — a data limite é 15."

    val bills = listOf(
        Bill("Alura — Assinatura", "Educação · 30/11", formatBrl(109.0), BillStatus.Overdue, "AL", Color(0xFFEEF2FF), Color(0xFF4F46E5)),
        Bill("ChatGPT Plus", "Assinaturas · 03/12", formatBrl(97.0), BillStatus.Pending, "GP", Color(0xFFF4F4F5), Color(0xFF3F3F46)),
        Bill("Smart Fit", "Saúde · 03/12", formatBrl(99.9), BillStatus.Pending, "SF", Color(0xFFFFF1F2), Color(0xFFE11D48)),
        Bill("Internet Vivo Fibra", "Contas · 06/12", formatBrl(119.99), BillStatus.Paid, "VV", Color(0xFFECFDF5), Color(0xFF059669)),
        Bill("Aluguel Apartamento", "Moradia · 08/12", formatBrl(1800.0), BillStatus.Pending, "AP", Color(0xFFECFDF5), Color(0xFF059669)),
        Bill("Condomínio", "Moradia · 08/12", formatBrl(450.0), BillStatus.Pending, "CD", Color(0xFFECFDF5), Color(0xFF059669)),
        Bill("Fatura Nubank", "Cartão · 11/12", formatBrl(4387.20), BillStatus.DueToday, "NU", Color(0xFFF5F3FF), Color(0xFF7C3AED)),
    )

    const val debtTotalCents = 13640
    const val debtInterestCents = 689
    const val debtFreeLabel = "Ago 2026"
    const val debtStrategyNote = "Ordenado pelo custo do juro, não pelo tamanho da dívida."

    val debts = listOf(
        Debt(1, "Rotativo Nubank", "13,9% a.m. · sem prazo definido", "R$ 4.387", "13,9% a.m.", Color(0xFFE11D48), 0.18f, Color(0xFFF43F5E)),
        Debt(2, "Empréstimo consignado", "18 de 36 parcelas · R$ 412/mês", "R$ 7.413", "1,9% a.m.", Color(0xFFB45309), 0.50f, Color(0xFFF59E0B)),
        Debt(3, "Parcelamento notebook", "4 de 10 parcelas · R$ 310/mês", "R$ 1.840", "0% a.m.", Color(0xFF059669), 0.40f, Color(0xFF10B981)),
    )

    const val negotiationTitle = "Ligação para o Nubank — antes do dia 15"
    val negotiationSteps = listOf(
        NegotiationStep(1, "Diga que é cliente há 4 anos, sem atraso acima de 10 dias nos últimos 24 meses."),
        NegotiationStep(2, "Peça o parcelamento do rotativo em 12x citando a taxa de 3,2% a.m. anunciada no app."),
        NegotiationStep(3, "Se recusarem, ofereça R$ 4.200 de entrada com o 13º e peça o saldo em 6x sem juros."),
        NegotiationStep(4, "Peça o número do protocolo e a proposta por escrito antes de aceitar."),
    )

    val budgets = listOf(
        Budget("Alimentação", 1120.0, 1200.0, "No ritmo atual fecha R$ 180 acima. Restam 20 dias.", Color(0xFFB45309), Color(0xFFF59E0B)),
        Budget("Transporte", 313.0, 600.0, "Folga de R$ 287.", Color(0xFF059669), Color(0xFF10B981)),
        Budget("Lazer", 1429.0, 800.0, "Estourado em R$ 629 — Rock in Rio e a hotel de réveillon.", Color(0xFFE11D48), Color(0xFFF43F5E)),
        Budget("Assinaturas", 283.0, 200.0, "Sete assinaturas ativas, três sem uso.", Color(0xFFE11D48), Color(0xFFF43F5E)),
        Budget("Saúde", 536.0, 700.0, "Dentro do previsto.", Color(0xFF059669), Color(0xFF10B981)),
    )

    val subscriptions = listOf(
        Subscription("Amazon Prime", "Sem acesso há 74 dias", formatBrl(14.9), "Cancelar"),
        Subscription("Alura", "Último acesso em setembro", formatBrl(109.0), "Cancelar"),
        Subscription("ChatGPT Plus", "Uso diário — manter", formatBrl(97.0), "Manter"),
    )

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
        else -> "47 lançamentos importados"
    }

    fun importSubtitle(stage: Int) = when (stage) {
        0 -> "Nubank, Itaú, Bradesco, Inter e C6 reconhecidos automaticamente."
        1 -> "Identificando estabelecimentos, datas e parcelas."
        else -> "R$ 4.387,20 em 47 lançamentos. 3 possíveis duplicados foram separados."
    }

    fun importCta(stage: Int) = if (stage == 2) "Revisar lançamentos" else "Escolher arquivo"

    val quickActions = listOf(
        QuickAction("R$", Color(0xFFECFDF5), Color(0xFF059669), "Lançar gasto", "Manual ou por voz"),
        QuickAction("↑", Color(0xFFEEF2FF), Color(0xFF4F46E5), "Importar fatura", "PDF, planilha ou foto"),
        QuickAction("?", Color(0xFFFFFBEB), Color(0xFFB45309), "Posso comprar?", "Simular antes de decidir"),
        QuickAction("◎", Color(0xFFFFF1F2), Color(0xFFE11D48), "Novo objetivo", "Compra, viagem ou reserva"),
    )

    val simPresets = listOf(350.0, 1200.0, 2400.0, 6800.0)
    const val defaultSimAmount = 2400.0

    fun simEffects(amount: Double): List<SimEffect> {
        val free = monthlyCapacity
        val ok = amount <= free
        val warn = amount > free && amount <= free * 2
        val slackDelta = free - amount
        return listOf(
            SimEffect(
                "Folga do mês", "depois da compra", formatBrl(slackDelta),
                if (slackDelta >= 0) Color(0xFF059669) else Color(0xFFE11D48),
            ),
            SimEffect(
                "Portugal", if (ok) "data mantida" else "nova previsão",
                if (ok) "Jul 2026" else if (warn) "Ago 2026" else "Out 2026",
                if (ok) Color(0xFF059669) else Color(0xFFB45309),
            ),
            SimEffect(
                "Reserva de emergência", "cobertura de custo fixo",
                if (ok) "2,1 meses" else "1,7 mês",
                if (ok) Color(0xFF059669) else Color(0xFFB45309),
            ),
        )
    }

    val initialMessages = listOf(
        ChatMessage(
            ChatRole.Ai,
            "Bom dia, João. Dezembro tem duas coisas fora do normal: o 13º entra dia 20 e a fatura do Nubank vem 38% acima da média. Quer decidir o destino do 13º agora?",
        ),
    )

    /** Same canned-answer heuristic as the prototype's `answer()` — placeholder until Phase 2 wires a real AI provider. */
    fun canned(question: String): String {
        val t = question.lowercase()
        return when {
            t.contains("13") ->
                "Proposta para o 13º de R$ 8.500: R$ 4.200 quitam o rotativo do Nubank (economiza R$ 611/mês de juros), R$ 2.500 vão para Portugal — antecipa a viagem de julho para maio — e R$ 1.800 ficam livres. Nada é movido sem você aprovar."
            t.contains("portugal") || t.contains("viagem") ->
                "Portugal está em R$ 5.500 de R$ 15.000. No ritmo atual você chega em julho. Se o bônus de março (R$ 3.000) for direcionado para lá, chega em maio e as passagens caem cerca de 18% fora da alta temporada."
            t.contains("nubank") || t.contains("fatura") || t.contains("dívida") || t.contains("divida") ->
                "O rotativo do Nubank custa 13,9% ao mês — R$ 611 só de juros. Um parcelamento negociado em 12x sai a 3,2% ao mês. Posso montar o roteiro da ligação com os números que você deve citar."
            t.contains("economizar") || t.contains("assinatura") ->
                "Três assinaturas somam R$ 174,80/mês e duas delas você não abriu nos últimos 60 dias. Cancelando Amazon Prime e Alura você libera R$ 123,90/mês — é uma parcela inteira de Portugal."
            else ->
                "Olhando seus últimos 90 dias: renda fixa de R$ 8.500, gasto médio de R$ 6.653 e capacidade real de poupança de R$ 1.847/mês. Me diga o que você está pensando em fazer e eu mostro o efeito antes de você decidir."
        }
    }

    val notifications = listOf(
        NotificationItem(Color(0xFFF43F5E), "Fatura do Nubank vence hoje", "R$ 4.387,20. Pagar o mínimo joga R$ 611 de juros para janeiro."),
        NotificationItem(Color(0xFFF59E0B), "Alimentação em 93% do limite", "Faltam 20 dias e R$ 80. Três jantares fora estouram a categoria."),
        NotificationItem(Color(0xFF10B981), "13º confirmado para o dia 20", "R$ 8.500. Ainda sem destino definido."),
    )
}
