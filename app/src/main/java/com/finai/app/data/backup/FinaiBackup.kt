package com.finai.app.data.backup

import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cópia dos dados do usuário para trocar de aparelho (Fase 7, item 12 — o backup do Android
 * está desligado, `allowBackup="false"`). Leva as mesmas tabelas que "Apagar todos os dados"
 * limpa, menos o registro de deduplicação de notificações (é só controle interno).
 *
 * **Nunca** leva chave de IA (`AiKeyStore`), contagem de cota (`uso_provedor_ia`) nem
 * preferências: são configuração deste aparelho, não dado financeiro. Exceção: os apps escolhidos
 * no lançamento por notificações ([capturaApps]), para não ter de escolher de novo; a captura em
 * si não é religada (o acesso às notificações é do Android do aparelho novo).
 *
 * Sugestões de lançamento por notificação ([sugestoes]) vão **sem** o texto original e sem as de
 * teste: o vínculo com o lançamento salvo continua valendo porque os ids são os mesmos.
 */
data class FinaiBackup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val transacoes: List<TransacaoEntity>,
    val faturas: List<FaturaCartaoEntity>,
    val contas: List<ContaEntity>,
    val objetivos: List<ObjetivoEntity>,
    val dividas: List<DividaEntity>,
    val orcamentos: List<OrcamentoCategoriaEntity>,
    val assinaturas: List<AssinaturaEntity>,
    val mensagens: List<MensagemChatEntity>,
    val sugestoes: List<SugestaoLancamentoEntity> = emptyList(),
    val capturaApps: Set<String> = emptySet(),
) {
    /** Resumo para a confirmação "substitui tudo": o que o arquivo traz. */
    fun summary(): String = listOf(
        transacoes.size to "lançamento(s)",
        contas.size to "conta(s)",
        objetivos.size to "objetivo(s)",
        dividas.size to "dívida(s)",
        orcamentos.size to "limite(s)",
        assinaturas.size to "assinatura(s)",
        mensagens.size to "mensagem(ns) do chat",
        sugestoes.count { it.estado == "pendente" } to "sugestão(ões) para confirmar",
    ).filter { it.first > 0 }.joinToString(", ") { "${it.first} ${it.second}" }.ifEmpty { "nenhum dado" }
}

class BackupFormatException(message: String) : Exception(message)

/**
 * JSON do backup. Cada campo novo de entidade entra com `opt*` e o mesmo padrão do Room, para
 * um arquivo de versão anterior continuar abrindo; um arquivo de versão **mais nova** que o
 * banco deste app é recusado — ele pode ter dado que este app não sabe guardar.
 */
object BackupCodec {
    const val FORMAT = "finai-backup"

    fun encode(b: FinaiBackup): String = JSONObject().apply {
        put("format", FORMAT)
        put("schemaVersion", b.schemaVersion)
        put("exportedAt", b.exportedAt)
        put("transacoes", JSONArray(b.transacoes.map { t ->
            JSONObject().put("id", t.id).put("data", t.data).put("descricao", t.descricao).put("valorCentavos", t.valorCentavos)
                .put("categoria", t.categoria).put("contaOrigem", t.contaOrigem).put("recorrente", t.recorrente).put("tipo", t.tipo)
                .put("origem", t.origem).putOpt("faturaId", t.faturaId).put("extra", t.extra).put("rendaPrincipal", t.rendaPrincipal)
                .putOpt("compraId", t.compraId).putOpt("parcelaNumero", t.parcelaNumero).putOpt("parcelasTotal", t.parcelasTotal)
                .putOpt("objetivoId", t.objetivoId).putOpt("contaDestino", t.contaDestino)
                .putOpt("recorrenteAte", t.recorrenteAte).putOpt("diaRecorrencia", t.diaRecorrencia)
                .put("excecoes", t.excecoes).putOpt("serieId", t.serieId)
                .put("competenciaDeslocamento", t.competenciaDeslocamento)
                .putOpt("rendaGrupo", t.rendaGrupo).putOpt("rendaParte", t.rendaParte).putOpt("rendaDivisao", t.rendaDivisao)
        }))
        put("faturas", JSONArray(b.faturas.map { f ->
            JSONObject().put("id", f.id).put("contaId", f.contaId).put("referencia", f.referencia)
                .putOpt("fechamento", f.fechamento).put("vencimento", f.vencimento)
        }))
        put("contas", JSONArray(b.contas.map { c ->
            JSONObject().put("id", c.id).put("nome", c.nome).put("valorCentavos", c.valorCentavos).put("vencimento", c.vencimento)
                .put("status", c.status).put("tipo", c.tipo).put("recorrente", c.recorrente)
        }))
        put("objetivos", JSONArray(b.objetivos.map { o ->
            JSONObject().put("id", o.id).put("tipo", o.tipo).put("nome", o.nome).put("valorAlvoCentavos", o.valorAlvoCentavos)
                .put("valorGuardadoCentavos", o.valorGuardadoCentavos).put("prazo", o.prazo).put("prioridade", o.prioridade)
                .put("descricao", o.descricao).putOpt("concluidoEm", o.concluidoEm)
                .put("aporteMensalCentavos", o.aporteMensalCentavos)
        }))
        put("dividas", JSONArray(b.dividas.map { d ->
            JSONObject().put("id", d.id).put("nome", d.nome).put("valorOriginalCentavos", d.valorOriginalCentavos)
                .put("valorAbertoCentavos", d.valorAbertoCentavos).put("taxaJurosMensalBasisPoints", d.taxaJurosMensalBasisPoints)
                .put("parcelasRestantes", d.parcelasRestantes).put("valorParcelaCentavos", d.valorParcelaCentavos)
                .put("parcelasTotais", d.parcelasTotais).putOpt("proximoVencimento", d.proximoVencimento)
                .putOpt("diaVencimento", d.diaVencimento).putOpt("quitadaEm", d.quitadaEm)
                .put("ajustesVencimento", d.ajustesVencimento)
        }))
        put("orcamentos", JSONArray(b.orcamentos.map { o ->
            JSONObject().put("categoria", o.categoria).put("limiteMensalCentavos", o.limiteMensalCentavos).put("mesReferencia", o.mesReferencia)
        }))
        put("assinaturas", JSONArray(b.assinaturas.map { a ->
            JSONObject().put("id", a.id).put("nome", a.nome).put("valorCentavos", a.valorCentavos).putOpt("ultimoUso", a.ultimoUso).put("status", a.status)
        }))
        put("mensagens", JSONArray(b.mensagens.map { m ->
            JSONObject().put("id", m.id).put("papel", m.papel).put("texto", m.texto).put("timestamp", m.timestamp)
                .put("conversaId", m.conversaId).putOpt("contexto", m.contexto)
        }))
        // Só as reais e sem o texto original (retenção limitada; nunca vai para arquivo).
        put("sugestoes", JSONArray(b.sugestoes.filter { it.origem == "notificacao" }.map { s ->
            JSONObject().put("id", s.id).put("origem", s.origem).put("pacote", s.pacote).put("appNome", s.appNome)
                .put("capturadoEm", s.capturadoEm).put("atualizadoEm", s.atualizadoEm).put("chaveSistema", s.chaveSistema)
                .put("assinatura", s.assinatura).put("tipo", s.tipo).putOpt("valorCentavos", s.valorCentavos)
                .put("valoresEncontrados", s.valoresEncontrados).put("descricao", s.descricao).putOpt("categoria", s.categoria)
                .put("data", s.data).put("dataExplicita", s.dataExplicita).putOpt("parcelas", s.parcelas)
                .putOpt("valorParcelaCentavos", s.valorParcelaCentavos).putOpt("cartaoFinal", s.cartaoFinal)
                .putOpt("contaSugerida", s.contaSugerida).put("estado", s.estado).put("possivelRepeticao", s.possivelRepeticao)
                .putOpt("avisadaEm", s.avisadaEm).putOpt("resolvidaEm", s.resolvidaEm).put("lancamentoIds", s.lancamentoIds)
        }))
        put("capturaApps", JSONArray(b.capturaApps.sorted()))
    }.toString(2)

    /** Lê e valida; [currentSchema] é a versão do banco deste app. */
    fun decode(text: String, currentSchema: Int): FinaiBackup {
        val root = try { JSONObject(text) } catch (e: Exception) { throw BackupFormatException("O arquivo não é um backup do FinAI.") }
        if (root.optString("format") != FORMAT) throw BackupFormatException("O arquivo não é um backup do FinAI.")
        val version = root.optInt("schemaVersion", -1)
        if (version < 1) throw BackupFormatException("O arquivo de backup está incompleto.")
        if (version > currentSchema) {
            throw BackupFormatException("Este backup foi feito numa versão mais nova do app. Atualize o FinAI antes de restaurar.")
        }
        return try {
            FinaiBackup(
                schemaVersion = version,
                exportedAt = root.optLong("exportedAt"),
                transacoes = root.list("transacoes") { o ->
                    TransacaoEntity(
                        id = o.getLong("id"), data = o.getLong("data"), descricao = o.getString("descricao"),
                        valorCentavos = o.getLong("valorCentavos"), categoria = o.getString("categoria"),
                        contaOrigem = o.optString("contaOrigem"), recorrente = o.optBoolean("recorrente"),
                        tipo = o.optString("tipo", "Gasto"), origem = o.optString("origem", "manual"),
                        faturaId = o.longOrNull("faturaId"), extra = o.optBoolean("extra"), rendaPrincipal = o.optBoolean("rendaPrincipal"),
                        compraId = o.optString("compraId").takeIf { it.isNotBlank() },
                        parcelaNumero = o.longOrNull("parcelaNumero")?.toInt(), parcelasTotal = o.longOrNull("parcelasTotal")?.toInt(),
                        objetivoId = o.longOrNull("objetivoId"), contaDestino = o.optString("contaDestino").takeIf { it.isNotBlank() },
                        recorrenteAte = o.optString("recorrenteAte").takeIf { it.isNotBlank() },
                        diaRecorrencia = o.longOrNull("diaRecorrencia")?.toInt(), excecoes = o.optString("excecoes"),
                        serieId = o.longOrNull("serieId"),
                        competenciaDeslocamento = o.optInt("competenciaDeslocamento", 0),
                        rendaGrupo = o.optString("rendaGrupo").takeIf { it.isNotBlank() }, rendaParte = o.optString("rendaParte").takeIf { it.isNotBlank() },
                        rendaDivisao = o.optString("rendaDivisao").takeIf { it.isNotBlank() },
                    )
                },
                faturas = root.list("faturas") { o ->
                    FaturaCartaoEntity(
                        id = o.getLong("id"), contaId = o.getLong("contaId"), referencia = o.optString("referencia"),
                        fechamento = o.longOrNull("fechamento"), vencimento = o.getLong("vencimento"),
                    )
                },
                contas = root.list("contas") { o ->
                    ContaEntity(
                        id = o.getLong("id"), nome = o.getString("nome"), valorCentavos = o.getLong("valorCentavos"),
                        vencimento = o.getLong("vencimento"), status = o.optString("status", "pendente"),
                        tipo = o.optString("tipo", "a_pagar"), recorrente = o.optBoolean("recorrente"),
                    )
                },
                objetivos = root.list("objetivos") { o ->
                    ObjetivoEntity(
                        id = o.getLong("id"), tipo = o.optString("tipo"), nome = o.getString("nome"),
                        valorAlvoCentavos = o.getLong("valorAlvoCentavos"), valorGuardadoCentavos = o.optLong("valorGuardadoCentavos"),
                        prazo = o.getLong("prazo"), prioridade = o.optInt("prioridade"), descricao = o.optString("descricao"),
                        concluidoEm = o.longOrNull("concluidoEm"),
                        aporteMensalCentavos = o.optLong("aporteMensalCentavos"),
                    )
                },
                dividas = root.list("dividas") { o ->
                    DividaEntity(
                        id = o.getLong("id"), nome = o.getString("nome"), valorOriginalCentavos = o.optLong("valorOriginalCentavos"),
                        valorAbertoCentavos = o.getLong("valorAbertoCentavos"), taxaJurosMensalBasisPoints = o.optInt("taxaJurosMensalBasisPoints"),
                        parcelasRestantes = o.optInt("parcelasRestantes"), valorParcelaCentavos = o.optLong("valorParcelaCentavos"),
                        parcelasTotais = o.optInt("parcelasTotais"), proximoVencimento = o.longOrNull("proximoVencimento"),
                        diaVencimento = o.longOrNull("diaVencimento")?.toInt(), quitadaEm = o.longOrNull("quitadaEm"),
                        ajustesVencimento = o.optString("ajustesVencimento"),
                    )
                },
                orcamentos = root.list("orcamentos") { o ->
                    OrcamentoCategoriaEntity(o.getString("categoria"), o.getLong("limiteMensalCentavos"), o.getString("mesReferencia"))
                },
                assinaturas = root.list("assinaturas") { o ->
                    AssinaturaEntity(
                        id = o.getLong("id"), nome = o.getString("nome"), valorCentavos = o.getLong("valorCentavos"),
                        ultimoUso = o.longOrNull("ultimoUso"), status = o.optString("status", "ativa"),
                    )
                },
                mensagens = root.list("mensagens") { o ->
                    MensagemChatEntity(
                        id = o.getLong("id"), papel = o.getString("papel"), texto = o.getString("texto"),
                        timestamp = o.getLong("timestamp"), conversaId = o.optLong("conversaId"), contexto = o.stringOrNull("contexto"),
                    )
                },
                // Backup de antes da versão 15 não tem estas chaves: lista vazia.
                sugestoes = root.list("sugestoes") { o ->
                    SugestaoLancamentoEntity(
                        id = o.getLong("id"), origem = "notificacao", pacote = o.optString("pacote"), appNome = o.optString("appNome"),
                        capturadoEm = o.getLong("capturadoEm"), atualizadoEm = o.optLong("atualizadoEm", o.getLong("capturadoEm")),
                        chaveSistema = o.optString("chaveSistema"), assinatura = o.optString("assinatura"), tipo = o.getString("tipo"),
                        valorCentavos = o.longOrNull("valorCentavos"), valoresEncontrados = o.optString("valoresEncontrados"),
                        descricao = o.optString("descricao"), categoria = o.stringOrNull("categoria"), data = o.getLong("data"),
                        dataExplicita = o.optBoolean("dataExplicita"), parcelas = o.longOrNull("parcelas")?.toInt(),
                        valorParcelaCentavos = o.longOrNull("valorParcelaCentavos"), cartaoFinal = o.stringOrNull("cartaoFinal"),
                        contaSugerida = o.stringOrNull("contaSugerida"), estado = o.optString("estado", "pendente"),
                        possivelRepeticao = o.optBoolean("possivelRepeticao"), avisadaEm = o.longOrNull("avisadaEm"),
                        resolvidaEm = o.longOrNull("resolvidaEm"), lancamentoIds = o.optString("lancamentoIds"),
                        textoOriginal = null, testeId = null,
                    )
                },
                capturaApps = root.optJSONArray("capturaApps")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet(),
            )
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("O arquivo de backup está corrompido ou incompleto.")
        }
    }

    private fun <T> JSONObject.list(key: String, map: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
    }

    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
