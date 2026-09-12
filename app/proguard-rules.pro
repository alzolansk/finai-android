# Regras do R8 para o build de release (Fase 6 — endurecimento, planning.md §9).
#
# O app não usa nenhum framework de reflexão sobre os seus próprios tipos:
# não há Gson/Moshi/Jackson (o JSON das APIs de IA é montado e lido à mão com
# org.json), nem injeção por reflexão. Por isso a regra geral é "deixe o R8
# ofuscar tudo" — as exceções abaixo são pontuais e cada uma tem um motivo
# concreto de quebrar em tempo de execução se faltar.

# ── Stack trace legível ────────────────────────────────────────────────────
# Sem isto, um crash em produção vira uma pilha sem arquivo nem linha. O
# mapping.txt gerado em app/build/outputs/mapping/release/ é o que traduz os
# nomes ofuscados de volta — guarde-o junto de cada versão publicada
# (ver RELEASE.md).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Anotações e assinaturas genéricas: Room e Compose dependem delas em runtime.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ── WorkManager ────────────────────────────────────────────────────────────
# FinanceCheckWorker é instanciado por REFLEXÃO, a partir do nome de classe que
# o WorkManager gravou no banco dele quando o trabalho foi agendado. Se o R8
# renomear a classe, um agendamento já persistido (inclusive de uma versão
# anterior do app, que é o caso normal depois de um update) deixa de resolver e
# a rotina proativa da Fase 5 simplesmente para de rodar — sem crash, sem log,
# o pior tipo de falha. A regra abaixo cobre qualquer Worker do app.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Room ───────────────────────────────────────────────────────────────────
# O Room gera as implementações dos DAOs e do Database e as carrega pelo nome
# ("FinaiDatabase_Impl"). As bibliotecas do Room já trazem regras próprias;
# estas são explícitas para não depender de mudança de consumer-rules numa
# atualização futura da dependência.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── Tink / EncryptedSharedPreferences (chaves de API, planning.md §7.4) ────
# O Tink resolve primitivas criptográficas por nome de classe registrado. Se o
# R8 remover ou renomear uma delas, AiKeyStore falha ao abrir o arquivo e o app
# se comporta como se nenhuma chave estivesse configurada — degradando em
# silêncio justamente a funcionalidade que o usuário acabou de configurar.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ── ML Kit Text Recognition (OCR da importação, Fase 4) ────────────────────
# Carrega os módulos de reconhecimento via Play Services/registro por nome.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**

# ── Coroutines ─────────────────────────────────────────────────────────────
# kotlinx-coroutines já traz consumer-rules; estas evitam avisos de classes
# opcionais (debug agent, service loader do Main dispatcher) ausentes no APK.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Compose ────────────────────────────────────────────────────────────────
# O runtime do Compose traz as próprias regras. Nada específico do app é
# necessário aqui: não há Composable alcançado por reflexão.

# ── org.json ───────────────────────────────────────────────────────────────
# Faz parte do framework Android (não é empacotado), então não há o que manter.

# ── O que deliberadamente NÃO tem regra ────────────────────────────────────
# domain/, state/, ui/ e os data classes de modelo podem ser ofuscados e
# encolhidos à vontade — nada os alcança por nome. Se um dia entrar uma
# biblioteca de serialização por reflexão, ELA precisará de keep rules aqui, e
# não o contrário.
