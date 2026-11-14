# Handoff · Reestruturação do assistente (tool calling, ações, acompanhamento)

Atualizado em 14/11/2026, branch `claude/intelligent-curie-erx3os`. O handoff anterior (FinAI v2,
fases 5–11) está mais abaixo, sem alteração.

**Estado em uma frase (atualizado após a sessão de compilação):** o assistente novo **compila, está ligado ao chat e à Caixa
e tem 629 testes JVM verdes** (`assembleDebug`, `assemblePessoal` e `compileDebugAndroidTestKotlin` também). **Não foi
visto em aparelho nem com modelo real**, e os testes instrumentados (`Migration19To20Test`, executor contra Room) estão
escritos mas não executados. O `20.json` foi gerado e commitado. O plano está em `docs/PLANO-ASSISTENTE-FINANCEIRO.md`.

## O que foi escrito (tudo sem compilar)

Domínio, Kotlin puro:
- `domain/projection/CashFlowProjection.kt`: projeção diária única (`CashFlowProjection`,
  `VariableProvision`, `PaymentWindow`). `FreeToSpend.build` agora delega a ela, então Hoje e o
  assistente partem da mesma série.
- `domain/projection/ScenarioEngine.kt`: cenários em memória (gasto, parcelas, mensal, mudança de
  renda, quitação) comparados com a base.
- `domain/assistant/AssistantModel.kt`: `AssistantSnapshot` (substitui `AssistantData`),
  `AssistantAnswer` (cartão), `AssistantSession`, `PendingAction`, `EntityRef`, `Fmt`, `NameMatch`.
- `domain/assistant/AssistantTools.kt`: reescrito. 17 consultas (`situacao`, `buscar_lancamentos`,
  `analisar_gastos`, `comparar_periodos`, `explicar_saldo`, `listar_compromissos`, `projetar_caixa`,
  `janela_pagamento`, `simular_cenario`, `montar_plano`, `revisar_meta`, `revisar_divida`,
  `acompanhar_plano`…). Assinatura nova: `run(call, snapshot)`.
- `domain/assistant/PlanBuilder.kt`: `PlanBuilder` (plano conjunto, plano × realizado), `SavedPlan`,
  `WatchRules` (acompanhamentos).
- `domain/assistant/AssistantActions.kt`: `ActionPlanner` com 16 ações. Cada uma devolve um
  `ActionPlan` (estado `Ready/Confirm/Clarify/Already/Unsupported`, antes/depois, `RowChange`).
  Inclui o caso obrigatório do salário (`registrar_recebimento_renda`): mesma ocorrência no mês via
  `RecurringSeries.editOnly`; antecipação que cruza o mês = ocorrência pulada + receita avulsa com
  `competenciaDeslocamento` ajustado; salário dividido pergunta a parte; recebimento parcial.
- `domain/assistant/AssistantIntents.kt`: `Authorization` (explícito × hipótese × sim × não) e
  `IntentParser` (regras locais de ação e de consulta, sem IA).
- `domain/assistant/AssistantOrchestrator.kt`: `AssistantOrchestrator`, `FactGuard` (centavos,
  sinal, ano), interfaces `ActionExecutor` e `SessionStore`.
- `domain/assistant/AssistantLoop.kt`: reduzido ao formato da linha "consultei: …".

Dados:
- Room **v20**: `MIGRATION_19_20` (`mensagens_chat.estrutura`, tabelas `sessoes_assistente`,
  `acoes_assistente`, `acompanhamentos`, `planos`), `AssistantEntities.kt`, `AssistantDaos.kt`,
  `OrcamentoCategoriaDao.delete`. **Falta `app/schemas/.../20.json`** (o KSP gera no primeiro build).
- `data/assistant/AssistantData.kt`: `FinancialSnapshotRepository`, `AssistantActionExecutor`
  (idempotência por chave, revalidação, transação, recibo no formato do backup, desfazer),
  `AssistantAnswerCodec`, `RoomSessionStore`, `AssistantWatcher`.
- Backup: `acompanhamentos`, `planos` e `mensagens.estrutura` exportados e restaurados; "Apagar
  todos os dados" limpa as tabelas novas.
- `ChatRepository.registrar` devolve o id e aceita `answer`; `ChatMessage` ganhou `answer` e `id`.
- `InboxCatalog.build(..., watches)`, `FinanceViewModel` (avalia acompanhamentos, `watchRules` no
  estado), `FinanceCheckWorker` (notifica só o acompanhamento que passou a valer).
- `AiPromptBuilder.chat`: parâmetros `actionCatalog` e `focus`; texto do sistema proíbe a IA de
  concluir "cabe" sozinha.
- `AppViewModel`: `send` usa o orquestrador; `undoAction`, `setWatchState`; saíram o
  `PurchaseAssistant`, `setFinanceSnapshot`, `setAssistantContext` e a compra pendente.

## O que falta, em ordem

1. **Aparelho/emulador:** instalar o APK `pessoal` (mesma chave de debug desta máquina: instala por cima) e conferir o
   cartão do chat, "Desfazer", "Ver na Agenda", a lista de acompanhamentos e o aviso `acompanhamento` na Caixa.
   Frases para testar: "Meu salário caiu adiantado este mês, recebi hoje", "Paguei a fatura do Nubank hoje",
   "Guarde R$ 200 na viagem", "Apague o uber de ontem" (pede sim), "Quando posso quitar o Picpay?", "Me avise quando couber quitar o Picpay".
2. **Instrumentados:** `./gradlew connectedDebugAndroidTest` (principalmente `Migration19To20Test`) e um teste do
   `AssistantActionExecutor` contra Room (idempotência por chave, revalidação, recibo, desfazer com conflito).
3. **Modelos reais:** a escolha de ferramenta pela IA (linha JSON) só foi testada com fakes; conferir com o Gemini real.
4. `planning-v2.md`: ampliar a fase 9 (consultas, decisões, memória e ações) e ligar a fase 10 aos planos aceitos.

## Limites conhecidos do que foi escrito

- Tool calling é pelo protocolo de linha JSON (`{"consulta":…}` / `{"acao":…}`) em todos os
  provedores. Function calling nativo do Gemini não foi implementado.
- Compra no cartão continua pelo cartão "Lançamento pronto" e pelo formulário.
- Item de fatura: o assistente só muda a categoria; valor, data e exclusão ficam na Agenda.
- O `ActionPlanner` calcula as linhas e o executor grava direto pelos DAOs (conta/fatura passa por
  `FinanceRepository.salvarConta`). As regras vêm das mesmas funções de domínio das telas, mas é um
  caminho de gravação próprio: precisa de teste contra o repositório.
- `domain/scenario/PurchaseAssistant` e `ScenarioSimulator` (modelo antigo por ciclo) ficaram no
  código, sem uso pelo chat. `FinanceViewModel.registrarCompra` também.
- Antecipação de salário cruzando o mês: o recebimento avulso não entra em
  `IncomePlanner.familyOf`; "Minha renda › regenerar" depois disso não foi analisado.

## Ambiente de build nesta sessão

- SDK instalado em `/opt/android-sdk` (platform 34, build-tools 34.0.0); `local.properties` aponta
  para lá (não versionado). `gradlew` ganhou bit de execução (commitado). O clone era raso: foi
  feito `git fetch --unshallow` para o `versionCode` (contagem de commits) ficar certo (104 + novos).
- `dl.google.com` e `maven.google.com` respondem. **`repo.maven.apache.org` responde de forma
  intermitente (200/404/429)** e o Gradle guarda as falhas em cache por 24 h.
- Um espelho de terceiros (init script apontando para o GCS) foi **negado** pela política da sessão.
  Não tentar de novo sem o usuário autorizar.
- Contorno em andamento, interrompido: baixar do próprio Maven Central, um artefato por vez com
  novas tentativas, para `/opt/m2local`, e apontar o Gradle para essa pasta. Scripts em
  `docs/build-ambiente/` (`localrepo.gradle` vai em `~/.gradle/init.d/`; `fetch-build.sh
  assemblePessoal` repete build → baixa o que faltou). Parou na 1ª iteração (87 MB baixados; o
  container é efêmero, então isso se perde).
- **Assinatura:** o `pessoal` usa a chave de debug da máquina que builda. A deste container é
  diferente da que assinou o app no celular: o APK daqui **não instala por cima** (pede
  desinstalar, o que apaga os dados). Avisar o usuário e sugerir exportar o backup antes, ou buildar
  na máquina dele.

---

# Handoff · FinAI v2, fases 5–11

Atualizado em 14/11/2026, branch `claude/awesome-hawking-pdynac`. Substitui o handoff da sessão
anterior (WIP sem compilar, commit `3dfd139`), que continua no histórico do Git.

**Estado em uma frase:** as fases 5–11 estão implementadas, ligadas ao app e cobertas por testes
JVM, mas **o Gradle não rodou** nesta sessão, então não há APK novo, nem teste instrumentado
executado, nem nada visto numa tela.

## Por que o Gradle não rodou

O `repo.maven.apache.org` respondeu 404 com `retry-after: 78872` (cerca de 22 h) para qualquer jar:
bloqueio do Maven Central para o IP deste ambiente, provavelmente pelos downloads em massa da sessão
anterior. `dl.google.com`/`maven.google.com` funcionam. Usar o espelho do Google para o Maven
Central (`maven-central.storage-download.googleapis.com`) num init script do Gradle foi recusado
pela política de permissões da sessão, então não foi feito.

Para retomar numa máquina (ou sessão) em que o Maven Central responda:

```bash
sh ./gradlew assembleDebug testDebugUnitTest assemblePessoal --max-workers=2
```

(`./gradlew` não tem bit de execução neste checkout.) O SDK precisa de `local.properties` com
`sdk.dir` (aqui foi `/opt/android-sdk`, instalado nesta sessão, fora do Git).

## Como foi validado sem Gradle

Roteiro reproduzível (arquivos em `/tmp/claude-0/`, efêmeros):

1. `kotlinc` 1.9.22 do GitHub; dependências AndroidX resolvidas pelos POMs do `maven.google.com`
   (130 jars, mais `material-icons-extended-android`); `kotlinx-coroutines-core` 1.6.4 e JUnit
   4.13.2 tirados da distribuição do Gradle em `/opt/gradle-8.14.3/lib`.
2. Plugin do Compose `androidx.compose.compiler:compiler:1.5.8` com os pacotes
   `org.jetbrains.kotlin.com.intellij` remapeados para `com.intellij` (ASM do próprio kotlinc),
   para rodar no `kotlinc` de linha de comando.
3. `R.java` gerado com `aapt2` (recursos do app + do `androidx.core`), `BuildConfig` de mentira.
4. Resultado: **app inteiro + `src/debug` + `androidTest` compilam** (com o plugin do Compose) e
   **565 testes JVM verdes**. Fora: `BackupCodecTest` e `CaptureBackupTest` (precisam do
   `org.json` real, do Maven Central), e `runTest` foi substituído por `runBlocking` num shim.

O que essa checagem **não** cobre: KSP/Room (SQL dos DAOs não verificado pelo compilador do Room),
R8, lint, recursos que só o AGP mescla, e qualquer comportamento em tempo de execução.

## O que foi feito nesta sessão

- **Compilação do WIP:** três erros (`themedText` sem import em `PlansScreen`, `selected`
  sombreado em `SimulatorScreen`).
- **Navegação v2** em `FinaiApp.kt` e `FinaiBottomNav.kt`: Hoje · Agenda · [+] · Gastos · Planos.
  `PlansScreen`, `SpendingScreen`, `SimulatorScreen` (camada) e `ClosingScreen` (camada) ligadas;
  `AppLinks` consumido; `ClosingCard` na Hoje; coach "Já sei" → `acknowledgePattern`, "Ver gastos"
  → Gastos; travas `captureBlocked`/`tipsBlocked`/`anyLayerOpen` incluem as camadas novas.
- **Fase 5:** `ui/components/QuickEntryStrip.kt` (Falar, Escrever, Notificações (N), Importar);
  `TransactionEntryScreen(quickEntry = QuickEntryHooks(...))` aplica o `QuickDraft` ao rascunho e diz
  o que faltou; menu do "+" com "Falar ou escrever" e "N das notificações"; `<queries>` para
  `RECOGNIZE_SPEECH`.
- **Fase 8:** "Lançar" do simulador abre uma instância própria do formulário (`prefillEntry`) com
  pagamento, cartão, parcelas e vencimento; "Lembrar depois de <entrada>" grava
  `FinaiPreferences.purchaseReminder` (a rotina já lia).
- **Fase 9:** `domain/assistant/AssistantLoop.kt` (até 3 consultas, trava de números, linha
  "consultei: …"); `AppViewModel.send` trata frase de lançamento antes de tudo (cartão "Lançamento
  pronto", `FinanceViewModel.saveQuickDraft`, Desfazer com `undoEntry`); memória no prompt
  (`AiPromptBuilder.chat(memory = …)`, máximo 10); painel "Sei sobre você" no `ChatOverlay`;
  `ImportViewModel` aprende e aplica regras de categoria.
- **Fase 10:** `AiViewModel.ensureClosingSummary` (uma chamada por mês, guardada no passo
  `resumo`, só se passar na trava); ações dos quatro passos.
- **Fase 11:** telas e componentes antigos removidos (lista em `planning-v2.md`);
  `NotificationContentBuilder` removido; `FinaiNotifier` ficou só com permissão/limpeza;
  dispensar/adiar um aviso cancela a notificação rica dele; `TipScript` reescrito com alvos nas
  telas novas; schema gerado à mão (identityHash conferido contra 4, 9, 12, 15, 16, 17 e o 18 do KSP);
  `CardPurchaseFlowTest` não usa mais a tela de Parcelamentos.
- **Correções achadas pelos testes:** descrição da frase perdia acento ("Pao"); o exemplo do
  lembrete diário em Configurações tinha "Nada hoje" gravando o dia de verdade.
- **Docs:** `CLAUDE.md`, `planning-v2.md` (status e ressalvas), `planning.md` §9/§11, `PRIVACY.md`.

## Merge com `claude/agenda-fatura-paga` (14/11/2026)

Os dois branches criaram uma Room 17→18 diferente. Ficou a do `agenda-fatura-paga` (`contas.pagoEm`,
`18.json` do KSP, já com APK gerado) como 17→18; as três tabelas da v2 passaram para
`MIGRATION_18_19` (`19.json` = 18 + `memoria`, `regras_categoria`, `fechamentos`, identityHash
`494b9b00…`; `Migration18To19Test`). `FINAI_DB_VERSION = 19`. O histórico de pagamentos entrou como
camada aberta pela Agenda "A seguir"; a tela de Parcelamentos que ele ainda abria não volta.

## O que falta, na ordem

1. **Build com Gradle:** `sh ./gradlew assembleDebug testDebugUnitTest assemblePessoal`. Se o
   `19.json` regravado pelo KSP mudar, commitar o novo. Conferir que os dois testes de backup passam.
2. **Instrumentados:** `connectedDebugAndroidTest` (inclui `Migration17To18Test` e `Migration18To19Test`,
   `CardPurchaseFlowTest` alterado, `HomeV2Test`).
3. **Emulador e protótipo:** comparação visual das fases 5–10 com os quadros `v2/Lancar`, `v2/Gastos`,
   `v2/Metas`, `v2/Compromissos`, `v2/Simular`, `v2/Assistente`, `v2/Fechamento`; tema claro;
   320 dp com fonte 150%; TalkBack nos elementos novos. Registrar em `docs/v2-qa/fase-N/`.
4. **Chave real de IA:** conferir o protocolo de linha JSON das consultas com o Gemini/Groq de verdade
   (modelos pequenos podem responder texto em vez do JSON; o app só perde a consulta).
5. **Marco M4:** APK `pessoal` para o celular do usuário.

## Diferenças em relação ao planning-v2 (decididas nesta implementação)

Registradas em `planning.md` §11 (14/11/2026): ferramentas só por linha JSON; busca de Gastos em
memória; "Quero o aviso" = limite da categoria; regras aprendidas na importação aplicadas depois da
análise (a captura por notificação ainda não as usa); notificações sem IA; chat de compra ainda no
`ScenarioSimulator` antigo. A busca no topo da Agenda (fase 4) continua pendente.
