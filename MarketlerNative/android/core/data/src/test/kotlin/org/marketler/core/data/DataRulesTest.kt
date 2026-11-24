package org.marketler.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.marketler.core.model.CompareGroup
import org.marketler.core.model.Item
import org.marketler.core.model.MarketRef
import org.marketler.core.model.PriceHistory
import org.marketler.core.model.ProductDetail

class DataRulesTest {
    private fun item(id: Long, name: String?, price: String?, best: Boolean? = null) =
        Item(id = id, name = name, price = price, priceText = price?.let { "$it TL" } ?: "0,00 TL", market = MarketRef(id, "m$id", "M$id", "#000000"), isBest = best)
    private fun group(vararg rows: Item) = CompareGroup(groupId = "1", title = "T", rows = rows.toList(), minPrice = "1.00", maxPrice = "2.00", diff = "1.00", diffText = "1,00 TL", diffPct = 50, savingText = "En ucuz markette 1,00 TL (%50) daha az.")

    @Test fun fiyatliGrupAynenKalir() {
        val g = group(item(1, "A", "1.00", true), item(2, "A", "2.00", false))
        assertSame(g, DataRules.compare(g))
    }

    @Test fun fiyatsizSatirCikarilir_ozetGosterilmez_hesaplanmaz() {
        val g = DataRules.compare(group(item(1, "A", null, true), item(2, "A", "2.00", false), item(3, "A", "3.00", false)))!!
        assertEquals(listOf(2L, 3L), g.rows.map { it.id })
        assertTrue(g.rows.all { it.isBest == null })
        assertNull(g.savingText); assertNull(g.diff); assertNull(g.diffText); assertNull(g.diffPct); assertNull(g.minPrice); assertNull(g.maxPrice)
    }

    @Test fun ikidenAzFiyatliKalirsaGrupGosterilmez() {
        assertNull(DataRules.compare(group(item(1, "A", null), item(2, "A", "2.00"))))
        assertNull(DataRules.compare(group(item(1, "", "1.00"), item(2, "A", "2.00"))))
    }

    @Test fun detayKurallari() {
        assertNull(DataRules.detail(ProductDetail(item(1, null, "1.00"))))
        assertNull(DataRules.detail(ProductDetail(item(1, "  ", "1.00"))))
        val d = DataRules.detail(ProductDetail(item(1, "A", null), history = PriceHistory(text = "x"), compare = group(item(1, "A", null), item(2, "A", "2.00"))))!!
        assertNull(d.history); assertNull(d.compare); assertEquals(DataRules.COMPARE_NO_PRICE_REASON, d.compareReason)
        val n = DataRules.detail(ProductDetail(item(1, "A", "1.00"), compareReason = "sunucu"))!!
        assertEquals("sunucu", n.compareReason)
    }

    @Test fun nullFiyatAsla0TLDegil() {
        val i = item(1, "A", null)
        assertNull(i.priceValue); assertNull(i.shownPriceText); assertEquals("Fiyat belirtilmemiş", i.priceLabel)
        assertNull(item(1, "A", "abc").priceValue)
        assertEquals(java.math.BigDecimal("0.00"), item(1, "A", "0.00").priceValue)   // sunucu gerçekten 0.00 gönderirse o değerdir
    }
}
