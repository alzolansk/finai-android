# FinAI v2 · Planning de implementação

Atualizado em 08/11/2026. Base: branch `renda-agenda-saldo-conciliacao` (commit `c457554`, Room v16).

Este documento transforma a auditoria e o protótipo v2 em fases de implementação. Ele não
substitui o `planning.md` (requisitos e princípios continuam lá). Ele diz **o que muda, em que
ordem, onde no código e como conferir**.

## Status de implementação

| Fase | Estado | Data |
| --- | --- | --- |
| 0 · Base | ✅ implementada (`MoneyRow` ligado à Hoje; Agenda na fase 4) | 08/11/2026 |
| 1 · Motor de saldo | ✅ implementada e conferida no emulador | 08/11/2026 |
| 2 · Hoje e "De onde vem o livre" | ✅ implementada e validada no emulador, com ressalvas; M1 no celular pendente | 08/11/2026 |
| 3 · Caixa | ✅ implementada e validada no emulador, com ressalvas | 08/11/2026 |
| 4 · Agenda | ✅ implementada, com ressalvas; validação visual no emulador adiada pelo usuário | 08/11/2026 |
| 5–11 | não iniciadas | |

Branch `claude/awesome-hawking-pdynac`. Evidência anterior das fases 0–1: 439 testes JVM verdes,
`assembleDebug` e `assemblePessoal` ok. Evidências atuais da fase 2 registradas abaixo.
Room v17 desde a fase 3; as fases 0–2 não alteraram o schema.

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
  `FinaiPreferences.safetyMarginCents` (padrão R$ 300; tela para trocar entregue na fase 2).
- Ligados ao motor novo: `FinancialPlan.build` (livre, reserva, falta, `safe`), `HomeSituation` (rótulo
  "LIVRE ATÉ O ADIANTAMENTO · 20/11"; por dia em reais inteiros, para baixo), `SpendExplanation` (conta
  "em conta − vai sair − margem = livre"), o bloco da IA (`aiCashFlowBlock`), o sino/worker, o simulador de
  compra (`FinanceSnapshot.safetyMarginCents`) e `AgendaBalance` (saldo de hoje = `AccountBalance`).
- Testes novos: `FreeToSpendTest` (14), `AccountBalanceTest` (5), `DemoScenarioV2Test` (3, em `src/testDebug`).
- Conferido no emulador Pixel 6 API 34 com o cenário de demonstração: a Início mostra **R$ 1.220** livre até
  o adiantamento de 20/11, 12 dias, **R$ 3.480** em conta e menor saldo **R$ 1.520 em 18/11**
  (`docs/v2-qa/fase-1/`).

### Fase 2 — Hoje e "De onde vem o livre" (08/11/2026)

- **Bloqueio de acesso resolvido pelo login do usuário.** A primeira tentativa mostrou
  "Sign in to view this page" e não houve implementação naquele momento. Após o login,
  o endereço antigo abriu o artefato autenticado "FinAI — Auditoria de design e IA", página
  "Protótipo v2", do mesmo UUID `da97f6eb-cc62-4dac-9696-e7932729f96a`.
  Conferidos o canvas, Hoje e Livre em Play, o selo Pago, a lista expandida das saídas,
  Agenda, Lançar e as regras 1, 2, 3 e 9.
- **Regularização das fases 0–1:** cenário debug corrigido para fatura R$ 1.320 em 10/11,
  academia R$ 110 em 12/11, internet R$ 120 em 15/11 e Picpay R$ 410 (5/12) em 18/11.
  Condomínio presumido removido; aluguel R$ 1.400 em 25/11. Metas corrigidas para
  Chile R$ 3.200/8.000, Notebook R$ 900/5.000 e reserva contínua R$ 4.100.
  O teste agora confere a composição dos R$ 1.960, além dos totais de saldo/livre.
- `HomeScreen`: título Hoje e data; cartão único de livre/falta e saldo, barra de três partes,
  conferência e Por quê, simulador; 7 dias com `MoneyRow`, selo e alternância paga/pendente
  via persistência existente; parcelas e lançamentos reutilizam as folhas da Agenda.
  Depois coach e duas metas. Balanço, segundo cartão de saldo, decisões e linha do ano saíram.
- `FreeExplainedScreen`: tela inteira com gráfico diário (inclui a próxima renda como referência,
  fora da janela do livre), mínimo âmbar, conta de cinco linhas e movimentos expansíveis.
  Margem Sem/150/300/500/personalizada em DataStore; conferência bancária reutiliza
  `Reconciliation` e `FinanceViewModel.reconcile`, com estado de salvamento e erro.
  Zero digitado é válido e centavos são preservados na apresentação e na gravação.
- `HomeWeek` usa os movimentos da Agenda e mantém a conta paga na semana para reabrir.
  `HomeCoach` compara com a média dos três meses completos anteriores (estritamente > 1,6×),
  exclui futuro e sistema e grava "Já sei" em `dismissedInsights`, com chave estável.
  Nenhuma chamada de IA na Hoje. Sem migração: Room permanece v16.
- **Validação concluída no emulador:** 448 testes JVM e 4 instrumentados verdes na versão
  final; smoke do app completo passou em 390 × 844 dp e 320 × 844 dp/fonte 150%, nos dois
  temas. Capturas conferidas e exportadas. Builds debug, androidTest e `assemblePessoal`
  verdes; APK em `app/build/outputs/apk/pessoal/app-pessoal.apk` (16.789.674 bytes).
  O marco M1 continua pendente de uso no celular. TalkBack real e suíte instrumentada
  completa não foram executados. Resultados e diferenças: `docs/v2-qa/fase-2/README.md`.

#### Ressalvas da fase 2 para regularização posterior

1. Caixa ainda abre o painel de avisos atual; ponto ainda segue o estado antigo. Persistência
   e conteúdo da Caixa são fase 3. A engrenagem de Configurações continua acessível no topo.
2. Barra inferior mantém Objetivos/Dívidas; Hoje foi renomeada. Gastos/Planos e os destinos
   correspondentes são fases 6–7; "Ver gastos" abre Limites e "Planos" abre Objetivos por enquanto.
3. A sugestão de margem de quatro dias no quadro foi substituída pelo padrão **fixo de R$ 300**
   já decidido na §8. Não há ajuste automático pela média de gasto.
4. Mantida a regra da fase 1: usar a margem sem saldo negativo deixa livre zero, sem chamar
   a diferença de falta. Entradas antes do mínimo tornam "vai sair" uma queda líquida;
   a explicação identifica entradas e saídas, sem somar novamente os movimentos depois do mínimo.
5. Coach da tela usa regra nova e dispensa em DataStore. Worker ainda usa `BehaviorCoach` antigo;
   integrar dispensa/notificações e memória do assistente nas fases 3/9. A ação de Objetivos
   "Mostrar dispensados" ainda limpa o conjunto compartilhado, inclusive dispensas do coach.
6. O cenário reproduz os movimentos conferidos de caixa e os saldos das metas, mas não inventa
   o histórico de pedidos dos três meses nem o gasto necessário para a cobertura da reserva.
   Usa âncora sintética de R$ 3.480 e data de conferência em 06/11; não reconstrói o histórico
   de conciliação R$ 3.515/−R$ 35 citado no quadro de regras.
   Portanto coach, cobertura em dias e sugestão de R$ 600 no Chile não são valores fixos de UI:
   dependem dos registros e das regras atuais (o plano prioriza Picpay pelos juros de 4,2%).
7. Sem renda recorrente mantém o fallback mensal da fase 1 e avisa isso; gráfico só com a
   janela disponível. Conciliação permanece **global**, mesmo selecionando a conta do ajuste:
   com várias contas, comparar a soma dos saldos, como a tela explica.
8. Fechamento escondido até a fase 10. Dicas/tour e limpeza do código antigo ficam na fase 11.
   Em 320 dp com fonte 150%, a barra inferior antiga trunca rótulos e o `MoneyRow` pago
   comprime título/subtítulo até reticências: regularizar o arranjo da linha e a navegação/fase 11.
   Teste em celular, TalkBack real e APIs antigas devem ser registrados
   separadamente; não considerar o aceite de acessibilidade final encerrado.

### Fase 3 — Caixa (08/11/2026)

- **Protótipo acessível antes de implementar:** sessão autenticada abriu o mesmo UUID;
  conferidos `v2/Caixa` em Play, seus três grupos, ações, rodapé e a regra 8 de `v2/Logicas`.
- **Room 16→17:** tabela `avisos`, migração não destrutiva e `17.json` gerado pelo KSP.
  IDs por evento, valor de referência, visto, resolução, dispensa e adiamento persistidos.
  Primeira leitura migra dispensas antigas do DataStore uma vez, inclusive quando o título mudou.
  Dispensado só reabre ao atingir limite, passar a atraso ou variar **mais de 20%**;
  adiado volta na data. Resolver uma conta ou captura é persistido junto da ação;
  reabrir explicitamente a conta ou desfazer a captura gera estado novo.
- **Caixa como rota:** ícone no topo de todas as telas e intent de notificação financeira;
  grupos Para confirmar / Agir até o dia / Acompanhar, contagem de pendentes, estado vazio,
  dispensa e Desfazer tudo. O ponto do cabeçalho é derivado dos não vistos no Room;
  abrir a Caixa marca vistos. Sai o painel antigo e a pílula “N para confirmar” das telas.
- **Capturas preservadas:** Salvar direto apenas com dados completos e sem possível duplicata;
  caso contrário, revisão existente. Editar abre o formulário preenchido. Ignorar e Desfazer
  mantêm a gravação transacional/idempotente e a regra de não gravar sem confirmação.
  O quadro Movimentação encontrada continua disponível fora da Caixa.
- **Um catálogo para tela/worker:** `InboxCatalog` + `InboxRules`, incluindo pontos de metas
  (saem de Objetivos) e coach novo. Worker respeita visto/dispensa/resolução/adiamento,
  mantém dedupe diário e só marca envio quando publicado. “Já sei” também o silencia.
  Notificações já entregues são canceladas ao pagar/dispensar/adiar.
- **Backup e limpeza:** estados incluídos no arquivo e no reset; sugestões de teste continuam
  fora do backup. Sincronização confere o snapshot antes de persistir, para não recriar avisos
  antigos durante uma limpeza/restauração. Cenário debug também limpa os estados da fase 3.
- **Validação:** builds debug/androidTest e 461 JVM verdes; 14 instrumentados direcionados
  passaram (Caixa, migração 16→17 e cadeia anterior, captura e reset). Smoke do app completo
  passou em 390 dp e 320 dp/fonte 150%, nos dois temas, com capturas. Ações empilhadas com
  fonte ampliada corrigem a quebra de palavras encontrada na conferência visual.
  Resultados, capturas e roteiro: `docs/v2-qa/fase-3/README.md`.

#### Ressalvas da fase 3 para regularização posterior

1. **Destinos e planejamento antigos:** Ver gastos abre Limites; metas/dívidas usam as telas
   atuais. Gastos e Planos, com cálculos/conteúdo novos, continuam nas fases 6–7.
2. **“Já sei” e decisões permanentes:** coach continua em DataStore, sincronizado com a Caixa
   e o worker; memória consultável do assistente fica para a fase 9. Desfazer tudo da Caixa
   não limpa os padrões que o usuário confirmou conhecer.
3. **Migração de dispensas:** o app antigo não gravava valor de referência. A migração mantém
   a dispensa e estabelece a referência na primeira observação; não reconstitui o valor histórico.
4. **Adiamento não é alarme exato:** volta na data ao abrir/retomar o app; entrega fora dele
   depende de WorkManager e permissão do Android. Sem promessa de horário fixo.
5. **Diferenças visuais deliberadas:** botões verdes/cabeçalho do app; valor em linha própria
   para acomodar fonte grande; Editar/Dispensar explícitos. Comparação por hierarquia e ordem,
   conforme §5, sem reprodução por pixel.
6. **Não validados:** aparelho físico, TalkBack real, APIs antigas, notificações reais de banco,
   entrega durante 24 horas, IA com chave real e suíte instrumentada completa. M1 permanece
   pendente; M2 só termina com as fases 3–5 e uso no celular.
7. **APK pessoal:** não regenerado nesta fase; o APK pessoal da fase 2 não contém a Caixa nova.
   O build debug desta fase está em `app/build/outputs/apk/debug/app-debug.apk`.

As pendências da fase 2 sobre painel antigo, ponto em memória, dispensa própria em Objetivos
e worker com coach antigo foram regularizadas acima. As demais ressalvas anteriores continuam.

### Fase 4 — Agenda (08/11/2026)

- **Referência lida antes de implementar:** `project/v2/Agenda.dc.html` e `v2/Logicas` do mesmo
  artefato (lidos pelos arquivos do canvas; o navegador pedia login). Abas "A seguir" e "Novembro".
- **Domínio:** `AgendaBalance.build` passou a seguir as regras do livre (`FreeToSpend`): pagamento
  atrasado **de qualquer mês** cai hoje (resolve a ressalva 6 das fases 0–1); entrada atrasada aparece
  mas não entra no saldo (`unconfirmedInflows`); conta paga adiantada fica no lugar
  (`settledAhead`) e já está no saldo de hoje; `lowestBeforeIncome` = menor saldo até a véspera da
  próxima renda (o mesmo da Hoje). `AgendaAhead.rows` monta as linhas (marca "HOJE · em conta",
  divisor "começa com R$ X · nada zera", cabeçalho do dia da renda, menor saldo).
  `AgendaMonth.movements` monta a aba do mês (mesmas regras + transferências neutras).
  `MonthCashFlow` ganhou `entraCents`/`saiCents`/`competenceShifted` (Sobra = `fechamentoCents`,
  o antigo balanço da Início).
- **UI:** abas "A seguir" | <nome do mês>; as duas usam `MoneyRow` (sem caixinha, selo
  "Pago"/"Recebido", valor esmaecido, estado lido pelo TalkBack). "A seguir": texto de abertura,
  "fica R$ X" por linha, menor saldo em âmbar, renda com fundo verde, entradas extras na lista,
  bloco recolhido de anteriores (linhas tocáveis). "Mês": navegação como antes, Entra / Sai /
  Sobra, grupos "Já passou" e "A partir de hoje". Tocar conta paga/reabre; parcela abre a folha
  (as duas abas); lançamento abre a folha. **Segurar** a conta abre `BillSheet` (itens da fatura,
  editar, excluir), que substitui o menu ⋮ das linhas; a dica `agenda.bill` diz isso.
  `MoneyRow` ganhou fundo (cartão/renda/menor saldo), toque longo, rótulos de ação e título/
  subtítulo em até 2 linhas (melhora a compressão com fonte 150% citada na fase 2).
  O cartão de parcelamentos saiu da Agenda e foi para **Dívidas** (único acesso à tela de
  Parcelamentos até Compromissos, fase 7).
- **Validação:** `assembleDebug`, `assembleDebugAndroidTest` e `testDebugUnitTest` verdes;
  **470 testes JVM**, zero falhas (novos: `AgendaBalanceTest` 7, `AgendaV2DemoTest` 2). Com o
  cenário de demonstração os números batem com o quadro: em conta R$ 3.480 → fica 2.160 → 2.050
  → 1.930 → menor saldo 1.520 (18/11) → 3.920 → 2.520; dezembro começa com R$ 2.520.
  Instrumentado novo `AgendaV2Test` (tocar paga/reabre, selo, `stateDescription`, sem caixinha nas
  duas abas, três números, toque longo). Rodada inicial no emulador (antes do pedido para parar):
  `AgendaV2Test` 3/3 verdes; `HomeV2Test.paidRowReopensAndPersistsInRoom` falhou uma vez por
  corrida (checava a tela no mesmo quadro da gravação; passou 2× isolado) e foi endurecido, junto
  com o `AgendaV2Test`; `TransactionEntryFlowTest.transferRoomAndAgenda` falha como antes desta
  fase (já registrado no CLAUDE.md). **A pedido do usuário, a validação no emulador parou aqui**:
  os testes endurecidos foram só compilados, e não houve smoke, capturas nem comparação visual.

#### Ressalvas da fase 4 para regularização posterior

1. **Validação visual pendente (feita pelo usuário depois):** smoke em 390 dp e 320 dp/fonte 150%,
   tema claro/escuro, capturas em `docs/v2-qa/fase-4/` e o checklist da §6 não foram feitos.
   Os instrumentados endurecidos (`AgendaV2Test`, `HomeV2Test`) não foram reexecutados.
2. **Busca no topo** do quadro não entrou: leva a Gastos com o campo focado, que é a fase 6.
3. **Números da aba do mês diferem do quadro** (R$ 6.000 / 5.180 / 820): o app mostra
   R$ 6.000 / 3.360 / 2.640 no cenário, porque Sai é o que está lançado (contas, gastos, parcelas,
   aportes), sem gasto médio variável nem o "Mercado 02/11" do quadro, que o cenário não tem.
   Entra é pela competência (o saldo do salário que cai em 05/12 conta em novembro; nota
   "Salário conta no mês trabalhado" aparece embaixo).
4. **13º (1ª parcela) não está no cenário de demonstração**; a regra de entrada extra na lista está
   coberta por `AgendaBalanceTest`. Adicioná-lo mudaria as projeções de metas da Hoje.
5. **Diferenças deliberadas de desenho:** a data entra sempre no subtítulo ("10/11 · cartão · N
   compras"); o dia do salário dividido ganha cabeçalho também em dezembro ("saldo"); a nota de
   menor saldo usa a janela até a próxima renda; selos antigos (Recorrente, Importado,
   Notificação) viraram texto no subtítulo; o cartão "Saldo hoje → Fim de dezembro" saiu
   (substituído pela marca "HOJE · em conta").
6. **Ações escondidas no toque longo:** editar/excluir conta e "Ver itens da fatura" só aparecem
   segurando a linha (e pela ação do TalkBack). Conferir no celular se fica fácil de achar.
7. Conta paga **depois** do vencimento vai para o bloco recolhido; tocar nela lá reabre e ela volta
   como atrasada (hoje). Entrada atrasada não recebida mostra "fora do saldo".
8. Celular, TalkBack real, APIs antigas e suíte instrumentada completa não executados; APK pessoal
   não regenerado. Marco M2 segue dependente das fases 3–5 no celular.

### Ressalvas (leia antes de testar no celular)
1. **Acesso ao protótipo regularizado após login (08/11).** As tentativas anteriores sem acesso
   ficam como histórico, não como bloqueio atual. A referência real confirmou os totais e
   revelou divergências na composição do cenário, corrigidas na fase 2 (ver registro acima).
   Comparações e limitações por fase estão em `docs/v2-qa/fase-0/` e `docs/v2-qa/fase-1/`.
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
7. **`MoneyRow` ligado à Hoje na fase 2.** A Agenda ainda usa suas linhas antigas até a fase 4.
8. **Testes antigos ajustados por causa da regra nova:** `FinancialPlanTest`, `GoalPlanningTest` e
   `CashFlowTimelineTest` (salário recorrente passou a começar no último ciclo e margem 0, porque o saldo
   contínuo somaria os meses anteriores); `PayCycleTest` (margem 0); `ScenarioSimulatorTest` (margem 0 no
   snapshot); `ReconciliationTest` (sem ajuste o saldo já parte do primeiro lançamento: R$ 8.800, antes
   R$ 5.000); `EntryOptionsTest` (10 categorias).
9. **Pendências da sessão das fases 0–1, atualizadas na fase 2:** R$ 101/dia foi recapturado,
   tema claro foi conferido e quatro fluxos instrumentados passaram. Há ressalva na barra antiga
   em 320 dp/fonte 150%. Permanecem pendentes TalkBack real, Agenda/chat na tela, celular e suíte
   instrumentada completa (`connectedDebugAndroidTest`); ver evidências atuais no QA da fase 2.

## 1. Fontes

- **Protótipo v2 (endereço atualizado pelo usuário em 08/11/2026):** https://claude.ai/code/artifact/da97f6eb-cc62-4dac-9696-e7932729f96a#page-5634c5cea0db
  - Acesso autenticado confirmado após login do usuário; quadros conferidos na página Protótipo v2.
  - Endereço anterior: https://claude.ai/artifact/Tzb24N1BzGMR8buHwVAtL9.
  - Descrição dos quadros abaixo preservada do planejamento anterior, sem nova verificação visual.
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
- [x] `v2/Caixa`: três grupos com cores, dois botões por item, item some ao agir, estado vazio,
      rodapé de dispensados.
- [x] Reabrir o app não mostra ponto vermelho para o que já foi visto.

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
- [x] Nenhuma caixinha nas duas abas (instrumentado `AgendaV2Test`; conferência visual pendente).
- [x] "A seguir": "fica R$" por linha, menor saldo destacado, divisor de mês sem zerar (`AgendaV2DemoTest`).
- [x] "Mês": três números no topo, Sobra = `fechamentoCents` (o antigo balanço da Início).
- [ ] Capturas lado a lado com o quadro, tema claro e 320 dp/fonte 150% (usuário fará).

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
