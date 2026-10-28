import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

configure<ApplicationExtension> {
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = localProps.getProperty("KEYSTORE_PATH")?.let { file(it) }
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD")
            keyAlias = localProps.getProperty("KEY_ALIAS")
            keyPassword = localProps.getProperty("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "montafra.beam"
        minSdk = 28 // BatteryManager.computeChargeTimeRemaining()
        targetSdk = 36
        versionCode = 38
        versionName = "2.2"
    }

    androidResources {
        localeFilters.addAll(listOf("en", "es", "it", "de", "fr", "uk", "cs", "pl", "pt", "ru", "in", "zh-rCN"))
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "montafra.beam"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Material 3 Expressive is still behind an opt-in annotation at material3 1.4. Opting in
        // once here beats sprinkling @OptIn over every file that touches a theme, a top bar or a
        // motion spec.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("com.google.android.material:material:1.13.0")

    // Material 3 Expressive - MaterialExpressiveTheme, MotionScheme, the flexible top bars - needs
    // material3 1.4+. The 2024 BOM this project used to pin only ever reached 1.3.
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
