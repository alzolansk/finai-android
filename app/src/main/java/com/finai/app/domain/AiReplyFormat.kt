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

    /**
     * Resposta pedida em linhas rotuladas ("Agora: …", "Próximo passo: …") →
     * pares rótulo/texto na ordem de [labels]. Aceita o rótulo em negrito, com
     * marcador de lista, sem acento ou com outra caixa; linha sem rótulo
     * continua o item anterior. `null` quando nenhum rótulo aparece — aí a
     * tela mostra o texto inteiro, sem estrutura.
     */
    fun labeled(raw: String, labels: List<String>): List<Pair<String, String>>? {
        val found = linkedMapOf<String, StringBuilder>()
        var current: String? = null
        HIGHLIGHT.replace(raw, "").lines().forEach { rawLine ->
            val line = rawLine.trim().replace(BULLET, "").trim()
            if (line.isEmpty()) return@forEach
            val match = LABELED_LINE.find(line)
            val label = match?.let { m -> labels.firstOrNull { fold(it) == fold(m.groupValues[1]) } }
            if (label != null) {
                current = label
                found.getOrPut(label) { StringBuilder() }.append(match.groupValues[2].trim())
            } else {
                current?.let { found.getValue(it).append(' ').append(line) }
            }
        }
        if (found.isEmpty()) return null
        return labels.mapNotNull { label -> found[label]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { label to it } }
    }

    data class Decision(val action: String, val reason: String?)

    /**
     * "Decisões para você" ([AiPromptBuilder.decisions]): uma decisão por
     * linha, "ação | porquê". Linha sem "|" vira decisão sem porquê — é o
     * caso de "está tudo sob controle".
     */
    fun decisions(raw: String): List<Decision> =
        HIGHLIGHT.replace(raw, "").lines()
            .map { it.trim().replace(HEADING, "").replace(BULLET, "").trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split('|', limit = 2)
                Decision(
                    action = parts[0].trim().trimEnd('.', ':').replace("**", ""),
                    reason = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .filter { it.action.isNotEmpty() }
            .take(3)

    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text.lowercase().trim(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

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

    // O prompt pede {{rótulo|valor}}, mas em respostas de continuação os modelos
    // escrevem {rótulo|valor} ou [rótulo|valor]. O "|" dentro é o que distingue
    // o destaque de texto comum (link de Markdown não tem "|").
    private val HIGHLIGHT = Regex("""[{\[]{1,2}\s*([^|{}\[\]\n]{1,40}?)\s*\|\s*([^|{}\[\]\n]{1,40}?)\s*[}\]]{1,2}""")
    private val LABELED_LINE = Regex("""^[*_]{0,2}([\p{L} ]{2,20}?)[*_]{0,2}\s*[:–—-]\s*[*_]{0,2}\s*(.+)$""")
    private val HEADING =Regex("""^#{1,6}\s*""")
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
