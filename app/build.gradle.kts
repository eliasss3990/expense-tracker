plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de release leída de variables de entorno (seteadas por el
// workflow de CI desde GitHub Secrets) - nunca se commitea un keystore
// ni sus contraseñas. Sin estas env vars (build local de un dev), el
// build type release queda sin signingConfig - assembleRelease sigue
// andando para probar la config de minify/shrink, pero el APK no se
// puede instalar hasta firmarlo (no es un caso de uso real local).
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank()

android {
    namespace = "com.eliasgonzalez.expensetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eliasgonzalez.expensetracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "0.6.0"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // El motor de LiteRT-LM pesa ~22 MB por arquitectura. x86_64 (~26 MB) solo sirve a emuladores y
            // ChromeOS, no a ningun telefono: se excluye del APK de release (pasaba de ~35 a ~61 MB). El resto de las
            // arquitecturas queda igual, asi que nada cambia en los dispositivos donde ya se instalaba. El build
            // de debug las conserva todas.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86")
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Runtime del modelo local (solo lo conoce data/analysis/LiteRtTransactionInterpreter). Version fijada, no latest.release.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // SQLite real en la JVM para probar las migraciones de la base sin emulador (DbMigrationTest).
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // org.json real en la JVM: el de android.jar es un stub que lanza "not mocked" (InterpretationCodec).
    testImplementation("org.json:json:20240303")
}
