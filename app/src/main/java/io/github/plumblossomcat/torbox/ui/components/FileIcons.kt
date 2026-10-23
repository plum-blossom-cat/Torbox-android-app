package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import io.github.plumblossomcat.torbox.core.model.TransferFile
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

private val VIDEO = setOf("mkv", "mp4", "avi", "mov", "m4v", "webm", "wmv", "flv", "ts", "m2ts", "mpg", "mpeg")
private val AUDIO = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "alac", "wma", "m4b")
private val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "001")
private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")
private val TEXT = setOf("txt", "nfo", "srt", "ass", "ssa", "vtt", "sub", "md", "pdf", "epub", "mobi", "log")
private val CODE = setOf("exe", "msi", "apk", "dmg", "pkg", "deb", "rpm", "appimage", "bin", "sh", "bat", "jar")

enum class FileKind { VIDEO, AUDIO, ARCHIVE, IMAGE, TEXT, PROGRAM, OTHER }

fun fileKind(name: String, mime: String? = null): FileKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in VIDEO || mime?.startsWith("video/") == true -> FileKind.VIDEO
        ext in AUDIO || mime?.startsWith("audio/") == true -> FileKind.AUDIO
        ext in ARCHIVE -> FileKind.ARCHIVE
        ext in IMAGE || mime?.startsWith("image/") == true -> FileKind.IMAGE
        ext in TEXT || mime?.startsWith("text/") == true -> FileKind.TEXT
        ext in CODE -> FileKind.PROGRAM
        else -> FileKind.OTHER
    }
}

fun TransferFile.kind(): FileKind = fileKind(name, mimeType)

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.VIDEO -> TbIcons.FileVideo
    FileKind.AUDIO -> TbIcons.FileAudio
    FileKind.ARCHIVE -> TbIcons.FileArchive
    FileKind.IMAGE -> TbIcons.FileImage
    FileKind.TEXT -> TbIcons.FileText
    FileKind.PROGRAM -> TbIcons.FileCode
    FileKind.OTHER -> TbIcons.File
}

/** Media players need a mime type to show up in the "Open with" chooser. */
fun TransferFile.guessMime(): String = mimeType ?: when (kind()) {
    FileKind.VIDEO -> "video/*"
    FileKind.AUDIO -> "audio/*"
    FileKind.IMAGE -> "image/*"
    FileKind.TEXT -> "text/plain"
    FileKind.ARCHIVE -> "application/zip"
    else -> "*/*"
}

val FileKind.isStreamable: Boolean get() = this == FileKind.VIDEO || this == FileKind.AUDIO
