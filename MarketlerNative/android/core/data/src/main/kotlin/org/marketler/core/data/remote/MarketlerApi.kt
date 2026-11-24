package org.marketler.core.data.remote

import org.marketler.core.model.ApiEnvelope
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Marketler.org API v1 (Rev3) — FAZ 2.2 kapsamındaki salt okunur uç noktalar (docs/FAZ1-UC-NOKTALAR.md 1, 2, 6, 7, 8, 10).
 * Yanıt gövdesi ham zarf olarak alınır; veri türüne göre RemoteRepository'de öğe öğe (hoşgörülü) çözülür.
 * null parametreler isteğe eklenmez (Retrofit kuralı) → API varsayılanı geçerli olur.
 */
interface MarketlerApi {
    @GET("home") suspend fun home(): Response<ApiEnvelope>
    @GET("markets") suspend fun markets(): Response<ApiEnvelope>
    @GET("categories") suspend fun categories(): Response<ApiEnvelope>

    /** Liste modu (ara.php). status: aktif|yaklasan|dolmus|hepsi · sort: new|asc|desc · limit 1–50. */
    @GET("products")
    suspend fun products(
        @Query("q") q: String?,
        @Query("market") market: String?,
        @Query("category") category: String?,
        @Query("status") status: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): Response<ApiEnvelope>

    @GET("products/{id}") suspend fun product(@Path("id") id: Long): Response<ApiEnvelope>

    /** 2–120 karakter; daha kısa sorguya sunucu boş liste döndürür. */
    @GET("suggest") suspend fun suggest(@Query("q") q: String): Response<ApiEnvelope>
}
