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
  telas novas; `18.json` gerado (identityHash conferido contra 4, 9, 12, 15, 16 e 17);
  `CardPurchaseFlowTest` não usa mais a tela de Parcelamentos.
- **Correções achadas pelos testes:** descrição da frase perdia acento ("Pao"); o exemplo do
  lembrete diário em Configurações tinha "Nada hoje" gravando o dia de verdade.
- **Docs:** `CLAUDE.md`, `planning-v2.md` (status e ressalvas), `planning.md` §9/§11, `PRIVACY.md`.

## O que falta, na ordem

1. **Build com Gradle:** `sh ./gradlew assembleDebug testDebugUnitTest assemblePessoal`. Se o
   `18.json` regravado pelo KSP mudar, commitar o novo. Conferir que os dois testes de backup passam.
2. **Instrumentados:** `connectedDebugAndroidTest` (inclui `Migration17To18Test`,
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
