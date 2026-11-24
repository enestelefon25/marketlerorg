package org.marketler.core.data

import org.marketler.core.model.Category
import org.marketler.core.model.CompareGroup
import org.marketler.core.model.Home
import org.marketler.core.model.Item
import org.marketler.core.model.Market
import org.marketler.core.model.ProductDetail
import org.marketler.core.model.Suggestion

/**
 * Eksik (NULL) veri kuralları — FAZ 2.2 U3 kararı. Hem API'den hem mock'tan gelen veriye aynı şekilde uygulanır.
 *
 * 1. Ürün adı boş/NULL → ürün LİSTELENMEZ (detayı da açılmaz: "Ürün bulunamadı").
 * 2. Fiyat NULL → ürün silinmez; "Fiyat belirtilmemiş" gösterilir (Item.priceLabel). NULL fiyat ASLA 0 TL sayılmaz.
 * 3. Fiyatı olmayan ürün fiyat KARŞILAŞTIRMASINA dahil edilmez. Böyle bir satır çıkarıldığında sunucunun o grup için
 *    hesapladığı özet (en ucuz işareti, fark, tasarruf cümlesi) NULL fiyatla hesaplandığı için GÖSTERİLMEZ; uygulama
 *    yerine yeni değer HESAPLAMAZ. Geriye 2'den az fiyatlı satır kalırsa grup gösterilmez.
 * 4. NULL market/katalog/kategori bilgisi çökmeye yol açmaz (modeller nullable; görünen metinlerde nötr karşılık).
 * 5. Hatalı bir kayıt diğerlerini engellemez (listeler öğe öğe okunur; kurallar öğe başına uygulanır).
 */
object DataRules {
    /** Karşılaştırma grubundan fiyatsız satır çıkarıldığında gösterilen açıklama (sunucunun hiç göndermediği bir durum). */
    const val COMPARE_NO_PRICE_REASON = "Karşılaştırma için yeterli fiyat bilgisi yok."

    fun isListable(item: Item): Boolean = !item.name.isNullOrBlank()

    fun items(list: List<Item>): List<Item> = list.filter(::isListable)

    fun compare(g: CompareGroup?): CompareGroup? {
        if (g == null) return null
        val named = g.rows.filter(::isListable)
        val priced = named.filter { it.hasPrice }
        if (priced.size == g.rows.size) return g
        if (priced.size < 2) return null
        return g.copy(rows = priced.map { it.copy(isBest = null) }, minPrice = null, maxPrice = null, diff = null, diffText = null, diffPct = null, savingText = null)
    }

    fun home(h: Home): Home = h.copy(
        markets = h.markets.filter { !it.slug.isNullOrBlank() },
        featured = h.featured.copy(items = items(h.featured.items)),
        topCompare = compare(h.topCompare),
    )

    fun markets(list: List<Market>): List<Market> = list.filter { !it.slug.isNullOrBlank() }
    fun categories(list: List<Category>): List<Category> = list.filter { !it.slug.isNullOrBlank() && !it.name.isNullOrBlank() }
    fun suggestions(list: List<Suggestion>): List<Suggestion> = list.filter { !it.name.isNullOrBlank() }

    /** Adsız ürünün detayı açılmaz. Fiyatsız ürünün fiyat geçmişi (NULL fiyatla hesaplanmış) gösterilmez. */
    fun detail(d: ProductDetail): ProductDetail? {
        if (!isListable(d.item)) return null
        val cmp = compare(d.compare)
        val reason = when {
            cmp != null -> null
            d.compare != null -> COMPARE_NO_PRICE_REASON
            else -> d.compareReason
        }
        return d.copy(history = if (d.item.hasPrice) d.history else null, compare = cmp, compareReason = reason)
    }
}
