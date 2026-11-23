import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin jest wbudowany w AGP 9, więc nie trzeba osobnej wtyczki kotlin-android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // generator kodu dla Hilt i Room
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Wersja z tagu (CI ustawia QBIT_VERSION_NAME=v1.2.3); lokalnie "0.1.0-dev".
val appVersionName: String = System.getenv("QBIT_VERSION_NAME")?.removePrefix("v") ?: "0.1.0-dev"

// versionCode musi rosnąć z każdym wydaniem: 1.2.3 → 10203.
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersionName)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
    ?: 1

// Podpis wydania: ze zmiennych środowiskowych (CI) albo z keystore.properties
// (lokalnie). Oba źródła są poza repo (zob. .gitignore).
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: keystoreProps.getProperty(prop)

android {
    namespace = "dev.milkthe.qbitplan"
    // API 37 w wersji „minor” 2; wymagają tego biblioteki Compose.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.milkthe.qbitplan"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        val storeFilePath = signingValue("QBIT_KEYSTORE_FILE", "storeFile")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("QBIT_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("QBIT_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("QBIT_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug" // debug i release mogą być zainstalowane obok siebie
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Schematy bazy trafiają do repo, żeby dało się pisać migracje.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
