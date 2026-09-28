import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Assinatura de release (Fase 6, planning.md §9). O keystore e suas senhas
// NUNCA entram no repositório: este bloco lê `keystore.properties` na raiz do
// projeto, que está no .gitignore. Veja `keystore.properties.example` e
// RELEASE.md para como gerar o seu.
//
// Sem esse arquivo o build de release continua funcionando (sai sem assinar),
// para que `assembleRelease` possa ser validado em CI ou por outra pessoa sem
// acesso ao keystore — só não dá para publicar o artefato resultante.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile")?.isNotBlank() == true

// versionCode = número de commits. Com ele fixo em 1, instalar um APK novo por
// cima do antigo não contava como versão nova para o launcher: vários (o da
// Samsung em especial) guardam o ícone em cache por pacote + versionCode, e o
// ícone novo da folha não aparecia nem depois de reiniciar o celular. Sem git
// disponível (ex.: cópia do código sem .git), cai para 1.
val gitCommitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "com.finai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.finai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = gitCommitCount
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // O app é pt-BR only (planning.md §4): todo texto de tela está em
        // português no próprio código. Limitar os recursos de idioma corta as
        // dezenas de traduções que Compose/Material/Play Services arrastam
        // para dentro do APK sem que nenhuma delas seja alcançável aqui.
        // "en" fica como fallback do sistema para strings de framework.
        resourceConfigurations += listOf("pt", "pt-rBR", "en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Só o build de debug entra no logcat com os diagnósticos de fluxo
            // (ver util/FinaiLog.kt) — em release o R8 remove esses trechos
            // porque BuildConfig.DEBUG vira constante `false`.
            isMinifyEnabled = false
        }
        release {
            // Fase 6, planning.md §7.4: ofuscação com R8 é uma das mitigações
            // declaradas para "chave de API pode ser extraída do APK". Ela não
            // torna a extração impossível (nada torna, num app que chama a API
            // direto), mas remove os nomes de classe/método que hoje entregam
            // de graça onde a chave é lida e como a requisição é montada.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        // Uso pessoal (sem Play Store, ver CLAUDE.md 26/09/2026): o mesmo build
        // minificado do release, mas assinado com a chave de debug — instala por cima
        // do app-debug já no celular sem perder dados, e com R8 + só arm64 fica
        // pequeno o bastante para mandar por mensagem.
        // ./gradlew assemblePessoal → app/build/outputs/apk/pessoal/app-pessoal.apk
        // (não usar -Pandroid.injected.build.abi: esse atalho de IDE marca o APK como
        // testOnly e o instalador do celular recusa com "pacote inválido").
        create("pessoal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // Só celulares 64-bit ARM: tira ~30 MB de bibliotecas do OCR de outras arquiteturas.
            ndk { abiFilters += "arm64-v8a" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG é o que apaga os logs de diagnóstico em release
        // (util/FinaiLog.kt). No AGP 8 ele não é gerado por padrão.
        buildConfig = true
    }
    sourceSets {
        // As faturas de exemplo em /samples servem para dois publicos: o usuario
        // testando no aparelho e o teste instrumentado, que importa exatamente
        // esses arquivos em vez de uma copia que poderia divergir deles.
        getByName("androidTest") {
            assets.srcDir(rootProject.file("samples"))
            // Schemas do Room como asset: é de onde o MigrationTestHelper lê a
            // versão antiga do banco. O teste de migração atual
            // (FinaiDatabaseMigrationTest) não o usa, porque não existe JSON das
            // versões 1 a 3 — exportSchema só foi ligado na Fase 6. Da versão 4
            // em diante o JSON existe, e a próxima migração já pode ser testada
            // por esse caminho sem mexer no build.
            assets.srcDir("$projectDir/schemas")
        }
    }
    lint {
        // Nenhum aviso do lint deve derrubar o build de release por si só, mas
        // um erro de verdade (uso de API acima do minSdk, recurso quebrado)
        // deve — é a checagem mais barata antes de subir um APK para a Play.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }

    testOptions {
        // AiRouter/etc. log via android.util.Log, which throws in a plain
        // JVM unit test unless stubbed methods return a default instead.
        unitTests.isReturnDefaultValues = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // Onde o Room grava o JSON de schema de cada versão (exportSchema = true em
    // FinaiDatabase). Versionado no git: é a referência para escrever a próxima
    // migração e o insumo do teste de migração instrumentado.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room (local persistence — scaffolded in Phase 0, wired up in Phase 1)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (preferences — scaffolded in Phase 0, used from Phase 2 for AI provider keys/quotas)
    implementation(libs.androidx.datastore.preferences)

    // Encrypted storage for the Gemini API key (planning.md §7.4) — Android Keystore-backed
    implementation(libs.androidx.security.crypto)

    // OCR on-device da importacao de fatura (Fase 4, planning.md §5) — modelo
    // latino empacotado no APK: roda offline, sem cota e sem enviar o arquivo
    // para fora do aparelho (planning.md §4).
    implementation(libs.mlkit.text.recognition)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager (Fase 5, planning.md §5/§9) — rotina periódica de notificações
    // proativas, funciona com o app fechado e sobrevive a reboot.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json de verdade nos testes JVM: o android.jar só tem stubs, e o codec do backup
    // (data/backup) depende dele.
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    // MigrationTestHelper, para testar migrações a partir da versão 4 (a
    // primeira com schema exportado). O teste de migração de hoje monta o banco
    // v1 com DDL explícito e não precisa dele — ver FinaiDatabaseMigrationTest.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
