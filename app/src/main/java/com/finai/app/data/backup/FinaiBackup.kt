package com.finai.app.data.backup

import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cópia dos dados do usuário para trocar de aparelho (Fase 7, item 12 — o backup do Android
 * está desligado, `allowBackup="false"`). Leva as mesmas tabelas que "Apagar todos os dados"
 * limpa, menos o registro de deduplicação de notificações (é só controle interno).
 *
 * **Nunca** leva chave de IA (`AiKeyStore`), contagem de cota (`uso_provedor_ia`) nem
 * preferências: são configuração deste aparelho, não dado financeiro.
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
        }))
        put("dividas", JSONArray(b.dividas.map { d ->
            JSONObject().put("id", d.id).put("nome", d.nome).put("valorOriginalCentavos", d.valorOriginalCentavos)
                .put("valorAbertoCentavos", d.valorAbertoCentavos).put("taxaJurosMensalBasisPoints", d.taxaJurosMensalBasisPoints)
                .put("parcelasRestantes", d.parcelasRestantes).put("valorParcelaCentavos", d.valorParcelaCentavos)
                .put("parcelasTotais", d.parcelasTotais).putOpt("proximoVencimento", d.proximoVencimento)
                .putOpt("diaVencimento", d.diaVencimento).putOpt("quitadaEm", d.quitadaEm)
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
                    )
                },
                dividas = root.list("dividas") { o ->
                    DividaEntity(
                        id = o.getLong("id"), nome = o.getString("nome"), valorOriginalCentavos = o.optLong("valorOriginalCentavos"),
                        valorAbertoCentavos = o.getLong("valorAbertoCentavos"), taxaJurosMensalBasisPoints = o.optInt("taxaJurosMensalBasisPoints"),
                        parcelasRestantes = o.optInt("parcelasRestantes"), valorParcelaCentavos = o.optLong("valorParcelaCentavos"),
                        parcelasTotais = o.optInt("parcelasTotais"), proximoVencimento = o.longOrNull("proximoVencimento"),
                        diaVencimento = o.longOrNull("diaVencimento")?.toInt(), quitadaEm = o.longOrNull("quitadaEm"),
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
