package org.marketler.core.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.marketler.core.data.DataError
import org.marketler.core.model.ApiEnvelope
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ağ katmanı ayarları. Adres derleme türünden gelir (FAZ 2.2.2); bu katman adres içermez, gizli anahtar kullanmaz. */
data class ApiConfig(
    /** "/" ile biten taban adres, ör. https://marketler.org/api/v1/ */
    val baseUrl: String,
    /** Üretimde true: yalnızca HTTPS kabul edilir (http adresiyle istemci oluşturulamaz). */
    val requireHttps: Boolean = true,
    /** İstek gönderilebilecek TEK alan adı (taban adresin alan adı). Başka adrese giden her istek ağa çıkmadan reddedilir. */
    val lockHost: Boolean = true,
    /** Sunucunun ETag / Cache-Control yanıtları için HTTP önbelleği (null = önbellek yok). */
    val cacheDir: File? = null,
    val cacheBytes: Long = 10L * 1024 * 1024,
    val userAgent: String = "MarketlerAndroid",
    val connectTimeoutSec: Long = 10,
    val readTimeoutSec: Long = 20,
)

object ApiClient {
    /** Sözleşmeye sonradan eklenen alanlar uygulamayı bozmaz; null alan açıkça yazılmaz. */
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    fun create(config: ApiConfig, client: OkHttpClient? = null): MarketlerApi {
        val url = config.baseUrl.toHttpUrl()
        require(url.toString().endsWith("/")) { "baseUrl '/' ile bitmeli" }
        require(!config.requireHttps || url.isHttps) { "Yalnızca HTTPS adresine izin var: ${url.host}" }
        val host = url.host
        val http = client ?: OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(config.connectTimeoutSec + config.readTimeoutSec + 5, TimeUnit.SECONDS)
            .followRedirects(false)                     // yönlendirme ile başka adrese/HTTP'ye geçiş yok
            .apply { config.cacheDir?.let { cache(Cache(it, config.cacheBytes)) } }
            .addInterceptor { chain ->
                val u = chain.request().url
                /* Alan adı ve şema kilidi: yalnızca taban adresin alan adı; üretimde yalnızca HTTPS. Ağa çıkmadan reddedilir. */
                if (config.lockHost && u.host != host) throw IOException("İzin verilmeyen adres: ${u.host}")
                if (config.requireHttps && !u.isHttps) throw IOException("Yalnızca HTTPS: ${u.host}")
                chain.proceed(chain.request().newBuilder().header("Accept", "application/json").header("User-Agent", config.userAgent).build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()
            .create(MarketlerApi::class.java)
    }
}

/**
 * Tek çağrı noktası: HTTP / ağ / ayrıştırma sonucunu ya başarılı zarfa ya da DataError'a çevirir (A.3 eşlemesi).
 * - 200 + success:true → zarf
 * - 404 NOT_FOUND → NotFound · 429 → RateLimited · 503 → Unavailable (Retry-After saniyesiyle)
 * - 400 / 405 / 413 / 404 ENDPOINT_NOT_FOUND → BadRequest (uygulamanın isteği hatalı)
 * - 500 ve diğerleri → Server · bağlantı/zaman aşımı → Network · bozuk gövde → Malformed
 */
internal suspend fun call(what: String, block: suspend () -> Response<ApiEnvelope>): ApiEnvelope {
    val resp = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: SerializationException) { throw DataError.Malformed(e) }
    catch (e: IllegalArgumentException) { throw DataError.Malformed(e) }   // kotlinx: eksik zorunlu alan vb.
    catch (e: IOException) { throw DataError.Network(e) }
    if (resp.isSuccessful) {
        val env = resp.body() ?: throw DataError.Malformed()
        if (!env.success || env.data == null) throw DataError.Malformed()
        return env
    }
    val err = try { resp.errorBody()?.string()?.let { ApiClient.json.decodeFromString(ApiEnvelope.serializer(), it).error } } catch (e: Exception) { null }
    val retryAfter = resp.headers()["Retry-After"]?.trim()?.toLongOrNull()
    throw when (resp.code()) {
        404 -> if (err?.code == "ENDPOINT_NOT_FOUND") DataError.BadRequest(404, err.code, err.message) else DataError.NotFound(what)
        429 -> DataError.RateLimited(retryAfter)
        503 -> DataError.Unavailable(retryAfter)
        400, 405, 413 -> DataError.BadRequest(resp.code(), err?.code, err?.message)
        else -> DataError.Server(resp.code(), err?.code)
    }
}
