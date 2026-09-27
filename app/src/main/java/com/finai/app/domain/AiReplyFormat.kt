package com.finai.app.domain

/**
 * Lê o texto cru que volta de qualquer provedor de IA e o transforma em blocos
 * que a UI sabe desenhar. Os provedores respondem em Markdown mesmo quando o
 * prompt pede texto simples, e os modelos menores seguem pior essa instrução —
 * então o app não pode depender do prompt: `**negrito**` vira ênfase, título e
 * marcador soltos somem, e valores em reais/percentuais são marcados para
 * ganhar destaque visual em vez de se perderem no meio da frase.
 *
 * Também extrai os destaques `{{rótulo|valor}}` que o prompt do chat pede
 * ([AiPromptBuilder.chat]): viram cartões acima da resposta. Se o modelo não
 * usar o formato, nada quebra — a resposta só sai sem cartões.
 *
 * Kotlin puro (sem Compose) para ser testável na JVM.
 */
object AiReplyFormat {

    data class Highlight(val label: String, val value: String) {
        val negative: Boolean get() = NEGATIVE_VALUE.containsMatchIn(value)
    }

    enum class Kind { Plain, Money, NegativeMoney, Percent }

    data class Span(val text: String, val strong: Boolean = false, val kind: Kind = Kind.Plain)

    sealed interface Block {
        val spans: List<Span>
        data class Paragraph(override val spans: List<Span>) : Block
        data class Bullet(override val spans: List<Span>) : Block
    }

    data class Reply(val highlights: List<Highlight>, val blocks: List<Block>)

    const val MAX_HIGHLIGHTS = 3

    fun parse(raw: String): Reply {
        val highlights = HIGHLIGHT.findAll(raw)
            .map { Highlight(it.groupValues[1].trim(), it.groupValues[2].trim()) }
            .filter { it.label.isNotEmpty() && it.value.isNotEmpty() }
            .take(MAX_HIGHLIGHTS)
            .toList()
        val body = HIGHLIGHT.replace(raw, "")

        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(inline(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }
        body.lines().forEach { rawLine ->
            val line = rawLine.trim()
                .replace(HEADING, "")
                .replace(QUOTE, "")
                .trim()
            when {
                line.isEmpty() || HORIZONTAL_RULE.matches(line) -> flushParagraph()
                BULLET.containsMatchIn(line) -> {
                    flushParagraph()
                    val content = line.replace(BULLET, "").trim()
                    if (content.isNotEmpty()) blocks += Block.Bullet(inline(content))
                }
                else -> paragraph += line
            }
        }
        flushParagraph()
        return Reply(highlights, blocks.filter { block -> block.spans.any { it.text.isNotBlank() } })
    }

    /**
     * Mesmo texto, sem nenhuma marcação — para onde não há como desenhar
     * ênfase (notificação do sistema) ou onde o texto vira dado (prompt de
     * histórico da conversa). Destaques viram "rótulo: valor".
     */
    fun plain(raw: String): String {
        val reply = parse(raw)
        return buildString {
            reply.highlights.forEach { appendLine("${it.label}: ${it.value}") }
            reply.blocks.forEach { block ->
                val text = block.spans.joinToString("") { it.text }
                appendLine(if (block is Block.Bullet) "• $text" else text)
            }
        }.trim()
    }

    private fun inline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var last = 0
        STRONG.findAll(text).forEach { match ->
            if (match.range.first > last) spans += values(cleanStray(text.substring(last, match.range.first)), strong = false)
            val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
            spans += values(cleanStray(content), strong = true)
            last = match.range.last + 1
        }
        if (last < text.length) spans += values(cleanStray(text.substring(last)), strong = false)
        return spans.filter { it.text.isNotEmpty() }
    }

    /** Itálico (`*x*`, `_x_`) vira texto normal; asterisco solto e crase somem. */
    private fun cleanStray(text: String): String = text
        .replace(ITALIC_STAR) { it.groupValues[1] }
        .replace(ITALIC_UNDERSCORE) { it.groupValues[1] }
        .replace("*", "")
        .replace("`", "")

    private fun values(text: String, strong: Boolean): List<Span> {
        val spans = mutableListOf<Span>()
        var last = 0
        VALUE.findAll(text).forEach { match ->
            if (match.range.first > last) spans += Span(text.substring(last, match.range.first), strong)
            val kind = when {
                match.value.endsWith("%") -> Kind.Percent
                NEGATIVE_VALUE.containsMatchIn(match.value) -> Kind.NegativeMoney
                else -> Kind.Money
            }
            spans += Span(match.value, strong, kind)
            last = match.range.last + 1
        }
        if (last < text.length) spans += Span(text.substring(last), strong)
        return spans
    }

    private val HIGHLIGHT = Regex("""\{\{([^|{}\n]{1,40})\|([^{}\n]{1,40})\}\}""")
    private val HEADING = Regex("""^#{1,6}\s*""")
    private val QUOTE = Regex("""^>\s*""")
    private val HORIZONTAL_RULE = Regex("""^([-*_]\s*){3,}$""")
    private val BULLET = Regex("""^([-*•·]|\d{1,2}[.)])\s+""")
    private val STRONG = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC_STAR = Regex("""(?<![\w*])\*(\S(?:[^*\n]*\S)?)\*(?![\w*])""")
    private val ITALIC_UNDERSCORE = Regex("""(?<![\w_])_(\S(?:[^_\n]*\S)?)_(?![\w_])""")

    // "R$ 1.234", "R$ 1.234,56", "-R$ 40", "R$ -4.011", "R$1234" e percentuais "2,5%".
    // O sinal só conta colado ao valor: em "Aluguel - R$ 1.200" o hífen é separador.
    private val VALUE = Regex("""[-−]?R\$\s?[-−]?\s?\d[\d.]*(?:,\d{1,2})?(?:\s?mil\b)?|\d+(?:,\d+)?\s?%""")
    private val NEGATIVE_VALUE = Regex("""^[-−]|R\$\s?[-−]""")
}
