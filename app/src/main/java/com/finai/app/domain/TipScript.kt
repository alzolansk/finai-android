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
    /** Planos (Metas | Compromissos). A chave antiga "goals" é mantida: quem já viu as dicas não as revê. */
    const val GOALS = "goals"
    const val SPENDING = "spending"
    const val CHAT = "chat"

    val screens: Set<String> = setOf(HOME, AGENDA, GOALS, SPENDING, CHAT)

    private val scripts: Map<String, List<Tip>> = mapOf(
        HOME to listOf(
            Tip("home.situation", "Quanto está livre", "O que dá para gastar até a próxima entrada, já descontado o que vence até lá e a sua margem. Toque para ver de onde vem."),
            Tip("home.week", "O que vence primeiro", "Os próximos 7 dias em ordem de data. Toque numa conta para pagar; tocar de novo reabre."),
            Tip("nav.add", "Lançar algo novo", "O + abre o lançamento. Dá para escrever ou falar a frase, como \"uber 23 ontem\"."),
        ),
        AGENDA to listOf(
            Tip("agenda.month", "Navegue pelos meses", "Use as setas para ver o que vence em outros meses."),
            Tip("agenda.bill", "Marque como paga", "Toque numa conta para marcar como paga (tocar de novo reabre). Segure para editar, excluir ou ver os itens da fatura. Numa parcela, o toque abre pagar e editar a data."),
            Tip("agenda.transactions", "Lançamentos do mês", "Toque num lançamento para ver os detalhes, editar (só este mês ou daqui em diante, se ele se repete) ou excluir."),
        ),
        GOALS to listOf(
            Tip("plans.tabs", "Metas e compromissos", "Metas é o que você quer alcançar. Compromissos junta empréstimos, parcelas no cartão, faturas e contas fixas."),
            Tip("plans.capacity", "Quanto cabe guardar", "A sobra média dos próximos 6 meses, contra o que as metas pedem por mês. Quando não cabe, o app mostra um conflito por vez."),
        ),
        SPENDING to listOf(
            Tip("spending.search", "Busca", "Procure qualquer lançamento por nome, categoria ou conta."),
            Tip("spending.categories", "Categorias e limites", "Toque numa categoria para ver os lançamentos e definir o limite do mês."),
        ),
        CHAT to listOf(
            Tip("chat.suggestions", "Comece por aqui", "Toque numa sugestão ou escreva sua pergunta. Frases como \"gastei 45 no ifood\" viram um lançamento para conferir."),
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
