package org.marketler.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test fun anaSiteUygulamaIcindeAcilir() {
        assertEquals(UrlAction.LoadInApp, UrlPolicy.decide("https://marketler.org/"))
        assertEquals(UrlAction.LoadInApp, UrlPolicy.decide("https://marketler.org/urun?id=5#yorum"))
        assertEquals(UrlAction.LoadInApp, UrlPolicy.decide("https://www.marketler.org/giris.php"))
        assertEquals(UrlAction.LoadInApp, UrlPolicy.decide("HTTPS://MARKETLER.ORG/"))
        assertEquals(UrlAction.LoadInApp, UrlPolicy.decide("https://marketler.org./"))
    }

    @Test fun benzerAlanAdlariHariciSayilir() {
        assertEquals(UrlAction.OpenInBrowser, UrlPolicy.decide("https://evilmarketler.org/"))
        assertEquals(UrlAction.OpenInBrowser, UrlPolicy.decide("https://marketler.org.evil.com/"))
        // Kullanıcı adı hilesi: gerçek host evil.com
        assertEquals(UrlAction.OpenInBrowser, UrlPolicy.decide("https://marketler.org@evil.com/"))
    }

    @Test fun hariciHttpsTarayicidaAcilir() {
        assertEquals(UrlAction.OpenInBrowser, UrlPolicy.decide("https://www.migros.com.tr/"))
        assertEquals(UrlAction.OpenInBrowser, UrlPolicy.decide("https://odeme.example.com/pay"))
    }

    @Test fun anaSiteninHttpAdresiHttpsYukseltilir() {
        assertEquals(
            UrlAction.UpgradeToHttps("https://marketler.org/sepet"),
            UrlPolicy.decide("http://marketler.org/sepet")
        )
    }

    @Test fun hariciHttpEngellenir() {
        assertEquals(UrlAction.Block, UrlPolicy.decide("http://example.com/"))
    }

    @Test fun tehlikeliSemalarEngellenir() {
        assertEquals(UrlAction.Block, UrlPolicy.decide("javascript:alert(1)"))
        assertEquals(UrlAction.Block, UrlPolicy.decide("file:///sdcard/a.html"))
        assertEquals(UrlAction.Block, UrlPolicy.decide("content://x/y"))
        assertEquals(UrlAction.Block, UrlPolicy.decide("data:text/html,hi"))
        assertEquals(UrlAction.Block, UrlPolicy.decide("blob:https://marketler.org/abc"))
        assertEquals(UrlAction.Block, UrlPolicy.decide("https://"))
        assertEquals(UrlAction.Block, UrlPolicy.decide("bozuk adres"))
    }

    @Test fun uygulamaSemalariHariciUygulamadaAcilir() {
        assertEquals(UrlAction.OpenExternalApp, UrlPolicy.decide("tel:+905551112233"))
        assertEquals(UrlAction.OpenExternalApp, UrlPolicy.decide("mailto:info@example.com"))
        assertEquals(UrlAction.OpenExternalApp, UrlPolicy.decide("whatsapp://send?text=merhaba"))
        assertEquals(
            UrlAction.OpenExternalApp,
            UrlPolicy.decide("intent://scan/#Intent;scheme=zxing;package=com.example;end")
        )
    }

    @Test fun hostKontrolu() {
        assertTrue(UrlPolicy.isInternalHost("marketler.org"))
        assertTrue(UrlPolicy.isInternalHost("cdn.marketler.org"))
        assertFalse(UrlPolicy.isInternalHost(null))
        assertFalse(UrlPolicy.isInternalHost(""))
        assertFalse(UrlPolicy.isInternalHost("xmarketler.org"))
    }
}
