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

/**
 * Veri kaynağı hatası: API hata kodlarının (A.3) ve ağ sorunlarının uygulamadaki karşılığı.
 * Mesajlar kullanıcıya gösterilebilir Türkçe metinlerdir; teknik ayrıntı (kod, HTTP durumu) ayrı alanlardadır.
 */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 404 NOT_FOUND: kayıt yok veya gizli. */
    class NotFound(what: String) : DataError("$what bulunamadı.")
    /** 503 SERVICE_UNAVAILABLE: sunucu veritabanına ulaşamıyor ya da hız sınırı deposu kullanılamıyor. */
    class Unavailable(val retryAfterSeconds: Long? = null) : DataError("Hizmet geçici olarak kullanılamıyor.")
    /** 429 RATE_LIMITED. */
    class RateLimited(val retryAfterSeconds: Long? = null) : DataError("Çok fazla istek gönderildi. Lütfen biraz bekleyin.")
    /** Bağlantı yok, zaman aşımı, DNS, TLS: sunucuya ulaşılamadı. */
    class Network(cause: Throwable? = null) : DataError("İnternet bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin.", cause)
    /** 500 ve beklenmeyen diğer sunucu durumları. */
    class Server(val httpStatus: Int, val code: String? = null) : DataError("Sunucuda beklenmeyen bir hata oluştu.")
    /** 400 / 404 ENDPOINT_NOT_FOUND / 405 / 413: uygulamanın gönderdiği istek hatalı (uygulama hatası; günlüğe yazılır). */
    class BadRequest(val httpStatus: Int, val code: String?, val serverMessage: String?) : DataError("İstek işlenemedi.")
    /** Yanıt sözleşmeye uymuyor (bozuk JSON, beklenmeyen yapı). */
    class Malformed(cause: Throwable? = null) : DataError("Sunucudan gelen veri okunamadı.", cause)
}

/**
 * Uygulamanın tek veri arayüzü. İki uygulaması var: MockRepository (gömülü örnek veri; testler ve mock derlemesi)
 * ve RemoteRepository (Rev3 API, FAZ 2.2.1). İkisi de aynı veri kurallarını (DataRules) uygular.
 * Hata durumunda DataError fırlatılır.
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
