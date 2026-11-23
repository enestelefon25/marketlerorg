import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/* İmzalama bilgileri depoda tutulmaz: proje kökündeki keystore.properties (git dışı) veya ortam değişkenleri. Yoksa release imzasız kalır. */
val ks = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun sv(env: String, key: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: ks.getProperty(key)
val storePath = sv("MARKETLER_KEYSTORE_FILE", "storeFile")
val hasSigning = storePath != null && file(storePath).exists()

android {
    namespace = "org.marketler.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.marketler.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.1-mock"   // FAZ 2.1: yalnızca mock veri
    }

    signingConfigs {
        if (hasSigning) create("release") {
            storeFile = file(storePath!!)
            storePassword = sv("MARKETLER_KEYSTORE_PASSWORD", "storePassword")
            keyAlias = sv("MARKETLER_KEY_ALIAS", "keyAlias")
            keyPassword = sv("MARKETLER_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"      // telefondaki WebView uygulamasının (org.marketler.app) yerine geçmez, yanına kurulur
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.compose.ui.tooling)
}
