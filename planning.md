# FinAI Android · Planning

Documento de levantamento de requisitos e planejamento para transformar o protótipo `FinAI Mobile.dc.html` (Claude Design) em um app Android nativo. Baseado no protótipo navegável, no transcript de design em `chats/chat1.md` e nas decisões abaixo, confirmadas antes de escrever este documento:

- **Stack:** Kotlin + Jetpack Compose (nativo, só Android, sem plano de iOS).
- **Chamadas de IA:** feitas diretamente do app às APIs dos provedores, sem backend intermediário.
- **Escopo deste documento:** requisitos funcionais e não funcionais, estratégia de IA gratuita e roadmap por fases.

---

## 1. Contexto

O app existe hoje como um site rodando localmente, usando a API do Gemini para gerar os insights financeiros. O uso ao longo do tempo levantou uma dúvida real: o limite diário gratuito do Gemini pode não sustentar o volume de chamadas que o app faz (chat, leitura de objetivos, veredito do simulador, decisões sugeridas, coach comportamental, importação de fatura). A migração para Android nativo é a oportunidade de resolver isso de forma estrutural, não só trocar de plataforma.

O princípio de privacidade que já aparece no protótipo ("Extrai os lançamentos sem enviar o PDF para fora do aparelho", tela de Importar) deve virar um requisito geral do produto: dados financeiros ficam no aparelho por padrão, e só o mínimo necessário é enviado para IAs externas.

## 2. Escopo

**Dentro do escopo:**
Todas as telas e fluxos do protótipo: Início, Agenda, Objetivos, Dívidas, Orçamentos/Limites, Importar fatura, Assistente (chat), Simulador "Posso comprar?", notificações da IA, menu de ação rápida (FAB).

**Fora do escopo (por enquanto):**
- iOS.
- Sincronização em nuvem / múltiplos aparelhos / login.
- Conexão automática com bancos (open finance). A entrada de dados é manual ou por importação de fatura (PDF, planilha, foto).
- Backend próprio. Todas as chamadas de IA partem do app.

## 3. Levantamento de requisitos funcionais

Requisitos extraídos tela a tela do protótipo, com a lógica de cada `onClick`/estado do `support.js` traduzida em comportamento esperado.

### 3.1 Início (Home)
- Saudação e resumo do que está pendente no mês.
- Cartões de **Objetivos** (2 principais + atalho para "ver todos"): nome, prazo, valor guardado vs meta, barra de progresso, nota contextual.
- Cartão **"Pode gastar hoje"**: saldo seguro diário calculado a partir de renda, contas fixas e metas already comprometidas; anel indicando dias restantes até o fim do mês; atalhos para o simulador de compra e para ajustar limites.
- **Decisões para você**: lista de sugestões (negociar dívida, cancelar assinatura, destinar 13º/bônus etc.), cada uma com motivo, impacto estimado, e ações de aceitar/adiar. Alternável entre três formatos de apresentação (Cartões, Feed, Chat) sem mudar o conteúdo.
- **Linha do tempo do ano**: entradas extras previstas (13º, bônus, restituição) e o que ainda não tem destino definido.
- **Próximos 7 dias**: lista curta de contas a vencer/receber, com atalho para a Agenda completa.
- **Coach de comportamento** (opcional, configurável): padrão identificado no gasto (ex.: delivery em dias de trabalho tarde), com atalho para aprofundar no chat.

### 3.2 Agenda
- Navegação por mês.
- Totais do mês: a pagar vs a receber, com contagem de itens.
- Dica contextual da IA sobre ordem de pagamento (ex.: evitar rotativo do cartão).
- Lista de contas com status (pago, pendente, atrasado, vence hoje, previsto).

### 3.3 Objetivos (Metas)
- Capacidade de poupança mensal calculada (renda menos gastos fixos e parcelas).
- Lista completa de objetivos (viagem, compra, reserva) com progresso, badge de status (no ritmo / reavaliar / prioridade), leitura da IA sobre o objetivo (conflito com outra meta, efeito de direcionar um bônus, etc.), ação principal (ex. "direcionar bônus", "adiar 3 meses") e atalho para simular no chat.

### 3.4 Dívidas
- Total em aberto, juros por mês, previsão de "livre de dívida".
- Ordem de ataque sugerida por custo do juro (não por valor da dívida), com barra de progresso e taxa de cada dívida.
- Roteiro de negociação passo a passo para uma ligação real (o que dizer, o que pedir, alternativa de proposta).
- Atalho para "ensaiar a ligação" no chat.

### 3.5 Orçamentos / Limites
- Barra de progresso por categoria (alimentação, transporte, lazer, assinaturas, saúde) com nota de projeção (ex.: "no ritmo atual fecha X acima").
- Lista de assinaturas que a IA identificou como pouco usadas, com ação de cancelar/manter.

### 3.6 Importar fatura
- Upload de PDF, planilha ou foto.
- Etapas visíveis do processamento: extração, classificação por categoria, detecção de assinatura/parcela, separação de duplicados para confirmação manual.
- Resultado: lançamentos prontos para revisar antes de entrar na Agenda.

### 3.7 Simulador "Posso comprar?"
- Valor da compra (presets + valor livre).
- Veredito (cabe / cabe mas custa tempo / não cabe) com explicação.
- Efeito da compra na folga do mês, na data do próximo objetivo e na cobertura da reserva de emergência.
- Atalho para perguntar mais no chat.

### 3.8 Assistente (Chat)
- Conversa livre sobre as finanças do usuário, com sugestões de pergunta prontas.
- Respostas devem citar números reais do usuário (extrato, metas, dívidas), não genéricos.
- Indicador de "pensando" enquanto a resposta é gerada.

### 3.9 Notificações
- Lista de avisos proativos (fatura vencendo, categoria perto do limite, entrada extra confirmada).

### 3.10 Ação rápida (FAB)
- Lançar gasto manual (texto ou voz).
- Importar fatura.
- Abrir o simulador.
- Criar novo objetivo.

## 4. Requisitos não funcionais

- **Idioma e formato:** pt-BR, valores em R$ com formatação brasileira (igual ao protótipo).
- **Privacidade em primeiro lugar:** dados financeiros do usuário (lançamentos, saldos, dívidas, nome) permanecem no aparelho por padrão (armazenamento local). Documentos importados (PDF/foto de fatura) são processados no aparelho sempre que possível; nada de upload de documento inteiro para terceiros.
- **Minimização de dados enviados à IA:** ao chamar qualquer provedor de IA externo, enviar o mínimo necessário (números agregados e texto da pergunta), nunca o documento original, nome completo, CPF ou números de conta.
- **Funcionamento offline parcial:** todas as telas com dados já carregados (Início, Agenda, Objetivos, Dívidas, Orçamentos) devem funcionar sem internet; só as funções que dependem de IA (chat, importação, insights novos) exigem conexão.
- **Resiliência a indisponibilidade de IA:** se todos os provedores de IA estiverem sem cota ou fora do ar, o app continua funcional (números e regras locais seguem calculados), só o texto explicativo da IA fica indisponível com uma mensagem clara.
- **Custo zero de operação:** nenhuma dependência paga obrigatória; tudo com camada gratuita.
- **Acessibilidade básica:** contraste adequado, áreas de toque grandes o suficiente (já refletido no protótipo), suporte a fonte do sistema.
- **Zero-cost hard limit:** o aplicativo não deve depender de nenhum recurso pago nem realizar fallback automático para tiers pagos. Todas as integrações devem operar exclusivamente em planos/modelos gratuitos. Ao esgotar todas as cotas gratuitas, funcionalidades determinísticas continuam disponíveis e recursos de IA entram em degradação graciosa até a renovação das cotas.
## 5. Arquitetura técnica proposta

- **UI:** Jetpack Compose, Material 3, seguindo a linguagem visual do protótipo (fundo `#fafafa`, cartões brancos, cartão escuro `#18181b`, verde `#10b981`, ilha de navegação escura flutuante).
- **Padrão de app:** MVVM (`ViewModel` + `StateFlow`) por tela, espelhando o `state`/`renderVals()` do `support.js` do protótipo.
- **Persistência local:** Room (SQLite) para lançamentos, contas, objetivos, dívidas, orçamentos e histórico de chat. É a fonte de verdade dos números do app.
- **Preferências e chaves de API:** `DataStore` (chaves de API dos provedores de IA guardadas com `EncryptedSharedPreferences`/Android Keystore, nunca em texto puro).
- **Rede:** Retrofit ou Ktor Client para chamar as APIs de IA.
- **Trabalho em segundo plano:** `WorkManager` para a rotina diária que recalcula saldo seguro, verifica orçamentos e decide se vale gerar uma notificação nova.
- **OCR de fatura:** ML Kit Text Recognition (on-device, gratuito, sem limite de uso) para extrair texto de foto/PDF antes de qualquer chamada de IA.

## 6. Princípio central: IA para linguagem, cálculo é local e determinístico

Este é o ponto que mais ajuda a resolver o problema de limite gratuito: hoje, se toda a "inteligência" do app depende de uma chamada de IA, cada tela vira uma chamada de API. A recomendação é inverter isso.

- **Sempre calculado localmente em Kotlin, sem IA:** saldo seguro do dia, progresso de metas, capacidade de poupança, juros e ordem de ataque das dívidas, progresso de orçamento, detecção de assinatura pouco usada, status de conta (atrasada/pendente/paga). São operações determinísticas sobre os dados do usuário; usar IA para isso custaria cota gratuita à toa e ainda arriscaria erro de conta feito por um modelo de linguagem.
- **Só passa pela IA:**
  - Redigir a explicação em linguagem natural de um resultado já calculado (ex.: o texto do veredito do simulador, a "leitura da IA" de um objetivo, o texto de uma notificação).
  - Conversa livre no chat.
  - Classificar categoria de um lançamento importado quando a regra local não reconhece o estabelecimento.
  - Gerar o roteiro de negociação de dívida.

Isso reduz drasticamente o número de chamadas por sessão de uso e torna viável caber no free tier de qualquer provedor.

## 7. Estratégia de IA: múltiplos provedores gratuitos

### 7.1 Por que não depender só do Gemini
O Gemini gratuito (AI Studio) segue sendo uma opção válida e de boa qualidade, mas um único provedor grátis é um ponto único de falha: se o limite diário estourar, todo o app perde a camada de linguagem/insight no mesmo instante. A solução é um roteador de provedores com fallback automático dentro do próprio app.

### 7.2 Provedores gratuitos sugeridos (para chamada direta do app)

| Provedor | Uso sugerido no app | Por que entra | Cuidado |
|---|---|---|---|
| **Google Gemini API (AI Studio)** | Provedor principal para tudo, incluindo leitura de imagem/PDF na importação de fatura | Já em uso, suporta texto e visão, boa qualidade em português | Limite diário e por minuto existe e pode mudar sem aviso; confirmar valores atuais antes de codar o roteador |
| **Groq** | Chat e textos curtos (veredito do simulador, decisões, notificações) | Inferência muito rápida, modelos abertos (Llama, Gemma) com cota diária gratuita generosa | Não é bom para o passo de leitura de imagem/PDF; usar só para texto |
| **OpenRouter** (modelos marcados `:free`) | Camada extra de fallback, cobre vários modelos abertos com uma única chave | Se um modelo/provedor específico esgota a cota do dia, dá para trocar de modelo sem trocar de integração | Cada modelo `:free` tem limite próprio, geralmente mais apertado que Gemini/Groq |
| **Mistral (La Plateforme)** | Fallback secundário para texto | Modelos pequenos de boa qualidade, tem camada gratuita | Cota mais modesta, tratar como reserva |
| **Cerebras Cloud** | Fallback para texto quando os anteriores estourarem | Inferência rápida, modelos Llama com cota diária gratuita | Catálogo de modelos mais limitado |

Deixar de fora: provedores cuja "camada gratuita" é na prática um trial de cartão de crédito com validade curta (ex.: créditos iniciais que expiram), porque isso não resolve o problema de sustentar o uso no longo prazo, só adia.

**Importante:** limites de free tier mudam com frequência e sem aviso prévio dos provedores. Antes de implementar o roteador (Fase 3 do roadmap), confirmar os limites atuais direto na documentação de cada provedor, não confiar em número fixo neste documento.

### 7.3 Estratégia de roteamento
- Cada provedor tem um adaptador com a mesma interface (`enviarPrompt(tarefa, contexto): Resposta`).
- Um controller local mantém, por provedor, quantas chamadas já foram feitas hoje (`DataStore`/Room), reiniciando à meia-noite.
- Ordem de tentativa: Gemini (texto e visão) → Groq (texto) → OpenRouter (texto) → Mistral (texto) → Cerebras (texto). Se todos estourarem a cota, o app mostra o número calculado localmente sem o texto explicativo, com uma mensagem tipo "explicação da IA indisponível hoje".
- Cache simples de respostas para perguntas/textos repetidos no mesmo dia (ex.: mesma pergunta do simulador com o mesmo valor), evitando gastar cota com a mesma chamada duas vezes.
- Tarefas de visão (leitura de fatura) ficam restritas ao Gemini, já que os demais provedores da lista não têm um modelo de visão gratuito confiável; o OCR local (ML Kit) já reduz bastante essa dependência ao extrair o texto antes de qualquer chamada de IA.

### 7.4 Riscos de chamar a IA direto do app (sem backend)
Esta foi a opção escolhida, então os riscos abaixo entram como itens de hardening, não como bloqueio:
- **Chave de API exposta:** qualquer chave embutida no APK pode ser extraída por engenharia reversa. Mitigar com: chave nunca em texto puro no código-fonte (usar Android Keystore/`EncryptedSharedPreferences`), ofuscação com R8/ProGuard, e monitorar uso de cada chave no painel do provedor para detectar abuso.
- **Abuso de cota por terceiros** (se a chave vazar, alguém pode consumir a cota gratuita do usuário): considerar, se isso se tornar um problema real após o lançamento, migrar para um backend leve (ex.: uma função serverless gratuita) só para intermediar as chamadas de IA, sem mudar o resto da arquitetura.
- **Dados enviados a terceiros:** reforça o requisito da seção 4 de minimizar o que é mandado no prompt.

## 8. Modelo de dados (entidades principais, Room)

- `Transacao` (id, data/hora original, descrição, valor, categoria, conta/cartão de origem, recorrente: bool, origem: manual/importado, faturaId opcional). A data/hora original não é substituída pelo vencimento da fatura, para sustentar análises de comportamento futuras.
- `FaturaCartao` (id, contaId, referência de banco/cartão, fechamento opcional, vencimento). Cada fatura cria uma única `Conta` a pagar na Agenda, cujo valor é a soma dos itens válidos vinculados.
- `Conta` (id, nome, valor, vencimento, status, tipo: a pagar/a receber, recorrente: bool)
- `Objetivo` (id, tipo, nome, valorAlvo, valorGuardado, prazo, prioridade)
- `Divida` (id, nome, valorAberto, taxaJurosMensal, parcelasRestantes, valorParcela)
- `OrcamentoCategoria` (categoria, limiteMensal, mêsReferência)
- `Assinatura` (nome, valor, últimoUso, status: ativa/cancelada)
- `MensagemChat` (id, papel: usuário/IA, texto, timestamp)
- `UsoProvedorIA` (provedor, data, quantidadeChamadasHoje)

## 9. Roadmap por fases

**Fase 0 · Fundamentos — ✅ concluída** (ver CLAUDE.md → Status atual para detalhes)
Projeto Android configurado (Compose, Navigation, Room, DataStore), design system com os tokens de cor/tipografia extraídos do protótipo, todas as telas navegáveis com dados fixos de exemplo (iguais ao protótipo), sem lógica real ainda.

**Fase 1 · MVP sem IA — ✅ concluída** (ver CLAUDE.md → Status atual para detalhes)
Entrada manual de lançamentos, contas, objetivos e dívidas. Todos os cálculos determinísticos funcionando de verdade: saldo seguro do dia, progresso de metas, capacidade de poupança, juros e ordem de dívidas, progresso de orçamento. O app já entrega valor sem depender de nenhuma cota de IA.

**Fase 2 · Camada de IA com um provedor — ✅ concluída** (ver CLAUDE.md → Status atual para detalhes)
Integração com Gemini para: chat do assistente, "leitura da IA" dos objetivos, veredito do simulador, texto das decisões sugeridas, roteiro de negociação de dívida. Adaptador de IA já desenhado por trás de uma interface única, mesmo usando só um provedor por enquanto.

**Fase 3 · Multi-provedor e resiliência — ✅ concluída** (ver CLAUDE.md → Status atual para detalhes)
Roteador com fallback entre os provedores da seção 7.2, controle local de cota diária por provedor, cache de respostas repetidas, e o comportamento de degradação graciosa quando todos os provedores estourarem a cota do dia.

**Fase 4 · Importação de fatura — ✅ concluída** (ver CLAUDE.md → Status atual para detalhes)
OCR on-device (ML Kit) + parsing determinístico de valores/datas + chamada de IA só para classificar categoria de itens ambíguos e detectar duplicados/assinaturas recorrentes.

**Fase 5 · Notificações proativas e coach comportamental — ✅ concluída** (ver CLAUDE.md → Status atual para detalhes)
Rotina diária via `WorkManager`: recalcula tudo localmente e só aciona a IA para redigir o texto da notificação quando o cálculo local indicar algo relevante (evita gerar notificação, e gastar cota, todo dia à toa).

**Fase 6 · Endurecimento — ✅ concluída no escopo decidido (uso pessoal, sem
publicação na Play Store)** (ver CLAUDE.md → Status atual)
Ofuscação e proteção das chaves de API, revisão de privacidade (o que sai do
aparelho vs o que fica), validação em aparelho físico.

**Decisão (26/09/2026): o app não vai ser publicado na Play Store** — é para
uso pessoal, instalado direto no aparelho. Isso reduz o escopo da Fase 6: tudo
que só existe por exigência da loja (keystore de assinatura de produção,
`targetSdk` mínimo da Play, ficha/imagens/formulário de segurança de dados,
política de privacidade publicada em URL pública) **não é necessário** e não
será feito. `RELEASE.md` e `play-store/` continuam no repo como referência,
caso essa decisão mude no futuro, mas não fazem parte do caminho atual.

O que ficou pronto e é o que importa para uso pessoal: R8/ProGuard ligados e
validados em release rodando, chave de API fora da query string e fora do
logcat, migrações de Room reais (o `fallbackToDestructiveMigration` saiu),
permissões revisadas e documentadas, `cleartextTrafficPermitted=false`, backup
automático desligado, escritas de banco e chamadas de IA sem caminho de
crash, e a validação em aparelho físico (feita pelo usuário em 26/09/2026 —
feedback detalhado ainda pendente de registro aqui).

Instalar para uso pessoal não precisa de keystore de produção nem de
`targetSdk` além do atual: `./gradlew assembleDebug` já gera um APK
instalável via `adb install`; `./gradlew assembleRelease` também funciona sem
`keystore.properties` (gera APK não assinado, dá para assinar com qualquer
keystore local via `apksigner` se quiser o benefício do R8 no dia a dia).

**Fase 7 · Clareza e confiança (branch `v2-clareza`, iniciada em 27/09/2026) — 🚧 em andamento**

Origem: crítica de produto/UX recebida em 27/09/2026 (análise do código e de capturas
antigas, sem sessão de uso no aparelho). Tese: o app comunica mais complexidade do que
precisa; a próxima evolução é **Home, legibilidade e confiança no valor disponível**,
não funcionalidade nova. Posicionamento: *planejador do dinheiro até a próxima entrada*.

Preservar: "Pode gastar hoje" e "Até o próximo salário"; Agenda + faturas + objetivos
conectados; revisão de importação com duplicatas; IA contextual (discutir decisão,
ensaiar negociação); identidade sóbria (branco, grafite, verde).

Itens, em ordem de prioridade:

1. ✅ **[Crítica] Um único veredito de disponibilidade.** Hoje "Pode gastar hoje" usa a sobra
   total do ciclo (`SafeToSpendCalculator.fromCycle`) enquanto `PayCycle.shortfall` pode
   apontar falta numa data intermediária — o usuário vê "pode gastar" e "vai faltar" ao
   mesmo tempo. A disponibilidade por data passa a comandar o resumo: com falta prevista,
   o valor gastável é zero e a falta (quanto, quando, qual compromisso causa) aparece antes
   de qualquer sugestão. Valor gastável = menor saldo corrido projetado até o salário, não
   a sobra final.
2. **[Alta] "Entenda este valor".** Decomposição simples do "Pode gastar hoje": período
   considerado, o que já entrou, o que está previsto, compromissos descontados, reserva de
   metas, o que falta cadastrar, e o aviso explícito de que não é saldo bancário e que a
   sobra do ciclo anterior não é carregada.
3. **[Alta] Home por urgência:** situação atual (uma frase + um valor com período
   explícito + uma ação pertinente) → próximos vencimentos → planejamento (balanço do mês,
   objetivos, decisões, linha do tempo do ano) com peso visual menor.
4. **[Alta] Legibilidade e contraste.** Corpo 14–16 sp, navegação 12–13 sp, poucos tamanhos
   (valor principal, título, corpo, legenda); Inter no app inteiro (hoje só no lançamento).
   `TextMuted #A1A1AA` (2,56:1 sobre branco) e texto branco sobre `Emerald #10B981`
   (2,54:1) sobem para ≥ 4,5:1. Alvos de toque ≥ 48 dp.
5. **[Alta] Renda principal explícita** em vez da palavra "salário" na descrição
   (`PayCycle.isSalary`) — é a pendência (a) já combinada, que agora vira a migração Room 9→10 (7→8 e 8→9 já foram usadas pelas conversas do chat e pelas
   metas concluídas), marcando como renda principal o que já casa
   com `isSalary`.
6. **[Alta] Onboarding mínimo.** Tour + assistente inicial de sete estados pedem trabalho
   antes do benefício. Pedir só o necessário para a primeira resposta útil (renda
   principal e data, compromissos próximos) e completar o resto progressivamente.
7. **[Média] Menos caixas, mais hierarquia; cor com significado estável** (vermelho =
   problema, âmbar = atenção, roxo só como auxiliar; IA sem superfície chamativa);
   movimentações em lista simples, cartões só para resumos e decisões.
8. **[Média] Ações reconhecíveis.** "Editar", "Ver todos", "Paguei a parcela" com área de
   toque real; destrutivas ("Excluir") num menu secundário.
9. **[Média] Estados vazios que orientam** (ação concreta: lançar conta, importar fatura) e
   distinção visual entre zero real, dado não cadastrado e estimativa.
10. **[Média] Navegação previsível:** acesso direto a gastos/limites (pendência (c) — Limites
    fora da barra) e ações frequentes explícitas em vez de tudo no "+".
11. **[Média] Importação:** total selecionado + confirmar fixos no rodapé; filtro
    "Precisa revisar".
12. **[Expansão] Exportação/restauração de dados** (arquivo controlado pelo usuário —
    responde a pergunta do §11 sobre backup) e ativação de IA mais simples.

Depois da fase (não agora): previsão por data com "e se eu adiar/antecipar", simulação
ligada ao prazo das metas, renda variável/múltiplos recebimentos, sincronização.
Adiado explicitamente: investimentos, marketplace, telas novas.

Critério de validação: sem ajuda, a pessoa responde rápido e certo **"Quanto posso
gastar?"**, **"O que vence primeiro?"** e **"Por que o app chegou a esse valor?"**.

## 10. Critérios de aceite por fase (resumo)

- **Fase 1 pronta quando:** os números de saldo seguro, orçamento e dívidas batem com uma planilha de conferência manual para os mesmos dados de entrada, sem nenhuma chamada de IA envolvida.
- **Fase 2 pronta quando:** todas as interações de IA do protótipo (chat, leitura de objetivo, veredito do simulador, decisões, roteiro de negociação) funcionam com o Gemini e citam números reais do usuário, não texto genérico.
- **Fase 3 pronta quando:** ao simular esgotamento de cota do provedor principal (bloqueando a chave em teste), o app troca automaticamente para o próximo provedor sem o usuário perceber erro, e mostra a mensagem de degradação graciosa apenas quando todos estiverem esgotados.
- **Fase 4 pronta quando:** uma fatura real (PDF ou foto) é importada, categorizada e as duplicatas ficam sinalizadas para revisão, sem o arquivo original sair do aparelho.
- **Fase 5 pronta quando:** o app roda um dia inteiro sem estar aberto e gera no máximo as notificações relevantes daquele dia, não uma por hora.
- **Fase 7 pronta quando:** "Pode gastar hoje" nunca aparece positivo junto de uma falta prevista no mesmo ciclo; o valor tem decomposição acessível em um toque; nenhum texto útil fica abaixo de 4,5:1 de contraste; e as três perguntas de validação da Fase 7 se respondem na primeira tela.

## 11. Perguntas em aberto

- O usuário final é só você (single-user, sem conta/login) ou o app deve prever múltiplos perfis no mesmo aparelho?
- Existe hoje algum dado real (lançamentos, dívidas, faturas) do site atual que precisa ser migrado para o app, ou o app Android começa do zero?
- Backup dos dados locais (export/import manual de um arquivo, por exemplo) é necessário já no MVP, ou pode ficar para uma fase posterior?
  - **Decisão provisória tomada na Fase 6, aguardando sua confirmação:** o backup
    automático do Android ficou **desligado** (`android:allowBackup="false"`),
    porque com ele ligado o `finai.db` inteiro — todo o histórico financeiro —
    seria copiado para a conta Google do usuário, contrariando o §4 ("dados
    permanecem no aparelho por padrão"). O preço é real e está assumido: **trocar
    de aparelho hoje perde todos os dados**, já que não existe export manual.
    Os arquivos `backup_rules.xml`/`data_extraction_rules.xml` já estão escritos
    e completos, então religar é trocar um atributo. Se a resposta for "quero
    backup", o caminho recomendado é implementar export/import de um arquivo
    (controlado pelo usuário) antes de reabilitar o backup automático.
