package com.finai.app.domain.importer

import com.finai.app.data.model.Categorias

/**
 * Classificação de categoria por regra local (planning.md §6: "Classificar
 * categoria de um lançamento importado **quando a regra local não reconhece**
 * o estabelecimento" é o único ponto em que a IA entra aqui). Cada acerto de
 * regra é um lançamento a menos para a IA olhar, e portanto cota gratuita
 * preservada.
 *
 * A tabela cobre as redes/apps que mais aparecem em fatura de cartão no Brasil.
 * Quando nenhuma bate, devolve "Outros" com `needsReview = true` — e é essa
 * lista de ambíguos que [com.finai.app.data.ai.ImportAiAssistant] manda em
 * lote, só com a descrição já normalizada, para a IA sugerir a categoria.
 */
object MerchantClassifier {

    private const val FALLBACK = "Outros"

    /** Intermediários de pagamento que aparecem antes do nome real da loja na fatura. */
    private val ACQUIRER_PREFIX = Regex(
        """^(pag|pagseguro|pg|mp|mercadopago|mercado pago|ebn|dl|ec|iz|cie|pay|paypal|stone|cielo|rede)\s*\*\s*""",
    )

    /** Palavra-chave (já sem acento, minúscula) -> categoria de [Categorias]. */
    private val RULES: List<Pair<List<String>, String>> = listOf(
        listOf(
            "ifood", "rappi", "uber eats", "supermercado", "mercado", "hipermerc", "atacad",
            "carrefour", "pao de acucar", "extra ", "assai", "sendas", "big bompreco", "zona sul",
            "padaria", "panificadora", "restaurante", "lanchonete", "pizzaria", "hamburgueria",
            "burger", "mcdonald", "bk ", "burger king", "subway", "outback", "habib", "china in box",
            "cafe", "starbucks", "acai", "sorveteria", "confeitaria", "churrascaria", "bar e",
            "emporio", "hortifruti", "sacolao", "acougue", "swift", "dia supermerc", "sams club",
        ) to "Alimentação",
        listOf(
            "uber", "99 ", "99app", "99 tecnologia", "cabify", "taxi", "posto ", "ipiranga",
            "shell", "petrobras", "br distribuidora", "combustivel", "estacionamento", "parking",
            "estapar", "onibus", "metro ", "metro sp", "cptm", "bilhete unico", "riocard",
            "pedagio", "sem parar", "conectcar", "veloe", "localiza", "movida", "unidas",
            "latam", "gol linhas", "azul linhas", "passagem aerea", "buser", "blablacar",
            "mecanica", "auto center", "pneus", "ipva", "detran", "licenciamento",
        ) to "Transporte",
        listOf(
            "netflix", "spotify", "amazon prime", "prime video", "disney", "hbo", "max ",
            "globoplay", "paramount", "apple.com/bill", "apple services", "itunes", "google play",
            "youtube premium", "deezer", "tidal", "crunchyroll", "kindle unlimited", "audible",
            "canva", "adobe", "microsoft 365", "office 365", "dropbox", "icloud", "chatgpt",
            "openai", "anthropic", "claude.ai", "github", "notion", "linkedin premium",
            "assinatura", "mensalidade plataforma",
        ) to "Assinaturas",
        listOf(
            "farmacia", "drogaria", "droga raia", "drogasil", "pacheco", "pague menos",
            "laboratorio", "fleury", "dasa", "sabin", "hospital", "clinica", "consultorio",
            "dentista", "odonto", "unimed", "amil", "bradesco saude", "sulamerica saude",
            "psicolog", "terapia", "fisioterapia", "oftalmo", "otica", "plano de saude",
            "academia", "smart fit", "bluefit", "gympass", "wellhub", "crossfit", "pilates",
        ) to "Saúde",
        listOf(
            "cinema", "cinemark", "kinoplex", "uci ", "ingresso.com", "sympla", "eventim",
            "ticketmaster", "teatro", "show ", "festival", "steam", "playstation", "xbox",
            "nintendo", "epic games", "riot games", "blizzard", "livraria", "saraiva",
            "amazon.com.br", "mercadolivre", "mercado livre", "shopee", "aliexpress", "shein",
            "magazine luiza", "magalu", "americanas", "casas bahia", "renner", "c&a", "riachuelo",
            "zara", "nike", "adidas", "centauro", "decathlon", "hotel", "airbnb", "booking",
            "viagem", "bar ", "pub ", "balada", "boate",
        ) to "Lazer",
        listOf(
            "aluguel", "condominio", "imobiliaria", "enel", "light ", "cemig", "copel", "cpfl",
            "equatorial", "energisa", "eletropaulo", "sabesp", "cedae", "copasa", "sanepar",
            "caesb", "comgas", "naturgy", "gas natural", "vivo", "claro", "tim ", "oi fibra",
            "net claro", "internet", "telefonica", "iptu", "seguro residencial", "leroy merlin",
            "telhanorte", "c&c casa", "casa e construcao", "ferragem", "material de construcao",
            "tok stok", "mobly", "madeiramadeira", "faxina", "diarista",
        ) to "Moradia",
    )

    /** Estabelecimentos que quase sempre são assinatura mensal — usado por [RecurrenceDetector]. */
    val KNOWN_SUBSCRIPTIONS: List<String> = listOf(
        "netflix", "spotify", "amazon prime", "prime video", "disney", "hbo", "globoplay",
        "paramount", "apple.com/bill", "itunes", "google play", "youtube premium", "deezer",
        "crunchyroll", "audible", "canva", "adobe", "microsoft 365", "office 365", "dropbox",
        "icloud", "chatgpt", "openai", "anthropic", "claude.ai", "github", "notion",
        "linkedin premium", "gympass", "wellhub", "smart fit", "bluefit", "unimed", "amil",
        "plano de saude", "seguro", "mensalidade", "assinatura",
    )

    fun classify(description: String): CategorySuggestion {
        val normalized = normalizeMerchant(description)
        val hit = RULES.firstOrNull { (keywords, _) -> keywords.any { normalized.contains(it) } }
        return if (hit != null) {
            CategorySuggestion(hit.second, CategorySource.RULE, needsReview = false)
        } else {
            CategorySuggestion(FALLBACK, CategorySource.FALLBACK, needsReview = true)
        }
    }

    /** Aceita uma categoria vinda da IA/planilha só se ela existir de fato em [Categorias]. */
    fun normalizeCategoryName(raw: String): String? {
        val candidate = foldAccents(raw.lowercase().trim())
        return Categorias.all.firstOrNull { foldAccents(it.lowercase()) == candidate }
    }

    /**
     * Descrição de fatura sem o ruído que atrapalha comparação: prefixo de
     * adquirente ("PAG*", "MP *"), número de loja, cidade/UF no fim, sufixos de
     * razão social. Usado pela classificação, pela detecção de duplicata e pela
     * de recorrência — e também é o texto (já mínimo) que vai para a IA.
     */
    fun normalizeMerchant(description: String): String {
        var text = foldAccents(description.lowercase())
        // Prefixo de adquirente ("PAG*NETFLIX", "MP *UBER") só sai quando é um
        // adquirente conhecido — "UBER *TRIP" tem o nome do estabelecimento
        // justamente antes do asterisco.
        text = text.replace(ACQUIRER_PREFIX, "")
        text = text.replace(Regex("""\*+"""), " ")
        text = text.replace(Regex("""\b\d{4,}\b"""), " ") // códigos de loja / final de cartão
        text = text.replace(Regex("""\b(ltda|me|epp|sa|s/a|eireli|cia)\b"""), " ")
        text = text.replace(Regex("""\s+-\s+[a-z\s]{2,20}\s+(br|[a-z]{2})$"""), " ") // "- sao paulo br"
        text = text.replace(Regex("""\s+br$"""), " ")
        text = text.replace(Regex("""[^a-z0-9\s./]"""), " ")
        return text.replace(Regex("""\s+"""), " ").trim()
    }
}
