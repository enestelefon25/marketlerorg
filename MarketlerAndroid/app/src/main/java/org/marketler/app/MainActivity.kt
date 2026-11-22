package org.marketler.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.max

/**
 * Tek ekran: ekran durumlarını (yükleniyor / site / hata) yönetir ve
 * işi ilgili sınıflara devreder.
 */
class MainActivity : AppCompatActivity(), WebViewManager.Listener {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var loadingOverlay: View
    private lateinit var errorView: View
    private lateinit var errorIcon: ImageView
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView

    private lateinit var messenger: UiMessenger
    private lateinit var networkManager: NetworkManager
    private lateinit var downloads: FileDownloadManager
    private lateinit var fileChooser: FileChooserManager
    private lateinit var webViewManager: WebViewManager

    private var currentError: WebViewManager.ErrorType? = null
    private var failedUrl: String? = null
    private var pageHadError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        bindViews()
        applyWindowInsets()

        messenger = UiMessenger(findViewById(R.id.content))
        networkManager = NetworkManager(this) { onNetworkAvailable() }
        downloads = FileDownloadManager(this, messenger)
        fileChooser = FileChooserManager(this)
        webViewManager = WebViewManager(webView, ExternalLinkOpener(this, messenger), this)
        webViewManager.configure()
        downloads.register()

        setupRefresh()
        setupBackNavigation()
        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener { retry() }

        val restored = savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        if (restored) {
            loadingOverlay.isVisible = false
        } else {
            loadUrl(AppConfig.HOME_URL)
        }
    }

    private fun bindViews() {
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        errorView = findViewById(R.id.errorView)
        errorIcon = findViewById(R.id.errorIcon)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
    }

    /** Kenardan kenara çizim: durum çubuğu arkası marka rengi, içerik sistem çubuklarının dışında. */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root)
        val content = findViewById<View>(R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            content.setPadding(0, 0, 0, max(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupRefresh() {
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_primary))
        // Yalnızca sayfanın en üstündeyken aşağı çekince yenile
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        swipeRefresh.setOnRefreshListener {
            if (currentError != null) retry() else webViewManager.reload()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webViewManager.canGoBack()) {
                    hideError()
                    webViewManager.goBack()
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.exit_confirm) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadUrl(url: String) {
        if (!networkManager.isOnline()) {
            failedUrl = url
            showError(WebViewManager.ErrorType.OFFLINE)
            return
        }
        hideError()
        webViewManager.load(url)
    }

    private fun retry() {
        val target = failedUrl ?: webView.url ?: AppConfig.HOME_URL
        if (!networkManager.isOnline()) {
            swipeRefresh.isRefreshing = false
            showError(WebViewManager.ErrorType.OFFLINE)
            return
        }
        failedUrl = null
        loadUrl(target)
    }

    private fun onNetworkAvailable() {
        // Bağlantı geri geldiyse ve çevrimdışı ekranı açıksa otomatik yeniden dene
        if (currentError == WebViewManager.ErrorType.OFFLINE) retry()
    }

    private fun showError(type: WebViewManager.ErrorType) {
        currentError = type
        val (title, message) = when (type) {
            WebViewManager.ErrorType.OFFLINE -> R.string.offline_title to R.string.offline_message
            WebViewManager.ErrorType.GENERIC -> R.string.error_title to R.string.error_message
            WebViewManager.ErrorType.SSL -> R.string.ssl_error_title to R.string.ssl_error_message
        }
        errorTitle.setText(title)
        errorMessage.setText(message)
        errorIcon.setImageResource(
            if (type == WebViewManager.ErrorType.OFFLINE) R.drawable.ic_offline else R.drawable.ic_logo_mark
        )
        errorView.isVisible = true
        loadingOverlay.isVisible = false
        progressBar.isVisible = false
        swipeRefresh.isRefreshing = false
    }

    private fun hideError() {
        currentError = null
        errorView.isVisible = false
    }

    // region WebViewManager.Listener

    override fun onPageStarted(url: String) {
        pageHadError = false
    }

    override fun onPageFinished(url: String) {
        swipeRefresh.isRefreshing = false
        if (!pageHadError) {
            hideError()
            loadingOverlay.isVisible = false
        }
    }

    override fun onProgressChanged(progress: Int) {
        if (progress in 1..99) {
            progressBar.isVisible = true
            progressBar.setProgressCompat(progress, true)
        } else {
            progressBar.isVisible = false
        }
    }

    override fun onMainFrameError(failingUrl: String?, type: WebViewManager.ErrorType) {
        pageHadError = true
        failedUrl = failingUrl
        showError(type)
    }

    override fun onBlockedNavigation() {
        messenger.show(getString(R.string.insecure_link_blocked))
    }

    override fun onShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean = fileChooser.show(callback, params)

    override fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) = downloads.onDownloadRequested(url, userAgent, contentDisposition, mimeType)

    override fun onRendererGone() {
        recreate()
    }

    override fun isOnline(): Boolean = networkManager.isOnline()

    // endregion

    override fun onStart() {
        super.onStart()
        networkManager.start()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        android.webkit.CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onStop() {
        networkManager.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        downloads.unregister()
        if (isFinishing) fileChooser.cleanup()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
