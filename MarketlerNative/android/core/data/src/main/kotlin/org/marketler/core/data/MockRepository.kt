package org.marketler.core.data

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.marketler.core.model.Category
import org.marketler.core.model.Envelope
import org.marketler.core.model.Home
import org.marketler.core.model.Item
import org.marketler.core.model.Market
import org.marketler.core.model.ProductDetail
import org.marketler.core.model.Suggestion
import java.util.Locale

/**
 * MOCK veri kaynağı (FAZ 2.1; FAZ 2.2'de testler ve mock derlemesi için korunur). AĞ BAĞLANTISI YOKTUR: veriler uygulamaya gömülü JSON dosyalarından okunur
 * (core/data/src/main/resources/mock/). Dosyalar Rev3 API'nin yerel kopyasından SENTETİK veriyle üretildi
 * (MarketlerNative/tools/mock_veri_uret.php); yapı gerçek API yanıtlarıyla aynıdır.
 *
 * Arama ve öneri, sitedeki ara.php ve asistan.php?oneri=1 kurallarını izler. Bilinen yaklaşıklıklar:
 * - Büyük/küçük harf duyarsız karşılaştırma Türkçe kurallarla yapılır; gerçek API MySQL utf8mb4_general_ci karşılaştırmasını
 *   kullanır (bazı aksanlı harflerde ve "En yeni" sıralamasındaki ad sırasında küçük farklar olabilir).
 * - Katalog durumu (aktif/yaklaşan/süresi dolmuş) veri üretildiği güne göre sabittir.
 */
class MockRepository(
    private val readResource: (String) -> String = { name ->
        MockRepository::class.java.getResourceAsStream("/mock/$name")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Mock kaynağı bulunamadı: $name")
    },
    /** Yükleniyor durumlarını gerçekçi göstermek için yapay gecikme (ms). Testlerde 0. */
    private val latencyMs: Long = 250,
) : MarketlerRepository {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val tr = Locale.forLanguageTag("tr-TR")

    private val homeEnv by lazy { json.decodeFromString<Envelope<Home>>(readResource("home.json")) }
    private val marketsEnv by lazy { json.decodeFromString<Envelope<List<Market>>>(readResource("markets.json")) }
    private val categoriesEnv by lazy { json.decodeFromString<Envelope<List<Category>>>(readResource("categories.json")) }
    private val itemsEnv by lazy { json.decodeFromString<Envelope<List<Item>>>(readResource("items.json")) }
    private val details by lazy { json.decodeFromString<Envelope<List<ProductDetail>>>(readResource("product_details.json")).data.associateBy { it.item.id } }

    override val dataGeneratedAt: String? get() = homeEnv.meta.generatedAt
    override val isMock: Boolean = true

    private suspend fun wait() { if (latencyMs > 0) delay(latencyMs) }

    /* Gerçek API ile aynı veri kuralları (DataRules) uygulanır. */
    override suspend fun home(): Home { wait(); return DataRules.home(homeEnv.data) }
    override suspend fun markets(): List<Market> { wait(); return DataRules.markets(marketsEnv.data) }
    override suspend fun categories(): List<Category> { wait(); return DataRules.categories(categoriesEnv.data) }
    override suspend fun product(id: Long): ProductDetail { wait(); return details[id]?.let(DataRules::detail) ?: throw DataError.NotFound("Ürün") }

    /** ara.php kuralları: her kelime adda geçmeli; market, kategori, durum süzgeci; sıralama + eşitlikte kalem no (Rev3 i.id). */
    override suspend fun products(query: ProductQuery): Page<Item> {
        wait()
        val words = query.q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.lowercase(tr) }
        val filtered = DataRules.items(itemsEnv.data).filter { it ->
            val st = it.catalog.status
            val statusOk = when (query.status) {
                ProductQuery.Status.AKTIF -> st == "live"
                ProductQuery.Status.YAKLASAN -> st == "soon"
                ProductQuery.Status.DOLMUS -> st == "past"
                ProductQuery.Status.HEPSI -> true
            }
            val name = it.nameText.lowercase(tr)
            statusOk && (query.market == null || it.market.slug == query.market) && (query.category == null || it.category.slug == query.category) &&
                words.all { w -> name.contains(w) }
        }
        val sorted = when (query.sort) {
            /* MySQL gibi: NULL fiyat artan sırada başta, azalan sırada sonda. */
            ProductQuery.Sort.ASC -> filtered.sortedWith(compareBy<Item, java.math.BigDecimal?>(nullsFirst()) { it.priceValue }.thenBy { it.id })
            ProductQuery.Sort.DESC -> filtered.sortedWith(compareByDescending<Item, java.math.BigDecimal?>(nullsFirst()) { it.priceValue }.thenBy { it.id })
            ProductQuery.Sort.NEW -> filtered.sortedWith(compareByDescending<Item> { it.catalog.startDate }.thenBy { it.nameText.lowercase(tr) }.thenBy { it.id })
        }
        val from = ((query.page - 1) * query.limit).coerceAtMost(sorted.size)
        return Page(sorted.subList(from, (from + query.limit).coerceAtMost(sorted.size)), query.page, query.limit, sorted.size)
    }

    /** asistan.php?oneri=1 kuralları: en az 2 harf, yalnızca aktif + yaklaşan kataloglar, en çok 5, aynı ad+market tekrarlanmaz. */
    override suspend fun suggest(q: String): List<Suggestion> {
        val qf = TurkishText.fold(q.trim())
        if (qf.length < 2) return emptyList()
        wait()
        val tokens = qf.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val pool = DataRules.items(itemsEnv.data).filter { it.catalog.status != "past" }.sortedWith(compareByDescending<Item> { it.catalog.startDate }.thenByDescending { it.id })
        data class Hit(val score: Int, val len: Int, val item: Item, val folded: String)
        val hits = pool.mapNotNull { item ->
            val f = TurkishText.fold(item.nameText)
            var sc = 0
            for (t in tokens) {
                val pos = f.indexOf(t)
                if (pos < 0) return@mapNotNull null
                sc += if (pos == 0 || f[pos - 1] == ' ') 2 else 1
                val end = pos + t.length
                if (end >= f.length || f[end] == ' ') sc += 1
            }
            if (f.startsWith(qf)) sc += 2
            Hit(sc, item.nameText.length, item, f)
        }.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.len })
        val seen = HashSet<String>()
        return hits.asSequence().filter { seen.add(it.folded + "|" + it.item.market.name) }.take(5).map {
            Suggestion(it.item.name, it.item.shownPriceText, it.item.market.name, it.item.catalog.slug, it.item.webUrl)
        }.toList()
    }
}

/** asistan-motor.php as_fold() karşılığı: Türkçe karakterleri sadeleştirir, küçük harfe çevirir, ondalık virgülü noktaya çevirir. */
object TurkishText {
    private val tr = Locale.forLanguageTag("tr-TR")
    fun fold(s: String): String {
        val lower = s.replace("İ", "i").replace("I", "ı").lowercase(tr)
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(when (c) { 'ı' -> 'i'; 'ğ' -> 'g'; 'ü' -> 'u'; 'ş' -> 's'; 'ö' -> 'o'; 'ç' -> 'c'; 'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'; 'é' -> 'e'; else -> c })
        return sb.toString().replace(Regex("(\\d)\\s*,\\s*(\\d)"), "$1.$2")
    }
}
