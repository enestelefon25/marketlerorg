package org.marketler.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marketler.app.ui.home.HomeScreen
import org.marketler.app.ui.product.ProductScreen
import org.marketler.app.ui.search.SearchScreen
import org.marketler.core.data.DisabledRepository
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.remote.ApiClient
import org.marketler.core.data.remote.ApiConfig
import org.marketler.core.data.remote.RemoteRepository
import org.marketler.core.ui.MarketlerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FAZ 2.2.2 ekran testleri: ekranlar GERÇEK ağ katmanıyla (Retrofit + OkHttp + RemoteRepository) çalışır; sahte sunucu,
 * Rev3 API'nin yerel kopyasından kaydedilmiş gerçek yanıtları (api-yerel) oynatır. Yükleme, hata, boş veri, NULL kuralları.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EkranVeriTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var repo: MarketlerRepository

    private data class Rec(val path: String, val method: String, val http: Int, val retryAfter: String?, val file: String)
    private fun res(name: String) = javaClass.getResourceAsStream("/api-yerel/$name")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    private val index = Json.parseToJsonElement(res("index.json")).jsonObject
    private val recs = index["yanitlar"]!!.jsonArray.map { it.jsonObject }.map {
        Rec(it["yol"]!!.jsonPrimitive.content, it["yontem"]!!.jsonPrimitive.content, it["http"]!!.jsonPrimitive.int,
            it["basliklar"]!!.jsonObject["retry-after"]?.jsonPrimitive?.content, it["dosya"]!!.jsonPrimitive.content)
    }
    private val dirty = index["kirli_kayitlar"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content.toLong() }
    private fun replay(r: Rec) = MockResponse().setResponseCode(r.http).setHeader("Content-Type", "application/json; charset=utf-8")
        .apply { r.retryAfter?.let { setHeader("Retry-After", it) } }.setBody(res(r.file))
    private fun rec(name: String) = recs.first { it.file == "$name.json" }
    private fun query(p: String) = p.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
        .associate { kv -> java.net.URLDecoder.decode(kv.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }
    private fun norm(path: String, m: Map<String, String>) = if (path == "products") mapOf("status" to "aktif", "sort" to "new", "page" to "1", "limit" to "24") + m else m

    /** Yol bazında özel yanıt (ör. hata); null dönerse kayıtlı yanıt oynatılır. */
    @Volatile private var override: ((String, Map<String, String>) -> MockResponse?)? = null

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val u = request.requestUrl!!
                val path = u.encodedPath.removePrefix("/api/v1/")
                val params = u.queryParameterNames.associateWith { u.queryParameter(it).orEmpty() }
                override?.invoke(path, params)?.let { return it }
                val r = recs.firstOrNull { it.method == request.method && it.path.substringBefore('?') == path && norm(path, query(it.path)) == norm(path, params) }
                    ?: return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("""{"success":true,"data":[],"meta":{"page":1,"limit":24,"total":0}}""")   // kayıtsız sorgu: boş sonuç
                return replay(r)
            }
        }
        server.start()
        repo = RemoteRepository(ApiClient.create(ApiConfig(server.url("/api/v1/").toString(), requireHttps = false)))
    }
    @After fun tearDown() { server.shutdown() }

    private fun waitText(t: String, substring: Boolean = true) {
        rule.waitUntil(8_000) { rule.onAllNodesWithText(t, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(t, substring = substring)[0].assertIsDisplayed()
    }
    /** Ekrandaki kaydırılabilir alanlardan hangisi metni içeriyorsa ona kaydırır (arama ekranında yatay süzgeç şeridi de var). */
    private fun scrollTo(t: String) {
        val nodes = rule.onAllNodes(hasScrollToNodeAction())
        val n = nodes.fetchSemanticsNodes().size
        for (i in 0 until n) { try { nodes[i].performScrollToNode(hasText(t, substring = true)); return } catch (e: AssertionError) { } }
        throw AssertionError("'$t' hiçbir kaydırılabilir alanda bulunamadı")
    }
    private fun noText(t: String) = assertEquals("'$t' görünmemeli", 0, rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().size)
    private fun home(label: String? = "Canlı veri") = rule.setContent { MarketlerTheme { HomeScreen(repo, {}, {}, {}, {}, {}, sourceLabel = label) } }
    private fun search(q: String) = rule.setContent { MarketlerTheme { SearchScreen(repo, q, null, null, false) {} } }
    private fun product(id: Long) = rule.setContent { MarketlerTheme { ProductScreen(repo, id, onBack = {}, onOpenProduct = {}) } }

    @Test fun anaSayfaGercekApiYanitiyla() {
        home()
        waitText("Canlı veri")
        scrollTo("Demlik Siyah Çay 1000 g"); waitText("Demlik Siyah Çay 1000 g")   // öne çıkanlar (API'den)
        scrollTo("Aynı ürün, farklı marketler")
        waitText("Isıtek Airfryer XA4200 4.2 L")
    }

    @Test fun anaSayfaHatasi_tekrarDeneIleDuzelir() {
        override = { path, _ -> if (path == "home") replay(rec("hata_503_veritabani")) else null }
        home()
        waitText("Hizmet geçici olarak kullanılamıyor.")
        override = null
        rule.onNodeWithText("Tekrar dene").performClick()
        waitText("Canlı veri")
        scrollTo("Demlik Siyah Çay 1000 g"); waitText("Demlik Siyah Çay 1000 g")
    }

    @Test fun baglantiYok() {
        server.shutdown()
        home()
        waitText("İnternet bağlantısı kurulamadı")
        waitText("Tekrar dene")
    }

    @Test fun aramaSonuclari_nullKurallari() {
        override = { path, p -> if (path == "products" && p["q"] == "Kirlitest") replay(rec("products_q_kirlitest")) else null }
        search("Kirlitest")
        waitText("Kirlitest Deterjan 3 kg")
        waitText("Fiyat belirtilmemiş")                           // NULL fiyat: ürün silinmedi
        noText("0,00 TL")                                         // ve 0 TL gösterilmedi
        noText("%100")
    }

    @Test fun bosAramaSonucu() {
        search("zzzqqq")
        waitText("Sonuç bulunamadı")
    }

    @Test fun aramaHatasi_veSonrakiSayfaHatasi() {
        override = { path, _ -> if (path == "products") replay(rec("hata_429_hiz")) else null }
        search("süt")
        waitText("Çok fazla istek gönderildi")
        override = { path, p -> when {
            path != "products" -> null
            p["page"] == "1" -> MockResponse().setHeader("Content-Type", "application/json").setBody(res(rec("products_hepsi_s1").file).replace("\"total\":", "\"total_eski\":").replace("\"meta\":{", "\"meta\":{\"total\":200,"))
            else -> replay(rec("hata_503_veritabani"))
        } }
        rule.onNodeWithText("Tekrar dene").performClick()
        waitText("200 ürün bulundu")
        rule.waitUntil(8_000) { rule.onAllNodesWithText("Tekrar dene").fetchSemanticsNodes().isNotEmpty() || runCatching { scrollTo("Tekrar dene") }.isSuccess }
        scrollTo("Tekrar dene")
        waitText("Hizmet geçici olarak kullanılamıyor.")          // liste korunur, hata listenin sonunda
    }

    @Test fun onerideFiyatGosterilmez() {
        search("")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("kirlitest")
        waitText("Kirlitest Deterjan 3 kg")
        waitText("A101")
        noText("89,90 TL"); noText("0,00 TL")
    }

    @Test fun urunDetayi_fiyatsizKarsilastirmayaGirmez() {
        product(dirty.getValue("fiyat_var_a101"))
        waitText("Kirlitest Deterjan 3 kg")
        waitText("89,90 TL")
        scrollTo("Karşılaştırma için yeterli fiyat bilgisi yok.")
        waitText("Karşılaştırma için yeterli fiyat bilgisi yok.")
        noText("0,00 TL")
    }

    @Test fun urunDetayi_fiyatYok() {
        product(dirty.getValue("fiyat_null_eski_var"))
        waitText("Kirlitest Bisküvi 200 g")
        waitText("Fiyat belirtilmemiş")
        noText("25,00 TL"); noText("%100"); noText("0,00 TL")
    }

    @Test fun urunDetayi_nullMarketVeKatalog() {
        product(dirty.getValue("market_null"))
        waitText("Kirlitest Renksiz Market Ürünü")
        waitText("15,00 TL")
    }

    @Test fun urunBulunamadi() {
        product(99_999_999)
        waitText("Ürün bulunamadı.")
    }

    @Test fun canliKapaliIkenAgaCikilmaz() {
        repo = DisabledRepository("Canlı veri henüz yayında değil.")
        val before = server.requestCount
        home(label = "Canlı veri")
        waitText("Canlı veri henüz yayında değil.")
        assertEquals(before, server.requestCount)
    }
}
