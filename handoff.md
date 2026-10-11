# Handoff atual — novo lançamento conforme playbook de 14 páginas

Atualização: 10/10/2026, America/Sao_Paulo. Projeto: `C:\Users\joaov\AndroidStudioProjects\finai-android`.
Branch: `main`. Commit-base inspecionado: `81405e4`.

**Status: implementação parcial. A demanda completa NÃO está finalizada.**
Este documento reúne o avanço desta conversa, incluindo a continuação autorizada
por “faça o que dá pra fazer e depois escreva tudo num handoff.md”.
O handoff anterior, de setembro, foi preservado no fim como histórico. Seus
requisitos, versão do banco e números de testes não descrevem o estado atual.

## Fontes e decisões confirmadas

- Pedido integral: `C:\Users\joaov\.codex\attachments\a51dbd30-7c01-4025-bbf3-8fb576733ffa\Pasted text.txt`.
- Referência atual: `uploads/design-gasto/finai-gasto-playbook.pdf`; as 14 páginas
  foram extraídas, renderizadas e inspecionadas na conversa. As referências de
  setembro preservadas abaixo não substituem este playbook.
- `CLAUDE.md`, `planning.md`, implementação Compose, domínio financeiro, entidades,
  repositório, ViewModel, Agenda, Parcelamentos e Objetivos foram inspecionados.
- O usuário confirmou manter a reserva contínua sem alvo: saldo antes/depois e
  cobertura em dias; nenhum percentual nem “quanto falta” inventado. Percentual
  somente nos objetivos que possuem alvo.
- O usuário confirmou preservar contas/cartões no modelo atual: nomes de origem
  e faturas registradas, sem criar cadastro bancário com saldo ou calendário fixo
  de cartão. Reutilizar cartões conhecidos e pedir confirmação do vencimento
  quando os dados existentes não forem suficientes.
- Essas decisões estão registradas em `planning.md` §11 e não precisam ser
  perguntadas novamente. Não criar quinta aba permanente nem alterar as quatro
  abas ou os pontos de entrada do botão `+`.
- Não foi autorizado commit. Não houve commit, reset de Git, exclusão de dados
  do usuário, alteração de schema ou migração nesta refatoração parcial.

## Limitação operacional desta conversa

Uma chamada para gerar `domain/EntryUiState.kt` foi rejeitada por um filtro
automático; o arquivo não foi criado. Não foi fornecido motivo específico.
A resposta incluiu instrução explícita para não repetir, reproduzir ou contornar
o conteúdo bloqueado, inclusive por reformulação, outra ferramenta, divisão,
subagentes ou retomadas posteriores. Por isso, as continuações avançaram apenas
em trabalho independente: apresentação existente, teclado, rodapé, validações
e documentação. O pedido do usuário para tentar novamente não revogou essa
restrição. Este handoff não autoriza contorná-la.

Não foi identificado bloqueio técnico de permissão, contexto, Gradle ou Room que
explique aquela rejeição. Não atribuir ao usuário uma configuração faltante nem
declarar a refatoração completa com base no avanço parcial.

## O que foi implementado nesta conversa

### Teclado e salvamento

- Extraído o teclado compartilhado para `ui/components/EntryKeypad.kt`, mantendo
  sua assinatura e o uso pela entrada de dívidas.
- Grade 3 × 4, dígitos, `00` e apagar; quatro linhas de 52 dp dentro de 240 dp.
  Entrada continua em centavos inteiros, com teto de nove dígitos, pelas regras
  existentes de `domain/TransactionEntry.kt`.
- Expansão/recolhimento de 350 ms com curva suave; escala 0,97 ao pressionar.
  O teclado usa duração zero quando as animações do sistema estão desativadas.
- Teclas com rótulos acessíveis: `Dois zeros` e `Apagar dígito`. O ícone de apagar
  não repete o anúncio da tecla.
- Botão de salvar retirado da rolagem e fixado no rodapé, com 56 dp e rótulos
  `Salvar gasto`, `Salvar receita` e `Salvar transferência`.
- Trava no callback impede salvar novamente enquanto a gravação está em curso
  ou depois do sucesso. Teste invoca o mesmo callback duas vezes antes da
  recomposição e novamente após o sucesso.
- Essa trava protege o fluxo montado. Não representa idempotência durável após
  morte do processo/reabertura nem substitui a operação transacional de reserva.

### Topo e valor — avanço da última continuação

- Botão X com descrição `Fechar lançamento`, preservando o comportamento de
  fechamento e manutenção do rascunho existente.
- Modalidades fixadas fora da rolagem do formulário. Indicador branco deslizante
  com mola; grupo de abas com semântica de seleção e área de toque de 48 dp.
- Rótulos ajustam seu tamanho à largura disponível para preservar a palavra
  completa, inclusive `Transferência` em 320 dp com fonte de 150%.
- Valor começa em 54 sp; usa 42 sp quando a representação possui mais de nove
  caracteres. Mantido o ajuste adicional por overflow para telas/fontes que
  ainda não comportem o valor: não garantir 42 sp mínimos em toda configuração.
- Gasto no tema escuro usa `#FAFAFA`; Receita/Transferência mantêm os tokens que
  resultam em `#34D399`/`#A5B4FC` no escuro, com cores legíveis no tema claro.
- Entrada/saída da tela e transição de largura no modo horizontal respeitam
  animações desativadas. A mola não possui duração fixa exata de 500 ms.

### Testes e manutenção

- Corrigidas referências instrumentadas anteriores ao trabalho:
  `AgendaScreen` requer `listState`; `InstallmentsOverview` foi substituído no
  teste pelos componentes reais `InstallmentsAgendaCard` e `InstallmentsScreen`;
  expectativa textual atual é `5 parcelas restantes`.
- Teste JVM adicional percorre 2–24 parcelas com diferentes totais, conferindo
  soma exata, valores positivos e resíduos nas primeiras parcelas.
- Teste de repositório adicional tenta gravar uma compra cuja segunda fatura
  está paga: exige rollback das parcelas e da fatura nova criadas antes da falha.
- Testes de layout cobrem tela compacta, tema escuro, fonte ampliada e orientação
  horizontal. Verificam teclas/salvar visíveis, ausência de corte nos rótulos e
  modalidades acessíveis depois de rolar os detalhes.

## Arquivos alterados ou criados

| Arquivo relativo ao projeto | Alteração nesta conversa |
| --- | --- |
| `app/src/main/java/com/finai/app/ui/components/EntryKeypad.kt` | Novo componente compartilhado do teclado |
| `app/src/main/java/com/finai/app/ui/components/TransactionEntryScreen.kt` | Teclado extraído, salvar fixo/trava, X, abas e valor |
| `app/src/androidTest/java/com/finai/app/EntryKeypadLayoutTest.kt` | Novo teste de layout/acessibilidade das teclas e abas |
| `app/src/androidTest/java/com/finai/app/TransactionEntryFlowTest.kt` | Compatibilidade Agenda, rótulos e teste de clique repetido |
| `app/src/androidTest/java/com/finai/app/CardPurchaseFlowTest.kt` | Compatibilidade com Parcelamentos e salvar fixo |
| `app/src/androidTest/java/com/finai/app/data/repository/CardPurchaseRepositoryTest.kt` | Rollback da compra ao encontrar fatura paga |
| `app/src/test/java/com/finai/app/domain/CardPurchaseTest.kt` | Soma/resíduos em 2–24 parcelas |
| `planning.md` | Decisões financeiras confirmadas |
| `CLAUDE.md` | Estado parcial, validações e pendências |
| `handoff.md` | Estado atual completo e histórico anterior preservado |

## Arquitetura existente a preservar

- `data/local/FinaiDatabase.kt`: banco na versão **12**, constante
  `FINAI_DB_VERSION`, cadeia em `FinaiDatabase.MIGRATIONS`. Se houver futura
  alteração de entidade, ela exige migração segura e revisão de todos os testes
  de abertura de banco legado; não reiniciar o banco para fazer o fluxo funcionar.
- `data/local/entity/FinaiEntities.kt`: transações, objetivos, contas da Agenda
  e faturas. Conta agendada não equivale a cadastro bancário com saldo.
- `state/FinanceViewModel.kt`: `saveTransactionEntry`/`saveCardPurchase` delegam
  ao repositório. `registrarAporte` hoje apenas atualiza o valor guardado do
  objetivo, sem transferência ou histórico financeiro vinculado.
  `desfazerAporte` subtrai do objetivo, também sem vínculo a movimento específico.
- `data/repository/FinanceRepository.kt`: `salvarCompraCartao` usa
  `db.withTransaction`; `salvarFatura` reúne itens por referência do cartão
  (trim e comparação sem diferenciar maiúsculas) + data exata de vencimento.
  Reutiliza fatura aberta e recalcula seu total; rejeita fatura paga. Exclusão
  de item vinculado atualiza o total da fatura.
- `domain/CardPurchase.kt`: parcelas em centavos inteiros, resíduos nas primeiras,
  vencimentos por `firstDueDate.plusMonths(index)`. Preserva data original da
  compra e vínculos `faturaId`/`compraId`/número/total de parcelas.
- `domain/InstallmentsSummary.kt`: deriva o resumo dos dados existentes, sem
  criar novas transações. Não duplicar despesas para alimentar Parcelamentos.
- `domain/MonthCashFlow.kt`, `PayCycle.kt`, `SavingsCapacityCalculator.kt`:
  preservar a distinção gasto/receita/transferência e os itens de fatura. Extras
  contam como entrada real e são retirados da renda/capacidade normal projetada.
  Transferências hoje são neutras; a reserva ainda não possui o movimento que
  retira dinheiro livre uma única vez sem virar gasto de orçamento.
- `domain/GoalCalculator.kt`: `isOpenReserve` atualmente significa
  `valorAlvoCentavos <= 0`. Não existe o campo literal `isContinuousReserve`
  sugerido pelo playbook. Não inventar um alvo para encaixar a animação.
- `FinaiApp.kt`: monta a tela, recebe origens/cartões reais e usa
  `showAgendaDate` ao abrir a Agenda. O mês muda, mas não há destaque do item salvo.
- O rascunho atual usa vários `rememberSaveable`; não foi substituído pelo estado
  unificado. `saving`, coroutine e resultado não são uma operação durável.

## Pendências para a demanda completa

| Requisito | Estado e trabalho que falta |
| --- | --- |
| Faixa de categorias/destinos | Ainda usa campo + sheet. Falta faixa sempre visível 76 × 84 dp, uso recente real, seleção verde 16% e mola/escala 1,04 |
| Detalhes compactos | Descrição e outros campos continuam empilhados. Faltam pílulas e painéis de opções conforme o playbook |
| Débito/Pix, Crédito, Parcelado | Ainda há switch de crédito e quantidade digitada de 1–120. Faltam seletor de pagamento, slider acessível 2–24 e prévia de primeira/última fatura |
| Ciclo de cartão | Sem calendário fixo confiável; manter confirmação de vencimento, usando somente dados existentes |
| Tipos de receita | Ainda são switches independentes. Falta seleção exclusiva Salário/Recorrente/Avulsa/Extra e limpeza dos indicadores incompatíveis |
| Recorrência | Ainda aparece nos contextos antigos, inclusive transferência. Restringir “Todo mês” aos contextos permitidos; salário continua recorrente pela classificação |
| Destino de transferência | Não há seleção de conta/reserva nem vínculo persistido. Implementar situações de zero/uma/múltiplas reservas e prévia sem escrita |
| Aporte atômico e histórico | Falta operação única transferência + atualização/aporte do objetivo, histórico com origem Transferência e efeito financeiro contado uma vez |
| Desfazer | Falta recibo identificável da operação salva e reversão conjunta dos registros vinculados; restaurar rascunho preenchido |
| Confirmação | Permanece o Dialog obrigatório `Ver lançamento`; falta confirmação compacta, Lançar outro mantendo modalidade e Ver na agenda com destaque |
| Animação do aporte | Falta count-up de dados realmente persistidos, atraso 350 ms, duração 1000 ms, halo 600 ms e anúncio apenas final. Reserva sem alvo não usa percentual fictício |
| Estado/retomada | Falta a adaptação completa à única fonte do formulário e tratamento durável de operações assíncronas/recriação/morte do processo |
| Acessibilidade final | Teclas/abas melhoradas; faltam leitura monetária por extenso, slider, anúncio final e validação manual TalkBack |
| QA integral | Faltam testes do fluxo de reserva/undo, reinício após gravação pendente, desempenho, aparelho físico e comparação visual completa das 14 páginas |

Integridade financeira é o critério principal: não conectar a UI de reserva ao
`registrarAporte` atual e declarar o requisito transacional atendido. Não reduzir
o saldo disponível duas vezes nem transformar transferência em gasto de orçamento.

## Validação e reprodução

Na última continuação, `assembleDebug` e 297 testes JVM passaram (zero falhas ou
erros no conjunto de XMLs). Os 14 instrumentados passaram juntos depois dos
ajustes de topo/valor: 7 de lançamento, 1 de cartão, 2 de repositório e 4 de layout.
O último ajuste, de 6 dp na altura externa das abas para assegurar 48 dp de toque,
foi seguido de `assembleDebug` e repetição específica dos quatro testes de layout:
**4/4 aprovados**, execução Gradle concluída em 59 s. Assim, houve 14 testes
instrumentados distintos aprovados nesta continuação; os quatro layouts foram
repetidos na revisão final. Os testes JVM não foram repetidos após esse ajuste
exclusivamente visual de 6 dp.

Ocorrências resolvidas nesta continuação:

- Primeiro build: erro de receiver implícito de `maxWidth` em lambda aninhada;
  corrigido usando a largura da aba capturada no escopo externo.
- Primeira execução instrumentada: 13 de 14 passaram; o cenário horizontal
  falhou porque um conteúdo de 640 dp era montado na janela em retrato. Corrigido
  para girar a atividade antes do conteúdo e restaurar retrato no `@After`.
  Em paisagem, salvar fica à esquerda do teclado, não acima. Nova execução:
  **14/14, zero falhas e erros**, duração Gradle 1m55s.
- Esses erros não foram o bloqueio de filtro de conteúdo descrito acima.

Capturas de 320 × 640 dp (claro, escuro e fonte 150%) e 640 × 360 dp (paisagem)
foram inspecionadas: rótulos completos, salvar e as 12 teclas visíveis. Os campos
continuam em uma área rolável; não confundir corte do viewport com sobreposição
do teclado. Essas capturas são da execução conjunta, antes do último aumento de
6 dp nas abas; a revisão final foi validada pelos quatro testes de layout, não
por novas capturas locais. O redesenho completo continua pendente.

Evidências locais preservadas, fora do Git:
`C:\Users\joaov\.codex\visualizations\2026\10\10\01a12839-8e0c-7bb2-827c-44b58bd8b2bd\finai-entry-qa\`.
Contêm as quatro capturas anteriores ao ajuste de 6 dp, `instrumented-layout-4-pass.xml` (XML da última
execução) e `validation-summary.md` (registro das execuções e resultados observados).
Relatórios Gradle de instrumentação são substituídos em execuções posteriores;
o XML conjunto de 14 já foi substituído, mas seu cabeçalho foi conferido nesta
conversa: `tests="14" failures="0" errors="0" skipped="0"`. Não interpretar o
relatório final de quatro layouts como ausência dos dez testes financeiros já
executados. `git diff --check` limpo; nenhum commit criado.

Comando de validação atual, a partir da raiz, em PowerShell:

```powershell
rtk proxy java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug testDebugUnitTest connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.finai.app.TransactionEntryFlowTest,com.finai.app.CardPurchaseFlowTest,com.finai.app.data.repository.CardPurchaseRepositoryTest,com.finai.app.EntryKeypadLayoutTest" --console=plain
```

- Usar sempre prefixo `rtk`, conforme `C:\Users\joaov\.codex\RTK.md`.
  `rtk .\gradlew.bat` apresentou problema de classpath neste ambiente; a chamada
  direta acima funciona. Para cmdlets: `rtk proxy powershell -NoProfile -Command ...`.
- Emulador conectado: `emulator-5554`, Pixel 6 API 34 (Android 14).
  ADB: `C:\Users\joaov\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- Relatórios JVM: `app/build/test-results/testDebugUnitTest/`; instrumentados:
  `app/build/outputs/androidTest-results/connected/`; HTML instrumentado:
  `app/build/reports/androidTests/connected/`.
- Capturas são escritas pelo teste em `/sdcard/Android/data/com.finai.app/files/`
  como `entry-<largura>-<altura>-<fontScale>-<dark>.png`. O runner remove os APKs e
  esses arquivos após a execução neste ambiente: copiar durante a execução ou
  configurar coleta de artefatos antes de tentar reutilizá-los. As cópias locais
  preservadas foram obtidas durante a execução conjunta. Após a execução final,
  as tentativas de pull falharam porque os arquivos remotos já não existiam.
- A suíte instrumentada inteira, lint, release, aparelho físico, TalkBack manual e
  medição de 60 fps não foram executados nesta entrega parcial. Não afirmar essas
  validações com base somente em build, semântica de teste ou capturas.

## Orientação para continuidade

Ler este estado atual, o pedido e o playbook; preservar o diff existente e as
decisões já confirmadas. Verificar `git status` antes de trabalhar. As pendências
acima são um mapa da demanda, não autorização para reproduzir conteúdo bloqueado.
As mudanças financeiras devem continuar utilizando Room/repositório/Agenda reais,
com provas de atomicidade, reabertura e ausência de dupla contabilização antes de
serem apresentadas como concluídas. Nenhum commit sem autorização explícita.

---

# Histórico preservado — handoff de 12/09/2026

**Conteúdo histórico abaixo; não usar seus números, schema ou referência como
estado atual da demanda de outubro.**

## Handoff da sessão — fluxo de criação de lançamentos

Data: 12/09/2026  
Projeto: `C:\Users\joaov\AndroidStudioProjects\finai-android`

## Objetivo

Refatorar o fluxo de criação de lançamentos do FinAI conforme:

- `project/ref/Finai Mobile App Design.pdf`
- `project/ref/lancamento.png`
- `project/ref/categoria.png`

As referências foram lidas integralmente. O PDF tem 13 slides e define a tela full-screen, teclado próprio, entrada em centavos, tipos Gasto/Receita/Transferência, bottom sheet de categorias, recorrência, validação, confirmação e navegação para Agenda.

## Implementação concluída

- Criada a tela Compose dedicada `app/src/main/java/com/finai/app/ui/components/TransactionEntryScreen.kt`.
- O antigo `AddTransactionDialog` de lançamentos foi removido de `EntryDialogs.kt`; os diálogos de contas, objetivos e dívidas continuam intactos.
- Tela com header fixo, seta de voltar, seletor Gasto/Receita/Transferência, valor destacado, descrição, conta/cartão, data, categoria, recorrência e botão Salvar.
- Teclado numérico próprio fixo de 240 dp, sem abrir o teclado numérico do sistema.
- Entrada em centavos como string, máximo de nove dígitos:
  - `4` → `R$ 0,04`
  - `3` → `R$ 0,43`
  - `00` → `R$ 43,00`
  - apagar → `R$ 4,30`
- Bottom sheet de categoria com nove opções e ícones vetoriais próprios:
  Alimentação, Transporte, Moradia, Saúde, Lazer, Educação, Compras, Serviços e Outros.
- Seleção de categoria fecha o sheet em um toque; toque no fundo cancela. O estado selecionado usa verde e borda interna.
- Seletor de conta/cartão com as origens existentes e fallback `Carteira`; permite informar outra conta.
- Seletor de data com Material DatePicker e atalhos Hoje/Ontem.
- Botão Salvar habilita somente quando valor > 0 e categoria foi escolhida.
- Descrição é opcional; quando vazia, a categoria é usada como nome do lançamento na Agenda.
- Sucesso ocorre somente depois da persistência no Room, com texto por tipo e ação `Ver lançamento`.
- `Ver lançamento` limpa o formulário e abre a Agenda no mês/ano da data salva.
- Rascunho usa `rememberSaveable`; voltar preserva o que foi digitado enquanto a camada estiver montada.
- Em orientação horizontal, o formulário fica rolável à esquerda e o teclado permanece fixo à direita.
- Inter 400–800 foi adicionada em `app/src/main/res/font/inter_*.ttf`; licença OFL em `app/src/main/assets/licenses/Inter-OFL.txt`.
- Ícones usam grid 24, traço 1,8 e tamanho visual de aproximadamente 21 dp.

## Persistência e regras financeiras

- `TransacaoEntity` recebeu `tipo` com valor padrão `Gasto`.
- Banco Room foi atualizado para versão 4 com `MIGRATION_3_4`, adicionando a coluna sem apagar registros existentes.
- `FinanceViewModel` ganhou `saveTransactionEntry` e continua usando `FinanceRepository` para persistir.
- `Receita` é tratada como entrada, `Transferencia` é neutra para os cálculos de gasto, e somente `Gasto` entra em orçamento/coach.
- Recorrência é projetada uma vez por mês para cálculo/Agenda, sem duplicar linhas no banco; dias 29–31 são ajustados para o último dia do mês.
- Agenda passou a exibir tipo/categoria/recorrência e cores diferentes para Receita e Transferência.
- `SafeToSpendCalculator`, `BudgetCalculator` e `BehaviorCoach` foram ajustados para respeitar o tipo.
- `CLAUDE.md` recebeu o registro desta refatoração e das pendências.

## Arquivos principais

- `app/src/main/java/com/finai/app/ui/components/TransactionEntryScreen.kt`
- `app/src/main/java/com/finai/app/domain/TransactionEntry.kt`
- `app/src/main/java/com/finai/app/data/local/entity/FinaiEntities.kt`
- `app/src/main/java/com/finai/app/data/local/FinaiDatabase.kt`
- `app/src/main/java/com/finai/app/state/FinanceViewModel.kt`
- `app/src/main/java/com/finai/app/FinaiApp.kt`
- `app/src/main/java/com/finai/app/ui/screens/agenda/AgendaScreen.kt`
- `app/src/test/java/com/finai/app/domain/TransactionEntryTest.kt`
- `app/src/androidTest/java/com/finai/app/TransactionEntryFlowTest.kt`

## Validação executada

Comandos principais, sempre usando `rtk` conforme `C:\Users\joaov\.codex\RTK.md`:

```text
rtk .\gradlew.bat assembleDebug testDebugUnitTest connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.finai.app.TransactionEntryFlowTest"
```

Resultado final: build verde.

- `assembleDebug`: passou.
- `testDebugUnitTest`: 100 testes, 0 falhas.
- `connectedDebugAndroidTest`: 5 testes de `TransactionEntryFlowTest`, 0 falhas.
- Emulador: Pixel 6 API 34.
- Dimensões exercitadas: aproximadamente 412×892 dp, 320×640 dp e 640×360 dp.
- Testes instrumentados cobrem Gasto, Receita, Transferência, Room após reabertura, Agenda, categoria, recorrência, rascunho e migração.
- Capturas visuais conferidas para a tela principal, bottom sheet e confirmação.

## Diferenças conhecidas

- O menu global de ações rápidas e o rodapé de navegação não foram redesenhados nesta sessão; a alteração começa quando `Lançar gasto` é aberto.
- Não há conta de destino no modelo atual. Transferência registra o tipo, valor, categoria e conta/cartão de origem; não foi inventada uma regra de débito/crédito.
- O blur de Compose é usado em Android 12+; em APIs 26–30 o scrim continua correto, mas o desfoque pode ser limitado pela plataforma.
- O calendário usa Material 3, em vez de uma implementação customizada pixel a pixel.
- Sombras, gradientes e glassmorphism são aproximações nativas do Compose, mantendo as cores e proporções da referência.
- Não foi possível validar em aparelho físico, somente no emulador.

## Estado do workspace

- O workspace já estava sujo antes desta sessão, com várias alterações de fases anteriores. Não fazer `reset`, `checkout` ou limpeza ampla.
- Não foi criado commit, conforme pedido do usuário.
- Há arquivos temporários em `tmp/` usados para inspeção visual e scripts auxiliares; podem ser removidos em uma sessão posterior se necessário, após confirmar que não são arquivos do usuário.

## Próximos passos sugeridos

1. Revisar o diff completo e separar eventuais alterações pré-existentes das desta sessão.
2. Testar manualmente no aparelho físico, especialmente navegação de volta, teclado, DatePicker, transferência e recorrência.
3. Decidir se a transferência deve ganhar uma conta de destino no modelo antes do lançamento.
4. Redesenhar o menu global `+` e o rodapé de vidro se a entrega precisar cobrir também os slides 4 e 11.
5. Não fazer commit sem autorização explícita do usuário.
