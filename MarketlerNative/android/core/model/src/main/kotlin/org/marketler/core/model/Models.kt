package org.marketler.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

/*
 * Marketler.org API v1 (Rev3) veri modelleri. Alan adları ve türleri docs/FAZ1-UC-NOKTALAR.md ile birebir aynıdır.
 * Kurallar: para değerleri sunucudan METİN gelir ("4599.00") ve BigDecimal olarak okunur; ekranda sunucunun *_text alanı
 * gösterilir. Uygulama fiyat, indirim, fark veya birim fiyat HESAPLAMAZ. Olmayan veri null kalır, uydurulmaz.
 *
 * FAZ 2.2.1 (U3 kararı): veritabanında DEFAULT NULL olan kolonların karşılığı olan alanlar nullable'dır. API bunları olduğu gibi
 * (null) gönderebilir; tek bir hatalı kayıt ayrıştırmayı bozmamalıdır. Listeler LenientListSerializer ile öğe öğe okunur: yapısı
 * bozuk bir öğe atlanır, diğerleri gösterilir. NULL fiyat ASLA 0 TL sayılmaz: fiyat yoksa price_text ("0,00 TL" gelebilir),
 * eski fiyat, indirim ve birim fiyat da gösterilmez (bkz. shown* alanları). Görsel alanları (image, cover, logo_url) ileride
 * kullanılmak üzere korunur.
 */

/** Yanıt zarfı (A.2). Ağ katmanı veriyi önce JsonElement olarak alır, sonra türüne göre (öğe öğe hoşgörülü) çözer. */
@Serializable
data class Envelope<T>(
    val success: Boolean,
    val data: T,
    val meta: Meta = Meta(),
)

/** Ağ yanıtının ham zarfı: başarılı yanıtta data, hata yanıtında error dolu (A.2 / A.3). */
@Serializable
data class ApiEnvelope(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val error: ApiErrorBody? = null,
    val meta: Meta = Meta(),
)

/** Hata gövdesi (A.3): {code, message}. Mesaj sabit Türkçe metindir. */
@Serializable
data class ApiErrorBody(val code: String = "", val message: String = "")

@Serializable
data class Meta(
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("api_version") val apiVersion: String? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
)

/**
 * Hoşgörülü liste: her öğe ayrı çözülür; yapısı bozuk öğe (yanlış tür vb.) atlanır ve sayılır.
 * Not: alanı null olan öğe "bozuk" değildir (alanlar nullable); iş kuralları (ör. adsız ürün) veri katmanında uygulanır.
 */
open class LenientListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)
    override val descriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<T> {
        val jd = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val arr = jd.decodeJsonElement() as? JsonArray ?: run { LenientDecoding.skipped.incrementAndGet(); return emptyList() }
        return arr.mapNotNull { e ->
            try { jd.json.decodeFromJsonElement(element, e) }
            catch (ex: SerializationException) { LenientDecoding.skipped.incrementAndGet(); null }
            catch (ex: IllegalArgumentException) { LenientDecoding.skipped.incrementAndGet(); null }
        }
    }
}

/** Atlanan (yapısı bozuk) öğe sayacı: veri katmanı günlüğe yazar ve testler doğrular. */
object LenientDecoding { val skipped = AtomicLong(0) }

object ItemListSerializer : LenientListSerializer<Item>(Item.serializer())
object MarketListSerializer : LenientListSerializer<Market>(Market.serializer())
object CategoryListSerializer : LenientListSerializer<Category>(Category.serializer())
object CatalogListSerializer : LenientListSerializer<CatalogSummary>(CatalogSummary.serializer())
object ShortcutListSerializer : LenientListSerializer<CategoryShortcut>(CategoryShortcut.serializer())
object SuggestionListSerializer : LenientListSerializer<Suggestion>(Suggestion.serializer())

@Serializable
data class MarketRef(val id: Long = 0, val slug: String? = null, val name: String? = null, val color: String? = null) {
    /** Görünen ad: ad yoksa kısa ad (slug), o da yoksa nötr "Market". */
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: slug?.takeIf { it.isNotBlank() }?.uppercase() ?: "Market"
}

@Serializable
data class Market(
    val id: Long = 0,
    val slug: String? = null,
    val name: String? = null,
    val color: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("live_count") val liveCount: Int = 0,
    @SerialName("soon_count") val soonCount: Int = 0,
) {
    val ref: MarketRef get() = MarketRef(id, slug, name, color)
    val label: String get() = ref.label
}

@Serializable
data class CategoryRef(val slug: String? = null, val name: String? = null)

@Serializable
data class Category(
    val slug: String? = null,
    val name: String? = null,
    val order: Int = 0,
    @SerialName("live_item_count") val liveItemCount: Int = 0,
)

@Serializable
data class UnitPrice(val value: String? = null, val unit: String? = null, val text: String? = null)

@Serializable
data class ImageRef(val url: String? = null)

@Serializable
data class CatalogRef(
    val id: Long? = null,
    val slug: String? = null,
    val title: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val status: String = "",
    @SerialName("status_text") val statusText: String = "",
)

/** Katalog durumu: live (aktif) · soon (yaklaşan) · past (süresi dolmuş) */
enum class CatalogStatus { LIVE, SOON, PAST, UNKNOWN;
    companion object { fun of(s: String) = when (s) { "live" -> LIVE; "soon" -> SOON; "past" -> PAST; else -> UNKNOWN } }
}

@Serializable
data class Item(
    val id: Long,
    @SerialName("fav_key") val favKey: String = "",
    val hash: String? = null,
    val name: String? = null,
    val category: CategoryRef = CategoryRef(),
    val market: MarketRef = MarketRef(),
    val price: String? = null,
    @SerialName("price_text") val priceText: String? = null,
    @SerialName("old_price") val oldPrice: String? = null,
    @SerialName("old_price_text") val oldPriceText: String? = null,
    @SerialName("discount_pct") val discountPct: Int? = null,
    @SerialName("unit_price") val unitPrice: UnitPrice? = null,
    val image: ImageRef? = null,
    val catalog: CatalogRef = CatalogRef(),
    @SerialName("campaign_text") val campaignText: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
    /* Bağlama göre gelen ek alanlar (yoksa null) */
    val note: JsonElement? = null,
    @SerialName("is_best") val isBest: Boolean? = null,
    @SerialName("history_text") val historyText: String? = null,
    @SerialName("unit_text") val unitText: String? = null,
) {
    /** Sunucunun fiyatı; yoksa veya sayı değilse null (ASLA 0 değil). */
    val priceValue: BigDecimal? get() = price?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
    val hasPrice: Boolean get() = priceValue != null
    /** Gösterilecek fiyat metni: fiyat yoksa null (sunucu NULL fiyat için "0,00 TL" gönderebilir; gösterilmez). */
    val shownPriceText: String? get() = if (hasPrice) priceText?.takeIf { it.isNotBlank() } else null
    val priceLabel: String get() = shownPriceText ?: PRICE_MISSING
    /** Eski fiyat, indirim ve birim fiyat yalnızca fiyat varsa (fiyat yokken sunucu %100 indirim üretebiliyor). */
    val shownOldPriceText: String? get() = if (hasPrice) oldPriceText?.takeIf { it.isNotBlank() } else null
    val shownDiscountPct: Int? get() = if (hasPrice && shownOldPriceText != null) discountPct else null
    val shownUnitPrice: UnitPrice? get() = if (hasPrice) unitPrice?.takeIf { !it.text.isNullOrBlank() } else null
    val nameText: String get() = name.orEmpty()
    val campaignLabel: String get() = campaignText.orEmpty()
    val catalogStatus: CatalogStatus get() = CatalogStatus.of(catalog.status)
    /** Ana sayfa öne çıkanlarının sunucu notu: metin, metin dizisi ya da yok. */
    val noteLines: List<String>
        get() = when (val n = note) {
            null, is JsonNull -> emptyList()
            is JsonPrimitive -> listOfNotNull(n.contentOrNull?.takeIf { it.isNotBlank() })
            is JsonArray -> n.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            else -> emptyList()
        }

    companion object { const val PRICE_MISSING = "Fiyat belirtilmemiş" }
}

@Serializable
data class CoverRef(val url: String? = null, @SerialName("full_url") val fullUrl: String? = null)

@Serializable
data class CatalogSummary(
    val id: Long = 0,
    val slug: String? = null,
    val title: String? = null,
    val label: String? = null,
    val market: MarketRef = MarketRef(),
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val status: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("date_text") val dateText: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
    val cover: CoverRef? = null,
    @SerialName("web_url") val webUrl: String? = null,
) {
    /** Başlık yoksa market adı (uydurma başlık üretilmez). */
    val titleLabel: String get() = title?.takeIf { it.isNotBlank() } ?: market.label
    val dateLabel: String get() = dateText.orEmpty()
}

@Serializable
data class CompareGroup(
    @SerialName("group_id") val groupId: String = "",
    val title: String? = null,
    @SerialName("is_manual") val isManual: Boolean = false,
    @Serializable(with = ItemListSerializer::class) val rows: List<Item> = emptyList(),
    @SerialName("min_price") val minPrice: String? = null,
    @SerialName("max_price") val maxPrice: String? = null,
    val diff: String? = null,
    @SerialName("diff_text") val diffText: String? = null,
    @SerialName("diff_pct") val diffPct: Int? = null,
    @SerialName("saving_text") val savingText: String? = null,
) {
    val titleLabel: String get() = title?.takeIf { it.isNotBlank() } ?: rows.firstOrNull { !it.name.isNullOrBlank() }?.nameText.orEmpty()
}

@Serializable
data class Featured(
    val title: String? = null,
    val subtitle: String? = null,
    @Serializable(with = ItemListSerializer::class) val items: List<Item> = emptyList(),
)

@Serializable
data class CategoryShortcut(val slug: String, val name: String)

@Serializable
data class Home(
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    @Serializable(with = MarketListSerializer::class) val markets: List<Market> = emptyList(),
    val featured: Featured = Featured(),
    @Serializable(with = CatalogListSerializer::class) @SerialName("live_catalogs") val liveCatalogs: List<CatalogSummary> = emptyList(),
    @Serializable(with = CatalogListSerializer::class) @SerialName("soon_catalogs") val soonCatalogs: List<CatalogSummary> = emptyList(),
    @SerialName("top_compare") val topCompare: CompareGroup? = null,
    @Serializable(with = ShortcutListSerializer::class) @SerialName("category_shortcuts") val categoryShortcuts: List<CategoryShortcut> = emptyList(),
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

/** /suggest öğesi. Not: sunucu NULL fiyatı "0,00 TL" olarak gönderir ve bu ayırt edilemez (bkz. FAZ2.2.1 raporu, B1). */
@Serializable
data class Suggestion(
    val name: String? = null,
    @SerialName("price_text") val priceText: String? = null,
    @SerialName("market_name") val marketName: String? = null,
    @SerialName("catalog_slug") val catalogSlug: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
)

/** Favori anahtarı (karar 4): "itemId:storeId:hash". Hash tek başına ürün kimliği DEĞİLDİR. */
data class FavKey(val itemId: Long, val storeId: Long, val hash: String) {
    override fun toString() = "$itemId:$storeId:$hash"
    companion object {
        private val RE = Regex("^([1-9][0-9]{0,9}):([1-9][0-9]{0,9}):([0-9a-f]{32})$")
        fun parse(s: String): FavKey? = RE.matchEntire(s)?.destructured?.let { (i, st, h) -> FavKey(i.toLong(), st.toLong(), h) }
    }
}
