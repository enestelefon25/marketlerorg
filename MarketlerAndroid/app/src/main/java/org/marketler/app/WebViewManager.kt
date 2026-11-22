package org.marketler.app

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * WebView'in güvenli yapılandırılmasından ve gezinme kurallarından sorumludur.
 *
 * - Yalnızca https://marketler.org (ve alt alan adları) uygulama içinde açılır.
 * - Diğer HTTPS siteleri tarayıcıya, tel:/mailto: vb. ilgili uygulamaya devredilir.
 * - HTTP, javascript:, file: vb. engellenir.
 * - SSL hatalarında bağlantı her zaman iptal edilir.
 * - JavaScript köprüsü (addJavascriptInterface) kullanılmaz.
 */
class WebViewManager(
    private val webView: WebView,
    private val linkOpener: ExternalLinkOpener,
    private val listener: Listener
) {

    enum class ErrorType { OFFLINE, GENERIC, SSL }

    interface Listener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onProgressChanged(progress: Int)
        fun onMainFrameError(failingUrl: String?, type: ErrorType)
        fun onBlockedNavigation()
        fun onShowFileChooser(
            callback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams
        ): Boolean
        fun onDownloadRequested(
            url: String,
            userAgent: String?,
            contentDisposition: String?,
            mimeType: String?
        )
        fun onRendererGone()
        fun isOnline(): Boolean
    }

    /** Şu anda yüklenmesi beklenen ana sayfa adresi (SSL hatasının ana sayfaya ait olup olmadığını anlamak için). */
    private var mainFrameUrlInFlight: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true

            // Yerel dosya erişimi kapalı
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false

            // HTTPS sayfada HTTP içerik asla yüklenmez
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            setGeolocationEnabled(false)
            @Suppress("DEPRECATION")
            saveFormData = false
            javaScriptCanOpenWindowsAutomatically = false
            // target="_blank" bağlantılar aynı görünümde açılır ve gezinme kurallarından geçer
            setSupportMultipleWindows(false)

            // Mobil görünüm sitenin kendi responsive tasarımından gelir
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT

            userAgentString = "$userAgentString MarketlerApp/${BuildConfig.VERSION_NAME}"
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = Client()
        webView.webChromeClient = ChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            listener.onDownloadRequested(url, userAgent, contentDisposition, mimeType)
        }
    }

    fun load(url: String) {
        mainFrameUrlInFlight = url
        webView.loadUrl(url)
    }

    fun reload() {
        mainFrameUrlInFlight = webView.url
        webView.reload()
    }

    /** Ana sayfa mı? (Geri tuşunda çıkış onayı için.) */
    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() = webView.goBack()

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()

            if (!request.isForMainFrame) {
                // iframe'ler (ör. ödeme/3D Secure) yalnızca HTTPS ile yüklenebilir
                return !url.startsWith("https://", ignoreCase = true) &&
                    !url.startsWith("about:", ignoreCase = true)
            }

            return when (val action = UrlPolicy.decide(url)) {
                UrlAction.LoadInApp -> {
                    mainFrameUrlInFlight = url
                    false
                }
                is UrlAction.UpgradeToHttps -> {
                    load(action.httpsUrl)
                    true
                }
                UrlAction.OpenInBrowser -> {
                    linkOpener.openInBrowser(url)
                    true
                }
                UrlAction.OpenExternalApp -> {
                    linkOpener.openExternalApp(url)
                    true
                }
                UrlAction.Block -> {
                    listener.onBlockedNavigation()
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            // Ek güvenlik katmanı: shouldOverrideUrlLoading'e uğramayan (ör. POST sonrası) yönlendirmeler
            if (url.startsWith("http://", ignoreCase = true)) {
                view.stopLoading()
                when (val action = UrlPolicy.decide(url)) {
                    is UrlAction.UpgradeToHttps -> load(action.httpsUrl)
                    else -> listener.onBlockedNavigation()
                }
                return
            }
            if (url.startsWith("https://", ignoreCase = true) && !UrlPolicy.isInternalHttpsUrl(url)) {
                // Sunucu yönlendirmesiyle başka bir siteye gidildi: tarayıcıda aç, uygulamada gösterme.
                view.stopLoading()
                linkOpener.openInBrowser(url)
                return
            }
            mainFrameUrlInFlight = url
            listener.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
            listener.onPageFinished(url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            val type = if (!listener.isOnline()) ErrorType.OFFLINE else ErrorType.GENERIC
            // İptal edilen gezinmeler (ör. indirmeye dönüşen bağlantılar) hata ekranı göstermesin
            if (type == ErrorType.GENERIC && error.errorCode == WebViewClient.ERROR_UNKNOWN) return
            listener.onMainFrameError(request.url.toString(), type)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Sertifika hataları ASLA yok sayılmaz.
            handler.cancel()
            if (error.url == mainFrameUrlInFlight || error.url == view.url) {
                listener.onMainFrameError(error.url, ErrorType.SSL)
            }
        }

        // Android 8.0+ cihazlarda çağrılır. WebView işlemi çökerse uygulama kapanmasın; ekran yeniden oluşturulur.
        @TargetApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            listener.onRendererGone()
            return true
        }
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            listener.onProgressChanged(newProgress)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean = listener.onShowFileChooser(filePathCallback, fileChooserParams)
    }
}
