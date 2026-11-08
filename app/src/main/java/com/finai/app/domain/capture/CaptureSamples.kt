package com.finai.app.domain.capture

/**
 * Os exemplos da área "Testar com notificações". São os mesmos textos que os testes automáticos
 * do parser usam, então o que a tela promete é o que está coberto.
 *
 * Cada exemplo tem uma ou mais etapas. [Step.slot] identifica a notificação: duas etapas com o
 * mesmo slot são a **mesma** notificação (mesmo id no Android), reenviada ou atualizada; slots
 * diferentes são notificações diferentes. Só a primeira etapa é editável; as outras derivam dela.
 */
object CaptureSamples {

    enum class Derive { Base, Same, Updated }

    data class Step(val label: String, val slot: Int, val derive: Derive)

    data class Sample(
        val number: Int,
        val name: String,
        val title: String,
        val text: String,
        /** O que deve acontecer, dito na tela antes do envio. */
        val expectation: String,
        val steps: List<Step> = listOf(Step("Envio", 0, Derive.Base)),
    ) {
        /** Título e texto de cada etapa, a partir do que o usuário deixou na primeira. */
        fun stepsFor(title: String, text: String): List<Pair<Step, Pair<String, String>>> = steps.map { step ->
            step to when (step.derive) {
                Derive.Base, Derive.Same -> title to text
                Derive.Updated -> title to "$text $UPDATE_SUFFIX"
            }
        }
    }

    /** O que a "atualização" acrescenta: um limite, que nunca pode virar o valor da compra. */
    const val UPDATE_SUFFIX = "Limite disponível: R$ 1.257,10."

    private const val CREDIT_TEXT = "Compra no crédito de R\$ 42,90 aprovada no Mercado."

    val all: List<Sample> = listOf(
        Sample(1, "Compra no crédito · Mercado", "Compra aprovada", CREDIT_TEXT,
            "Compra no crédito de R$ 42,90. Falta escolher o cartão e confirmar a fatura."),
        Sample(2, "Compra no débito · Padaria", "Compra no débito", "Compra no débito de R\$ 18,50 aprovada na Padaria.",
            "Compra no débito de R$ 18,50. Falta escolher a conta."),
        Sample(3, "Pix enviado", "Pix enviado", "Você enviou um Pix de R\$ 120,00 para Ana Souza.",
            "Pix enviado de R$ 120,00 para Ana Souza. Falta a conta e a categoria."),
        Sample(4, "Pix recebido", "Pix recebido", "Você recebeu um Pix de R\$ 850,00 de Carlos Lima.",
            "Receita de R$ 850,00, de Carlos Lima."),
        Sample(5, "Compra parcelada em 3x", "Compra parcelada",
            "Compra no crédito aprovada na Loja Eletro: total de R\$ 300,00 em 3 parcelas de R\$ 100,00.",
            "Compra de R$ 300,00 no total, 3 parcelas de R$ 100,00. Falta o cartão e a fatura."),
        Sample(6, "Reenvio da mesma notificação", "Compra aprovada", CREDIT_TEXT,
            "A segunda chegada é a mesma notificação: vira \"repetição ignorada\", sem outra sugestão.",
            listOf(Step("Envio", 0, Derive.Base), Step("Reenvio", 0, Derive.Same))),
        Sample(7, "Atualização da mesma notificação", "Compra aprovada", CREDIT_TEXT,
            "A notificação é atualizada com o limite: a sugestão existente é atualizada, nenhuma nova é criada, e o limite não vira valor.",
            listOf(Step("Envio", 0, Derive.Base), Step("Atualização", 0, Derive.Updated))),
        Sample(8, "Duas compras iguais", "Compra no débito", "Compra no débito de R\$ 9,90 aprovada na Cafeteria.",
            "Duas notificações diferentes com o mesmo texto: duas sugestões; a segunda avisa que pode ser repetida e você decide.",
            listOf(Step("1ª compra", 0, Derive.Base), Step("2ª compra", 1, Derive.Same))),
        Sample(9, "Compra recusada", "Compra recusada", "Compra de R\$ 89,90 recusada na Loja X. Saldo insuficiente.",
            "Ignorada: compra recusada não é lançamento."),
        Sample(10, "Propaganda de limite", "Seu limite aumentou",
            "Aproveite: agora você tem R\$ 3.000,00 de limite disponível para compras parceladas em até 12x.",
            "Ignorada: é propaganda; o limite nunca vira valor de compra."),
        Sample(11, "Mensagem sem valor", "Compra aprovada", "Sua compra foi aprovada. Toque para ver os detalhes no app.",
            "Ignorada: não diz o valor."),
        Sample(12, "Estorno", "Estorno realizado", "O estorno de R\$ 42,90 da compra no Mercado foi creditado no seu cartão.",
            "Reconhecido como estorno e marcado \"Precisa revisar\": não vira gasto nem receita sozinho."),
        Sample(13, "Mensagem ambígua", "Compras aprovadas", "Compras aprovadas: R\$ 25,00 na Padaria e R\$ 40,00 na Farmácia.",
            "Dois valores sem dizer qual é qual: a sugestão pede para você escolher o valor."),
    )

    fun byNumber(number: Int): Sample = all.first { it.number == number }
}
