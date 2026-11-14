# Crash na abertura e ícone antigo — 14/11/2026

## Causa confirmada no aparelho

O logcat do Samsung SM-S918B mostrou `Room cannot verify the data integrity`:
hash esperado `787505c6742a9e8abef84d582f9f6fe0`, encontrado
`494b9b007c0b3519393b9d2289c5fa62`. Uma cópia do banco confirmou
`PRAGMA user_version = 20`, mas estrutura e hash da versão 19: faltavam
`mensagens_chat.estrutura` e as quatro tabelas do novo assistente.

A migração 19→20 não roda quando o banco já está marcado como 20. Não foi
possível determinar qual instalação anterior avançou apenas o número da versão.
Limpar o cache não altera esse estado do banco.

## Correção

- Room v21 e `MIGRATION_20_21` registradas na cadeia compartilhada.
- Se `mensagens_chat.estrutura` ainda não existe, executa o DDL da migração 19→20.
- Bancos v20 completos não recebem alterações nas tabelas. O Room valida o
  resultado e atualiza o hash dentro da sua transação de migração.
- Schema v21 exportado pelo KSP, com a mesma estrutura da v20 completa.
- Sem apagar dados, editar o hash manualmente ou desinstalar o app.

## Evidências

- `assembleDebug`, `assembleDebugAndroidTest` e 629 testes JVM passaram.
- Cinco testes instrumentados passaram no Samsung: os dois de
  `Migration20To21Test` e os testes de 17→18, 18→19 e 19→20.
- A regressão reproduz schema/hash 19 com `user_version=20` e abre pelo Room;
  também verifica a preservação de um banco 20 completo com dados do assistente.
- APK debug instalado por atualização e app completo aberto com os dados reais.
- Comparação das 16 tabelas de dados antigas antes/depois: todas as linhas e
  colunas preexistentes iguais. `PRAGMA integrity_check = ok`, versão final 21.
- `assemblePessoal` passou, incluindo R8 e lint vital.
- APK pessoal instalado por atualização: três aberturas com processo novo passaram,
  sem novas linhas de crash no logcat. Tela Hoje conferida no aparelho.

Backups e capturas do aparelho ficaram fora do repositório, em
`C:\Users\joaov\Documents\Codex\FinAI-recovery\2026-11-14`.

## Ícone do launcher

O recurso do APK carregado diretamente mostrava a folha correta, enquanto
`PackageManager.getApplicationIcon`, `getActivityIcon` e `LauncherApps.getIcon`
devolviam o desenho do Android. O Theme Park estava ativo e tinha uma imagem
por pacote em `/data/overlays/themepark/icons/com.finai.app.png`, datada de
12/09/2026. Essa substituição existe fora dos dados do FinAI e sobrevive à
reinstalação. Alterar versionCode ou limpar cache do app não corrige a imagem
fixada no pacote de ícones.

Ajuste no aparelho: Theme Park → Icon → pacote ativo → Edit → escolher app →
FinAI com a folha → Reset. Salvar uma cópia (`glasssfinai`, conservando o pacote
original `glasss`) e aplicar. Nenhuma mudança nos recursos do ícone do APK.
O pacote corrigido foi aplicado e a folha foi conferida na busca do launcher,
inclusive depois de instalar o APK pessoal.

## Complemento: painéis do assistente fechavam o app

Após corrigir a abertura, abrir "Sei sobre você e acompanho" ou o histórico
do assistente causava outro crash, `IndexOutOfBoundsException: Index -1 out of
bounds for length 0`. O mapping do APK pessoal e a reprodução em debug apontaram
para `androidx.compose.runtime.Stack.pop` / `ComposerImpl.exitGroup`.

Em `ChatOverlay`, os dois painéis encerravam antecipadamente a lambda inline
do `Column` com `return@Column`. Ao trocar o conteúdo, isso desbalanceava os
grupos de composição. A correção usa `if / else if / else` para compor memória,
histórico ou conversa, sem esses retornos.

`ChatOverlayNavigationTest` reproduziu o crash antes da correção nos dois testes
e passou depois dela no Samsung. Cobre dados existentes, painéis vazios,
alternância repetida, volta à conversa, manutenção do rascunho e abertura de
uma conversa do histórico. Não envia mensagens nem altera dados financeiros.

`assembleDebug`, `assembleDebugAndroidTest` e `assemblePessoal` passaram.
APK pessoal atualizado por cima do existente para validar os dois painéis
com os dados reais do aparelho.
No APK pessoal, dois ciclos de abrir/fechar memória e histórico passaram, assim
como Voltar do Android em ambos os painéis. O processo permaneceu o mesmo e o
logcat não registrou novos crashes durante a validação. Capturas e roteiro
reproduzível em `FinAI-recovery\2026-11-14\assistant-crash`, fora do repositório.
