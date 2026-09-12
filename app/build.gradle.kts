plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.finai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.finai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    sourceSets {
        // As faturas de exemplo em /samples servem para dois publicos: o usuario
        // testando no aparelho e o teste instrumentado, que importa exatamente
        // esses arquivos em vez de uma copia que poderia divergir deles.
        getByName("androidTest") { assets.srcDir(rootProject.file("samples")) }
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
