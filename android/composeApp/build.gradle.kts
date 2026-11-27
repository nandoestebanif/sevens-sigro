import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
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
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
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
            implementation(libs.androidx.biometric) // entrar con huella (BiometricPrompt)
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
        versionCode = 16
        versionName = "1.1.4"
        // Release: red sin excepciones http. Debug: también emulador y PC local (MobSF, 27/11).
        manifestPlaceholders["redSegura"] = "@xml/seguridad_red"
        // SQLite empaquetado (Room KMP) trae su .so por ABI (~2 MB cada una). x86 de 32 bits no existe en
        // celulares; x86_64 se queda para el emulador.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["redSegura"] = "@xml/seguridad_red_debug"
        }
        getByName("release") {
            // R8: quita el código y los recursos que no se usan (Material Icons Extended pesa varios MB).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // Perfil de arranque escrito a mano (src/androidMain/baselineProfiles): con KMP, AGP no mira esa
    // carpeta por su cuenta.
    sourceSets["main"].baselineProfiles.srcDir("src/androidMain/baselineProfiles")
    lint {
        // El detector de LiveData de AGP 8.7 se cae con Kotlin 2.1 (IncompatibleClassChangeError) y tumba
        // lintVitalAnalyzeRelease, o sea assembleRelease. La app no usa LiveData: se apaga solo esa regla.
        disable += "NullSafeMutableLiveData"
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

// Fuentes (Inter, Sora) y logos de SEVEN'S: composeResources/ → pe.sevens.sigro.recursos.Res
compose.resources {
    packageOfResClass = "pe.sevens.sigro.recursos"
    generateResClass = always
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

// Informe del compilador de Compose (estabilidad y funciones que se saltan), para docs/rendimiento-app.md:
// ./gradlew :composeApp:compileReleaseKotlinAndroid -Psigro.metricasCompose --rerun-tasks → build/compose-metricas
if (providers.gradleProperty("sigro.metricasCompose").isPresent) {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose-metricas")
        metricsDestination = layout.buildDirectory.dir("compose-metricas")
    }
}

// Cobertura de pruebas para SonarQube (rúbrica: «cobertura de domain y data ≥ 40 %»).
// ./gradlew :composeApp:koverXmlReportDebug → build/reports/kover/reportDebug.xml (lo lee sonar.coverage.jacoco.xmlReportPaths)
// Se mide solo dominio y datos: la interfaz de Compose se prueba en el celular (matriz de dispositivos), no con pruebas unitarias.
kover {
    reports {
        filters {
            includes {
                packages("pe.sevens.sigro.dominio", "pe.sevens.sigro.dominio.casosdeuso", "pe.sevens.sigro.datos.repositorio", "pe.sevens.sigro.datos.remoto")
            }
            // Los DTO @Serializable solo transportan datos: el código que genera kotlinx.serialization (write$Self,
            // $$serializer) suma ramas que no son lógica nuestra. Lo que se mide es el repositorio que los usa.
            excludes {
                annotatedBy("kotlinx.serialization.Serializable")
                classes("*\$\$serializer")
            }
        }
    }
}
