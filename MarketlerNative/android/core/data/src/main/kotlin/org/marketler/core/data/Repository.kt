package org.marketler.core.data

import org.marketler.core.model.Category
import org.marketler.core.model.Home
import org.marketler.core.model.Item
import org.marketler.core.model.Market
import org.marketler.core.model.ProductDetail
import org.marketler.core.model.Suggestion

/** Ürün listesi sorgusu: API /products parametrelerinin karşılığı (ara.php). */
data class ProductQuery(
    val q: String = "",
    val market: String? = null,
    val category: String? = null,
    val status: Status = Status.AKTIF,
    val sort: Sort = Sort.NEW,
    val page: Int = 1,
    val limit: Int = 24,
) {
    enum class Status(val api: String, val label: String) { AKTIF("aktif", "Aktif"), YAKLASAN("yaklasan", "Yaklaşan"), DOLMUS("dolmus", "Süresi dolmuş"), HEPSI("hepsi", "Tümü") }
    enum class Sort(val api: String, val label: String) { NEW("new", "En yeni"), ASC("asc", "En düşük fiyat"), DESC("desc", "En yüksek fiyat") }
}

data class Page<T>(val items: List<T>, val page: Int, val limit: Int, val total: Int) {
    val hasMore: Boolean get() = page * limit < total
}

/** Veri kaynağı hatası: API hata kodlarının (A.3) uygulamadaki karşılığı. */
sealed class DataError(message: String) : Exception(message) {
    class NotFound(what: String) : DataError("$what bulunamadı.")
    class Unavailable : DataError("Hizmet geçici olarak kullanılamıyor.")
}

/**
 * Uygulamanın tek veri arayüzü. FAZ 2.1'de yalnızca MockRepository uygulanır (ağ bağlantısı YOK).
 * Sonraki aşamada aynı arayüzü Rev3 API'ye bağlanan bir uygulama karşılayacak; ekranlar değişmeyecek.
 */
interface MarketlerRepository {
    /** Verinin üretildiği an (API'de meta.generated_at). Ekranda "son güncelleme" olarak gösterilir. */
    val dataGeneratedAt: String?
    /** Mock modda true: arayüz bunu kullanıcıya açıkça belirtir. */
    val isMock: Boolean
    suspend fun home(): Home
    suspend fun markets(): List<Market>
    suspend fun categories(): List<Category>
    suspend fun products(query: ProductQuery): Page<Item>
    suspend fun suggest(q: String): List<Suggestion>
    suspend fun product(id: Long): ProductDetail
}
