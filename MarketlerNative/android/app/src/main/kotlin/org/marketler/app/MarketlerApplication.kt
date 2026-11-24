package org.marketler.app

import android.app.Application
import android.util.Log
import org.marketler.core.data.DisabledRepository
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.MockRepository
import org.marketler.core.data.remote.ApiClient
import org.marketler.core.data.remote.ApiConfig
import org.marketler.core.data.remote.RemoteRepository
import java.io.File

/**
 * Uygulama kapsayıcısı (elle bağımlılık yönetimi; Hilt planı FAZ2.1-RAPORU bölüm 9).
 * Veri kaynağı derleme sürümünden gelir (app/build.gradle.kts → productFlavors):
 *  - mock : gömülü örnek veri, ağ yok.
 *  - yerel: yerel Rev3 kopyası (yalnızca 10.0.2.2, HTTP yalnızca bu adres için; yalnızca debug).
 *  - canli: https://marketler.org/api/v1/ (yalnızca HTTPS + alan adı kilidi). CANLI_API_ACIK=false iken ağa hiç çıkılmaz.
 */
class MarketlerApplication : Application() {
    val repository: MarketlerRepository by lazy { createRepository() }

    /** Ana sayfadaki veri kaynağı etiketi. */
    val sourceLabel: String
        get() = when (BuildConfig.VERI_KAYNAGI) {
            "mock" -> "Örnek veri (mock): gerçek fiyat değildir"
            "yerel" -> "Yerel test sunucusu (geliştirme)"
            else -> "Canlı veri"
        }

    private fun createRepository(): MarketlerRepository = when (BuildConfig.VERI_KAYNAGI) {
        "mock" -> MockRepository()
        "yerel" -> remote(requireHttps = false)
        "canli" -> if (BuildConfig.CANLI_API_ACIK) remote(requireHttps = true)
                   else DisabledRepository("Canlı veri henüz yayında değil. Bu sürüm, canlıya alma onayından sonra etkinleştirilecek.")
        else -> error("Bilinmeyen veri kaynağı: ${BuildConfig.VERI_KAYNAGI}")
    }

    private fun remote(requireHttps: Boolean): MarketlerRepository {
        val config = ApiConfig(
            baseUrl = BuildConfig.API_ADRESI,
            requireHttps = requireHttps,
            lockHost = true,
            cacheDir = File(cacheDir, "api"),
            userAgent = "MarketlerAndroid/${BuildConfig.VERSION_NAME}",
        )
        return RemoteRepository(ApiClient.create(config)) { Log.w("MarketlerVeri", it) }
    }
}
