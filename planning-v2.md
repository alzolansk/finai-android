# FinAI v2 · Planning de implementação

Atualizado em 08/11/2026. Base: branch `renda-agenda-saldo-conciliacao` (commit `c457554`, Room v16).

Este documento transforma a auditoria e o protótipo v2 em fases de implementação. Ele não
substitui o `planning.md` (requisitos e princípios continuam lá). Ele diz **o que muda, em que
ordem, onde no código e como conferir**.

## Status de implementação

| Fase | Estado | Data |
| --- | --- | --- |
| 0 · Base | ✅ implementada (`MoneyRow` ainda sem tela: ver ressalva 7) | 08/11/2026 |
| 1 · Motor de saldo | ✅ implementada e conferida no emulador | 08/11/2026 |
| 2–11 | não iniciadas | |

Branch `claude/awesome-hawking-pdynac`. 439 testes JVM verdes, `assembleDebug` e `assemblePessoal` ok.
O Room continua na v16 (nenhuma migração nas fases 0–1).

### Fase 0 — o que foi feito
- `Categorias.all` é a lista única de 10 (Alimentação, Moradia, Transporte, Saúde, Lazer, Compras, Educação,
  Serviços, Assinaturas, Outros); `TransactionEntry.categories` aponta para ela. `Categorias.system`
  ("Dívidas", "Ajuste de saldo") fica fora. O `MerchantClassifier` ganhou regras de Educação e Serviços.
  "+", Limites, importação, classificador e prompt da IA leem a mesma lista (`CategoriasTest`).
- `ui/components/MoneyRow.kt`: avatar 38 dp, nome + selo "Pago"/"Recebido", valor esmaecido, "fica R$ X",
  toque de 60 dp com `Role.Button` e estado lido pelo TalkBack, sem caixinha. Token `Sky` novo
  (`Amber` já existia).
- `src/debug/.../DemoScenarioV2.kt` + `DemoScenarioReceiver` (só no build debug; o `pessoal` não leva):
  `adb shell am broadcast -a com.finai.app.DEBUG_LOAD_DEMO -n com.finai.app/.debug.DemoScenarioReceiver`
  apaga os dados e grava o cenário do protótipo (o aparelho precisa estar em 08/11/2026).

### Fase 1 — o que foi feito
- `domain/balance/`: `AccountBalance` (regra 1), `NextIncomeFinder`/`NextIncome` (regra 3) e `FreeToSpend`
  (regra 2, com a série diária, o "vai sair", a falta e o bloco da IA). Margem em
  `FinaiPreferences.safetyMarginCents` (padrão R$ 300; **ainda sem tela para trocar**, vem na Fase 2).
- Ligados ao motor novo: `FinancialPlan.build` (livre, reserva, falta, `safe`), `HomeSituation` (rótulo
  "LIVRE ATÉ O ADIANTAMENTO · 20/11"; por dia em reais inteiros, para baixo), `SpendExplanation` (conta
  "em conta − vai sair − margem = livre"), o bloco da IA (`aiCashFlowBlock`), o sino/worker, o simulador de
  compra (`FinanceSnapshot.safetyMarginCents`) e `AgendaBalance` (saldo de hoje = `AccountBalance`).
- Testes novos: `FreeToSpendTest` (14), `AccountBalanceTest` (5), `DemoScenarioV2Test` (3, em `src/testDebug`).
- Conferido no emulador Pixel 6 API 34 com o cenário de demonstração: a Início mostra **R$ 1.220** livre até
  o adiantamento de 20/11, 12 dias, **R$ 3.480** em conta e menor saldo **R$ 1.520 em 18/11**
  (`docs/v2-qa/fase-1/`).

### Ressalvas (leia antes de testar no celular)
1. **Comparação visual com o protótipo não foi feita.** O artefato
   `claude.ai/artifact/Tzb24N1BzGMR8buHwVAtL9` devolveu "not found" nas duas tentativas (08/11), não aparece
   na lista de artefatos da conta logada no Claude Code e nenhum projeto do Claude Design tem a v2. A
   conferência foi pelos números do próprio planning (R$ 3.480 / R$ 1.520 / R$ 1.220 / R$ 101).
   **Os valores de academia (180), condomínio (340) e internet (120) do cenário são palpites** que somam os
   R$ 1.960 do quadro; se o quadro tiver outros, ajustar `DemoScenarioV2` e o teste.
2. **O saldo agora parte do primeiro lançamento (até 24 meses), não do dia 1º do mês.** Quem já usa o app
   vai ver o "saldo em conta" mudar na primeira abertura (o histórico passa a contar). Conciliar com o banco
   (ajuste) reancora. É a decisão A4, mas é a mudança de maior efeito destas duas fases:
   **testar no celular antes das próximas (marco M1).**
3. **Falta só quando o saldo previsto fica negativo.** Entre zero e a margem, o livre é R$ 0 e o app **não**
   chama de falta (`FreeToSpend.insideMargin`). O planning dizia "livre negativo vira falta"; segui o
   caminho menos alarmista. Para o literal, é uma linha em `FreeToSpend.build`.
4. **Sem renda cadastrada, o caminho é o antigo** (livre do mês inteiro): com saldo contínuo e só gastos
   lançados, um "falta R$ 5.000" seria enganoso.
5. **`PayCycle` não mudou** (ainda tem a regra "sobra do ciclo anterior não entra"). Quem ainda o usa:
   `CashFlowTimeline` (só sem renda, para a IA) e `PeriodProjection`/`ScenarioSimulator` nos ciclos
   **futuros**. Por isso o simulador de compra mistura o livre novo (período atual, com margem) com o piso
   por ciclo sem margem nos seguintes, até a Fase 8 trocá-lo.
6. **Conta vencida e não paga sai hoje; entrada vencida e não recebida não conta** (não há como saber
   quando cai). Na Agenda "A seguir" a conta vencida de mês passado continua escondida (Fase 4).
7. **`MoneyRow` ainda não está ligado a nenhuma tela** (entra na Início e na Agenda nas Fases 2 e 4).
8. **Testes antigos ajustados por causa da regra nova:** `FinancialPlanTest`, `GoalPlanningTest` e
   `CashFlowTimelineTest` (salário recorrente passou a começar no último ciclo e margem 0, porque o saldo
   contínuo somaria os meses anteriores); `PayCycleTest` (margem 0); `ScenarioSimulatorTest` (margem 0 no
   snapshot); `ReconciliationTest` (sem ajuste o saldo já parte do primeiro lançamento: R$ 8.800, antes
   R$ 5.000); `EntryOptionsTest` (10 categorias).
9. **Não executados:** instrumentados (`connectedDebugAndroidTest`), tema claro, 320 dp com fonte 150%,
   TalkBack, Agenda/chat na tela. O R$ 101 por dia foi corrigido depois da captura e não foi recapturado.

## 1. Fontes

- **Protótipo v2 (referência visual de cada fase):** https://claude.ai/artifact/Tzb24N1BzGMR8buHwVAtL9
  - Página **"Protótipo v2"**: 11 telas de 390 × 844 dp, navegáveis em Play.
    `v2/Hoje`, `v2/Livre`, `v2/Caixa`, `v2/Agenda`, `v2/Gastos`, `v2/Metas`, `v2/Compromissos`,
    `v2/Lancar`, `v2/Assistente`, `v2/Simular`, `v2/Fechamento`.
  - Quadros `v2/Estrutura` (o que muda, por resposta) e `v2/Logicas` (as 10 regras de cálculo).
  - Página **"Auditoria"**: mapa do app, fraquezas (A/B/C) e IA (P1–P11), com os comentários
    do usuário no quadro 2.
- Respostas do usuário (08/11/2026), registradas em `planning.md` §11.

## 2. Decisões do usuário que este plano respeita

| Tema | Decisão |
| --- | --- |
| Escopo | Reorganizar a navegação inteira. App só para uso pessoal. Dados continuam no aparelho. |
| Lançamento | **O formulário do "+" continua inteiro** (gasto, receita, transferência, teclado, parcelas, reserva, desfazer). Jeitos novos (voz, frase, notificação, importar) **entram ao lado**, nunca no lugar. |
| Agenda | **Sem caixinha de seleção.** Tocar paga, tocar de novo reabre; o estado aparece como selo "Pago"/"Recebido" e valor esmaecido. As duas visões (A seguir e Mês) continuam. |
| Saldo | A sobra não zera no salário (A4). Conciliação com o banco já existe e vira a âncora. |
| Cartão | Só vencimento, sem limite (A5). |
| Avisos | Dispensado não volta; ponto vermelho persistente (A8). |
| Compra simulada | Só grava com pedido explícito; quando grava, entra na fatura certa (A6). |
| Categorias | Uma lista só (A1). |
| Dívidas | Dívidas, parcelamentos e faturas num lugar só (B9). |
| Início | Fica: livre, saldo em conta (fundidos), 7 dias, metas, coach só com novidade. Sai: "Decisões para você", linha do tempo do ano. |
| Objetivos | Sem "Falta até o salário", sem bullets; "Entenda" vira gráfico curto. |

## 3. Invariantes (valem em todas as fases)

1. **Cálculo é Kotlin puro em `domain/`, com teste JVM.** A IA só escreve texto e escolhe
   consultas; nenhum número da tela vem dela (`planning.md` §6). `ScenarioReplyGuard` continua
   barrando valor ou data fora dos fatos.
2. **Nada é apagado do Room sem migração.** Migrações só `ADD COLUMN`/`CREATE TABLE`, com teste
   `MigrationXToYTest` e o `N.json` gerado pelo build e commitado.
3. **O formulário de lançamento (`TransactionEntryScreen`) não perde nenhum caminho.** Toda fase
   que mexe nele roda os testes `TransactionEntryFlowTest`, `CardPurchaseFlowTest` e
   `EntryKeypadLayoutTest`.
4. **Tocar para pagar continua com um toque**, nas mesmas linhas de hoje (conta, fatura, parcela
   abre a folha como hoje).
5. **Sem modelo pago, sem chave em texto puro, sem dado sensível no prompt** (CLAUDE.md, Convenções).
6. Uma fase só termina com `./gradlew assembleDebug testDebugUnitTest` verde e a comparação
   visual da §5 feita e registrada.

## 4. Mapa de navegação v2

```
Barra:  Hoje · Agenda · [+] · Gastos · Planos
Topo:   Caixa (com ponto) · Assistente (orbe)

Hoje ──► De onde vem o livre (gráfico + conta + conferir com o banco)
     ──► Posso comprar?  ──► Lançar compra (formulário/fatura)
     ──► Fechamento do mês (dias 1–7 do mês, ou pendente)
Agenda: A seguir | Mês        ──► folhas atuais (AgendaSheets) e Fatura
Gastos: busca, mês/ano, categorias com limite, assinaturas
Planos: Metas | Compromissos  ──► criar/editar meta (telas atuais), Fatura, Minha renda
[+]:    formulário atual + faixa "Falar · Escrever · Notificações (N) · Importar"
```

O que deixa de existir como destino próprio: aba Dívidas (vira Planos › Compromissos), Limites
(vira parte de Gastos), Parcelamentos (vira parte de Compromissos), sino/painel de avisos (vira
Caixa), folha "Posso comprar?" antiga, folha de texto "Entenda este valor".

## 5. Como usar o protótipo para comparação visual

O protótipo é a referência de **hierarquia, conteúdo e ordem**, não de pixel. Cada fase lista os
quadros de referência e um checklist do que tem que bater.

**Preparar o emulador no mesmo tamanho dos quadros (390 × 844 dp):**

```bash
adb shell wm size 1170x2532
```

```bash
adb shell wm density 480
```

(1170 ÷ 3 = 390 dp; 2532 ÷ 3 = 844 dp. Para voltar: `adb shell wm size reset` e
`adb shell wm density reset`.)

**Capturar** (o `screencap` sai branco com a GPU swiftshader, ver CLAUDE.md):

```bash
adb emu screenrecord screenshot docs/v2-qa/fase-N/
```

**Procedimento por fase:**

1. Abrir o quadro de referência em modo foco (no canvas, duplo clique no quadro) e o print do
   emulador lado a lado, no mesmo tamanho.
2. Usar os mesmos dados do protótipo. A fase 0 cria `domain/DemoScenarioV2.kt`
   (só em `src/debug/` ou atrás de uma ação de debug, nunca no build `pessoal`) que grava o
   cenário do protótipo: 08/11, R$ 3.480 em conta, fatura Nubank R$ 1.320 em 10/11, academia,
   internet, Picpay 5/12, adiantamento R$ 2.400 em 20/11, metas Chile/Notebook/Reserva etc.
   Assim os números da tela e do quadro têm que ser **iguais**, o que também valida o cálculo.
3. Preencher a tabela de conferência em `docs/v2-qa/fase-N/README.md`:

   | Item do checklist | Igual | Diferente (por quê) | Print |
   | --- | --- | --- | --- |

4. Conferir também tema claro, 320 dp de largura com fonte 150% e TalkBack nos elementos novos.
   O protótipo só desenha o tema escuro; no claro vale o mesmo layout com os tokens claros.
5. Divergência intencional (plataforma, acessibilidade, dado real) vai na coluna "por quê". Se a
   divergência muda o desenho, atualizar o quadro no canvas antes de fechar a fase, para o
   protótipo continuar sendo a verdade.

**Tradução de cores do protótipo para `FinaiColors`** (sem token novo, salvo onde marcado):

| Protótipo | Token |
| --- | --- |
| #09090B fundo, #141418 cartão, #24242A borda | `Background`, `Surface`, `Stroke` (escuro) |
| #F4F4F5 texto e botão principal | `TextStrong` / `InkStrong` |
| #A1A1AA texto de apoio | `TextMuted` |
| #34D399 livre, entrada, selo "Pago" | `Emerald` (acento) / `EmeraldDark` (texto) |
| #F59E0B / #FBBF24 margem, menor saldo, "apertado" | **novo** `Amber` / `AmberText` |
| #FB7185 falta, atraso | `RoseDark` |
| #60A5FA reserva contínua, parcelado no cartão | **novo** `Sky` |

## 6. Fases

Ordem pensada por dependência: primeiro a base (categorias, linha de lista, motor de saldo),
depois as telas que dependem dela. Marcos de teste no celular (o usuário testa o conjunto):
**M1** = fases 0–2, **M2** = 3–5, **M3** = 6–8, **M4** = 9–11.

---

### Fase 0 · Base: categorias únicas, linha de lista, cenário de demonstração

**Referência visual:** `v2/Agenda` e `v2/Hoje` (só a linha de lista), `v2/Lancar` (faixa de
categorias), `v2/Gastos` (nomes de categoria).

**Domínio**
- `data/model/Categorias.kt` vira a **única** lista: Alimentação, Moradia, Transporte, Saúde,
  Lazer, Compras, Educação, Serviços, Assinaturas, Outros (10). `TransactionEntry.categories`
  passa a apontar para ela. Categorias de sistema à parte e fora de limite/coach:
  `DebtSchedule.PAYMENT_CATEGORY` ("Dívidas") e ajuste de saldo.
- Quem usa hoje cada lista: `BudgetsScreen:69`, `ImportScreen:575`, `ImportAiAssistant:60`,
  `MerchantClassifier`, `EntryOptions:70–73`, `InitialSetupWizard:305`, `TransactionEntryScreen:155/434`,
  `SuggestionDialog:323`. Todos passam a ler `Categorias.all`.
- Sem migração Room: categoria é texto e as duas listas antigas cabem na nova.
- `domain/DemoScenarioV2.kt` (debug): cenário do protótipo, para a §5.

**UI**
- `ui/components/MoneyRow.kt` (novo): avatar 38 dp (sigla ou ícone da categoria, verde para
  entrada), nome + selo "Pago"/"Recebido", subtítulo, valor à direita (esmaecido quando pago) e
  linha opcional "fica R$ X". Toque inteiro de 60 dp, `Role.Button`, `stateDescription`
  "pago"/"pendente". **Nenhuma caixinha.**
- Faixa de categorias do formulário ganha Assinaturas (10 itens; a faixa já rola).

**Testes:** `CategoriasTest` (sem duplicata, sistema fora de `all`), ajuste dos testes de
importação/merchant que esperam a lista antiga.

**Comparação visual**
- [ ] Linha da Agenda: avatar, nome, selo "Pago" ao tocar, valor esmaecido, sem caixinha.
- [ ] Faixa de categorias com as 10, na ordem de uso recente.

**Pronto quando:** a mesma categoria aparece no "+", em Limites, na importação e no prompt de
categoria da IA.

---

### Fase 1 · Motor de saldo: saldo contínuo, próxima entrada e margem

**Referência visual:** quadro `v2/Logicas` regras 1, 2 e 3; números de `v2/Hoje` e `v2/Livre`.

**Domínio** (Kotlin puro, `domain/balance/`)
- `AccountBalance` (regra 1): saldo em conta hoje = âncora + efetivados desde a âncora.
  Âncora = ajuste de conciliação mais recente (`tipo = "Ajuste"`, já existe). Sem conciliação:
  soma de tudo que foi efetivado desde o primeiro lançamento (hoje `AgendaBalance.startMonthFor`
  parte do mês corrente; essa regra muda e `AgendaBalance` passa a usar `AccountBalance`).
- `NextIncome` (regra 3): primeira renda recorrente depois de hoje (adiantamento ou saldo do
  `IncomeSplit`), com fallback para a renda principal estimada (`PayCycle.salariesOf`).
- `FreeToSpend` (regra 2): `saldoDia(d)` de hoje até a véspera da próxima entrada, com as
  mesmas regras de entrada/saída do `CashFlowTimeline.monthMovements` (fatura pelo vencimento,
  conta paga adiantada conta hoje, aporte = saída, transferência entre contas neutra);
  `livre = min(saldoDia) − margem`; `porDia = livre ÷ dias`. Negativo vira
  `Shortfall(valor, data, causa)`. Devolve também a série diária (para o gráfico da fase 2).
- **Margem de segurança:** `FinaiPreferences.safetyMarginCents` (DataStore), **padrão R$ 300
  fixos** (decisão de 08/11). O usuário troca em "De onde vem o livre"; 0 = sem margem. Não é
  recalculada sozinha.
- Quem passa a ler o motor novo: `FinancialPlan.build` (o `livreCents` e a falta),
  `SafeToSpendCalculator`, `HomeSituation`, `ScenarioSimulator` (fase 8) e o bloco da IA
  (`FinanceUiState.aiCashFlowBlock`). `PayCycle` fica só como "janela até a próxima entrada" e
  para a renda; a regra "sobra do ciclo anterior não entra" sai.
- `planning.md` §11 (decisão de 10/10 "o saldo recomeça a cada salário") é **revogada** por A4;
  registrar lá.

**Testes** (`FreeToSpendTest`, `AccountBalanceTest`): cenário do protótipo dá exatamente
R$ 3.480 em conta, menor R$ 1.520 em 18/11, livre R$ 1.220 com margem 300 e R$ 101/dia;
sobra de outubro aparece em novembro; ajuste de conciliação reancora; renda em duas partes troca
a janela em 20/11; falta prevista substitui o livre; aporte reduz uma vez só. Rodar os testes
existentes de `PayCycleTest`, `FinancialPlanTest`, `CashFlowTimelineTest` e ajustar só onde a
regra de carry muda (anotar cada ajuste no commit).

**Comparação visual:** nenhuma tela nova; conferir que a Início atual já mostra R$ 1.220 com o
cenário de demonstração.

**Pronto quando:** Início, Agenda "A seguir", Objetivos e chat dão o mesmo livre para o mesmo dia.

---

### Fase 2 · Hoje e "De onde vem o livre"

**Referência visual:** `v2/Hoje`, `v2/Livre`.

**UI**
- `HomeScreen` reescrita, nesta ordem:
  1. Topo: símbolo, Caixa (ponto/número, fase 3 liga o número real), orbe do Assistente.
  2. Título "Hoje" + data.
  3. **Cartão do livre** (`FreeCard`): rótulo "LIVRE ATÉ O <ADIANTAMENTO|SALÁRIO> · dd/MM",
     valor grande, "cerca de R$ X por dia nos próximos N dias", barra segmentada
     (vai sair / margem / livre) com as três legendas, linha "Em conta R$ X · conferido há N dias"
     → "Por quê ›", botões "Posso comprar?" e "Conferir saldo". Com falta: o mesmo cartão em
     rosa, "FALTAM R$ X EM dd/MM" e a causa; nunca dois números.
  4. Próximos 7 dias com `MoneyRow` (fase 0), toque paga/reabre, link "Agenda ›".
  5. Coach só com novidade (fase 9 liga a memória; nesta fase, só a regra 9 do `v2/Logicas` e o
     botão "Já sei, não avisar" gravando em `dismissedInsights`).
  6. Metas resumidas (2 primeiras + "Plano de novembro: guardar R$ X em <meta>").
  7. Cartão do fechamento do mês (fase 10; até lá, escondido).
- Saem da Início: `SaldoCard` (balanço do mês vai para Agenda › Mês e Gastos),
  `ProjectedBalanceCard` (fundido no cartão do livre), `DecisionsCard`, `TimelineSection`
  (entradas extras passam a aparecer na Agenda "A seguir").
- `SpendExplanationSheet` → `ui/screens/home/FreeExplainedScreen.kt`: gráfico de barras do saldo
  por dia (hoje claro, menor saldo âmbar, entrada verde), conta de 5 linhas
  (em conta − vai sair = menor saldo − margem = livre; "vai sair" expande a lista), seletor da
  margem (Sem / 150 / 300 / 500 / personalizado), bloco "Conferir com o banco" (reaproveita a
  lógica do `ReconcileSheet`) e a nota "A sobra não some".

**Testes:** `HomeSituationTest` atualizado; instrumentado `HomeV2Test` (cartão mostra livre,
falta substitui livre, tocar numa conta dos 7 dias marca paga e reabre).

**Comparação visual**
- [ ] `v2/Hoje`: ordem dos blocos; um número grande só; barra com três partes e legendas;
      "Em conta … · conferido há N dias" + "Por quê ›"; dois botões; 7 dias sem caixinha.
- [ ] Sem "Decisões para você", sem linha do tempo, sem segundo cartão de saldo.
- [ ] `v2/Livre`: gráfico com menor saldo marcado, 5 linhas batendo com o cartão, margem
      trocando o livre na hora, conferência com o banco no fim.

**Marco M1:** APK `pessoal` para o usuário (fases 0–2).

---

### Fase 3 · Caixa: avisos com estado + "para confirmar"

**Referência visual:** `v2/Caixa`.

**Dados — Room 16→17** (`MIGRATION_16_17`, `CREATE TABLE`):
`avisos(id TEXT PK, tipo TEXT, estado TEXT, valorRef INTEGER, vistoEm TEXT, resolvidoEm TEXT,
dispensadoEm TEXT, adiadoAte TEXT)`. `Migration16To17Test`. Migra os ids de
`FinaiPreferences.dismissedInsights` para `estado = dispensado` na primeira leitura.

**Domínio** (`domain/inbox/`)
- `InboxItem` = `FinanceAlert` (de `AlertCalculator`, agora com `valueCents` e `kind`) +
  sugestões pendentes de `CaptureRepository` + pontos de `GoalPlanning` que pedem decisão.
- `InboxRules.visible(alerts, estados, hoje)` (regra 8 do `v2/Logicas`): resolvido some;
  dispensado só reabre se piorou (limite ≥ 100%, conta passou a atrasada, ou
  |valor − valorRef| > 20%); adiado volta na data. Novo = sem `vistoEm`.
- O id do aviso deixa de levar números no texto (hoje o título com números mudava a chave e o
  aviso "voltava"). Id estável por evento: `fatura-<id>-<mês>`, `limite-<categoria>-<mês>`, etc.

**UI**
- `ui/screens/inbox/InboxScreen.kt` (rota própria, aberta pelo ícone do topo e pela notificação):
  grupos "Para confirmar" (notificações capturadas, com Salvar / Editar → formulário do "+"
  preenchido / Ignorar), "Agir até <dia>", "Acompanhar"; ações por item (Marcar paga, Lembrar no
  dia, Ver gastos, Dispensar); estado vazio; bloco "N dispensados · não voltam sozinhos" com
  "Desfazer tudo".
- Ponto do topo = itens com estado novo, lido do Room (sai o `seenAlertIds` de `FinaiApp:230`).
- Removidos: `NotificationsPanel`, indicador "N para confirmar" das telas (vira o número da Caixa).
  O quadro "Movimentação encontrada" do lançamento por notificação continua como hoje.
- `FinanceCheckWorker` não notifica aviso dispensado/resolvido; dedupe diário continua.
- Pontos de atenção de Objetivos deixam de ter dispensa própria (vão para a Caixa).

**Testes:** `InboxRulesTest` (dispensado não volta; volta ao estourar; volta com +20%; adiado volta
na data; id estável), `Migration16To17Test`, instrumentado do fluxo dispensar → reabrir app →
continua dispensado.

**Comparação visual**
- [ ] `v2/Caixa`: três grupos com cores, dois botões por item, item some ao agir, estado vazio,
      rodapé de dispensados.
- [ ] Reabrir o app não mostra ponto vermelho para o que já foi visto.

---

### Fase 4 · Agenda

**Referência visual:** `v2/Agenda` (as duas abas).

- Linhas com `MoneyRow` (fase 0): **sem caixinha**, selo "Pago"/"Recebido", valor esmaecido;
  toque alterna como hoje; parcela de dívida abre a folha como hoje (decisão de 11/10).
- "A seguir": saldo do `AccountBalance` (fase 1), "fica R$ X" em cada linha, menor saldo em âmbar,
  entrada de renda com fundo verde, divisor de mês "começa com R$ X · nada zera", entradas extras
  (13º) na lista, marca "HOJE · em conta R$ X". Mantém o bloco recolhido de anteriores.
- Aba "Mês" (nome do mês): três números (Entra / Sai / Sobra = `MonthCashFlow`, o antigo balanço
  da Início), grupos "Já passou" e "A partir de hoje", navegação de mês como hoje, cartão de
  parcelamentos sai (vai para Compromissos).
- Busca no topo leva a Gastos com o campo focado (fase 6).
- `AgendaSheets`, edição de recorrente e exceções: sem mudança.

**Testes:** `AgendaBalanceTest` ajustado ao saldo contínuo; instrumentado: tocar paga, tocar de
novo reabre, selo aparece, TalkBack lê "pago".

**Comparação visual**
- [ ] Nenhuma caixinha nas duas abas.
- [ ] "A seguir": "fica R$" por linha, menor saldo destacado, divisor de mês sem zerar.
- [ ] "Mês": três números no topo, balanço igual ao que a Início mostrava antes.

---

### Fase 5 · Lançar: formulário atual + jeitos novos

**Referência visual:** `v2/Lancar`.

**O formulário atual fica inteiro.** Nada do `TransactionEntryScreen` é removido: tipos,
pagamento, parcelas, fatura, receita em 4 tipos, renda em duas partes, destinos de
transferência, pílulas, "Todo mês", confirmação, Desfazer, Lançar outro, Ver na agenda, edição.

**O que entra**
- **"+" continua abrindo o menu atual** (`QuickActionSheet`, decisão de 08/11): "Lançar gasto"
  em destaque e "Mais ações" (importar, simular, novo objetivo). O menu ganha uma linha
  "Falar ou escrever" (abre o formulário já com o campo de frase ativo) e, quando houver, o item
  "N das notificações" (abre a Caixa em "Para confirmar").
- Faixa no topo do formulário, rolável: **Falar** · **Escrever** · **Notificações (N)** ·
  **Importar fatura**. O quadro `v2/Lancar` mostra o formulário que o menu abre.
- `domain/quickentry/QuickEntryParser.kt` (Kotlin puro): frase → rascunho
  (`"uber 23 ontem"`, `"gastei 45,90 no ifood no nubank"`, `"recebi 300 do freela"`,
  `"guardei 200 na reserva"`): valor, data relativa, tipo, conta/cartão pelos nomes conhecidos
  (`EntryOptions.recentAccounts`), categoria por `MerchantClassifier` + regras aprendidas
  (fase 9). O que faltar fica em branco no formulário, nunca chutado.
- **Escrever:** campo de uma linha que preenche o formulário na hora (o usuário confere e salva
  pelo botão de sempre). **Falar:** `RecognizerIntent` com `EXTRA_PREFER_OFFLINE`; o texto vai
  para o mesmo parser. Sem IA nesta fase.
- O mesmo parser serve o assistente (fase 9) e a captura de notificação (quando a regra fixa não
  reconhecer).

**Testes:** `QuickEntryParserTest` (≥ 25 frases reais em pt-BR, valores com vírgula/ponto,
"ontem", "sexta", "dia 10", contas desconhecidas ficam em branco); os três instrumentados do
formulário verdes sem alteração de comportamento; novo `QuickEntryFlowTest` (frase preenche,
salvar grava igual ao manual).

**Comparação visual**
- [ ] `v2/Lancar`: abas, faixa de jeitos novos acima do valor, valor grande, faixa de
      categorias, Débito/Pix · Crédito · Parcelado, linha da fatura, pílulas, salvar, teclado.
- [ ] Receita e Transferência idênticas ao app atual.

**Marco M2:** APK para o usuário (fases 3–5).

---

### Fase 6 · Gastos

**Referência visual:** `v2/Gastos` (Outubro, Novembro e 2026).

**Domínio** (`domain/spending/SpendingAnalysis.kt`)
- Total do período (mês até hoje, mês fechado, ano), comparação com o mesmo dia do mês
  anterior, tendência de 6 meses, por categoria com limite (`orcamento_categorias`), ritmo
  ("passa do limite em N dias" = gasto ÷ dias × dias do mês), maiores estabelecimentos.
  Só `tipo = Gasto` e categorias de `Categorias.all` (sistema e ajuste fora). Itens de fatura
  pela data da compra (análise de consumo, não de caixa: dizer isso no rodapé).
- Busca: `TransacaoDao.search(q)` (`LIKE` em descrição/categoria/origem, ignorando acento via
  coluna normalizada em memória) + filtro por período.
- `LimitSuggestion` (regra 10): média de 3 meses, −5% onde estourou, arredondado a R$ 10.
- Assinaturas: `AssinaturaEntity` + `RecurrenceDetector`, total mensal e anual. O app não tem
  sinal de uso de um serviço, então o destaque é "cobrada há N meses seguidos", nunca "sem uso"
  (o quadro do protótipo foi corrigido para isso).

**UI:** `ui/screens/spending/SpendingScreen.kt` (aba), absorve `BudgetsScreen` (definir/ajustar
limite por toque na categoria, "Sugerir limites") e a lista de assinaturas. Entrada pela barra.

**Testes:** `SpendingAnalysisTest`, `LimitSuggestionTest`, DAO de busca instrumentado.

**Comparação visual**
- [ ] Busca no topo, seletor Outubro/Novembro/2026, total grande com comparação em uma frase,
      barras de 6 meses, categorias com barra e nota, assinaturas com total mensal e anual.

---

### Fase 7 · Planos: Metas e Compromissos

**Referência visual:** `v2/Metas` (antes e depois de "Mudar para nov/27"), `v2/Compromissos`
(com e sem contas fixas).

**Domínio**
- `SavingsCapacity` (regra 4): sobra por mês = entradas − saídas previstas (contas, parcelas,
  gasto variável médio) nos próximos 6 meses; `cabeGuardar` = média. Substitui o "sobra prevista
  12 meses" e o card "Metas pedem × Sobra".
- `GoalConflictResolver` (regra 5): se `pedem > cabeGuardar`, para a meta de menor prioridade
  calcula o primeiro prazo em que todas cabem. Devolve **um** conflito por vez. "Manter" grava a
  decisão (Caixa, `estado = dispensado` com `valorRef = cabeGuardar`; volta se a sobra cair 20%).
- Selo da meta: **Em dia / Não cabe / Contínua** (+ Concluída). `GoalPace` fica para a IA, a
  tela usa o selo.
- `CommitmentsSchedule` (regra 7): junta `DebtSchedule` (empréstimos), `InstallmentsSummary`
  (parcelado no cartão, inclusive estimadas de fatura importada), faturas abertas e contas fixas
  recorrentes; `comprometido(m)` por tipo e `livreDeParcelas`.

**UI**
- `ui/screens/plans/PlansScreen.kt` com abas Metas | Compromissos.
- Metas: cartão "Quanto cabe guardar por mês" + "Suas metas pedem" com barra + o conflito com dois
  botões; cartões de meta com nome, selo, guardado/alvo, %, barra e **uma** linha; tocar abre
  detalhes curtos (último aporte, ritmo de 3 meses, "Guardar agora", "Perguntar"). Criar, editar e
  aporte reaproveitam `GoalCreateScreen`, `GoalEditScreen`, `GoalContributionSheet`.
  Saem: livre/falta, pontos de atenção, "Mostrar dispensados", análise de IA fixa.
- Compromissos: total em parcelas, R$/mês, "livre de parcelas em <mês>", gráfico empilhado
  (empréstimo / cartão / contas fixas opcionais), grupos Empréstimos, Parcelado no cartão,
  Faturas (abre `InvoiceScreen`), Contas fixas; cartão de oportunidade quando há dívida com juros
  ≥ 3% a.m. (cálculo de antecipação em `DebtCalculator`).
  `DebtsScreen` e `InstallmentsScreen` são removidos depois que tudo estiver aqui (o
  "Paguei a parcela", quitadas e o roteiro de negociação vão para os detalhes do empréstimo).

**Testes:** `SavingsCapacityTest`, `GoalConflictResolverTest` (cenário: pedem R$ 1.625, cabe
R$ 820, Notebook → nov/27 com R$ 220/mês), `CommitmentsScheduleTest` (R$ 1.010 até mar, 600 em
abr, 410 até jun, livre em jul/27).

**Comparação visual**
- [ ] Metas: nenhum "livre", "falta até o salário" ou bullet; um conflito, dois botões; o botão
      muda a barra para verde e o selo para "Em dia".
- [ ] Compromissos: total, data de fim das parcelas, gráfico, quatro grupos.

---

### Fase 8 · Posso comprar? (modelo novo)

**Referência visual:** `v2/Simular` (Pix, 1×, 3×, 6×, 10×, com e sem desconto).

**Domínio** (`domain/scenario/PurchaseSimulatorV2.kt`, regra 6)
- Projeção mensal de 6 meses a partir de `AccountBalance` (saldo contínuo) com as saídas
  previstas, **sem descontar aportes planejados das metas** (decisão de 08/11: como é hoje,
  aporte só sai do saldo quando é registrado). `menorSaldo(m)` por mês, com e sem a compra, para cada forma de pagamento.
- Veredito por forma: `pior − margem < 0` não cabe; `< R$ 300` apertado; senão cabe. Melhor =
  menos parcelas entre as que cabem. Efeito nas metas = valor ÷ `cabeGuardar` em meses de atraso
  (igual para qualquer forma: dizer isso).
- Desconto à vista e juros do parcelamento como entrada opcional.
- `PurchaseIntentParser` continua para o chat; perguntas de volta só para o que muda a conta.

**UI:** `ui/screens/simulator/SimulatorScreen.kt`: nome + valor, chip de desconto, cinco formas
com ponto colorido, cartão do veredito (selo, frase, detalhe, gráfico com e sem a compra e a
linha da margem), nota das metas, "Lançar no <cartão> em N×" e "Lembrar depois de <entrada>".
"Lançar" abre o **formulário do "+" preenchido** (Crédito/Parcelado com o cartão), que já grava
na fatura certa (`salvarCompraCartao`). Isso fecha A6. O chat de compra usa o mesmo simulador e
mostra o resultado como cartão que abre esta tela.
Saem: `BuySimulatorSheet`, `PurchaseScenario.toRecords`.

**Testes:** `PurchaseSimulatorV2Test` com os números do quadro (Pix R$ 320 apertado; 1× R$ 700
em jan, melhor; 10× R$ 1.520), instrumentado "Lançar em 3×" cria parcelas dentro das faturas.

**Comparação visual**
- [ ] Cinco formas com cor; trocar muda selo, frase e gráfico; desconto só no Pix; botão diz a
      forma escolhida.

**Marco M3:** APK para o usuário (fases 6–8).

---

### Fase 9 · Assistente que consulta, lança e lembra

**Referência visual:** `v2/Assistente`.

**Ferramentas (P1)**
- `AiProvider` ganha `generateWithTools(request, tools)`; `OpenAiCompatibleAiProvider` usa o
  campo `tools` do `chat/completions`; `GeminiAiProvider` usa `functionDeclarations`. Provedor
  ou modelo sem suporte: fallback de duas chamadas (a IA devolve JSON com a consulta, o app
  executa, a IA responde). Conferir suporte do modelo gratuito de cada provedor na documentação
  antes (CLAUDE.md, regra dos `DEFAULT_MODEL`).
- Ferramentas, todas locais e determinísticas (`domain/assistant/AssistantTools.kt`):
  `buscar_lancamentos(texto, periodo)`, `total_por_categoria(categoria, periodo)`,
  `comparar_periodos(categoria, a, b)`, `saldo_previsto(data)`, `livre_hoje()`,
  `simular_compra(valor, forma)` (fase 8), `rascunho_lancamento(frase)` (fase 5).
  Máximo de 3 chamadas de ferramenta por pergunta. Resultados viram o `factsBlock` do
  `ScenarioReplyGuard`.
- A resposta mostra "consultei: …" (o que a ferramenta buscou), destaques `{{rótulo|valor}}` e
  ações (Ver os pedidos, Avisar se passar de R$ X).

**Lançar pelo chat (P2):** frase de lançamento → `QuickEntryParser` primeiro; cartão "Lançamento
pronto" com Salvar / Editar (formulário preenchido); salvo mostra Desfazer.

**Memória (P6) — Room 17→18** (`MIGRATION_17_18`): `memoria(id, texto, tipo, criadoEm, origem)`.
Entram só fatos confirmados pelo usuário ("Já sei", "Manter separadas", "Academia não é corte").
Painel "Sei sobre você · N" com apagar. Vai no prompt do chat (no máximo 10 fatos, curtos).
Fica no backup (`FinaiBackup`) e sai no "Apagar todos os dados".

**Aprender com correções (P7):** corrigir a categoria de um estabelecimento grava regra local
(`regras_categoria(estabelecimentoNormalizado, categoria)`, mesma migração 17→18); importação,
captura e parser usam antes da IA.

**Coach:** só novidade (regra 9), "Já sei" grava memória e silencia o padrão.

**Testes:** `AssistantToolsTest`, fakes de provedor com tool call e sem suporte (fallback),
`ScenarioReplyGuard` com resultado de ferramenta, `Migration17To18Test`.

**Comparação visual**
- [ ] Chip "Sei sobre você", cartão de lançamento pronto, linha "consultei", dois destaques,
      ações em chips, campo com microfone.

---

### Fase 10 · Fechamento do mês

**Referência visual:** `v2/Fechamento` e o cartão verde no fim de `v2/Hoje`.

- `domain/closing/MonthClosing.kt`: entrou, saiu, sobrou real × previsto, maiores diferenças por
  categoria contra a média, recorrentes esperados sem ocorrência ("luz não veio"), limites
  sugeridos (fase 6), destino da sobra (meta com recomendação do plano), padrão a observar.
- Estado por mês e por passo em `fechamentos(mes, passo, resposta, em)` (mesma migração 17→18).
  Resposta dada não é perguntada de novo.
- Cartão na Hoje do dia 1 ao 7, ou enquanto houver passo pendente; some depois.
- Um parágrafo de resumo escrito pela IA (uma chamada por mês, guardada; sem IA, frase
  determinística). Passa pelo guard.
- Ações reais: "Lançar R$ 140" abre o formulário preenchido; "Aplicar" grava limites; "Guardar
  R$ 600" usa `registrarAporte`; "Quero o aviso" cria regra de aviso (Caixa).

**Testes:** `MonthClosingTest` (cenário de outubro do quadro), instrumentado do fluxo dos 4 passos.

**Comparação visual**
- [ ] Três números, resumo, quatro passos numerados que viram check com a resposta, contador
      "N de 4".

---

### Fase 11 · Limpeza, documentação e release

- Remover código morto: `DecisionsCard`, `TimelineSection`/`ExtraIncomeTimeline` na Início,
  `SpendExplanation` em texto, pontos de atenção de `GoalPlanning`, `NotificationsPanel`,
  `BuySimulatorSheet`, `DebtsScreen`, `InstallmentsScreen`, `BudgetsScreen`. O `QuickActionSheet`
  fica (decisão de 08/11).
- `TipScript`/dicas por tela reescritas para a navegação nova.
- Atualizar `CLAUDE.md` (Status atual), `planning.md` §9 (Fase 8 = v2) e §11, `PRIVACY.md`
  (memória e regras ficam no aparelho; o que vai no prompt).
- `assembleDebug`, `assemblePessoal`, `testDebugUnitTest`, `connectedDebugAndroidTest`,
  `lintDebug` sem erro novo.
- Comparação visual final de todas as telas, tema claro e escuro, 320 dp com fonte 150%.

**Marco M4:** APK final para o usuário.

## 7. Banco de dados: resumo das migrações

| Versão | Fase | Mudança |
| --- | --- | --- |
| 16 → 17 | 3 | `CREATE TABLE avisos` |
| 17 → 18 | 9–10 | `CREATE TABLE memoria`, `regras_categoria`, `fechamentos` |

Margem de segurança, preferência do "+" e modo da Agenda ficam em DataStore. Nenhuma coluna
existente muda de significado, exceto a leitura do saldo (fase 1), que é cálculo.

## 8. Decisões

| # | Pergunta | Resposta (08/11/2026) | Fase |
| --- | --- | --- | --- |
| 1 | Margem de segurança: quanto o app separa e não chama de livre? | **R$ 300 fixos** como padrão, ajustável em "De onde vem o livre" (Sem / 150 / 300 / 500 / outro valor) | 1 |
| 2 | Aportes planejados das metas contam como saída prevista? | **Não. Como hoje:** aporte só sai do saldo quando registrado | 7–8 |
| 3 | "+" abre o formulário direto ou o menu? | **Menu atual**, com a linha "Falar ou escrever" | 5 |
| 4 | Aba Dívidas some (vira Planos › Compromissos)? | **Sim** | 7 |
| 5 | Fechamento do mês com IA ou texto fixo? | **IA**, uma chamada por mês, com texto fixo se ela falhar | 10 |

## 9. Riscos

- **Mudança da regra de saldo (fase 1)** mexe em quase todo cálculo. Mitigação: cenário de
  demonstração com números fechados, testes antes da UI e um marco (M1) só com isso na mão do
  usuário.
- **Tool calling em modelos gratuitos** varia por provedor. Mitigação: fallback em duas chamadas e
  teste com fakes; nada da tela depende da IA.
- **Reconhecimento de voz offline** depende do pacote de idioma do aparelho. Mitigação: sem
  pacote, o botão abre o reconhecimento online do sistema com aviso, ou fica escondido.
- **Remoção de telas** pode esconder função usada. Mitigação: cada tela removida tem a lista do
  que ela fazia e onde foi parar, conferida no PR da fase.
