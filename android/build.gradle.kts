plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover) apply false
}

// Versiones fijas de TODAS las dependencias, también las indirectas: quedan en gradle.lockfile (raíz y composeApp) y el
// build falla si alguna cambia sin querer. Tras subir una versión en libs.versions.toml:
//   ./gradlew :composeApp:dependencies --write-locks
// El proyecto raíz no declara dependencias propias: su gradle.lockfile queda en «empty=» (si alguna aparece, el build
// falla hasta volver a escribir los bloqueos).
allprojects {
    dependencyLocking { lockAllConfigurations() }
}
