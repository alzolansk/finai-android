# FinAI · Revisão de privacidade (o que sai do aparelho vs. o que fica)

Fase 6 do `planning.md` (§9). Este documento é a auditoria do que o app
efetivamente faz — cada afirmação aqui aponta para o arquivo que a implementa,
para que a próxima pessoa possa conferir em vez de confiar.

Ele também é a base para preencher a seção **Data safety** da Play Store
(ver `play-store/data-safety.md`) e para escrever a política de privacidade
pública, que a Play exige por URL (ver `play-store/politica-de-privacidade.md`).

---

## 1. O que nunca sai do aparelho

| Dado | Onde fica | Garantia |
|---|---|---|
| Lançamentos, contas, objetivos, dívidas, orçamentos, assinaturas | Room, `finai.db` | Não existe nenhum código de upload. A única classe que abre conexão de rede é `data/ai/*AiProvider.kt`, e ela só envia o que `domain/AiPromptBuilder.kt` monta. |
| Histórico do chat | Room, tabela `mensagens_chat` | Idem. Só a pergunta atual + até 8 mensagens anteriores entram no prompt, quando o usuário envia uma mensagem. |
| Arquivo importado (PDF, planilha, foto da fatura) | Nunca é copiado | `data/importer/StatementImporter.kt` lê a `Uri` via `ContentResolver`, extrai texto no aparelho (ML Kit *bundled*, `PdfRenderer`, leitor de .csv/.xlsx próprio) e descarta. Não há upload de arquivo, nem cópia para cache, nem envio de imagem para API de visão. |
| Chaves de API dos provedores | `EncryptedSharedPreferences` (AES-256-GCM, chave mestra no Android Keystore), arquivo `finai_ai_keys.xml` | `data/prefs/AiKeyStore.kt`. Verificado no aparelho: nome e valor aparecem cifrados no XML. Excluído de backup e de transferência entre aparelhos. |
| Contagem de uso por provedor | DataStore `finai_ai_usage` | Só provedor, data e contagem. Sem PII. |
| Qualquer identificador de usuário | — | O app não tem login, não gera device id próprio, não usa analytics, crash reporting nem SDK de anúncio. Nenhuma dependência de telemetria está no `app/build.gradle.kts`. |

Backup automático do Android está **desligado** (`android:allowBackup="false"`),
então nem mesmo o banco vai para a conta Google do usuário. Ver a justificativa
e o trade-off no `AndroidManifest.xml` e em `RELEASE.md`.

## 2. O que sai do aparelho, e exatamente para onde

Só existe um caminho de saída: uma chamada HTTPS para o provedor de IA que o
**próprio usuário** configurou com a chave dele. Sem chave configurada, o app
não faz nenhuma requisição de rede — nem na primeira abertura, nem nunca.

Destinos possíveis (§7.2 do planning, na ordem de fallback):
`generativelanguage.googleapis.com`, `api.groq.com`, `openrouter.ai`,
`api.mistral.ai`, `api.cerebras.ai`.

O conteúdo enviado é montado **só** em `domain/AiPromptBuilder.kt` — é o único
arquivo que decide o que a IA recebe. O que entra:

- **Números já agregados e calculados localmente**: folga do mês, capacidade de
  poupança, progresso de um objetivo, saldo e taxa de uma dívida, total gasto
  por categoria. Nunca a lista de lançamentos.
- **Rótulos escolhidos pelo próprio usuário**: nome de um objetivo ("Portugal —
  10 dias"), nome de uma dívida ("Rotativo Nubank"), nome de categoria.
- **A pergunta que o usuário digitou no chat**, mais até 8 mensagens anteriores
  da mesma conversa.
- **Na importação**: estabelecimento normalizado + valor + dia/mês, no formato
  `3. mercado sao joao — R$ 189,90 em 12/03`.
  `MerchantClassifier.normalizeMerchant` remove prefixo de adquirente, sufixo de
  razão social e **qualquer sequência de 4+ dígitos** — que é o que carregaria
  final de cartão ou código de loja. Há teste que falha se isso regredir
  (`ImportAnalyzerTest`).

O que **não** entra, por construção: nome completo, CPF, número de conta ou
cartão, o documento importado, o nome do arquivo importado, a linha original da
fatura, qualquer imagem. Os três primeiros nem existem no modelo de dados
(`planning.md` §8) — não há campo para eles.

## 3. Minimização de chamadas

Menos chamada é menos dado saindo, além de ser o que faz o app caber no free
tier (`planning.md` §6):

- Cálculo é sempre local e determinístico (`domain/`). A IA só redige texto
  sobre um resultado que já existe.
- Importação usa no máximo **3 chamadas por arquivo** (uma por tipo de pergunta,
  em lote de até 25 itens), e só para os itens que a regra local não resolveu.
- A rotina diária (`FinanceCheckWorker`) faz no máximo **2 chamadas por
  execução**, e **zero** num dia sem nada relevante — o roteador nem é
  instanciado.
- Respostas idênticas no mesmo dia vêm de `AiResponseCache` (memória), sem nova
  requisição.

## 4. Permissões

Duas declaradas pelo app: `INTERNET` (as chamadas de IA acima) e
`POST_NOTIFICATIONS` (Fase 5; negada, o app funciona por completo).
Mais quatro herdadas do WorkManager, todas *normal permissions* que não dão
acesso a dado nenhum — a lista completa e o porquê de cada uma estão comentados
no `AndroidManifest.xml`.

Não há permissão de armazenamento: a importação usa o Storage Access Framework,
em que o usuário escolhe um arquivo por vez.

## 5. Transporte

Todo tráfego é HTTPS, com `cleartextTrafficPermitted="false"` declarado
explicitamente em `res/xml/network_security_config.xml`. A chave de API vai em
header (`x-goog-api-key` no Gemini, `Authorization: Bearer` nos demais), nunca
em query string — o que a manteria fora de logs de proxy e de mensagens de
exceção.

## 6. Logs

`util/FinaiLog.kt` é o único lugar do app que escreve no logcat. Em release,
diagnósticos de fluxo somem (guardados por `BuildConfig.DEBUG`, que o R8 resolve
como constante) e avisos/erros saem sem stack trace, sem corpo de resposta e sem
mensagem de exceção — só a classe da exceção. Corpo de resposta de provedor só é
logado em debug, e ainda assim passa por `redactKeys`.

Motivo: o logcat de um aparelho é legível por ferramentas de suporte e vai junto
em bug report do sistema.

## 7. Limitação conhecida e assumida

Chamar a API de IA direto do app significa que a chave está no aparelho e pode
ser extraída por quem tiver acesso físico ou root — nenhuma ofuscação resolve
isso, e `planning.md` §7.4 já registra esse risco como aceito, com três
mitigações: chave só em armazenamento cifrado, R8 ligado, e monitorar o uso da
chave no painel do provedor. A chave é **do usuário**, não do desenvolvedor:
quem a extrai consome a cota gratuita dele, não a de terceiros.

Se isso virar um problema real depois do lançamento, a saída registrada no
planning é intermediar as chamadas por uma função serverless gratuita — sem
mudar o resto da arquitetura.
