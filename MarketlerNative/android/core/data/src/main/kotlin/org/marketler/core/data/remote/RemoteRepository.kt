package org.marketler.core.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import org.marketler.core.data.DataError
import org.marketler.core.data.DataRules
import org.marketler.core.data.MarketlerRepository
import org.marketler.core.data.Page
import org.marketler.core.data.ProductQuery
import org.marketler.core.model.Category
import org.marketler.core.model.CategoryListSerializer
import org.marketler.core.model.Home
import org.marketler.core.model.Item
import org.marketler.core.model.ItemListSerializer
import org.marketler.core.model.LenientDecoding
import org.marketler.core.model.Market
import org.marketler.core.model.MarketListSerializer
import org.marketler.core.model.ProductDetail
import org.marketler.core.model.Suggestion
import org.marketler.core.model.SuggestionListSerializer

/**
 * Rev3 API'ye bağlanan veri kaynağı (FAZ 2.2.1). MockRepository ile aynı arayüz ve aynı veri kuralları (DataRules).
 * Uygulama hiçbir fiyat/indirim/fark hesaplamaz; sunucunun değerini gösterir ya da (NULL ise) göstermez.
 *
 * @param log Atlanan/süzülen kayıtlar ve uygulama kaynaklı istek hataları için günlük (Android'de Log.w; testte liste).
 */
class RemoteRepository(
    private val api: MarketlerApi,
    private val log: (String) -> Unit = {},
) : MarketlerRepository {

    @Volatile private var generatedAt: String? = null
    override val dataGeneratedAt: String? get() = generatedAt
    override val isMock: Boolean = false

    override suspend fun home(): Home {
        val env = call("Ana sayfa") { api.home() }
        generatedAt = env.meta.generatedAt ?: generatedAt
        val h = decode("home", Home.serializer(), env.data)
        val clean = DataRules.home(h)
        report("home", h.featured.items.size - clean.featured.items.size, "adsız öne çıkan ürün")
        return clean
    }

    override suspend fun markets(): List<Market> = DataRules.markets(decode("markets", MarketListSerializer, call("Marketler") { api.markets() }.data))

    override suspend fun categories(): List<Category> = DataRules.categories(decode("categories", CategoryListSerializer, call("Kategoriler") { api.categories() }.data))

    override suspend fun products(query: ProductQuery): Page<Item> {
        val env = call("Ürünler") {
            api.products(query.q.trim().ifEmpty { null }, query.market, query.category, query.status.api, query.sort.api, query.page, query.limit.coerceIn(1, 50))
        }
        val raw = decode("products", ItemListSerializer, env.data)
        val items = DataRules.items(raw)
        report("products", raw.size - items.size, "adsız ürün")
        /* Sayfalama sunucunun meta değerleriyle yürür: süzülen kayıtlar sayfa sınırını kaydırmaz (tekrar/kayıp olmaz). */
        return Page(items, env.meta.page ?: query.page, env.meta.limit ?: query.limit, env.meta.total ?: raw.size)
    }

    override suspend fun suggest(q: String): List<Suggestion> {
        val t = q.trim()
        if (t.length < 2) return emptyList()       // sunucu kuralıyla aynı (≥2 karakter); boşuna istek gönderilmez
        return DataRules.suggestions(decode("suggest", SuggestionListSerializer, call("Öneriler") { api.suggest(t.take(120)) }.data))
    }

    override suspend fun product(id: Long): ProductDetail {
        val d = decode("product", ProductDetail.serializer(), call("Ürün") { api.product(id) }.data)
        return DataRules.detail(d) ?: run { log("product/$id: adı boş ürün gösterilmedi"); throw DataError.NotFound("Ürün") }
    }

    private fun <T> decode(where: String, s: KSerializer<T>, data: JsonElement?): T {
        val before = LenientDecoding.skipped.get()
        val v = try { ApiClient.json.decodeFromJsonElement(s, data ?: throw DataError.Malformed()) }
        catch (e: SerializationException) { throw DataError.Malformed(e) }
        catch (e: IllegalArgumentException) { throw DataError.Malformed(e) }
        report(where, (LenientDecoding.skipped.get() - before).toInt(), "yapısı bozuk kayıt")
        return v
    }

    private fun report(where: String, n: Int, what: String) { if (n > 0) log("$where: $n $what atlandı") }
}
