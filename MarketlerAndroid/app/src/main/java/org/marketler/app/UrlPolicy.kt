package org.marketler.app

import java.net.URI
import java.util.Locale

/** Bir bağlantıya tıklandığında ne yapılacağı. */
sealed interface UrlAction {
    /** marketler.org (HTTPS) – uygulama içinde aç. */
    data object LoadInApp : UrlAction

    /** marketler.org'a HTTP ile gidilmek istendi – HTTPS'e yükseltilerek açılır. */
    data class UpgradeToHttps(val httpsUrl: String) : UrlAction

    /** Başka bir HTTPS sitesi – Android tarayıcısında aç. */
    data object OpenInBrowser : UrlAction

    /** tel:, mailto:, intent: vb. – ilgili Android uygulamasında aç. */
    data object OpenExternalApp : UrlAction

    /** Güvensiz veya desteklenmeyen adres (harici HTTP, javascript:, file: ...) – engelle. */
    data object Block : UrlAction
}

/**
 * Hangi adresin nerede açılacağına karar verir.
 * Android sınıflarına bağımlı değildir; JVM birim testleriyle doğrulanır.
 */
object UrlPolicy {

    private val externalAppSchemes = setOf(
        "tel", "mailto", "sms", "smsto", "geo", "whatsapp", "market", "intent"
    )

    fun decide(url: String): UrlAction {
        val scheme = url.substringBefore(':', missingDelimiterValue = "")
            .trim()
            .lowercase(Locale.ROOT)

        return when (scheme) {
            "https" -> {
                val host = hostOf(url) ?: return UrlAction.Block
                if (isInternalHost(host)) UrlAction.LoadInApp else UrlAction.OpenInBrowser
            }
            "http" -> {
                val host = hostOf(url) ?: return UrlAction.Block
                if (isInternalHost(host)) {
                    UrlAction.UpgradeToHttps("https" + url.trim().substring(4))
                } else {
                    UrlAction.Block
                }
            }
            in externalAppSchemes -> UrlAction.OpenExternalApp
            else -> UrlAction.Block
        }
    }

    /** marketler.org veya *.marketler.org mu? (Büyük/küçük harf ve sondaki nokta önemsenmez.) */
    fun isInternalHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        return h == AppConfig.ROOT_DOMAIN || h.endsWith("." + AppConfig.ROOT_DOMAIN)
    }

    /** Sayfanın uygulama içinde güvenle gösterilebilecek bir marketler.org HTTPS adresi olup olmadığı. */
    fun isInternalHttpsUrl(url: String?): Boolean =
        url != null && decide(url) == UrlAction.LoadInApp

    private fun hostOf(url: String): String? =
        try {
            URI(url.trim()).host
        } catch (_: Exception) {
            null
        }
}
