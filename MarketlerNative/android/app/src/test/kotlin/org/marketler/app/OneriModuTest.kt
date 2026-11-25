package org.marketler.app

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.marketler.app.ui.search.SearchScreen
import org.marketler.app.ui.search.TAG_SUGGESTIONS
import org.marketler.app.ui.search.TAG_SUGGESTION_INFO
import org.marketler.app.ui.search.TAG_SUGGESTION_ROW
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.MockRepository
import org.marketler.core.data.remote.ApiClient
import org.marketler.core.data.remote.ApiConfig
import org.marketler.core.data.remote.RemoteRepository
import org.marketler.core.ui.MarketlerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * B1 düzeltmesi (öneri modu) — kullanıcının EKRANDA GÖRDÜĞÜ BÜTÜN içerik denetlenir, yalnızca öneri bileşeni değil:
 * ekrandaki her metin, düzenlenebilir metin ve erişilebilirlik açıklaması toplanır; fiyat KALIBINA uyan hiçbir şey
 * (ör. "275,00 TL", "≈ 55,00 TL/L", "%20", "Fiyat belirtilmemiş") olmamalıdır. Belirli bir fiyat değerine bakılmaz.
 * Aynı senaryolar iki veri kaynağıyla ayrı ayrı çalışır: mock (OneriModuMockTest) ve kayıtlı API yanıtları (OneriModuKayitliApiTest).
 */
abstract class OneriModuTestBase {
    @get:Rule val rule = createComposeRule()
    protected abstract fun repo(): MarketlerRepository
    /** Kaynağa göre öneri getiren yazı ve hiç öneri getirmeyen yazı. */
    protected abstract val oneriYazisi: String
    protected open val oneriYok: String = "zzqqxxw"

    private val priceRe = Regex("""\d+,\d{2}\s*TL|TL/(kg|L)|%\s?\d+|-%\d+|Fiyat belirtilmemiş""", RegexOption.IGNORE_CASE)

    /** Ekranda görünen bütün metinler (birleştirilmemiş ağaç: her Text ayrı). */
    private fun screenTexts(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            n.config.getOrNull(SemanticsProperties.EditableText)?.let { out += it.text }
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.let { out += it }
            n.children.forEach(::walk)
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }
    private fun prices() = screenTexts().filter { priceRe.containsMatchIn(it) }
    private fun assertNoPriceAnywhere(where: String) = assertEquals("$where: ekranda fiyat görünmemeli. Ekran: ${screenTexts()}", emptyList<String>(), prices())
    private fun assertNormalScreenHidden(where: String) {
        val t = screenTexts()
        assertTrue("$where: ürün sayısı gizli olmalı: $t", t.none { it.contains("ürün bulundu") || it == "Aranıyor…" })
        assertTrue("$where: süzgeçler gizli olmalı: $t", t.none { it == "Market" || it == "Kategori" || it.startsWith("Durum:") || it == "En yeni" })
    }
    private fun idle(ms: Long = 400) { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS); rule.waitForIdle() }
    private fun waitTag(tag: String) = rule.waitUntil(8_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitResults() = rule.waitUntil(8_000) { screenTexts().any { it.contains("ürün bulundu") } && prices().isNotEmpty() }
    private fun field() = rule.onNode(hasSetTextAction())

    /** Kaynağı hazırlar (JUnit üst sınıfın @Before'unu önce çalıştırdığı için alt sınıf hazırlığı buradan çağrılır). */
    protected open fun prepare() {}

    @Before fun open() {
        prepare()
        rule.setContent { MarketlerTheme { SearchScreen(repo(), "", null, null, false) {} } }
        waitResults()                                    // başlangıç: normal arama ekranı (fiyatlı sonuçlar)
    }

    @Test fun yazinca_yalnizcaOneriler_hicFiyatYok() {
        field().performTextInput(oneriYazisi); idle()
        waitTag(TAG_SUGGESTION_ROW)
        val rows = rule.onAllNodesWithTag(TAG_SUGGESTION_ROW, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("öneri satırı çizilmeli", rows.isNotEmpty())
        rows.forEach { r ->                               // her satırda yalnızca ad ve market (2 metin)
            val texts = mutableListOf<String>(); fun w(n: SemanticsNode) { n.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }; n.children.forEach(::w) }; w(r)
            assertTrue("öneri satırı yalnızca ad ve market içermeli: $texts", texts.size in 1..2 && texts.none { priceRe.containsMatchIn(it) })
        }
        assertNoPriceAnywhere("öneriler açık")
        assertNormalScreenHidden("öneriler açık")
    }

    @Test fun oneriyeDokununca_fiyatliSonuclar() {
        field().performTextInput(oneriYazisi); idle(); waitTag(TAG_SUGGESTION_ROW)
        rule.onAllNodesWithTag(TAG_SUGGESTION_ROW, useUnmergedTree = true)[0].performClick()
        waitResults()
        assertEquals(0, rule.onAllNodesWithTag(TAG_SUGGESTIONS).fetchSemanticsNodes().size)
        assertTrue(prices().isNotEmpty())
    }

    @Test fun araDugmesi_normalSonuclar() {
        field().performTextInput(oneriYazisi); idle(); waitTag(TAG_SUGGESTION_ROW)
        assertNoPriceAnywhere("Ara'dan önce")
        field().performImeAction()
        waitResults()
        assertEquals(0, rule.onAllNodesWithTag(TAG_SUGGESTIONS).fetchSemanticsNodes().size)
    }

    @Test fun oneriYoksa_bilgilendirme_fiyatYok() {
        field().performTextInput(oneriYok); idle()
        rule.waitUntil(8_000) { screenTexts().any { it.contains("eşleşen öneri bulunamadı") } }
        assertNoPriceAnywhere("öneri yok"); assertNormalScreenHidden("öneri yok")
    }

    @Test fun tekHarf_bilgilendirme_fiyatYok() {
        field().performTextInput("s"); idle()
        waitTag(TAG_SUGGESTION_INFO)
        assertTrue(screenTexts().any { it.contains("en az 2 harf") })
        assertNoPriceAnywhere("tek harf"); assertNormalScreenHidden("tek harf")
    }

    @Test fun temizleDugmesi_normalEkran() {
        field().performTextInput(oneriYazisi); idle(); waitTag(TAG_SUGGESTION_ROW)
        rule.onAllNodesWithContentDescription("Temizle")[0].performClick(); idle()
        waitResults()
        assertEquals(0, rule.onAllNodesWithTag(TAG_SUGGESTIONS).fetchSemanticsNodes().size)
        assertTrue(screenTexts().any { it == "Market" })
    }

    @Test fun yaziSilinince_normalEkran() {
        field().performTextInput(oneriYazisi); idle(); waitTag(TAG_SUGGESTION_ROW)
        field().performTextClearance(); idle()
        waitResults()
        assertEquals(0, rule.onAllNodesWithTag(TAG_SUGGESTIONS).fetchSemanticsNodes().size)
    }

    @Test fun aramadanSonraYeniYazi_tekrarYalnizcaOneriler() {
        field().performTextInput(oneriYazisi); idle(); waitTag(TAG_SUGGESTION_ROW)
        field().performImeAction(); waitResults()
        field().performTextInput("x"); idle()                // sonuçlar açıkken yeniden yazmaya başlamak
        rule.waitUntil(8_000) { rule.onAllNodesWithTag(TAG_SUGGESTIONS).fetchSemanticsNodes().isNotEmpty() }
        assertNoPriceAnywhere("yeniden yazı"); assertNormalScreenHidden("yeniden yazı")
    }
}

/** Mock veri kaynağı (mock sürümünün kullandığı MockRepository). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OneriModuMockTest : OneriModuTestBase() {
    override fun repo() = MockRepository(latencyMs = 0)
    override val oneriYazisi = "sü"
}

/** Kayıtlı API yanıtları: Rev3 yerel kopyasının gerçek yanıtları, gerçek Retrofit/OkHttp/RemoteRepository ile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OneriModuKayitliApiTest : OneriModuTestBase() {
    override val oneriYazisi = "kirlitest"
    private lateinit var server: MockWebServer
    private lateinit var remote: RemoteRepository
    override fun repo() = remote

    private fun res(name: String) = javaClass.getResourceAsStream("/api-yerel/$name")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    private val recs = Json.parseToJsonElement(res("index.json")).jsonObject["yanitlar"]!!.jsonArray.map { it.jsonObject }
    private fun body(file: String) = MockResponse().setHeader("Content-Type", "application/json; charset=utf-8").setBody(res(file))
    private fun file(path: String) = recs.first { it["yol"]!!.jsonPrimitive.content == path }["dosya"]!!.jsonPrimitive.content

    override fun prepare() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val u = request.requestUrl!!; val path = u.encodedPath.removePrefix("/api/v1/")
                return when (path) {
                    "markets" -> body(file("markets")); "categories" -> body(file("categories"))
                    "suggest" -> if (u.queryParameter("q") == "kirlitest") body(file("suggest?q=kirlitest"))
                                 else MockResponse().setHeader("Content-Type", "application/json").setBody("""{"success":true,"data":[],"meta":{}}""")
                    "products" -> body(if (u.queryParameter("q").isNullOrBlank()) file("products?limit=24") else file("products?q=Kirlitest&status=hepsi"))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        remote = RemoteRepository(ApiClient.create(ApiConfig(server.url("/api/v1/").toString(), requireHttps = false)))
    }
    @After fun stopServer() { server.shutdown() }
}
