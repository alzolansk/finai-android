# Lançamento por notificações

O FinAI lê as notificações dos apps financeiros que você escolher (banco, cartão, carteira
digital), prepara uma sugestão de lançamento e pergunta se está certa. **Nada entra nas suas
contas antes de você tocar em "Salvar lançamento".** O texto é interpretado só no aparelho, por
regras fixas: não vai para IA nem para a internet.

## Como ativar

1. Abra **Configurações (engrenagem) → Lançamentos por notificações**.
2. Toque em **Ler notificações (acesso a notificações)**. O Android abre a tela de acesso a
   notificações: ligue o **FinAI** e confirme o aviso do Android. Volte ao app: a linha passa a
   dizer "Permitido".
   - O Android dá esse acesso para *todas* as notificações do aparelho de uma vez. O FinAI
     descarta, sem ler o conteúdo, tudo que não vem dos apps escolhidos no passo 4.
3. **Mostrar avisos do FinAI** é outra permissão (a de o FinAI mostrar as próprias
   notificações). Ela só serve para o aviso "Movimentação encontrada" aparecer quando você está
   em outro app. Sem ela, as sugestões aparecem dentro do FinAI e em "Para confirmar".
4. Toque em **Aplicativos acompanhados** e marque os apps do banco/cartão. Os financeiros
   conhecidos aparecem primeiro; os outros ficam em "Outros aplicativos", com busca. Evite apps
   de mensagens ou e-mail.
5. Ligue **Ler notificações dos apps escolhidos**. O mesmo botão pausa a captura.

## Como funciona no dia a dia

- **FinAI aberto:** o quadro "Movimentação encontrada" abre no centro da tela, uma sugestão por
  vez. Se você estiver preenchendo um formulário, numa confirmação ou em Configurações, o quadro
  não abre por cima: aparece um aviso rápido no topo e a sugestão espera na fila. Nas telas
  principais, o indicador "N para confirmar" leva à lista.
- **Outro app aberto:** chega a notificação "Movimentação encontrada" do FinAI. Tocar nela abre
  exatamente essa sugestão. O FinAI nunca abre uma tela sozinho por cima de outro app.
- No quadro: **Salvar lançamento** (só com tudo preenchido), **Editar** (abre o formulário do
  "+" preenchido; fechar sem salvar mantém a sugestão pendente) e **Ignorar**. O X deixa para
  depois: a sugestão fica em **Para confirmar**.
- Compra no crédito ou parcelada usa a mesma regra de fatura do "+": se o vencimento da fatura
  não for certo, o quadro pede para confirmar; na primeira compra de um cartão, use Editar para
  informar o vencimento. Compra de crédito nunca é salva como débito.
- Depois de salvar: **Ver na agenda** (abre no dia, com o lançamento ou a fatura destacada) e
  **Desfazer** (tira o lançamento e a sugestão volta para "Para confirmar").

## Formatos reconhecidos

Cobertos por exemplos e testes (`NotificationParserTest`, `CaptureSamples`):

| Mensagem | O que vira |
| --- | --- |
| "Compra no crédito de R$ 42,90 aprovada no Mercado." | Compra no crédito: pede cartão e fatura |
| "Compra no débito de R$ 18,50 aprovada na Padaria." | Compra no débito: pede a conta |
| "Compra de R$ 42,90 APROVADA em MERCADO" (sem dizer a forma) | Compra: pede Débito/Pix ou Crédito |
| "… total de R$ 300,00 em 3 parcelas de R$ 100,00" / "em 4x de R$ 25,00" | Compra parcelada com o número de parcelas da mensagem |
| "Você enviou um Pix de R$ 120,00 para Ana Souza." | Pix enviado (gasto): pede conta e categoria |
| "Você recebeu um Pix de R$ 850,00 de Carlos Lima." | Pix recebido: receita avulsa |
| Estorno, cancelamento, pagamento de fatura, TED/DOC | **Precisa revisar**: não vira gasto nem receita sozinho |
| Compra recusada, propaganda, oferta de crédito, aviso de limite/saldo, mensagem sem valor | Ignorada |

Regras: valores só em reais (`R$ 1.234,56`, `R$1234,56`); limite, saldo e cashback citados
na mensagem nunca viram o valor; vários valores sem explicação → você escolhe o valor; data
escrita na mensagem ("em 12/10", "ontem") ou, se não houver, o dia da notificação (o quadro diz
qual); o nome do app nunca define a conta ou o cartão (só um nome de conta/cartão que você já
usa, citado no texto, vem marcado). Categoria sugerida pela tabela local de estabelecimentos,
sempre editável. **Cada banco escreve de um jeito: não há garantia de reconhecer todos.** O
que não for reconhecido é descartado, nunca adivinhado.

## Repetições e fatura importada

- **Mesma notificação de novo** (reenvio, atualização do texto): o Android mantém a mesma chave
  para ela. Reenvio idêntico é ignorado; atualização da mesma compra atualiza a sugestão
  pendente e nunca reabre uma já salva ou ignorada. Mesma mensagem em outra notificação, pouco
  depois, vira uma segunda sugestão marcada "pode ser repetida" — duas compras iguais existem.
- **Mesma compra por outro caminho:** o quadro avisa quando já existe um lançamento parecido
  (mesmo valor, data próxima, mesmo lugar), mas deixa salvar. Na **importação de fatura**, a
  compra confirmada pela notificação aparece como duplicata (desmarcada) com o motivo "Já
  lançado pela notificação de dd/MM"; valor igual com nome diferente vira "possível"; outro
  cartão nunca é "provável"; cada parcela só bate com o próprio número. Nada é unido ou apagado
  sozinho.

## Testar sem compra real

**Configurações → Lançamentos por notificações → Testar com notificações.** Escolha um dos 13
exemplos (dá para editar título e mensagem e "Restaurar exemplo"):

1. Compra no crédito · Mercado — 2. Débito · Padaria — 3. Pix enviado — 4. Pix recebido —
5. Parcelada 3× — 6. Reenvio da mesma notificação — 7. Atualização da mesma notificação —
8. Duas compras iguais — 9. Compra recusada — 10. Propaganda de limite — 11. Mensagem sem
valor — 12. Estorno — 13. Mensagem ambígua.

- **A · Simular a leitura:** manda o texto direto para o mesmo leitor e fluxo de sugestões e
  mostra o que foi reconhecido e o que falta. Não prova que o Android liberou o acesso nem que o
  listener funciona.
- **B · Enviar notificação de teste:** publica uma notificação real do FinAI ("Teste do
  FinAI", canal "Testes do FinAI"). Escolha **Agora**, **Em 5 s** ou **Em 15 s**, toque em
  **Enviar notificação de teste** e saia do app. Ela passa pelo leitor de notificações do
  Android; aparece então "Teste do FinAI · Movimentação encontrada". Os exemplos 6, 7 e 8 mandam
  duas notificações com 4 s entre elas.
- **Envios recentes** mostra o estado real de cada envio: aguardando envio → notificação
  publicada → recebida pelo listener → sugestão criada / sugestão atualizada / repetição
  ignorada / mensagem não reconhecida. "Recebida" só aparece quando o leitor de fato leu.
- Sugestões de teste têm a marca "Teste do FinAI". Confirmar uma delas mostra o fluxo inteiro
  e **não grava nada** nas finanças. **Limpar testes** apaga só os envios e as sugestões de
  teste (e as notificações deles).

## Dados e privacidade

- Sugestões ficam no Room (`sugestoes_lancamento`) e sobrevivem a fechar o app. O texto
  original fica só enquanto a sugestão está pendente, no máximo 30 dias; sugestões resolvidas
  somem depois de 90 dias. Nada do conteúdo vai para o logcat.
- Backup (Privacidade e dados): leva as sugestões reais **sem** o texto original e a lista de
  apps escolhidos; testes não vão. Backup antigo continua abrindo.
- **Apagar todos os dados** apaga as sugestões e os testes e **pausa a captura** até você ligar
  de novo.

## Limitações conhecidas

- Formatos fora da tabela acima não são reconhecidos (ou pedem revisão).
- Se o Android encerrar o processo do FinAI, ele religa o leitor sozinho, mas uma notificação
  que chegue nesse intervalo pode não ser lida (comportamento do sistema).
- Na primeira compra num cartão sem fatura no FinAI, o vencimento é informado pelo Editar.
- O "cartão final 1234" da mensagem só aparece como dica: o cartão é sempre escolhido por você.
- Validado em emulador (Pixel 6, Android 14). Não testado em aparelho físico nem com
  notificações reais de bancos.

## Para desenvolvimento: roteiro com adb

O teste instrumentado `CaptureListenerE2ETest.finaiTestNotificationIsReadByTheRealListener`
roda sozinho. Para a captura de um app que não é o FinAI (o `com.android.shell` faz papel de
banco), com os APKs instalados:

```bash
adb shell cmd notification allow_listener com.finai.app/com.finai.app.data.capture.FinanceNotificationListener
adb shell am instrument -w -e shellE2E true -e class com.finai.app.data.capture.CaptureListenerE2ETest#shell1_selectShellAsBankApp com.finai.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell "cmd notification post -t 'Aquecimento' aquece 'Olá'"
adb shell "cmd notification post -S bigtext -t 'Compra aprovada' e2e 'Compra no crédito de R\$ 42,90 aprovada no Mercado.'"
adb shell am instrument -w -e shellE2E true -e class com.finai.app.data.capture.CaptureListenerE2ETest#shell2_verifyCaptureFromSelectedApp com.finai.app.test/androidx.test.runner.AndroidJUnitRunner
```

O `am instrument` encerra o processo do app; a primeira notificação depois dele só religa o
leitor (por isso o "aquecimento").
