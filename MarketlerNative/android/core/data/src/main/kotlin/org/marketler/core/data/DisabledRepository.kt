package org.marketler.core.data

import org.marketler.core.model.Category
import org.marketler.core.model.Home
import org.marketler.core.model.Item
import org.marketler.core.model.Market
import org.marketler.core.model.ProductDetail
import org.marketler.core.model.Suggestion

/**
 * Kapalı veri kaynağı: hiçbir ağ isteği göndermez, her çağrıda DataError.Disabled verir.
 * "canli" sürümü, canlıya alma onayına kadar (CANLI_API_ACIK=false) bunu kullanır: uygulama açılır, ekranlar
 * açık bir "henüz yayında değil" mesajı gösterir; marketler.org'a istek gitmez.
 */
class DisabledRepository(private val reason: String) : MarketlerRepository {
    override val dataGeneratedAt: String? = null
    override val isMock: Boolean = false
    private fun no(): Nothing = throw DataError.Disabled(reason)
    override suspend fun home(): Home = no()
    override suspend fun markets(): List<Market> = no()
    override suspend fun categories(): List<Category> = no()
    override suspend fun products(query: ProductQuery): Page<Item> = no()
    override suspend fun suggest(q: String): List<Suggestion> = emptyList()
    override suspend fun product(id: Long): ProductDetail = no()
}
