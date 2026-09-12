# Política de Privacidade — FinAI

**Última atualização:** [VOCÊ — data da publicação]
**Aplicativo:** FinAI (`com.finai.app`)
**Responsável:** [VOCÊ — nome ou razão social]
**Contato:** [VOCÊ — e-mail]

> Este é o rascunho do texto. A Google Play exige uma **URL pública** para a
> política de privacidade: publique este conteúdo em qualquer lugar acessível
> (GitHub Pages, um site próprio) e cole a URL na Play Console e na ficha da
> loja. Os quatro campos **[VOCÊ]** acima precisam ser preenchidos antes de
> publicar — não foram preenchidos aqui porque dependem de dados seus.

---

## Resumo

O FinAI é um aplicativo de finanças pessoais que funciona no seu aparelho. Ele
não tem servidor próprio, não exige cadastro nem login, não usa rastreamento,
não exibe anúncios e não vende dados. Os seus dados financeiros ficam gravados
apenas no seu dispositivo.

A única situação em que alguma informação sai do aparelho é se **você** decidir
ativar o assistente de inteligência artificial, cadastrando uma chave de API de
um provedor à sua escolha. Isso é opcional e está desligado por padrão.

## 1. Dados que ficam no seu aparelho

Tudo o que você registra no app é gravado localmente, em um banco de dados no
próprio dispositivo:

- lançamentos de gastos e receitas;
- contas a pagar e a receber;
- objetivos e valores guardados;
- dívidas, taxas e parcelas;
- limites por categoria e assinaturas;
- histórico de conversa com o assistente;
- as chaves de API que você cadastrar, guardadas de forma criptografada, usando
  o sistema de chaves seguras do próprio Android.

Nada disso é enviado para o desenvolvedor. Não existe cópia em nuvem: o backup
automático do Android está desativado para este aplicativo, justamente para que
seus dados financeiros não sejam copiados para fora do aparelho.

## 2. Importação de fatura

Ao importar uma fatura em PDF, planilha ou foto, o arquivo é lido **dentro do
aparelho**: o reconhecimento de texto (OCR) roda localmente, sem conexão. O
arquivo original nunca é enviado, copiado ou armazenado pelo aplicativo — apenas
os lançamentos que você confirmar são gravados no banco local.

O aplicativo não pede permissão de armazenamento: você escolhe um arquivo por
vez pelo seletor do sistema, e o acesso vale só para aquele arquivo.

## 3. Assistente de IA (opcional)

Se você cadastrar a chave de um provedor de inteligência artificial (Google
Gemini, Groq, OpenRouter, Mistral ou Cerebras), o aplicativo passa a enviar, por
conexão segura (HTTPS), o mínimo necessário para que o texto explicativo seja
escrito:

- valores já somados e calculados, como a folga do mês, o progresso de uma meta
  ou o saldo e a taxa de uma dívida;
- nomes que você mesmo escreveu, como o nome de uma meta ou de uma dívida;
- a pergunta que você digitar no assistente, junto com as últimas mensagens da
  mesma conversa;
- na importação, o nome do estabelecimento já limpo de códigos e números, mais
  o valor e o dia, e somente para os itens que a regra local não conseguiu
  classificar.

**Nunca são enviados:** a lista dos seus lançamentos, o arquivo de fatura
importado, nome completo, CPF, número de conta ou de cartão. O aplicativo não
armazena esses dados em momento algum.

O envio é feito diretamente do seu aparelho para o provedor que você escolheu,
usando a sua própria chave. O desenvolvedor do FinAI não recebe cópia, não
intermedeia essas chamadas e não tem acesso ao seu conteúdo. O tratamento dos
dados pelo provedor escolhido é regido pela política de privacidade dele, que
recomendamos ler antes de cadastrar a chave.

Se você não cadastrar nenhuma chave, o aplicativo não faz nenhuma conexão de
rede, e todos os cálculos e telas continuam funcionando.

## 4. Permissões

- **Internet** — usada apenas para as chamadas ao provedor de IA descritas
  acima.
- **Notificações** — para os avisos sobre contas vencendo e limites de
  categoria. Se você recusar, o aplicativo continua funcionando por completo.

O aplicativo não solicita acesso a localização, contatos, câmera, microfone,
telefone, calendário ou arquivos.

## 5. Dados de terceiros e rastreamento

O FinAI não utiliza ferramentas de análise de uso, de publicidade ou de
relatório de falhas. Não há identificadores de publicidade, cookies ou
rastreadores. Nenhum dado é vendido ou compartilhado para fins comerciais.

## 6. Retenção e exclusão

Como os dados ficam somente no seu aparelho, você tem controle total sobre eles:

- lançamentos, objetivos e dívidas podem ser excluídos individualmente dentro do
  aplicativo;
- as chaves de IA podem ser removidas a qualquer momento nas configurações, o
  que encerra qualquer envio;
- desinstalar o aplicativo apaga todos os dados definitivamente. Como não há
  backup em nuvem, não existe cópia a ser recuperada — nem por você, nem por
  ninguém.

## 7. Crianças

O FinAI não é direcionado a menores de 18 anos e não coleta intencionalmente
dados de crianças.

## 8. Alterações nesta política

Mudanças relevantes serão refletidas neste documento, com a data de atualização
no topo, e acompanharão a versão correspondente do aplicativo na Google Play.

## 9. Contato

Dúvidas sobre esta política: [VOCÊ — e-mail de contato].
