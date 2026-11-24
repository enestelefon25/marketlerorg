/* Saf Kotlin: depo arayüzü, MOCK veri kaynağı (testler ve mock derlemesi için korunur) ve FAZ 2.2.1 ağ katmanı (Retrofit + kotlinx.serialization). */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
tasks.test {
    /* Yerel Rev3 API kopyasına karşı isteğe bağlı uçtan uca test: MK_YEREL_API=http://127.0.0.1:8099/api/v1/ */
    System.getenv("MK_YEREL_API")?.let { environment("MK_YEREL_API", it) }
}
