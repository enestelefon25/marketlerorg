package org.marketler.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marketler.core.data.DisabledRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Collections

/**
 * YALNIZCA "canli" sürümünde çalışır (src/testCanli). Gerçek MainActivity ve gerçek MarketlerApplication ile
 * (BuildConfig: VERI_KAYNAGI=canli, CANLI_API_ACIK=false) uygulama açılır, sekmeler gezilir, arama yazılır.
 * Ağ bağlantısı denemesi olup olmadığı JVM'in vekil seçicisiyle (ProxySelector) izlenir: OkHttp ve HttpURLConnection
 * her bağlantıdan önce buna sorar. Beklenen: HİÇ bağlantı denemesi yok.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CanliErisimKapaliTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val attempts: MutableList<URI> = Collections.synchronizedList(mutableListOf())
    private var previous: ProxySelector? = null

    @Before fun watchNetwork() {
        previous = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> { attempts += uri; return listOf(Proxy.NO_PROXY) }
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        })
    }
    @After fun restore() { ProxySelector.setDefault(previous) }

    private fun waitText(t: String) = rule.waitUntil(8_000) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) { rule.onNode(hasText(label) and hasClickAction()).performClick(); rule.waitForIdle() }

    @Test fun canliKapaliIkenHicAgIstegiYok() {
        assertEquals("canli", BuildConfig.VERI_KAYNAGI)
        assertFalse(BuildConfig.CANLI_API_ACIK)
        val app = ApplicationProvider.getApplicationContext<MarketlerApplication>()
        assertTrue("canlı kapalıyken depo DisabledRepository olmalı", app.repository is DisabledRepository)

        waitText("Canlı veri henüz yayında değil")
        tab("Ara"); waitText("Canlı veri henüz yayında değil")
        rule.onNode(hasSetTextAction()).performTextInput("süt"); rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        tab("Kataloglar"); tab("Karşılaştır"); tab("Asistan"); tab("Ana Sayfa")
        waitText("Canlı veri henüz yayında değil")
        rule.onAllNodesWithText("Tekrar dene")[0].performClick(); rule.waitForIdle()
        Thread.sleep(500)
        assertEquals("ağ bağlantısı denemesi olmamalı: $attempts", 0, attempts.size)
    }

    /** Kontrol (pozitif): izleme yöntemi gerçek bir istekte bağlantı denemesini YAKALAR. Yukarıdaki "0" sonucu bu yüzden anlamlıdır. */
    @Test fun izlemeYontemiGercekIstegiYakalar() {
        val server = okhttp3.mockwebserver.MockWebServer().apply { enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"success":true,"data":[],"meta":{}}""")); start() }
        try {
            val repo = org.marketler.core.data.remote.RemoteRepository(org.marketler.core.data.remote.ApiClient.create(
                org.marketler.core.data.remote.ApiConfig(server.url("/api/v1/").toString(), requireHttps = false)))
            kotlinx.coroutines.runBlocking { repo.markets() }
            assertTrue("bağlantı denemesi yakalanmalıydı", attempts.isNotEmpty())
        } finally { server.shutdown() }
    }
}
