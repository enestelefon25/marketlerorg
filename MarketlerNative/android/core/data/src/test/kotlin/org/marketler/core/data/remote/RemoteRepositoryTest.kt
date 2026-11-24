package org.marketler.core.data.remote

import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.marketler.core.data.DataError
import org.marketler.core.data.DataRules
import org.marketler.core.data.ProductQuery
import org.marketler.core.model.Item
import java.util.concurrent.TimeUnit

/**
 * Ağ katmanı testleri. Sahte sunucu (MockWebServer), Rev3 API'nin YEREL kopyasından kaydedilmiş GERÇEK HTTP yanıtlarını
 * (gövde, durum kodu, Retry-After) oynatır: src/test/resources/api-yerel (tools/api_test_verisi_uret.php).
 * Kayıtlarda bilerek eklenmiş NULL alanlı "kirli" kayıtlar vardır (index.json → kirli_kayitlar).
 */
class RemoteRepositoryTest {
    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()
    private lateinit var repo: RemoteRepository
    private lateinit var api: MarketlerApi

    private data class Rec(val path: String, val method: String, val http: Int, val retryAfter: String?, val file: String)
    private val index = Json.parseToJsonElement(res("index.json")).jsonObject
    private val recs = index["yanitlar"]!!.jsonArray.map { it.jsonObject }.map {
        Rec(it["yol"]!!.jsonPrimitive.content, it["yontem"]!!.jsonPrimitive.content, it["http"]!!.jsonPrimitive.int,
            it["basliklar"]!!.jsonObject["retry-after"]?.jsonPrimitive?.content, it["dosya"]!!.jsonPrimitive.content)
    }
    private val dirty = index["kirli_kayitlar"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content.toLong() }

    private fun res(name: String) = javaClass.getResourceAsStream("/api-yerel/$name")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun replay(r: Rec) = MockResponse().setResponseCode(r.http).setHeader("Content-Type", "application/json; charset=utf-8")
        .apply { r.retryAfter?.let { setHeader("Retry-After", it) } }.setBody(res(r.file))
    private fun rec(name: String) = recs.first { it.file == "$name.json" }

    /** Kayıtlı yanıtlar, isteğin yolu + sorgusu birebir eşleşirse oynatılır; özel yanıtlar `override` ile verilir. */
    private var override: ((RecordedRequest) -> MockResponse?)? = null

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                override?.invoke(request)?.let { return it }
                val u = request.requestUrl!!
                val path = u.encodedPath.removePrefix("/api/v1/")
                val params = u.queryParameterNames.associateWith { u.queryParameter(it).orEmpty() }
                val r = recs.firstOrNull { it.method == request.method && it.path.substringBefore('?') == path && norm(path, query(it.path)) == norm(path, params) }
                    ?: return MockResponse().setResponseCode(599).setBody("kayıt yok: $path $params")
                return replay(r)
            }
        }
        server.start()
        api = ApiClient.create(ApiConfig(server.url("/api/v1/").toString(), requireHttps = false, readTimeoutSec = 2, connectTimeoutSec = 2))
        repo = RemoteRepository(api) { logs += it }
    }
    @After fun tearDown() { server.shutdown() }
    private fun query(p: String): Map<String, String> = p.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
        .associate { kv -> java.net.URLDecoder.decode(kv.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }
    /** Sözleşmedeki varsayılanlar (status=aktif, sort=new, page=1, limit=24) eklenerek karşılaştırılır: parametre sırası önemsiz. */
    private fun norm(path: String, m: Map<String, String>) = if (path == "products") mapOf("status" to "aktif", "sort" to "new", "page" to "1", "limit" to "24") + m else m

    private suspend fun allItems(): List<Item> {
        val out = mutableListOf<Item>(); var page = 1
        while (true) { val p = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, limit = 50, page = page)); out += p.items; if (!p.hasMore) break; page++ }
        return out
    }

    /* ---------- sözleşme: gerçek yanıtlar çözülüyor ---------- */

    @Test fun anaSayfaGercekYanittanOkunur() = runTest {
        val h = repo.home()
        assertTrue(h.markets.size >= 3); assertTrue(h.featured.items.isNotEmpty()); assertEquals(8, h.categoryShortcuts.size)
        assertNotNull(h.topCompare); assertTrue(h.topCompare!!.rows.size >= 2); assertNotNull(h.topCompare!!.savingText)
        assertNotNull(repo.dataGeneratedAt); assertFalse(repo.isMock)
    }

    @Test fun istekParametreleriSozlesmeyeUygun() = runTest {
        repo.products(ProductQuery())
        val r1 = server.takeRequest(); val u = r1.requestUrl!!
        assertEquals("application/json", r1.getHeader("Accept")); assertEquals("MarketlerAndroid", r1.getHeader("User-Agent"))
        assertEquals("/api/v1/products", u.encodedPath)
        assertNull(u.queryParameter("q")); assertNull(u.queryParameter("market")); assertNull(u.queryParameter("category"))
        assertEquals("aktif", u.queryParameter("status")); assertEquals("new", u.queryParameter("sort"))
        assertEquals("1", u.queryParameter("page")); assertEquals("24", u.queryParameter("limit"))
        override = { MockResponse().setBody("""{"success":true,"data":[],"meta":{"page":1,"limit":50,"total":0}}""") }
        repo.products(ProductQuery(q = "  Süt şeker ", market = "bim", category = "gida", status = ProductQuery.Status.DOLMUS, sort = ProductQuery.Sort.DESC, limit = 999))
        val u2 = server.takeRequest().requestUrl!!
        assertEquals("Süt şeker", u2.queryParameter("q")); assertEquals("bim", u2.queryParameter("market")); assertEquals("gida", u2.queryParameter("category"))
        assertEquals("dolmus", u2.queryParameter("status")); assertEquals("desc", u2.queryParameter("sort")); assertEquals("50", u2.queryParameter("limit"))
    }

    /* ---------- U3: NULL veri kuralları ---------- */

    @Test fun adiBosUrunListelenmez_digerleriGosterilir() = runTest {
        val all = allItems()
        assertTrue(all.size > 60)
        assertFalse(all.any { it.id == dirty["ad_null"] || it.id == dirty["ad_bos"] })
        assertTrue(all.all { !it.name.isNullOrBlank() })
        assertTrue(logs.any { it.contains("adsız ürün") })
    }

    @Test fun fiyatNullUrunSilinmez_veSifirTLGosterilmez() = runTest {
        val all = allItems()
        val noPrice = all.first { it.id == dirty["fiyat_null_bim"] }
        assertNull(noPrice.price); assertEquals("0,00 TL", noPrice.priceText)           // sunucunun gönderdiği (B1)
        assertFalse(noPrice.hasPrice); assertNull(noPrice.priceValue); assertNull(noPrice.shownPriceText)
        assertEquals(Item.PRICE_MISSING, noPrice.priceLabel)
        val withOld = all.first { it.id == dirty["fiyat_null_eski_var"] }
        assertEquals(100, withOld.discountPct); assertEquals("25,00 TL", withOld.oldPriceText)  // sunucunun gönderdiği (B2)
        assertNull(withOld.shownOldPriceText); assertNull(withOld.shownDiscountPct); assertEquals(Item.PRICE_MISSING, withOld.priceLabel)
        assertTrue(all.filter { it.hasPrice }.none { it.priceLabel == "0,00 TL" })
    }

    @Test fun fiyatsizUrunKarsilastirmayaDahilEdilmez() = runTest {
        val raw = Json.parseToJsonElement(res(rec("product_fiyat_var_a101").file)).jsonObject["data"]!!.jsonObject["compare"]!!.jsonObject
        assertEquals(2, raw["rows"]!!.jsonArray.size)                                     // sunucu grubu NULL fiyatlı satırla döndürüyor (B3)
        val d = repo.product(dirty["fiyat_var_a101"]!!)
        assertNull(d.compare); assertEquals(DataRules.COMPARE_NO_PRICE_REASON, d.compareReason)
        val d2 = repo.product(dirty["fiyat_null_bim"]!!)
        assertEquals(Item.PRICE_MISSING, d2.item.priceLabel); assertNull(d2.compare); assertNull(d2.history)
    }

    @Test fun normalKarsilastirmaDegismedenGelir() = runTest {
        val d = repo.product(rec("product_normal_karsilastirmali").path.substringAfter("products/").toLong())
        val g = d.compare!!
        assertEquals(3, g.rows.size); assertTrue(g.rows.first().isBest == true); assertNotNull(g.savingText); assertNull(d.compareReason)
    }

    @Test fun adiBosUrunDetayiAcilmaz() = runTest {
        try { repo.product(dirty["ad_null"]!!); fail() } catch (e: DataError.NotFound) { }
        try { repo.product(dirty["ad_bos"]!!); fail() } catch (e: DataError.NotFound) { }
    }

    @Test fun nullMarketKatalogKategoriCokmeyeYolAcmaz() = runTest {
        val m = repo.product(dirty["market_null"]!!)
        assertNull(m.item.market.name); assertNull(m.item.market.color); assertEquals("KIRLI", m.item.market.label)
        assertNull(m.item.catalog.title); assertEquals("KIRLI", m.catalog!!.titleLabel)
        val k = repo.product(dirty["kategori_null"]!!)
        assertNull(k.item.category.name); assertNull(k.item.category.slug)
        val h = repo.product(dirty["hash_null"]!!)
        assertNull(h.item.hash); assertEquals("${h.item.id}:3:", h.item.favKey)     // geçersiz favori anahtarı (B5)
        val home = repo.home()
        assertTrue(home.markets.any { it.slug == "kirli" && it.name == null })
        assertTrue(home.liveCatalogs.any { it.title == null && it.titleLabel == "KIRLI" })
        assertFalse(repo.categories().any { it.slug == null })
    }

    @Test fun oneriler() = runTest {
        val s = repo.suggest("kirlitest")
        assertTrue(s.isNotEmpty() && s.size <= 5); assertTrue(s.all { !it.name.isNullOrBlank() })
        val before = server.requestCount
        assertTrue(repo.suggest(" s ").isEmpty()); assertEquals(before, server.requestCount)   // <2 karakter: istek gönderilmez
    }

    /* ---------- hata eşlemesi (A.3) ---------- */

    @Test fun hataKodlari() = runTest {
        try { repo.product(99_999_999); fail() } catch (e: DataError.NotFound) { assertEquals("Ürün bulunamadı.", e.message) }
        fun use(name: String) { val r = rec(name); override = { replay(r) } }
        use("hata_400_limit"); try { repo.home(); fail() } catch (e: DataError.BadRequest) { assertEquals(400, e.httpStatus); assertEquals("INVALID_PARAMETER", e.code) }
        use("hata_405_yontem"); try { repo.home(); fail() } catch (e: DataError.BadRequest) { assertEquals(405, e.httpStatus) }
        use("hata_404_yol"); try { repo.home(); fail() } catch (e: DataError.BadRequest) { assertEquals("ENDPOINT_NOT_FOUND", e.code) }
        use("hata_429_hiz"); try { repo.home(); fail() } catch (e: DataError.RateLimited) { assertEquals(rec("hata_429_hiz").retryAfter?.toLong(), e.retryAfterSeconds) }
        use("hata_503_veritabani"); try { repo.home(); fail() } catch (e: DataError.Unavailable) { assertEquals(120L, e.retryAfterSeconds) }
        override = { MockResponse().setResponseCode(500).setBody("""{"success":false,"error":{"code":"INTERNAL_ERROR","message":"x"}}""") }
        try { repo.home(); fail() } catch (e: DataError.Server) { assertEquals(500, e.httpStatus); assertEquals("INTERNAL_ERROR", e.code) }
        override = { MockResponse().setResponseCode(301).setHeader("Location", "http://baska.example/") }
        try { repo.home(); fail() } catch (e: DataError.Server) { assertEquals(301, e.httpStatus) }   // yönlendirme izlenmez
    }

    @Test fun bozukYanitlar() = runTest {
        override = { MockResponse().setBody("<html>bakım</html>") }
        try { repo.home(); fail() } catch (e: DataError.Malformed) { }
        override = { MockResponse().setBody("""{"success":false,"meta":{}}""") }
        try { repo.markets(); fail() } catch (e: DataError.Malformed) { }
        override = { MockResponse().setBody("""{"success":true,"data":{"item":{"name":"x"}}}""") }   // zorunlu id yok
        try { repo.product(1); fail() } catch (e: DataError.Malformed) { }
    }

    @Test fun yapisiBozukOgeDigerleriniEngellemez() = runTest {
        override = { MockResponse().setBody("""{"success":true,"data":[{"id":"abc","name":"bozuk"},{"id":7,"name":"Sağlam Ürün","price":"5.00","price_text":"5,00 TL"},42],"meta":{"page":1,"limit":24,"total":3}}""") }
        val p = repo.products(ProductQuery())
        assertEquals(listOf(7L), p.items.map { it.id }); assertEquals(3, p.total)
        assertTrue(logs.any { it.contains("2 yapısı bozuk kayıt") })
    }

    @Test fun agHatalari() = runTest {
        override = { MockResponse().setBody("{}").setHeadersDelay(5, TimeUnit.SECONDS) }
        try { repo.home(); fail() } catch (e: DataError.Network) { }          // zaman aşımı
        server.shutdown()
        try { repo.home(); fail() } catch (e: DataError.Network) { }          // sunucuya ulaşılamıyor
    }

    @Test fun uretimdeYalnizcaHttps() {
        try { ApiClient.create(ApiConfig("http://marketler.org/api/v1/")); fail() } catch (e: IllegalArgumentException) { }
        ApiClient.create(ApiConfig("https://marketler.org/api/v1/"))
    }
}
