package org.marketler.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

/*
 * Marketler.org API v1 (Rev3) veri modelleri. Alan adları ve türleri docs/FAZ1-UC-NOKTALAR.md ile birebir aynıdır.
 * Kurallar: para değerleri sunucudan METİN gelir ("4599.00") ve BigDecimal olarak okunur; ekranda sunucunun *_text alanı
 * gösterilir. Uygulama fiyat, indirim, fark veya birim fiyat HESAPLAMAZ. Olmayan veri null kalır, uydurulmaz.
 */

@Serializable
data class Envelope<T>(
    val success: Boolean,
    val data: T,
    val meta: Meta = Meta(),
)

@Serializable
data class Meta(
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("api_version") val apiVersion: String? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
)

@Serializable
data class MarketRef(val id: Long, val slug: String, val name: String, val color: String)

@Serializable
data class Market(
    val id: Long,
    val slug: String,
    val name: String,
    val color: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("live_count") val liveCount: Int = 0,
    @SerialName("soon_count") val soonCount: Int = 0,
)

@Serializable
data class CategoryRef(val slug: String? = null, val name: String)

@Serializable
data class Category(
    val slug: String,
    val name: String,
    val order: Int = 0,
    @SerialName("live_item_count") val liveItemCount: Int = 0,
)

@Serializable
data class UnitPrice(val value: String, val unit: String, val text: String)

@Serializable
data class ImageRef(val url: String)

@Serializable
data class CatalogRef(
    val id: Long? = null,
    val slug: String,
    val title: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val status: String,
    @SerialName("status_text") val statusText: String,
)

/** Katalog durumu: live (aktif) · soon (yaklaşan) · past (süresi dolmuş) */
enum class CatalogStatus { LIVE, SOON, PAST, UNKNOWN;
    companion object { fun of(s: String) = when (s) { "live" -> LIVE; "soon" -> SOON; "past" -> PAST; else -> UNKNOWN } }
}

@Serializable
data class Item(
    val id: Long,
    @SerialName("fav_key") val favKey: String,
    val hash: String? = null,
    val name: String,
    val category: CategoryRef,
    val market: MarketRef,
    val price: String,
    @SerialName("price_text") val priceText: String,
    @SerialName("old_price") val oldPrice: String? = null,
    @SerialName("old_price_text") val oldPriceText: String? = null,
    @SerialName("discount_pct") val discountPct: Int? = null,
    @SerialName("unit_price") val unitPrice: UnitPrice? = null,
    val image: ImageRef? = null,
    val catalog: CatalogRef,
    @SerialName("campaign_text") val campaignText: String,
    @SerialName("web_url") val webUrl: String,
    /* Bağlama göre gelen ek alanlar (yoksa null) */
    val note: JsonElement? = null,
    @SerialName("is_best") val isBest: Boolean? = null,
    @SerialName("history_text") val historyText: String? = null,
    @SerialName("unit_text") val unitText: String? = null,
) {
    val priceValue: BigDecimal get() = BigDecimal(price)
    val catalogStatus: CatalogStatus get() = CatalogStatus.of(catalog.status)
    /** Ana sayfa öne çıkanlarının sunucu notu: metin, metin dizisi ya da yok. */
    val noteLines: List<String>
        get() = when (val n = note) {
            null, is JsonNull -> emptyList()
            is JsonPrimitive -> listOfNotNull(n.contentOrNull?.takeIf { it.isNotBlank() })
            is JsonArray -> n.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            else -> emptyList()
        }
}

@Serializable
data class CoverRef(val url: String, @SerialName("full_url") val fullUrl: String)

@Serializable
data class CatalogSummary(
    val id: Long,
    val slug: String,
    val title: String,
    val label: String? = null,
    val market: MarketRef,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val status: String,
    @SerialName("status_text") val statusText: String,
    @SerialName("date_text") val dateText: String,
    @SerialName("item_count") val itemCount: Int,
    val cover: CoverRef? = null,
    @SerialName("web_url") val webUrl: String,
)

@Serializable
data class CompareGroup(
    @SerialName("group_id") val groupId: String,
    val title: String,
    @SerialName("is_manual") val isManual: Boolean,
    val rows: List<Item>,
    @SerialName("min_price") val minPrice: String,
    @SerialName("max_price") val maxPrice: String,
    val diff: String,
    @SerialName("diff_text") val diffText: String,
    @SerialName("diff_pct") val diffPct: Int,
    @SerialName("saving_text") val savingText: String? = null,
)

@Serializable
data class Featured(val title: String, val subtitle: String, val items: List<Item>)

@Serializable
data class CategoryShortcut(val slug: String, val name: String)

@Serializable
data class Home(
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    val markets: List<Market>,
    val featured: Featured,
    @SerialName("live_catalogs") val liveCatalogs: List<CatalogSummary>,
    @SerialName("soon_catalogs") val soonCatalogs: List<CatalogSummary>,
    @SerialName("top_compare") val topCompare: CompareGroup? = null,
    @SerialName("category_shortcuts") val categoryShortcuts: List<CategoryShortcut>,
)

@Serializable
data class PriceHistory(
    @SerialName("prev_price") val prevPrice: String? = null,
    @SerialName("prev_price_text") val prevPriceText: String? = null,
    @SerialName("prev_date") val prevDate: String? = null,
    @SerialName("change_pct") val changePct: Int = 0,
    @SerialName("is_lowest") val isLowest: Boolean = false,
    @SerialName("is_highest") val isHighest: Boolean = false,
    @SerialName("other_count") val otherCount: Int = 0,
    val text: String = "",
)

@Serializable
data class ProductDetail(
    val item: Item,
    val history: PriceHistory? = null,
    val compare: CompareGroup? = null,
    @SerialName("compare_reason") val compareReason: String? = null,
    val catalog: CatalogSummary? = null,
)

/** /suggest öğesi */
@Serializable
data class Suggestion(
    val name: String,
    @SerialName("price_text") val priceText: String,
    @SerialName("market_name") val marketName: String,
    @SerialName("catalog_slug") val catalogSlug: String,
    @SerialName("web_url") val webUrl: String,
)

/** Favori anahtarı (karar 4): "itemId:storeId:hash". Hash tek başına ürün kimliği DEĞİLDİR. */
data class FavKey(val itemId: Long, val storeId: Long, val hash: String) {
    override fun toString() = "$itemId:$storeId:$hash"
    companion object {
        private val RE = Regex("^([1-9][0-9]{0,9}):([1-9][0-9]{0,9}):([0-9a-f]{32})$")
        fun parse(s: String): FavKey? = RE.matchEntire(s)?.destructured?.let { (i, st, h) -> FavKey(i.toLong(), st.toLong(), h) }
    }
}
