package com.finai.app.data.model

/**
 * A lista única de categorias de gasto (planning-v2 §6, Fase 0). O "+", Limites, a importação,
 * o classificador de estabelecimentos e o prompt de categoria da IA leem daqui: antes eram duas
 * listas diferentes (a do formulário e a dos limites) e a mesma compra mudava de nome entre telas.
 */
object Categorias {
    val all = listOf(
        "Alimentação", "Moradia", "Transporte", "Saúde", "Lazer",
        "Compras", "Educação", "Serviços", "Assinaturas", "Outros",
    )

    /** Categoria de sobra: o que nenhuma regra reconhece. */
    const val OTHER = "Outros"

    /**
     * Categorias de sistema: gravadas pelo app, nunca escolhidas pelo usuário, e fora de limite,
     * coach e análise de consumo. Ficam fora de [all] de propósito.
     */
    val system = listOf(
        com.finai.app.domain.DebtSchedule.PAYMENT_CATEGORY,
        com.finai.app.domain.ADJUSTMENT_LABEL,
    )

    fun isSystem(name: String) = name in system
}
