package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth

/**
 * Exceção de uma ocorrência de lançamento recorrente ("Somente este mês"), identificada pelo
 * mês de referência. Campo nulo = igual ao da série. A data fica sempre dentro de [mes]: uma
 * ocorrência nunca muda de mês, então nenhum mês ganha duas cobranças da mesma série.
 */
data class OccurrenceOverride(
    val mes: YearMonth,
    val data: LocalDate? = null,
    val valorCentavos: Long? = null,
    val descricao: String? = null,
    val categoria: String? = null,
    val contaOrigem: String? = null,
    /** Ocorrência excluída só neste mês: a série continua nos outros. */
    val pulada: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !pulada && data == null && valorCentavos == null && descricao == null && categoria == null && contaOrigem == null
}

/**
 * Texto de [TransacaoEntity.excecoes]: uma exceção por linha, campos com chave
 * ("2026-11;v=13500;d=2026-11-05;s=Internet%20nova"). Textos vão em URL-encoding (UTF-8), então
 * qualquer descrição cabe. Linha ou campo ilegível é ignorado, nunca derruba a projeção.
 */
object OccurrenceCodec {
    private const val CACHE_LIMIT = 256
    private val cache = object : LinkedHashMap<String, List<OccurrenceOverride>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<OccurrenceOverride>>?) = size > CACHE_LIMIT
    }

    fun encode(list: List<OccurrenceOverride>): String =
        list.filterNot { it.isEmpty }.sortedBy { it.mes }.distinctBy { it.mes }.joinToString("\n") { o ->
            buildList {
                add(o.mes.toString())
                o.data?.let { add("d=$it") }
                o.valorCentavos?.let { add("v=$it") }
                o.descricao?.let { add("s=" + enc(it)) }
                o.categoria?.let { add("c=" + enc(it)) }
                o.contaOrigem?.let { add("o=" + enc(it)) }
                if (o.pulada) add("x=1")
            }.joinToString(";")
        }

    /** As projeções leem as exceções a cada mês; o texto quase sempre é vazio ou se repete. */
    fun decode(text: String): List<OccurrenceOverride> {
        if (text.isBlank()) return emptyList()
        synchronized(cache) { cache[text]?.let { return it } }
        val parsed = text.lineSequence().mapNotNull(::decodeLine).toList()
            .sortedBy { it.mes }.distinctBy { it.mes }
        synchronized(cache) { cache[text] = parsed }
        return parsed
    }

    private fun decodeLine(line: String): OccurrenceOverride? {
        val parts = line.trim().split(';')
        val mes = runCatching { YearMonth.parse(parts.first()) }.getOrNull() ?: return null
        var o = OccurrenceOverride(mes)
        parts.drop(1).forEach { part ->
            val value = part.substringAfter('=', "")
            o = when (part.substringBefore('=')) {
                // Data fora do mês de referência não vale: a ocorrência não muda de mês.
                "d" -> o.copy(data = runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { YearMonth.from(it) == mes })
                "v" -> o.copy(valorCentavos = value.toLongOrNull()?.takeIf { it > 0 })
                "s" -> o.copy(descricao = dec(value))
                "c" -> o.copy(categoria = dec(value))
                "o" -> o.copy(contaOrigem = dec(value))
                "x" -> o.copy(pulada = value == "1")
                else -> o
            }
        }
        return o.takeUnless { it.isEmpty }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}

// ── a série ─────────────────────────────────────────────────────────────

val TransacaoEntity.overrides: List<OccurrenceOverride> get() = OccurrenceCodec.decode(excecoes)

val TransacaoEntity.startMonth: YearMonth get() = YearMonth.from(data.toLocalDate())

/** Último mês da série (inclusive); nulo = sem fim. */
val TransacaoEntity.seriesEnd: YearMonth?
    get() = recorrenteAte?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

/**
 * Dia-base da recorrência: o gravado ([TransacaoEntity.diaRecorrencia]) ou o da data inicial.
 * Começar no último dia do mês (30/09, 28/02) quer dizer "todo fim de mês" = 31, a mesma regra
 * de [monthlyOccurrence].
 */
val TransacaoEntity.recurrenceDay: Int
    get() = diaRecorrencia?.coerceIn(1, 31) ?: data.toLocalDate().let { if (it.dayOfMonth == it.lengthOfMonth()) 31 else it.dayOfMonth }

/** Lançamento que a Agenda e as projeções repetem todo mês. Item de fatura nunca se repete. */
val TransacaoEntity.isRecurringSeries: Boolean get() = recorrente && faturaId == null

/** A série tem ocorrência no mês (entre o início e o fim). */
fun TransacaoEntity.isActiveIn(month: YearMonth): Boolean =
    isRecurringSeries && month >= startMonth && seriesEnd.let { it == null || month <= it }

/**
 * Data da ocorrência de [month] pela regra da série, sem exceção: no mês inicial, a data
 * gravada; nos outros, o dia-base (29–31 num mês mais curto cai no último dia, só naquele mês).
 */
fun TransacaoEntity.regularDate(month: YearMonth): LocalDate =
    if (month == startMonth) data.toLocalDate()
    else month.atDay(recurrenceDay.coerceAtMost(month.lengthOfMonth()))

/**
 * A ocorrência de [month] já com a exceção daquele mês aplicada, com o mesmo id da série (é uma
 * projeção, não um registro). Nula fora da série.
 */
fun TransacaoEntity.occurrenceFor(month: YearMonth): TransacaoEntity? {
    if (!isActiveIn(month)) return null
    val o = overrides.firstOrNull { it.mes == month }
    if (o?.pulada == true) return null
    if (o == null) return if (month == startMonth) this else copy(data = regularDate(month).toEpochMillis())
    return copy(
        data = (o.data ?: regularDate(month)).toEpochMillis(),
        valorCentavos = o.valorCentavos ?: valorCentavos,
        descricao = o.descricao ?: descricao,
        categoria = o.categoria ?: categoria,
        contaOrigem = o.contaOrigem ?: contaOrigem,
    )
}

/** A ocorrência de [month] tem alguma exceção ("Somente este mês"). */
fun TransacaoEntity.hasOverrideIn(month: YearMonth): Boolean = isRecurringSeries && overrides.any { it.mes == month }

// ── edição ──────────────────────────────────────────────────────────────

/** Alcance de uma alteração num lançamento recorrente. */
enum class EditScope { OnlyThis, ThisAndFollowing }

/** O que o formulário de edição devolve: os campos editáveis de uma ocorrência. */
data class EntryChange(
    val data: LocalDate,
    val valorCentavos: Long,
    val descricao: String,
    val categoria: String,
    val contaOrigem: String,
    val contaDestino: String? = null,
)

/** Primeiro registro da série: o próprio id no trecho original, [TransacaoEntity.serieId] nos seguintes. */
val TransacaoEntity.seriesRoot: Long get() = serieId ?: id

/**
 * Gravação de uma edição ou exclusão: o registro atualizado (nulo se ele some), o novo trecho
 * (se a série foi segmentada) e os trechos seguintes que a mudança substituiu.
 */
data class SeriesEdit(
    val update: TransacaoEntity?,
    val insert: TransacaoEntity? = null,
    val delete: List<TransacaoEntity> = emptyList(),
)

/**
 * Regras de edição da Agenda. Funções puras: devolvem o que gravar, o repositório grava numa
 * transação. Nenhuma edição cria um registro por mês; a projeção continua sendo a única fonte
 * das ocorrências.
 */
object RecurringSeries {

    /** Avulso: grava direto no registro. */
    fun editSingle(entry: TransacaoEntity, change: EntryChange): TransacaoEntity {
        require(change.valorCentavos > 0) { "O valor precisa ser maior que zero." }
        return entry.copy(
            data = change.data.toEpochMillis(), valorCentavos = change.valorCentavos,
            descricao = change.descricao.trim().ifEmpty { entry.descricao }, categoria = change.categoria,
            contaOrigem = change.contaOrigem, contaDestino = change.contaDestino ?: entry.contaDestino,
        )
    }

    /** Os trechos da mesma série que começam depois de [month] (criados por mudanças anteriores). */
    fun laterSegments(series: TransacaoEntity, month: YearMonth, all: List<TransacaoEntity>): List<TransacaoEntity> =
        all.filter { it.id != series.id && it.isRecurringSeries && it.seriesRoot == series.seriesRoot && it.startMonth > month }

    fun edit(
        series: TransacaoEntity, month: YearMonth, change: EntryChange, scope: EditScope,
        later: List<TransacaoEntity> = emptyList(),
    ): SeriesEdit = when (scope) {
        EditScope.OnlyThis -> SeriesEdit(editOnly(series, month, change))
        EditScope.ThisAndFollowing -> editFrom(series, month, change, later)
    }

    /**
     * "Somente este mês": grava (ou troca) a exceção de [month]. Só o que difere da série entra
     * na exceção; se nada difere, a exceção some e o mês volta à regra da série.
     */
    fun editOnly(series: TransacaoEntity, month: YearMonth, change: EntryChange): TransacaoEntity {
        validate(series, month, change)
        val descricao = change.descricao.trim().ifEmpty { series.descricao }
        val o = OccurrenceOverride(
            mes = month,
            data = change.data.takeIf { it != series.regularDate(month) },
            valorCentavos = change.valorCentavos.takeIf { it != series.valorCentavos },
            descricao = descricao.takeIf { it != series.descricao },
            categoria = change.categoria.takeIf { it != series.categoria },
            contaOrigem = change.contaOrigem.takeIf { it != series.contaOrigem },
        )
        return series.copy(excecoes = OccurrenceCodec.encode(series.overrides.filter { it.mes != month } + o))
    }

    /**
     * "Este mês e os próximos". No mês inicial do trecho, muda o próprio registro. Depois dele,
     * segmenta: o registro atual termina no mês anterior (os meses passados não mudam) e um novo
     * registro começa em [month] com os valores novos. Os trechos seguintes da mesma série
     * ([later], de mudanças anteriores) são substituídos por ele, e as exceções de meses
     * seguintes continuam valendo.
     *
     * O dia-base só muda se a data foi mudada nesta edição. Uma data excepcional do mês, que a
     * tela só mostrou, fica como exceção daquele mês e não vira a regra da sequência.
     */
    fun editFrom(series: TransacaoEntity, month: YearMonth, change: EntryChange, later: List<TransacaoEntity> = emptyList()): SeriesEdit {
        validate(series, month, change)
        val shown = requireNotNull(series.occurrenceFor(month)).data.toLocalDate()
        val regular = series.regularDate(month)
        val dateChanged = change.data != shown
        val day = if (dateChanged) change.data.dayOfMonth else series.recurrenceDay
        val start = if (dateChanged) change.data else regular
        // Data excepcional que não foi tocada: continua só naquele mês.
        val keptDate = OccurrenceOverride(month, data = shown).takeIf { !dateChanged && shown != regular }
        val descricao = change.descricao.trim().ifEmpty { series.descricao }
        val values: TransacaoEntity.() -> TransacaoEntity = {
            copy(
                valorCentavos = change.valorCentavos, descricao = descricao, categoria = change.categoria,
                contaOrigem = change.contaOrigem, contaDestino = change.contaDestino ?: contaDestino,
            )
        }
        val followers = later.filter { it.startMonth > month }
        // Até onde a série ia: o fim do último trecho (nulo = sem fim).
        val end = (followers + series).maxByOrNull { it.startMonth }!!.recorrenteAte
        val keptOverrides = series.overrides.filter { it.mes > month } + followers.flatMap { it.overrides } + listOfNotNull(keptDate)
        if (month == series.startMonth) {
            val rest = series.overrides.filter { it.mes < month } + keptOverrides
            return SeriesEdit(
                series.values().copy(
                    data = start.toEpochMillis(),
                    diaRecorrencia = if (dateChanged) day else series.diaRecorrencia,
                    recorrenteAte = end,
                    excecoes = OccurrenceCodec.encode(rest),
                ),
                delete = followers,
            )
        }
        val before = series.copy(
            recorrenteAte = month.minusMonths(1).toString(),
            excecoes = OccurrenceCodec.encode(series.overrides.filter { it.mes < month }),
        )
        val after = series.values().copy(
            id = 0,
            data = start.toEpochMillis(),
            recorrenteAte = end,
            diaRecorrencia = day,
            excecoes = OccurrenceCodec.encode(keptOverrides),
            serieId = series.seriesRoot,
        )
        return SeriesEdit(before, after, followers)
    }

    /** Excluir só a ocorrência de [month]: a série continua nos outros meses. */
    fun deleteOnly(series: TransacaoEntity, month: YearMonth): TransacaoEntity {
        require(series.isActiveIn(month)) { "Esse mês não faz parte da recorrência." }
        return series.copy(excecoes = OccurrenceCodec.encode(
            series.overrides.filter { it.mes != month } + OccurrenceOverride(month, pulada = true)))
    }

    /**
     * Excluir de [month] em diante: o trecho termina no mês anterior (ou some, se começa em
     * [month]) e os trechos seguintes da série somem. Os meses anteriores ficam como estão.
     */
    fun deleteFrom(series: TransacaoEntity, month: YearMonth, later: List<TransacaoEntity> = emptyList()): SeriesEdit {
        require(series.isActiveIn(month)) { "Esse mês não faz parte da recorrência." }
        val followers = later.filter { it.startMonth > month }
        if (month == series.startMonth) return SeriesEdit(null, delete = followers + series)
        return SeriesEdit(
            series.copy(
                recorrenteAte = month.minusMonths(1).toString(),
                excecoes = OccurrenceCodec.encode(series.overrides.filter { it.mes < month }),
            ),
            delete = followers,
        )
    }

    private fun validate(series: TransacaoEntity, month: YearMonth, change: EntryChange) {
        require(series.isActiveIn(month)) { "Esse mês não faz parte da recorrência." }
        require(change.valorCentavos > 0) { "O valor precisa ser maior que zero." }
        require(YearMonth.from(change.data) == month) { "A data precisa ficar no mesmo mês." }
    }

    /**
     * Mês de referência de uma ocorrência mostrada na Agenda. Como a ocorrência nunca sai do
     * próprio mês, é o mês da data mostrada.
     */
    fun referenceMonth(occurrence: TransacaoEntity): YearMonth = YearMonth.from(occurrence.data.toLocalDate())
}
