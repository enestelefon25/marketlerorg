package org.marketler.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marketler.app.ui.home.HomeScreen
import org.marketler.app.ui.product.ProductScreen
import org.marketler.app.ui.search.SearchScreen
import org.marketler.core.data.remote.ApiClient
import org.marketler.core.data.remote.ApiConfig
import org.marketler.core.data.remote.RemoteRepository
import org.marketler.core.ui.MarketlerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uçtan uca ekran testi: ekranlar ÇALIŞAN yerel Rev3 API kopyasına (php -S + MariaDB, sentetik ve kirli veri) bağlanır.
 * Yalnızca MK_YEREL_API verilirse çalışır (ör. http://127.0.0.1:8099/api/v1/); yalnızca yerel adres kabul edilir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YerelApiEkranTest {
    @get:Rule val rule = createComposeRule()
    private val base = System.getenv("MK_YEREL_API")
    private lateinit var repo: RemoteRepository

    @Before fun setUp() {
        assumeTrue("MK_YEREL_API verilmedi; atlandı", !base.isNullOrBlank())
        val host = base!!.toHttpUrl().host
        require(host == "127.0.0.1" || host == "localhost") { "Yalnızca yerel API: $host" }
        repo = RemoteRepository(ApiClient.create(ApiConfig(base, requireHttps = false)))
    }
    private fun waitText(t: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(t, substring = true)[0].assertIsDisplayed()
    }

    @Test fun anaSayfa() { rule.setContent { MarketlerTheme { HomeScreen(repo, {}, {}, {}, {}, {}, sourceLabel = "Yerel test sunucusu") } }; waitText("Yerel test sunucusu"); waitText("Marketler") }
    @Test fun arama() { rule.setContent { MarketlerTheme { SearchScreen(repo, "Kirlitest", null, null, false) {} } }; waitText("Kirlitest Deterjan 3 kg"); waitText("Fiyat belirtilmemiş") }
    @Test fun urunDetayi() { rule.setContent { MarketlerTheme { ProductScreen(repo, 3, onBack = {}, onOpenProduct = {}) } }; waitText("Isıtek Airfryer XA4200 4.2 L"); waitText("5.199,00 TL") }
}
