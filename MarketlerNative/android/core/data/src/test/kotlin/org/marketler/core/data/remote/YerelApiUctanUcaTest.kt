package org.marketler.core.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.marketler.core.data.DataError
import org.marketler.core.data.ProductQuery

/**
 * Uçtan uca test: ÇALIŞAN yerel Rev3 API kopyasına (php -S + MariaDB, sentetik ve kirli veri) gerçek HTTP istekleri.
 * Yalnızca MK_YEREL_API ortam değişkeni verilirse çalışır (ör. http://127.0.0.1:8099/api/v1/); yoksa atlanır.
 * Sunucu: php MarketlerNative/tools/api_test_verisi_uret.php --site=... --acik-kal=600
 * Canlı sisteme ASLA bağlanmaz: adres yalnızca 127.0.0.1 / localhost olabilir.
 */
class YerelApiUctanUcaTest {
    private val base = System.getenv("MK_YEREL_API")
    private lateinit var repo: RemoteRepository
    private val logs = mutableListOf<String>()

    @Before fun setUp() {
        assumeTrue("MK_YEREL_API verilmedi; uçtan uca test atlandı", !base.isNullOrBlank())
        val host = base!!.toHttpUrl().host
        require(host == "127.0.0.1" || host == "localhost") { "Yalnızca yerel API: $host" }
        repo = RemoteRepository(ApiClient.create(ApiConfig(base!!, requireHttps = false))) { logs += it }
    }

    @Test fun anaSayfaAramaDetayOneri() = runTest {
        val h = repo.home()
        assertTrue(h.markets.isNotEmpty() && h.featured.items.isNotEmpty())
        val first = repo.products(ProductQuery(limit = 5))
        assertTrue(first.items.isNotEmpty() && first.total >= first.items.size)
        val d = repo.product(first.items.first().id)
        assertEquals(first.items.first().id, d.item.id)
        assertTrue(repo.suggest("sü").isNotEmpty())
        assertTrue(repo.categories().isNotEmpty() && repo.markets().isNotEmpty())
    }

    @Test fun sayfalamadaTekrarYok_adsizlarSuzulur() = runTest {
        for (sort in ProductQuery.Sort.values()) {
            val ids = mutableListOf<Long>(); var page = 1; var total = 0
            while (true) {
                val p = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, sort = sort, limit = 7, page = page))
                ids += p.items.map { it.id }; total = p.total; if (!p.hasMore) break; page++
            }
            assertEquals("$sort", ids.size, ids.toSet().size)
            assertTrue(ids.size <= total)
        }
        assertTrue(logs.any { it.contains("adsız ürün") })
    }

    @Test fun aramaVeSuzgecler() = runTest {
        val p = repo.products(ProductQuery(q = "Kirlitest", status = ProductQuery.Status.HEPSI, limit = 50))
        assertTrue(p.items.isNotEmpty()); assertTrue(p.items.all { it.nameText.contains("Kirlitest") })
        assertTrue(p.items.any { !it.hasPrice && it.shownPriceText == null })
        repo.products(ProductQuery(market = "bim", limit = 50)).items.forEach { assertEquals("bim", it.market.slug) }
        assertEquals(0, repo.products(ProductQuery(q = "zzzqqq")).total)
    }

    @Test fun gercekHataYanitlari() = runTest {
        try { repo.product(99_999_999); fail() } catch (e: DataError.NotFound) { }
        assertFalse(repo.suggest("a").isNotEmpty())
    }
}
