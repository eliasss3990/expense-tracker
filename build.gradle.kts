plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // Procesador de simbolos: androidx.appfunctions-compiler genera el servicio y el esquema de las AppFunctions.
    id("com.google.devtools.ksp") version "2.3.13" apply false
}
