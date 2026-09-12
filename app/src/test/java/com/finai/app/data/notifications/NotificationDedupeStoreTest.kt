package com.finai.app.data.notifications

import com.finai.app.data.local.dao.NotificacaoEnviadaDao
import com.finai.app.data.local.entity.NotificacaoEnviadaEntity
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O critério de aceite da Fase 5 em uma frase (planning.md §10): "no máximo
 * as notificações relevantes daquele dia, não uma por hora". Um fake do DAO
 * troca o Room por um `MutableMap` em memória — mesma ideia de
 * `AiRouterTest`'s fakes, sem Context/Room nenhum num teste de JVM pura.
 */
class NotificationDedupeStoreTest {

    private class FakeDao : NotificacaoEnviadaDao {
        val registros = mutableMapOf<String, NotificacaoEnviadaEntity>()
        override suspend fun find(chave: String) = registros[chave]
        override suspend fun upsert(registro: NotificacaoEnviadaEntity) { registros[registro.chave] = registro }
        override suspend fun removerExceto(chavesAtivas: List<String>) {
            registros.keys.retainAll(chavesAtivas.toSet())
        }
    }

    private val hoje = LocalDate.of(2026, 3, 21)

    @Test
    fun `evento nunca visto deve ser enviado`() = runTest {
        val store = NotificationDedupeStore(FakeDao())
        assertTrue(store.shouldSend("atrasado:1", hoje))
    }

    @Test
    fun `evento ja enviado hoje nao deve ser enviado de novo`() = runTest {
        val store = NotificationDedupeStore(FakeDao())
        store.markSent("atrasado:1", hoje)
        assertFalse(store.shouldSend("atrasado:1", hoje))
    }

    @Test
    fun `mesmo evento enviado ontem pode ser enviado de novo hoje`() = runTest {
        val store = NotificationDedupeStore(FakeDao())
        store.markSent("atrasado:1", hoje.minusDays(1))
        assertTrue(store.shouldSend("atrasado:1", hoje))
    }

    @Test
    fun `prune remove eventos que nao estao mais ativos e preserva os ativos`() = runTest {
        val dao = FakeDao()
        val store = NotificationDedupeStore(dao)
        store.markSent("atrasado:1", hoje)
        store.markSent("orcamento-estourado:Lazer", hoje)

        store.pruneExcept(listOf("orcamento-estourado:Lazer")) // "atrasado:1" foi resolvido (conta paga)

        assertTrue(dao.registros.containsKey("orcamento-estourado:Lazer"))
        assertFalse(dao.registros.containsKey("atrasado:1"))
    }
}
