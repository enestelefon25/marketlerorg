package org.marketler.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import java.net.URISyntaxException

/** marketler.org dışındaki bağlantıları güvenli şekilde Android'e devreder. */
class ExternalLinkOpener(
    private val activity: Activity,
    private val messenger: UiMessenger
) {

    /** HTTPS bağlantısını cihazın varsayılan tarayıcısında açar. */
    fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        start(intent)
    }

    /** tel:, mailto:, whatsapp:, intent: gibi bağlantıları ilgili uygulamada açar. */
    fun openExternalApp(url: String) {
        if (url.startsWith("intent:", ignoreCase = true)) {
            openIntentUri(url)
            return
        }
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    private fun openIntentUri(url: String) {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: URISyntaxException) {
            messenger.show(activity.getString(R.string.no_app_for_link))
            return
        }
        // Güvenlik: web sayfası belirli bir bileşeni (uygulamanın kendi iç ekranları dahil) hedefleyemesin.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null

        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && fallback.startsWith("https://", ignoreCase = true)) {
                openInBrowser(fallback)
            } else {
                messenger.show(activity.getString(R.string.no_app_for_link))
            }
        } catch (_: SecurityException) {
            messenger.show(activity.getString(R.string.no_app_for_link))
        }
    }

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            messenger.show(activity.getString(R.string.no_app_for_link))
        } catch (_: SecurityException) {
            messenger.show(activity.getString(R.string.no_app_for_link))
        }
    }
}
