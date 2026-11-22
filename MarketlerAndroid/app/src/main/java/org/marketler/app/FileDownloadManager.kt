package org.marketler.app

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * WebView'den gelen indirmeleri Android'in sistem DownloadManager'ı ile
 * ortak "Downloads" klasörüne kaydeder. İlerleme sistem bildiriminde gösterilir,
 * tamamlandığında uygulama içinde "Aç" seçeneği sunulur.
 *
 * Not: Bu sınıf [onCreate] sırasında oluşturulmalıdır (izin isteği kaydı için).
 */
class FileDownloadManager(
    private val activity: AppCompatActivity,
    private val messenger: UiMessenger
) {
    private data class PendingDownload(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?
    )

    private val systemDownloadManager =
        activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Bu oturumda başlatılan indirmeler: id -> dosya adı */
    private val activeDownloads = mutableMapOf<Long, String>()
    private var pending: PendingDownload? = null
    private var receiverRegistered = false

    // Yalnızca Android 7–9 için: ortak Downloads klasörüne yazma izni.
    private val storagePermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pending
            pending = null
            if (request == null) return@registerForActivityResult
            if (granted) enqueue(request) else {
                messenger.show(activity.getString(R.string.download_permission_denied))
            }
        }

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val fileName = activeDownloads.remove(id) ?: return
            if (isSuccessful(id)) {
                messenger.show(
                    activity.getString(R.string.download_completed, fileName),
                    activity.getString(R.string.download_open)
                ) { openDownloadedFile(id) }
            } else {
                messenger.show(activity.getString(R.string.download_failed))
            }
        }
    }

    fun register() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            activity,
            completionReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            // Yayın, sistemin DownloadManager sürecinden gelir.
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    fun unregister() {
        if (!receiverRegistered) return
        activity.unregisterReceiver(completionReceiver)
        receiverRegistered = false
    }

    /** WebView DownloadListener'ından çağrılır. */
    fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        // blob:, data: ve HTTP adresleri DownloadManager ile güvenli şekilde indirilemez.
        if (!url.startsWith("https://", ignoreCase = true)) {
            messenger.show(activity.getString(R.string.download_unsupported))
            return
        }
        val request = PendingDownload(url, userAgent, contentDisposition, mimeType)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = request
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        enqueue(request)
    }

    private fun enqueue(download: PendingDownload) {
        val fileName = URLUtil.guessFileName(
            download.url, download.contentDisposition, download.mimeType
        )
        val mimeType = resolveMimeType(download.mimeType, fileName)

        try {
            val request = DownloadManager.Request(Uri.parse(download.url)).apply {
                mimeType?.let { setMimeType(it) }
                // Oturum gerektiren dosyalar için yalnızca o adrese ait çerezler gönderilir.
                CookieManager.getInstance().getCookie(download.url)?.let {
                    addRequestHeader("Cookie", it)
                }
                download.userAgent?.let { addRequestHeader("User-Agent", it) }
                setTitle(fileName)
                setDescription(activity.getString(R.string.app_name))
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val id = systemDownloadManager.enqueue(request)
            activeDownloads[id] = fileName
            messenger.show(activity.getString(R.string.download_started, fileName))
        } catch (_: IllegalArgumentException) {
            messenger.show(activity.getString(R.string.download_failed))
        } catch (_: IllegalStateException) {
            messenger.show(activity.getString(R.string.download_failed))
        } catch (_: SecurityException) {
            messenger.show(activity.getString(R.string.download_failed))
        }
    }

    private fun resolveMimeType(reported: String?, fileName: String): String? {
        val generic = reported.isNullOrBlank() ||
            reported.equals("application/octet-stream", ignoreCase = true)
        if (!generic) return reported
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: reported
    }

    private fun isSuccessful(id: Long): Boolean {
        val cursor = systemDownloadManager.query(DownloadManager.Query().setFilterById(id))
            ?: return false
        cursor.use {
            if (!it.moveToFirst()) return false
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    private fun openDownloadedFile(id: Long) {
        val uri = systemDownloadManager.getUriForDownloadedFile(id) ?: return
        val type = systemDownloadManager.getMimeTypeForDownloadedFile(id)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            messenger.show(activity.getString(R.string.no_app_for_link))
        }
    }
}
