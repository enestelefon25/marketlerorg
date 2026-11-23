package org.marketler.core.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.marketler.core.model.FavKey
import java.math.BigDecimal

class MockRepositoryTest {
    private val repo = MockRepository(latencyMs = 0)

    @Test fun mockVerisiRev3SozlesmesineGoreOkunur() = runTest {
        val home = repo.home()
        assertEquals(3, home.markets.size)
        assertTrue(home.featured.title.isNotBlank())
        assertTrue(home.featured.items.isNotEmpty())
        assertEquals(8, home.categoryShortcuts.size)
        assertEquals(14, repo.categories().size)
        assertNotNull(repo.dataGeneratedAt)
        assertTrue(repo.isMock)
    }

    @Test fun fiyatlarMetinOlarakGelirVeBigDecimalOkunur() = runTest {
        val all = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, limit = 500)).items
        assertTrue(all.size >= 60)
        all.forEach { assertEquals(it.price, BigDecimal(it.price).setScale(2).toPlainString()); assertTrue(it.priceText.endsWith(" TL")) }
    }

    @Test fun indirimYalnizcaSunucuVerdiyseVardir() = runTest {
        val all = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, limit = 500)).items
        all.forEach { if (it.discountPct != null) assertNotNull(it.oldPrice) else assertNull(it.oldPrice) }
    }

    @Test fun favoriAnahtariTekStandart() = runTest {
        repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, limit = 500)).items.forEach {
            val k = FavKey.parse(it.favKey) ?: return@forEach fail("geçersiz fav_key: ${it.favKey}")
            assertEquals(it.id, k.itemId); assertEquals(it.market.id, k.storeId); assertEquals(it.hash, k.hash)
        }
        assertNull(FavKey.parse("1:${"a".repeat(32)}"))          // eski storeId:hash biçimi
        assertNull(FavKey.parse("0:1:${"a".repeat(32)}"))
        assertNull(FavKey.parse("5:1:${"A".repeat(32)}"))
    }

    @Test fun aramaVarsayilanYalnizcaAktif() = runTest {
        val p = repo.products(ProductQuery(limit = 500))
        assertTrue(p.items.isNotEmpty()); p.items.forEach { assertEquals("live", it.catalog.status) }
    }

    @Test fun aramaKelimelerinHepsiAddaGecmeli() = runTest {
        val p = repo.products(ProductQuery(q = "süt", limit = 500, status = ProductQuery.Status.HEPSI))
        assertTrue(p.items.size >= 5); p.items.forEach { assertTrue(it.name.lowercase().contains("süt")) }
        assertEquals(0, repo.products(ProductQuery(q = "zzzqqq")).total)
    }

    @Test fun turkceBuyukKucukHarf() = runTest {
        val a = repo.products(ProductQuery(q = "şeker", status = ProductQuery.Status.HEPSI)).total
        val b = repo.products(ProductQuery(q = "ŞEKER", status = ProductQuery.Status.HEPSI)).total
        assertTrue(a > 0); assertEquals(a, b)
        assertTrue(repo.products(ProductQuery(q = "İnci", status = ProductQuery.Status.HEPSI)).total > 0)
    }

    @Test fun marketVeKategoriSuzgeci() = runTest {
        repo.products(ProductQuery(market = "bim", limit = 500)).items.forEach { assertEquals("bim", it.market.slug) }
        repo.products(ProductQuery(category = "gida", limit = 500)).items.forEach { assertEquals("gida", it.category.slug) }
    }

    @Test fun siralamaVeEsitlikteKalemNo() = runTest {
        val asc = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, sort = ProductQuery.Sort.ASC, limit = 500)).items
        for (i in 1 until asc.size) {
            val c = asc[i - 1].priceValue.compareTo(asc[i].priceValue)
            assertTrue(c <= 0); if (c == 0) assertTrue(asc[i - 1].id < asc[i].id)
        }
        val desc = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, sort = ProductQuery.Sort.DESC, limit = 500)).items
        for (i in 1 until desc.size) assertTrue(desc[i - 1].priceValue >= desc[i].priceValue)
    }

    @Test fun sayfalamadaTekrarVeKayipYok() = runTest {
        for (sort in ProductQuery.Sort.values()) for (lim in listOf(3, 5, 7, 24)) {
            val q = ProductQuery(status = ProductQuery.Status.HEPSI, sort = sort, limit = lim)
            val full = repo.products(q.copy(limit = 500)).items.map { it.id }
            val got = mutableListOf<Long>(); var page = 1
            while (true) { val p = repo.products(q.copy(page = page)); got += p.items.map { it.id }; if (!p.hasMore) break; page++ }
            assertEquals("$sort/$lim", full, got); assertEquals(got.size, got.toSet().size)
        }
    }

    @Test fun oneriKurallari() = runTest {
        assertTrue(repo.suggest("s").isEmpty())
        val s = repo.suggest("sü")
        assertTrue(s.isNotEmpty() && s.size <= 5)
        s.forEach { assertTrue(TurkishText.fold(it.name).contains("su")) }
        assertTrue(repo.suggest("isitek").isNotEmpty())              // ISITEK / Isıtek yazım farkı sadeleştirilir
        assertEquals(s.size, s.map { TurkishText.fold(it.name) + it.marketName }.toSet().size)
    }

    @Test fun urunDetayi() = runTest {
        val any = repo.products(ProductQuery(q = "airfryer", limit = 10)).items.first { it.market.slug == "a101" }
        val d = repo.product(any.id)
        assertEquals(any.id, d.item.id)
        assertNotNull(d.compare); assertTrue(d.compare!!.rows.size >= 2)
        assertTrue(d.compare!!.rows.first().isBest == true)
        try { repo.product(99_999_999); fail() } catch (e: DataError.NotFound) { }
    }

    @Test fun olmayanVeriUydurulmaz() = runTest {
        val all = repo.products(ProductQuery(status = ProductQuery.Status.HEPSI, limit = 500)).items
        assertTrue(all.any { it.image == null }); assertFalse(all.all { it.image != null })
    }

    @Test fun turkceSadelestirme() {
        assertEquals("isitek airfryer", TurkishText.fold("ISITEK Airfryer"))
        assertEquals("inci su 1.5 l", TurkishText.fold("İnci Su 1,5 L"))
        assertEquals("seker cay ogun", TurkishText.fold("Şeker Çay Öğün"))
    }
}
