package org.marketler.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * HTML `<input type="file">` desteği. Galeri/dosya seçici açılır; site görsel kabul ediyorsa
 * sistem kamera uygulaması da seçenek olarak sunulur. Uygulama CAMERA izni istemez.
 *
 * Not: Bu sınıf [onCreate] sırasında oluşturulmalıdır (sonuç kaydı için).
 */
class FileChooserManager(private val activity: AppCompatActivity) {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraFile: File? = null
    private var cameraUri: Uri? = null

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            deliverResult(result.resultCode, result.data)
        }

    fun show(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        // Önceki yarım kalan seçim varsa iptal et; aksi halde WebView yeni seçim açmaz.
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        clearCamera()

        val contentIntent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        val mimeTypes = params.acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.contains('/') }
            .distinct()
        when (mimeTypes.size) {
            0 -> Unit
            1 -> contentIntent.type = mimeTypes.first()
            else -> contentIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        }
        if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        val chooser = Intent.createChooser(
            contentIntent,
            params.title ?: activity.getString(R.string.file_chooser_title)
        )
        if (acceptsImages(params)) {
            createCameraIntent()?.let {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
            }
        }

        return try {
            launcher.launch(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            clearCamera()
            false
        }
    }

    private fun deliverResult(resultCode: Int, data: Intent?) {
        val callback = filePathCallback ?: return
        filePathCallback = null

        if (resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            clearCamera()
            return
        }

        val uris = mutableListOf<Uri>()
        data?.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
        }
        if (uris.isEmpty()) data?.data?.let(uris::add)

        // Kamera ile çekildiyse sonuç Intent'i genellikle boş gelir; dosyayı kullan.
        if (uris.isEmpty()) {
            val file = cameraFile
            val uri = cameraUri
            if (file != null && uri != null && file.length() > 0) uris.add(uri)
        }

        callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    private fun acceptsImages(params: WebChromeClient.FileChooserParams): Boolean {
        val types = params.acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (types.isEmpty()) return true
        val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif")
        return types.any { it.startsWith("image/") || it == "*/*" || it in imageExtensions }
    }

    private fun createCameraIntent(): Intent? {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(activity.packageManager) == null) return null
        return try {
            val dir = File(activity.cacheDir, "camera").apply { mkdirs() }
            val file = File.createTempFile("IMG_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
            )
            cameraFile = file
            cameraUri = uri
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.clipData = ClipData.newRawUri("", uri)
            intent.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            intent
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun clearCamera() {
        cameraFile?.let { if (it.length() == 0L) it.delete() }
        cameraFile = null
        cameraUri = null
    }

    /** Uygulama kapanırken önbellekteki eski kamera dosyalarını temizler. */
    fun cleanup() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        File(activity.cacheDir, "camera").listFiles()?.forEach { it.delete() }
    }
}
