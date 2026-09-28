# Fase 7 · Clareza e confiança: o que falta

Handoff para a próxima sessão do Claude Code. Escrito em 28/09/2026, ao fim da sessão
que abriu a fase. A lista oficial dos itens, com prioridade e critério de aceite, está em
`planning.md` §9 "Fase 7" e §10. Este arquivo detalha **como** fazer cada item que falta:
arquivos, armadilhas e o que conta como pronto. Ao terminar um item, marque ✅ no
`planning.md`, registre o que mudou no `CLAUDE.md` e atualize ou apague a seção dele aqui.

## Onde estamos

- Branch **`v2-clareza`**, criada a partir de `main` @ `747ee3e`. O `main` é a versão
  instalada no celular do usuário e não deve receber nada desta fase até ela ser aprovada.
- **Atenção:** durante a sessão, o repositório voltou sozinho para o `main`,
  provavelmente pelo Android Studio. Rode `git branch --show-current` antes de editar e
  de commitar.
- Feitos:
  - **Item 1** (`29c4d9c`): "Pode gastar hoje" parte do dia mais apertado
    (`PayCycle.floor`), e a falta prevista vem primeiro.
  - **Item 2** (`6c7b0aa`): folha "Entenda este valor" (`domain/SpendExplanation.kt`,
    `ui/components/SpendExplanationSheet.kt`).
- 165 testes JVM verdes. O banco Room está na **versão 9**.
- APK `pessoal` dos itens 1 e 2 foi enviado, mas **o usuário só vai testar quando a fase
  inteira estiver pronta** (decisão de 28/09/2026). Não mande APK a cada item e não pergunte
  pelo retorno no meio do caminho. Confira você mesmo no emulador (ver "Como testar"),
  inclusive o caso **com salário** da folha "Entenda este valor", que até agora só foi
  coberto por teste JVM. Um único APK sai no fim da fase (ver "Entrega final").
- Decisões do usuário já tomadas estão em "Decisões" no fim deste arquivo. Uma ainda está
  em aberto (a do dia fixo da dívida).

## Ordem sugerida

3 → 4 → 5 → 6 → 10 (só o "+") → 7/8/9 (juntos, tela a tela) → 11 → 12 → entrega final.

O 3 e o 4 mudam a Início inteira e tocam os mesmos arquivos, então é melhor fazer um
logo depois do outro. O 5 precisa de migração do banco, então vale fechar o 3 e o 4
antes, para não misturar mudança de schema com mudança visual no mesmo APK.

---

## Item 3 · Início por urgência [Alta]

**Problema.** Ordem atual em `ui/screens/home/HomeScreen.kt` (`HomeScreen`, o
`LazyColumn` perto da linha 90): saudação → `PayCycleCard` → `SaldoCard` (balanço do mês)
→ `GoalsCarousel` → `SafeToSpendCard` → `DecisionsCard` → `TimelineSection` →
`NextWeekSection` → `CoachCard`. "Pode gastar hoje" fica em quarto lugar, e os próximos 7
dias aparecem quase no fim.

**O que fazer.**
1. Nova ordem: **situação** → **próximos vencimentos** → **planejamento**.
   - **Situação:** um único bloco no topo que junta `PayCycleCard` e `SafeToSpendCard`.
     Leva uma frase de situação ("Seu dinheiro cobre os próximos compromissos" ou
     "Falta dinheiro antes do próximo salário"), um valor principal com o período
     explícito, **uma** ação pertinente e o link "Entenda este valor".
     - A ação pertinente depende do estado: com falta, "Ver compromissos" (abre a
       Agenda); sem salário, "Informar renda"; no caso normal, "Posso comprar?".
     - **Decidido (28/09/2026): o número principal é o livre até o salário**, não o
       valor por dia. Hoje são dois cartões com números parecidos ("R$ X livres" e
       "Pode gastar R$ Y hoje"); vira um só:
       - Rótulo: "LIVRE ATÉ O SALÁRIO · 30/10" (ou "LIVRE ATÉ O FIM DO MÊS" sem ciclo).
       - Valor grande: `safeToday.slackThisMonthCents.coerceAtLeast(0)`, que é o dia mais
         apertado menos a reserva das metas, **não** `PayCycle.livreCents`. É o mesmo
         número da linha "Livre até o salário" da folha "Entenda este valor"; os dois
         precisam bater.
       - Apoio, menor: "cerca de R$ Y por dia · N dias", com `safeTodayCents`.
       - Com falta: o valor grande vira "Faltam R$ X" em vermelho, como já é hoje.
       - Ajuste o rótulo do resumo da IA (`FinanceUiState.toAiSummaryText`) e o
         `safeNote` para falarem do mesmo número. Hoje o resumo abre com "Pode gastar
         hoje".
   - **Próximos vencimentos:** `NextWeekSection` sobe para logo abaixo da situação.
   - **Planejamento:** depois, com peso visual menor: `SaldoCard`, `GoalsCarousel`,
     `DecisionsCard`, `TimelineSection`, `CoachCard`.
2. **"Ajustar limites" continua no bloco de situação.** Decisão do usuário: Limites fica
   acessível só pelos caminhos de sempre, que são este botão e os avisos de orçamento do
   sino. Não crie outra entrada (ver item 10). Pode virar um botão secundário menor, mas
   não pode sumir.
3. A saudação ("Bom dia" + "Você tem N contas...") pode encolher, porque a frase de
   situação passa a ser a mensagem principal.

**Cuidados.**
- `FinaiApp.kt` (perto da linha 230) passa cerca de 25 parâmetros para `HomeScreen`. Se
  mexer na assinatura, confira todos.
- O `LaunchedEffect` que chama `aiViewModel.ensureDecisions` depende de `safeNote`. Não
  quebre a memoização.
- A frase de situação é cálculo local: deriva de `safeToday.shortfall`, `payCycle` e
  `floor`. Não use IA para ela (planning.md §6).

**Pronto quando:** sem rolar a tela, dá para responder "Quanto posso gastar?" e "O que
vence primeiro?". Confira no emulador em 412×892 dp e em 320×640 dp.

---

## Item 4 · Legibilidade e contraste [Alta]

**Números medidos.**
- Tamanhos de fonte mais usados no app: 13 sp (69×), 12 sp (55×), 11 sp (49×), 10 sp
  (26×), 12,5 sp (18×), 13,5 sp (15×), 11,5 sp (14×), 9,5 sp (6×) e 9 sp (5×).
- `FinaiColors.TextMuted` (#A1A1AA, 2,56:1 sobre branco) aparece em 17 lugares da
  `HomeScreen`, 9 da Agenda, 7 da Fatura e 6 de cada uma das telas Dívidas, Limites e
  Configurações.
- Texto branco sobre `Emerald` (#10B981, 2,54:1): botão "Posso comprar?", barra de
  confirmação da importação e outros.

**O que fazer.**
1. **Fonte única.** Hoje `ui/theme/Type.kt` usa `FontFamily.Default` (Roboto), e só o
   lançamento usa Inter (`TransactionEntryScreen.kt:77`, arquivos `res/font/inter_*`).
   Mova a `FontFamily` Inter para `ui/theme/Type.kt` e use-a no `FinaiTypography`. Os
   `Text(...)` com `fontSize` solto não herdam a família do tema se o `LocalTextStyle`
   não a tiver, então confira se o `MaterialTheme` está aplicando.
2. **Escala curta** em `Type.kt`: valor principal (~34), título (20), subtítulo (16),
   corpo (15), legenda (13) e navegação (12–13).
   - Nenhum texto útil abaixo de 12 sp.
   - Os rótulos em CAIXA ALTA de 10 sp (por exemplo "PODE GASTAR HOJE" e "DIAS") viram
     12 sp ou viram texto normal.
   - Substitua os `fontSize = X.sp` soltos por `MaterialTheme.typography.*` aos poucos,
     tela a tela.
3. **Cores.**
   - `TextMuted` passa para cerca de #71717A (4,8:1). Confira se `TextTertiary` também
     passa de 4,5:1.
   - Botões com texto branco usam `EmeraldDark` (#059669, ~3,8:1, ainda abaixo) ou
     #047857 (~5,5:1). Mantenha `Emerald` só como acento sem texto em cima: barras de
     progresso e o anel dos dias.
   - Calcule o contraste com a fórmula WCAG antes de escolher. Não chute.
4. **Alvos de toque de 48 dp.** Ações pequenas: "Ver todos", "Lançar receita" (com
   `clickable` direto no `Text`), "Abrir agenda", "Pular tudo" no assistente inicial.
   Envolva em `Box(Modifier.heightIn(min = 48.dp).clickable ...)`, como foi feito com
   "Entenda este valor" em `HomeScreen.SafeToSpendCard`.
   - Achado no emulador: "Pular tudo" (`InitialSetupWizard.WizardHeader`) não respondeu
     na primeira tentativa.

**Cuidados.** Fontes maiores quebram layouts fixos. Os cartões de meta têm largura fixa
de 236 dp (`GoalTeaserCard`), o anel de dias tem 76 dp e a barra de baixo tem rótulos.
Confira em 320×640 dp.

**Pronto quando:** nenhum texto útil fica abaixo de 4,5:1 nem de 12 sp, e a família é a
mesma em todas as telas.

---

## Item 5 · Renda principal explícita [Alta] · migração Room 9 → 10

**Problema.** `PayCycle.isSalary` reconhece o salário pela palavra "salário"/"holerite" na
descrição. Sem ela, o app usa a maior receita recorrente
(`PayCycle.salariesOf`). O assistente inicial grava a renda como **"Renda mensal"**,
recorrente (`InitialSetupWizard.kt:269`/`286`), então quem aceita o padrão cai no
"salário deduzido". A folha "Entenda este valor" já avisa disso na seção "Para o valor
ficar mais fiel".

**O que fazer.**
1. `TransacaoEntity.rendaPrincipal: Boolean` (ou `salario`), com `MIGRATION_9_10` em
   `data/local/FinaiDatabase.kt`.
   - A migração marca como renda principal as receitas que **hoje** casam com
     `isSalary`. A lógica de nome precisa ser replicada em SQL ou feita num passo
     Kotlin depois da migração; prefira SQL com `LOWER`/`LIKE` e documente a limitação
     de acentos. Se nenhuma casar, marca a maior receita recorrente.
   - Suba `version` para 10 e acrescente a migração ao array `MIGRATIONS`. O
     `app/schemas/` gera o JSON da versão 10 sozinho.
   - Estenda `FinaiDatabaseMigrationTest` (androidTest).
2. `PayCycle.salariesOf` passa a usar a flag. O nome continua só como fallback para
   dados antigos, se precisar. `salarioPeloNome` vira "definido pelo usuário ou
   deduzido".
3. **UI.** No lançamento (`TransactionEntryScreen.kt`), a Receita ganha o switch "É minha
   renda principal", exclusivo com "Entrada extra", como já acontece entre Extra e
   Recorrente. Na Agenda, receitas ganham "Marcar como renda principal", no mesmo padrão
   de "Marcar extra". O assistente inicial grava a flag.
4. Troque os textos que mandam escrever "Salário" na descrição: o `PayCycleCard` vazio,
   `SpendExplanation.missing` e o caminho `fromMonth`.

**Cuidados.**
- Voltar para um APK v9 com banco v10 apaga os dados, porque
  `fallbackToDestructiveMigrationOnDowngrade` fica ativo. Como o usuário só testa no fim,
  isso vira um aviso da **entrega final**, não deste item.
- **Pergunta em aberto: dia fixo da dívida.** A pendência antiga (a) do `CLAUDE.md` também
  pede migração. Na primeira vez que foi perguntado, o usuário não entendeu. Explique
  assim:
  > "Uma dívida que vence todo dia 29 hoje fica guardada só com a *próxima* data. Quando
  > você paga a parcela de 29/01, a próxima seria 29/02, que não existe, então vira 28/02.
  > A partir daí o app acha que ela vence no último dia de cada mês (31/03, 30/04...) em
  > vez de voltar para o dia 29. Para corrigir, o app precisa guardar o dia combinado (29).
  > Quer que eu faça isso junto com a renda principal, numa única atualização do banco?"

  **Padrão, se ele não se opuser: juntar.** Como ele só instala no fim, uma migração ou
  duas dá no mesmo para ele, e juntar é menos código de migração. A mudança é
  `DividaEntity.diaVencimento: Int?`, preenchido na migração com o dia do
  `proximoVencimento` atual. `DebtSchedule` (projeção, `afterPayment`, `undoPayment`) e
  `DebtCalculator` ("Livre em") passam a usar esse dia em vez de derivar do vencimento, e
  a regra de fim de mês de `monthlyOccurrence` só vale quando o dia combinado for ≥ 29.

---

## Item 6 · Onboarding mínimo [Alta]

**Problema.** Tour (`OnboardingTour.kt`, 7 passos) seguido do assistente inicial
(`InitialSetupWizard.kt`: Welcome, Income, Accounts, RecurringBills, Debts, Goals e
conclusão). É muito trabalho antes de ver qualquer benefício.

**O que fazer.**
- O assistente pede só **renda principal + data do próximo recebimento** e, opcional,
  **as contas desta semana**. Termina mostrando o resultado real: "Até o salário você tem
  R$ X, R$ Y por dia".
- Dívidas, metas e gastos fixos viram convites na própria tela, no estado vazio (ver
  item 9), e não passos obrigatórios.
- Depende do item 5, que define a flag de renda principal que o assistente grava.

**Decidido (28/09/2026): o tour vira dicas dentro de cada tela, com destaque no componente
explicado.** O `OnboardingTour` de 7 passos em tela cheia sai.

Como construir:
1. **Registro de alvos.** Um `TipTargetRegistry` exposto por `CompositionLocal` em
   `FinaiApp`, e um `Modifier.tipTarget("home.safe")` que, via `onGloballyPositioned`,
   grava os limites do componente na raiz (`boundsInRoot`). Nada de coordenada fixa.
2. **Camada de destaque.** Um overlay por cima de tudo que escurece a tela com um
   **recorte** no formato do alvo (retângulo arredondado com folga de ~8 dp).
   - Desenhe em `Canvas` com `Path` + `PathFillType.EvenOdd`, ou com
     `graphicsLayer(compositingStrategy = Offscreen)` + `BlendMode.Clear`.
   - Um balão com título curto, uma frase, "1 de 3" e os botões "Próximo" e "Entendi"
     fica acima ou abaixo do alvo, conforme o espaço.
   - O toque fora do balão avança ou fecha e **não** passa para a tela de trás.
   - O botão voltar do sistema fecha a dica: entra no `anyLayerOpen` do `FinaiApp`.
3. **Roteiro por tela**, poucas dicas cada, só com os componentes que existem na hora.
   Se o alvo não está na tela (lista vazia, cartão ausente), pule a dica.
   - **Início:** o bloco de situação e o valor livre; "Entenda este valor"; os próximos
     vencimentos; o "+" central.
   - **Agenda:** navegação de mês; toque numa conta para marcar como paga; lançamentos
     do mês.
   - **Objetivos:** o cartão da meta e o aporte; a leitura da IA.
   - **Dívidas:** ordem pelo custo do juro; "Paguei a parcela"; "Ensaiar a ligação".
   - **Limites:** só quando o usuário chegar lá pelo caminho de sempre.
   - **Assistente (chat):** as sugestões de pergunta.
4. **Quando mostrar.** Na primeira visita a cada tela, depois do assistente inicial, e
   nunca com outra camada aberta (chat, "+", folhas, comemoração). Espere o layout
   estabilizar, ~300 ms depois de a tela aparecer, para os limites estarem certos.
5. **Persistência.** `FinaiPreferences` ganha um `Set<String>` de telas já vistas
   (`tipsSeen`), que substitui o uso de `onboardingComplete` para o tour.
   - "Rever tour guiado" (Configurações → Ajuda) limpa o conjunto.
   - "Apagar todos os dados" também limpa (hoje chama `restartOnboarding`).
   - Quem já concluiu o tour antigo **não** deve ver as dicas de novo sozinho: na
     primeira leitura, se `onboardingComplete == true`, marque todas as telas como
     vistas.
6. **Visual:** fundo escuro com ~60% de opacidade, balão branco, texto com os tamanhos e
   contrastes do item 4, e alvo de toque de 48 dp nos botões do balão.
7. **Teste:** o registro e o roteiro (quais dicas aparecem, dado quais alvos existem e o
   que já foi visto) em Kotlin puro, testável na JVM. O desenho é conferido no emulador.

---

## Item 10 · Navegação previsível [Média]

**Problema.**
- Limites (`FinaiDestination.Budgets`) não está na barra de baixo
  (`ui/components/FinaiBottomNav.kt`). Só se chega lá por "Ajustar limites" no cartão
  preto ou por um aviso. Isso é a pendência (c) do `CLAUDE.md`: o usuário não achava a
  tela.
- O "+" (`QuickActionSheet` + `FinaiFixtures` perto da linha 48) mistura lançar,
  importar, simular e novo objetivo.

**Decidido (28/09/2026): Limites não ganha acesso mais fácil.** A barra de baixo fica
como está (Início, Agenda, Objetivos, Dívidas), e Limites continua só pelos caminhos de
sempre: "Ajustar limites" no bloco de situação da Início e os avisos de orçamento do sino.
A pendência (c) do `CLAUDE.md` fica **encerrada por decisão do usuário**; atualize o
`CLAUDE.md` para dizer isso. Não crie atalho, aba ou bloco novo para Limites em nenhum item
desta fase (7, 8, 9 e as dicas do item 6 incluídos).

O que sobra deste item é **só o "+"**: "Lançar gasto" vem em destaque como ação primária,
e importar, simular e novo objetivo ficam como secundárias. Limites e Importação continuam
empilhando por cima da tela atual (`navigateTo` em `FinaiApp.kt`), como na correção do
botão voltar.

---

## Itens 7, 8 e 9 · Hierarquia, ações e estados vazios [Média]

Faça tela a tela, junto com o item 4.

- **7 · Menos caixas, cor com significado.**
  - Movimentações (Agenda, itens da fatura, lançamentos em Limites) viram lista simples
    com divisor, sem cartão com borda em volta de cada item.
  - Cartão só para resumo e decisão.
  - Roxo: hoje aparece no selo "Prioridade" da meta e no cartão do Coach (fundo
    índigo). Troque por neutro ou âmbar.
  - Vermelho = problema, âmbar = atenção.
  - Superfícies de IA ("Decisões para você", Coach) ficam neutras, sem destaque maior
    que os números.
- **8 · Ações reconhecíveis.**
  - "Editar", "Excluir", "Paguei a parcela" e "Marcar extra" são textos pequenos
    clicáveis nas telas (16 ocorrências de Editar/Excluir em `ui/screens`).
  - Ação frequente vira botão de 48 dp. Excluir vai para um menu de três pontos (⋮)
    com confirmação.
- **9 · Estados vazios que orientam.**
  - Exemplo: "Nada agendado para este mês." (`AgendaScreen.kt:185`). Todo vazio ganha
    uma ação concreta: "Lançar conta", "Importar fatura".
  - Diferencie **zero real** ("R$ 0 gastos em Lazer") de **sem cadastro** ("Nenhum
    limite definido – definir") e de **estimativa** (selo "estimado", como o do salário
    estimado no ciclo).

---

## Item 11 · Importação com confirmação fixa [Média]

`ui/screens/importer/ImportScreen.kt`: `ConfirmBar` (linha ~582) é um `item {}` do
`LazyColumn` depois de todos os itens (linha 126). Numa fatura grande, fica lá embaixo.

- Tire a barra do `LazyColumn` e fixe-a no rodapé (`Scaffold`/`Box` com
  `align(BottomCenter)` + `navigationBarsPadding`). Deixe um `contentPadding` inferior
  para ela não cobrir o último item.
- Filtro "Precisa revisar": duplicata `POSSIBLE`/`LIKELY`, sem categoria ("Outros" com
  selo "a revisar") e recorrência. Chips no topo da lista, no mesmo estilo dos filtros da
  `InvoiceScreen`.

---

## Item 12 · Exportar e restaurar dados [Expansão]

Responde à pergunta do `planning.md` §11 sobre backup: `allowBackup="false"`, e trocar de
aparelho perde tudo.

- **Exportar:** um JSON com as tabelas de dado do usuário, as mesmas que
  `FinanceRepository.apagarTodosOsDados` limpa. `mensagens_chat` e
  `notificacoes_enviadas` são opcionais. `uso_provedor_ia` fica de fora. Salve via SAF
  (`ActivityResultContracts.CreateDocument`), sem permissão nova.
- **Restaurar:** `OpenDocument`, valida a versão do schema, confirma que **substitui
  tudo** e grava numa transação Room.
- Nunca inclua chaves de IA (`AiKeyStore`). Registre no `PRIVACY.md` que o arquivo
  exportado sai do controle do app.
- Ponto de entrada: Configurações → "Privacidade e dados".
- A "ativação de IA mais simples" (segunda metade do item) fica para depois. Sem
  backend, não há como evitar a chave; no máximo dá para melhorar o texto e o link de
  cada provedor, que já existem.

---

## Como testar neste ambiente

- **Testes JVM:** `./gradlew testDebugUnitTest`. Rode a cada item.
- **APK:** `./gradlew assemblePessoal` gera `app/build/outputs/apk/pessoal/app-pessoal.apk`
  (~16 MB). **Não mande ao usuário a cada item**; use no emulador. Ele sai uma vez só, na
  entrega final.
- **Emulador:** `~/AppData/Local/Android/Sdk/emulator/emulator.exe -avd Pixel_6_API_34
  -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`, em background. O `adb` fica
  em `~/AppData/Local/Android/Sdk/platform-tools/adb.exe` (não está no PATH). Encerre com
  `adb emu kill` ao terminar, porque a máquina fica sem memória.
- **Screenshot:** `adb emu screenrecord screenshot <arquivo.png>`. O `screencap` sai
  branco com swiftshader.
- **O emulador tem override de resolução 1236×2676**, então as coordenadas do screenshot
  **não** batem com as do `input tap`. Pegue as coordenadas pelo `uiautomator`:
  `adb exec-out uiautomator dump /dev/tty | tr '>' '\n' | grep 'texto'`, leia o `bounds`
  e toque no centro.
- **Dados do emulador:** têm dados de teste sem nenhuma receita com "salário", então só
  exercitam o caminho sem ciclo. Para ver o ciclo, lance uma receita recorrente
  "Salário" pelo "+".
- **Tour e assistente:** voltam a aparecer depois de instalar por cima. Use "Pular" e
  depois "Pular tudo", pelas coordenadas do `uiautomator`.

## Entrega final

Quando todos os itens estiverem feitos:
1. Rode `testDebugUnitTest` e `connectedDebugAndroidTest`, este principalmente pela
   migração 9 → 10 em `FinaiDatabaseMigrationTest`. Faça uma passada completa no
   emulador: todas as telas, as dicas de cada uma, e os casos com e sem salário.
2. Gere o `pessoal` e **copie para o scratchpad com nome único**, por exemplo
   `finai-fase7-clareza.apk`, antes do SendUserFile. Envios repetidos de
   `app-pessoal.apk` travaram em "baixando" no celular.
3. Avise, em destaque: **"depois de instalar este APK, não dá para voltar ao do `main`
   sem perder os dados"**. O banco sobe para a versão 10, e o downgrade apaga tudo.
   Ele instala por cima do app atual sem perder nada, porque o `versionCode` é maior.
4. Se ele baixar pelo navegador do celular, o arquivo vem como `.apk.zip`: ele deve
   renomear e tirar o `.zip`, sem extrair.
5. Só depois do teste dele: merge de `v2-clareza` no `main`, com o aval dele.

## Decisões

Tomadas pelo usuário em 28/09/2026 (também registradas no `planning.md` §11):

1. **Número principal da Início: o livre até o salário** (item 3). O valor por dia vira
   apoio.
2. **Tour: dicas dentro de cada tela, com destaque no componente explicado** (item 6).
3. **Limites continua escondido de propósito:** só por "Ajustar limites" e pelos avisos
   (itens 3 e 10). A pendência (c) está encerrada.
4. **Teste só no fim:** nenhum APK no meio da fase (ver "Entrega final").

Em aberto:

5. **Dia fixo da dívida junto com a renda principal** (item 5). O usuário não entendeu a
   primeira pergunta. Use a explicação do item 5. Padrão, se ele não se opuser: juntar.
