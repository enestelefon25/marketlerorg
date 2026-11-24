package org.marketler.core.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.marketler.core.data.DataError

/**
 * Yönlendirme güvenliği (FAZ 2.2.2 kabul hazırlığı): API'nin (veya araya giren birinin) döndürdüğü 3xx yanıtı
 * İZLENMEZ. Başka alan adına, HTTP'ye veya aynı sunucuda başka yola yönlendirme yapılırsa hedefe HİÇ istek gitmez;
 * uygulama hatayı DataError.Server(3xx) olarak görür ve veri göstermez.
 * İstemci, uygulamanın kullandığıyla aynı ApiClient.create() yapılandırmasıdır.
 */
class YonlendirmeGuvenligiTest {
    private lateinit var api: MockWebServer      // "marketler.org" yerine geçen sunucu
    private lateinit var hedef: MockWebServer    // yönlendirmenin gösterdiği başka sunucu (saldırgan / yanlış adres)
    private lateinit var repo: RemoteRepository

    @Before fun setUp() {
        api = MockWebServer().apply { start() }
        hedef = MockWebServer().apply { start() }
        hedef.enqueue(MockResponse().setBody("""{"success":true,"data":[],"meta":{}}"""))
        repo = RemoteRepository(ApiClient.create(ApiConfig(api.url("/api/v1/").toString(), requireHttps = false)))
    }
    @After fun tearDown() { api.shutdown(); hedef.shutdown() }

    private fun redirect(code: Int, location: String) = api.enqueue(MockResponse().setResponseCode(code).setHeader("Location", location))

    private fun assertBlocked(code: Int) = runTest {
        try { repo.markets(); fail("yönlendirme izlenmemeliydi") } catch (e: DataError.Server) { assertEquals(code, e.httpStatus) }
        assertEquals("yönlendirilen adrese istek GİTMEMELİ", 0, hedef.requestCount)
        assertEquals(1, api.requestCount)
    }

    @Test fun baskaAlanAdinaYonlendirmeIzlenmez() { for (c in listOf(301, 302, 303, 307, 308)) { setUpEach(); redirect(c, hedef.url("/api/v1/markets").toString().replace("127.0.0.1", "localhost")); assertBlocked(c) } }
    @Test fun httpAdresineYonlendirmeIzlenmez() { redirect(302, "http://marketler.org/api/v1/markets"); assertBlocked(302) }
    @Test fun ayniSunucudaBaskaYolaYonlendirmeIzlenmez() {
        redirect(301, api.url("/baska/yol").toString()); assertBlocked(301)
    }
    @Test fun goreliYonlendirmeIzlenmez() { redirect(307, "/api/v1/markets?x=1"); assertBlocked(307) }

    /** Ek güvence: üretim yapılandırmasında http:// veya başka şemalı taban adresle istemci kurulamaz. */
    @Test fun uretimdeHttpTabanAdresReddedilir() {
        for (u in listOf("http://marketler.org/api/v1/", "http://10.0.2.2:8099/api/v1/")) {
            try { ApiClient.create(ApiConfig(u, requireHttps = true)); fail(u) } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("HTTPS")) }
        }
    }

    private fun setUpEach() { tearDown(); setUp() }
}
