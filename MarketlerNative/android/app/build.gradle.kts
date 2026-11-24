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
        versionCode = 3
        versionName = "0.2.2.2"   // FAZ 2.2.2: ekranlar veri kaynağına (mock / yerel / canlı) bağlandı
    }

    /*
     * FAZ 2.2.2 veri kaynağı sürümleri (kullanıcı kararı):
     *  - mock : uygulamaya gömülü örnek veri. INTERNET izni YOK.
     *  - yerel: geliştirme. Bilgisayardaki yerel Rev3 kopyası, emülatörden http://10.0.2.2:8099. HTTP istisnası YALNIZCA
     *           10.0.2.2 için ve YALNIZCA bu sürümün kaynak klasöründe (src/yerel). Release derlemesi yoktur (aşağıda kapatıldı).
     *  - canli: https://marketler.org/api/v1/ . Yalnızca HTTPS, yalnızca bu alan adı (ağ istemcisinde ayrıca zorlanır).
     *           CANLI_API_ACIK=false: canlıya alma onayına kadar uygulama canlı API'ye HİÇ istek göndermez.
     * Adresler gizli bilgi değildir; APK'da anahtar, parola veya veritabanı bilgisi yoktur.
     */
    flavorDimensions += "veri"
    productFlavors {
        create("mock") {
            dimension = "veri"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            buildConfigField("String", "VERI_KAYNAGI", "\"mock\"")
            buildConfigField("String", "API_ADRESI", "\"\"")
            buildConfigField("boolean", "CANLI_API_ACIK", "false")
        }
        create("yerel") {
            dimension = "veri"
            applicationIdSuffix = ".yerel"
            versionNameSuffix = "-yerel"
            buildConfigField("String", "VERI_KAYNAGI", "\"yerel\"")
            buildConfigField("String", "API_ADRESI", "\"http://10.0.2.2:8099/api/v1/\"")
            buildConfigField("boolean", "CANLI_API_ACIK", "false")
        }
        create("canli") {
            dimension = "veri"
            versionNameSuffix = "-canli"
            buildConfigField("String", "VERI_KAYNAGI", "\"canli\"")
            buildConfigField("String", "API_ADRESI", "\"https://marketler.org/api/v1/\"")
            /* Canlıya alma onayı verildiğinde true yapılır (FAZ 2.2.3). */
            buildConfigField("boolean", "CANLI_API_ACIK", "false")
        }
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
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    /* Ekran testleri, core/data testlerindeki yerel API kayıtlarını (api-yerel) ortak kullanır. */
    sourceSets.getByName("test").resources.srcDir("../core/data/src/test/resources")
}

/* "yerel" yalnızca geliştirme içindir: release derlemesi hiç oluşturulmaz (HTTP istisnası yayın APK'sına giremez). */
androidComponents {
    beforeVariants(selector().withFlavor("veri" to "yerel").withBuildType("release")) { it.enable = false }
}

/* Robolectric, Android çalışma kitaplığını ilk çalıştırmada Maven Central'dan indirir. Çevrimdışı ortamda:
   gradle test -Probolectric.dependency.dir=/yol (klasörde android-all-instrumented jar'ı olmalı). */
tasks.withType<Test>().configureEach {
    System.getenv("MK_YEREL_API")?.let { environment("MK_YEREL_API", it) }   // isteğe bağlı yerel API ekran testi
    (project.findProperty("robolectric.dependency.dir") as String?)?.let { systemProperty("robolectric.offline", "true"); systemProperty("robolectric.dependency.dir", it) }
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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.compose.ui.test.manifest)
}
