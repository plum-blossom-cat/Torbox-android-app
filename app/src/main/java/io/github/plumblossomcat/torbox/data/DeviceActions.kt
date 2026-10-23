package io.github.plumblossomcat.torbox.data

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Everything that leaves the app: system downloads, other apps, the clipboard, the browser. */
class DeviceActions(private val context: Context) {

    /** Hands the file to Android's download manager. Returns false if it refused. */
    fun save(url: String, fileName: String, title: String = fileName): Boolean = runCatching {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
        val request = DownloadManager.Request(url.toUri())
            .setTitle(title)
            .setDescription("TorBox")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TorBox/$safeName")
        } else {
            // Writing to the shared Downloads folder needs a storage permission before Android 10.
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeName)
        }
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.enqueue(request)
        true
    }.getOrDefault(false)

    /** Opens the link in whichever app handles [mime] (video players for video, etc.). */
    fun openWith(url: String, mime: String, chooserTitle: String = "Open with"): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(url.toUri(), mime)
            putExtra("title", chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(Intent.createChooser(view, chooserTitle))
    }

    fun share(text: String, subject: String? = null): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        return start(Intent.createChooser(send, null))
    }

    fun copy(text: String, label: String = "TorBox") {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Whether Android shows its own "copied" confirmation, so we can avoid a duplicate toast. */
    val systemConfirmsCopy: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun browse(url: String, dark: Boolean) {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(if (dark) 0xFF0D0D0B.toInt() else 0xFFF2F0E9.toInt())
            .build()
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            tab.launchUrl(context, url.toUri())
        } catch (_: ActivityNotFoundException) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
