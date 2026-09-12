# Play Store · Formulário "Segurança dos dados" (Data safety)

Respostas para o formulário da Play Console, campo a campo, com a justificativa
técnica de cada uma. A fonte de verdade do comportamento é `PRIVACY.md`; se o
app mudar, atualize os dois.

> **Regra da Play:** o formulário descreve o que o app **coleta** (envia para
> fora do aparelho, para você ou para terceiros) e o que **compartilha**. Dado
> que só existe no aparelho **não** conta como coleta. É por isso que quase tudo
> aqui é "não".

---

## Coleta e compartilhamento de dados

**O app coleta ou compartilha algum dos tipos de dados de usuário exigidos?**
→ **Sim** (por causa de um único item, abaixo).

### Tipo: Informações financeiras → "Outras informações financeiras"

| Campo | Resposta |
|---|---|
| Este dado é coletado? | **Não** |
| Este dado é compartilhado? | **Sim** |
| É obrigatório ou opcional? | **Opcional** — o usuário escolhe se configura uma chave de IA. Sem chave, nada é enviado. |
| Finalidade | **Funcionalidade do app** |
| É transferido usando criptografia? | **Sim** (HTTPS, com cleartext bloqueado por `network_security_config.xml`) |
| O usuário pode pedir a exclusão? | **Sim** — removendo a chave nas configurações, o envio cessa; a exclusão do conteúdo já enviado segue a política do provedor escolhido por ele. |

**Justificativa.** "Coletado" seria enviar para servidor do desenvolvedor —
o FinAI não tem servidor nenhum. "Compartilhado" cobre o envio para terceiro:
quando o usuário configura a chave dele de um provedor de IA, valores agregados
(folga do mês, progresso de meta, saldo de dívida) e rótulos que ele mesmo
escreveu vão no prompt. Não vão lançamentos individuais, documento importado,
nome completo, CPF nem número de conta — esses campos não existem no modelo de
dados. Detalhe em `PRIVACY.md` §2.

### Tipo: Mensagens → "Outras mensagens no app"

| Campo | Resposta |
|---|---|
| Coletado? | **Não** |
| Compartilhado? | **Sim**, mesma condição e finalidade do item acima |

**Justificativa.** A pergunta que o usuário digita no chat, mais até 8 mensagens
anteriores da conversa, vão para o provedor de IA que ele configurou. O
histórico em si fica no Room, no aparelho.

### Todos os demais tipos → **Não coletado, não compartilhado**

Vale a pena registrar explicitamente porque a Console pergunta um por um:

- **Localização** (aproximada/precisa): não. Sem permissão de localização.
- **Informações pessoais** (nome, e-mail, ID, endereço, telefone, etnia,
  orientação, etc.): não. O app não tem cadastro, login nem conta.
- **Informações financeiras** → histórico de compras, informações de pagamento,
  pontuação de crédito: não.
- **Saúde e fitness**: não.
- **Fotos e vídeos**: **não**. A foto da fatura é lida pelo Storage Access
  Framework, processada por OCR no aparelho e descartada — nunca é enviada nem
  copiada.
- **Arquivos e documentos**: **não**, mesma razão (o PDF/planilha nunca sai).
- **Áudio**: não. Sem permissão de microfone.
- **Calendário, Contatos**: não.
- **Atividade no app** (interações, busca no app, outras ações): não. Sem
  analytics.
- **Navegação na Web**: não.
- **Informações e desempenho do app** (registros de falha, diagnósticos): **não**.
  Não há SDK de crash reporting. Os relatórios que a própria Play Console mostra
  são coletados pelo Android, não pelo app — e não entram neste formulário.
- **IDs do dispositivo ou outros**: não. O app não lê nem gera nenhum
  identificador.

## Práticas de segurança

| Pergunta | Resposta | Justificativa |
|---|---|---|
| Os dados são criptografados em trânsito? | **Sim** | Só HTTPS; cleartext bloqueado no `network_security_config.xml`. |
| Você fornece um jeito de o usuário pedir a exclusão dos dados? | **Sim** | Os dados são locais: desinstalar o app apaga tudo (sem backup em nuvem, `allowBackup="false"`). Objetivos, dívidas e lançamentos também podem ser excluídos um a um dentro do app. |
| O app segue a Política de Famílias? | Não aplicável (público-alvo adulto) | Ver classificação de conteúdo na ficha. |
| Os dados foram verificados de forma independente? | **Não** | Sem auditoria externa. |

## Contas e exclusão (formulário de exclusão de conta)

O app **não tem contas de usuário**. Quando a Console perguntar sobre exclusão
de conta, a resposta é que o app não oferece criação de conta — todos os dados
ficam no aparelho e são removidos com a desinstalação.

---

## O que revisar antes de enviar

1. Se algum dia entrar analytics, crash reporting, anúncio ou login, **este
   formulário muda** — declaração errada é motivo de suspensão.
2. Se o app passar a enviar lançamento individual (e não só agregado) para a IA,
   revise a linha de "Informações financeiras".
3. Confira `PRIVACY.md` §2 para a lista exata do que entra no prompt hoje.
