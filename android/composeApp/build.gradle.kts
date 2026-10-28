import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iOS: el mismo código común; el framework se compila en una Mac con Xcode (ver LEEME)
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // Escritorio (JVM): para probar la interfaz en la PC sin emulador y generar capturas del diseño.
    // No es un producto: el almacenero usa Android o iOS.
    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.work.runtime)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "pe.sevens.sigro"
    compileSdk = 35
    defaultConfig {
        applicationId = "pe.sevens.sigro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-sprint1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

compose.desktop {
    application {
        mainClass = "pe.sevens.sigro.EscritorioKt"
    }
}

// ./gradlew :composeApp:capturas  → PNG de cada pantalla en build/capturas (claro y oscuro)
tasks.register<JavaExec>("capturas") {
    group = "sigro"
    description = "Genera capturas de las pantallas con datos de ejemplo"
    val desktop = kotlin.targets.getByName("desktop").compilations.getByName("main")
    dependsOn(desktop.compileTaskProvider)
    classpath = files(desktop.output.allOutputs, desktop.runtimeDependencyFiles)
    mainClass.set("pe.sevens.sigro.CapturasKt")
    maxHeapSize = "768m"
    args(layout.buildDirectory.dir("capturas").get().asFile.absolutePath)
}
