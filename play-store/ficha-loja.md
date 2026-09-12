# Play Store · Ficha da loja (pt-BR)

Textos prontos para colar na Play Console. Tudo aqui descreve o que o app
**realmente faz hoje** — nenhuma promessa de recurso inexistente, que além de
ser propaganda enganosa é motivo de reprovação na revisão.

Nada aqui contém ID de aplicativo, conta de desenvolvedor ou URL que dependa de
você — esses campos estão marcados como **[VOCÊ]**.

---

## Identificação

- **Nome do app** (máx. 30 caracteres): `FinAI — controle financeiro`
- **Nome do pacote**: `com.finai.app`
- **Categoria**: Finanças
- **Tags sugeridas**: finanças pessoais, orçamento, controle de gastos
- **Tipo**: Aplicativo · Gratuito · Sem compras no app · Sem anúncios
- **E-mail de contato**: **[VOCÊ]**
- **URL da política de privacidade**: **[VOCÊ]** — publique
  `play-store/politica-de-privacidade.md` e cole a URL aqui

## Descrição curta (máx. 80 caracteres)

```
Seus gastos, metas e dívidas em um só lugar — com os dados no seu aparelho.
```

## Descrição completa (máx. 4000 caracteres)

```
O FinAI organiza sua vida financeira sem tirar seus dados do celular.

Tudo que ele calcula — quanto você pode gastar hoje, quanto sobra no mês, o
progresso de cada meta, quanto seus juros custam, quanto falta para quitar cada
dívida — é conta feita no seu aparelho, a partir do que você lançou. Não há
conta para criar, não há login, não há sincronização com nuvem e não há conexão
com o seu banco.

O QUE VOCÊ CONSEGUE FAZER

• Pode gastar hoje — um valor diário seguro, já descontadas as contas do mês e o
  que você separou para as metas.
• Agenda — o mês inteiro de contas a pagar e a receber, com o que está atrasado,
  o que vence hoje e o que já foi pago.
• Objetivos — viagem, reserva de emergência, uma compra grande: acompanhe quanto
  falta e em quanto tempo chega, com base na sua capacidade real de poupança.
• Dívidas — ordem de ataque sugerida pelo custo do juro, não pelo tamanho da
  dívida, com previsão de quando você fica livre.
• Limites por categoria — alimentação, transporte, lazer, assinaturas: veja o
  quanto já foi e a projeção de como o mês fecha.
• Posso comprar? — simule uma compra antes de fazer e veja o efeito na folga do
  mês e na data do seu próximo objetivo.
• Importar fatura — escolha um PDF, uma planilha ou uma foto da fatura e o app
  lê os lançamentos no próprio aparelho, separa o que parece duplicado e
  identifica assinaturas, para você revisar antes de confirmar.
• Avisos na hora certa — conta vencendo, categoria perto do limite, assinatura
  parada, entrada extra confirmada.

SEUS DADOS FICAM COM VOCÊ

Lançamentos, contas, metas e dívidas ficam gravados só no seu aparelho. O
arquivo da fatura que você importa é lido ali mesmo e nunca é enviado para
lugar nenhum. O app não tem servidor, não usa rastreamento, não mostra anúncios
e não pede permissão de localização, contatos, câmera ou microfone.

ASSISTENTE DE IA — OPCIONAL, E COM A SUA CHAVE

Se você quiser explicações escritas em linguagem natural (a leitura de uma meta,
o veredito de uma compra, um roteiro para negociar uma dívida, ou conversar com
o assistente), basta cadastrar uma chave gratuita de um provedor de IA nas
configurações. Você escolhe qual: Gemini, Groq, OpenRouter, Mistral ou Cerebras.
A chave fica criptografada no seu aparelho.

Mesmo nesse caso, o que sai daqui é o mínimo: valores já somados e os nomes que
você mesmo escreveu. Nunca o seu extrato, nunca o documento importado, nunca
CPF ou número de conta — o app sequer guarda esses dados.

Sem chave configurada, o app não faz nenhuma chamada de rede, e todos os
números continuam funcionando normalmente.

FUNCIONA SEM INTERNET

Todas as telas de números funcionam offline. Só o texto escrito pela IA depende
de conexão — e, quando ela não está disponível, o app diz isso com clareza em
vez de inventar.
```

## Novidades desta versão (release notes, máx. 500 caracteres)

```
Primeira versão pública.

• Saldo seguro do dia, agenda de contas, objetivos, dívidas e limites por
  categoria, tudo calculado no aparelho.
• Importação de fatura por PDF, planilha ou foto, com leitura offline.
• Avisos proativos sobre contas e orçamentos.
• Assistente de IA opcional, com a sua própria chave gratuita.
```

## Recursos gráficos — **[VOCÊ]** precisa produzir

A Play Console não aceita a ficha sem estes arquivos. Nenhum deles pode ser
gerado a partir do código; use o app rodando e o design em `project/ref/`.

| Item | Especificação | Onde conseguir |
|---|---|---|
| Ícone da loja | 512×512 PNG, 32-bit, sem transparência | Derive de `app/src/main/res/mipmap-*/ic_launcher` |
| Gráfico de destaque | 1024×500 PNG/JPG | Precisa ser criado |
| Screenshots de celular | 2 a 8, mín. 320 px no menor lado, proporção entre 16:9 e 9:16 | `adb shell screencap` nas telas Início, Agenda, Objetivos, Dívidas, Limites e Importar |
| Screenshots de tablet (opcional) | 7" e 10" | Só se for declarar suporte a tablet |
| Vídeo (opcional) | URL do YouTube | — |

Sugestão de ordem das screenshots, que segue o fluxo de valor do app:
Início ("Pode gastar hoje") → Agenda → Objetivos → Dívidas → Limites → Importar.

> Antes de tirar as screenshots, cadastre dados que você não se importe de
> mostrar publicamente — o app abre com um conjunto inicial de exemplo, mas se
> você já usou com dados reais, eles aparecem nas imagens.

## Outros formulários da Console — **[VOCÊ]**

- **Classificação de conteúdo**: questionário obrigatório. O app não tem
  conteúdo sensível, violência, apostas nem compras; é um utilitário de
  finanças pessoais.
- **Público-alvo e conteúdo**: adulto (18+ é o mais seguro para app financeiro);
  o app **não** é direcionado a crianças.
- **App de finanças**: a Play tem um formulário extra para apps financeiros.
  Como o FinAI **não** é instituição financeira, não faz empréstimo, não
  processa pagamento e não se conecta a banco, a resposta é que ele é uma
  ferramenta de gestão pessoal — mas leia as perguntas com atenção, porque
  declaração errada nessa categoria costuma travar a revisão.
- **Segurança dos dados**: ver `play-store/data-safety.md`.
