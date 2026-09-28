package com.finai.app.domain

/** Uma dica de tela: aponta para o componente registrado como [targetId]. */
data class Tip(val targetId: String, val title: String, val body: String)

/**
 * Roteiro das dicas por tela (planning.md §9 Fase 7, item 6). Substitui o tour de 7 passos
 * em tela cheia: cada tela mostra poucas dicas, na primeira visita, apontando para o
 * componente de verdade. Kotlin puro — quem decide *quais* dicas aparecem é [visibleTips];
 * o desenho e a medição dos componentes ficam em `ui/components/Tips.kt`.
 */
object TipScript {
    const val HOME = "home"
    const val AGENDA = "agenda"
    const val GOALS = "goals"
    const val DEBTS = "debts"
    const val BUDGETS = "budgets"
    const val CHAT = "chat"

    val screens: Set<String> = setOf(HOME, AGENDA, GOALS, DEBTS, BUDGETS, CHAT)

    private val scripts: Map<String, List<Tip>> = mapOf(
        HOME to listOf(
            Tip("home.situation", "Sua situação", "Quanto está livre até o próximo salário, já descontado o que vence até lá e o destino da sobra (dívida cara ou metas)."),
            Tip("home.explain", "De onde vem o número", "Toque em \"Entenda este valor\" para ver a conta passo a passo."),
            Tip("home.week", "O que vence primeiro", "As contas e lançamentos dos próximos 7 dias, em ordem de data."),
            Tip("nav.add", "Lançar algo novo", "Gastos, receitas e importação de fatura começam pelo +."),
        ),
        AGENDA to listOf(
            Tip("agenda.month", "Navegue pelos meses", "Use as setas para ver o que vence em outros meses."),
            Tip("agenda.bill", "Marque como paga", "Toque numa conta para marcar como paga (tocar de novo reabre) e numa parcela para registrar o pagamento."),
            Tip("agenda.transactions", "Lançamentos do mês", "Tudo que você lançou ou importou aparece aqui, com as ações de cada item."),
        ),
        GOALS to listOf(
            Tip("goals.card", "Sua meta", "Progresso, prazo e quanto o plano do app destina a ela até o salário. Use o botão verde para registrar um aporte."),
            Tip("goals.insight", "Leitura da IA", "A IA explica o plano para esta meta: o momento, o próximo passo e o risco. Ela não muda o destino do dinheiro."),
        ),
        DEBTS to listOf(
            Tip("debts.list", "Ordem de pagamento", "As dívidas estão ordenadas pelo custo do juro, não pelo tamanho."),
            Tip("debts.pay", "Paguei a parcela", "Registre cada parcela paga: a dívida avança e o gasto entra no mês."),
            Tip("debts.call", "Ensaiar a ligação", "Treine a negociação com a IA fazendo o papel do atendente."),
        ),
        BUDGETS to listOf(
            Tip("budgets.list", "Limites por categoria", "Toque numa categoria para definir ou ajustar o limite do mês."),
        ),
        CHAT to listOf(
            Tip("chat.suggestions", "Comece por aqui", "Toque numa sugestão ou escreva sua pergunta sobre seus números."),
        ),
    )

    fun forScreen(screen: String): List<Tip> = scripts[screen].orEmpty()

    /**
     * Dicas a mostrar agora: nenhuma se a tela já foi vista; senão, só as que têm o
     * componente na tela (lista vazia, cartão ausente → a dica é pulada).
     */
    fun visibleTips(screen: String, availableTargets: Set<String>, seen: Set<String>): List<Tip> =
        if (screen in seen) emptyList() else forScreen(screen).filter { it.targetId in availableTargets }
}
