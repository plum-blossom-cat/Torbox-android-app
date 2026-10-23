package io.github.plumblossomcat.torbox.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import io.github.plumblossomcat.torbox.core.api.UploadFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Magnet

/** Something another app handed us to add: a magnet, a link, or a .torrent/.nzb file. */
data class PendingAdd(
    val kind: TransferKind,
    val text: String? = null,
    val file: UploadFile? = null,
    val id: Long = System.nanoTime(),
)

/** Deep link from a notification into a specific download. */
data class PendingOpen(val kind: TransferKind, val id: Long)

object IncomingIntent {
    const val EXTRA_OPEN_KIND = "torbox.open_kind"
    const val EXTRA_OPEN_ID = "torbox.open_id"
    private const val MAX_FILE_BYTES = 25L * 1024 * 1024

    fun open(intent: Intent?): PendingOpen? {
        intent ?: return null
        val kind = intent.getStringExtra(EXTRA_OPEN_KIND)?.let { runCatching { TransferKind.valueOf(it) }.getOrNull() } ?: return null
        val id = intent.getLongExtra(EXTRA_OPEN_ID, -1).takeIf { it >= 0 } ?: return null
        return PendingOpen(kind, id)
    }

    fun parse(intent: Intent?, resolver: ContentResolver): PendingAdd? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return null
                if (data.scheme.equals("magnet", ignoreCase = true)) {
                    PendingAdd(TransferKind.TORRENT, text = data.toString())
                } else {
                    fromUri(data, intent.type, resolver)
                }
            }
            Intent.ACTION_SEND -> {
                val stream = streamExtra(intent)
                if (stream != null) return fromUri(stream, intent.type, resolver)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                fromText(text)
            }
            else -> null
        }
    }

    fun fromText(raw: String): PendingAdd? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val magnet = Regex("magnet:\\?[^\\s]+", RegexOption.IGNORE_CASE).find(text)?.value
        if (magnet != null) return PendingAdd(TransferKind.TORRENT, text = magnet)
        if (Magnet.infoHash(text) != null && !text.contains("://")) return PendingAdd(TransferKind.TORRENT, text = text)
        val links = Regex("https?://[^\\s]+").findAll(text).map { it.value }.toList()
        if (links.isEmpty()) return null
        val nzb = links.all { it.substringBefore('?').endsWith(".nzb", ignoreCase = true) }
        return PendingAdd(if (nzb) TransferKind.USENET else TransferKind.WEB, text = links.joinToString("\n"))
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    fun fromUri(uri: Uri, mime: String?, resolver: ContentResolver): PendingAdd? {
        val name = displayName(uri, resolver) ?: uri.lastPathSegment ?: "file"
        val resolvedMime = mime ?: resolver.getType(uri)
        val isNzb = name.endsWith(".nzb", ignoreCase = true) || resolvedMime == "application/x-nzb"
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = input.readBytes()
                if (buffer.size > MAX_FILE_BYTES) null else buffer
            }
        }.getOrNull() ?: return null
        return if (isNzb) {
            PendingAdd(TransferKind.USENET, file = UploadFile(name, bytes, "application/x-nzb"))
        } else {
            PendingAdd(TransferKind.TORRENT, file = UploadFile(name.ifBlank { "file.torrent" }, bytes, "application/x-bittorrent"))
        }
    }

    fun displayName(uri: Uri, resolver: ContentResolver): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
