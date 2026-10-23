package io.github.plumblossomcat.torbox.core.model

import io.github.plumblossomcat.torbox.core.api.FileDto
import io.github.plumblossomcat.torbox.core.api.TorrentDto
import io.github.plumblossomcat.torbox.core.api.UsenetDto
import io.github.plumblossomcat.torbox.core.api.WebDownloadDto
import io.github.plumblossomcat.torbox.core.util.Timestamps
import java.time.Instant

/**
 * The three download types TorBox offers. They share almost every endpoint shape, differing
 * only in path segment and id parameter names, which this enum centralises.
 */
enum class TransferKind(
    val segment: String,
    /** Query parameter used by `requestdl`. */
    val linkIdParam: String,
    /** Body key used by the control endpoint. */
    val controlIdKey: String,
    val controlPath: String,
    val createPath: String,
    /** Value of `type` for queued downloads and integrations. */
    val apiType: String,
    val label: String,
    val pluralLabel: String,
) {
    TORRENT("torrents", "torrent_id", "torrent_id", "controltorrent", "createtorrent", "torrent", "Torrent", "Torrents"),
    USENET("usenet", "usenet_id", "usenet_id", "controlusenetdownload", "createusenetdownload", "usenet", "Usenet", "Usenet"),
    WEB("webdl", "web_id", "webdl_id", "controlwebdownload", "createwebdownload", "webdl", "Web", "Web");

    companion object {
        fun fromApiType(value: String?): TransferKind? = entries.firstOrNull { it.apiType.equals(value, ignoreCase = true) }
    }
}

/** A coarse, presentation-friendly state derived from TorBox's free-form `download_state`. */
enum class TransferState(val label: String) {
    DOWNLOADING("Downloading"),
    PREPARING("Preparing"),
    SEEDING("Seeding"),
    READY("Ready"),
    PAUSED("Paused"),
    STALLED("Stalled"),
    QUEUED("Queued"),
    FAILED("Failed"),
    ;

    val isActive: Boolean get() = this == DOWNLOADING || this == PREPARING || this == STALLED
    val isDone: Boolean get() = this == READY || this == SEEDING
}

data class TransferFile(
    val id: Long,
    /** Path inside the download, e.g. `Show/Season 1/Episode 1.mkv`. */
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val infected: Boolean,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

data class Transfer(
    val kind: TransferKind,
    val id: Long,
    val name: String,
    val hash: String,
    val size: Long,
    /** 0.0 – 1.0 */
    val progress: Double,
    val state: TransferState,
    val rawState: String,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val etaSeconds: Long,
    val seeds: Long,
    val peers: Long,
    val ratio: Double,
    val availability: Double,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val expiresAt: Instant?,
    val files: List<TransferFile>,
    val cached: Boolean,
    val downloadReady: Boolean,
    val tracker: String?,
    val originalUrl: String?,
    val magnet: String?,
    val totalUploaded: Long,
    val errorMessage: String? = null,
) {
    val key: String get() = "${kind.name}:$id"
}

object TransferMapper {

    fun state(raw: String, finished: Boolean, present: Boolean, active: Boolean, progress: Double): TransferState {
        val s = raw.lowercase().trim()
        // qBittorrent suffixes finished-torrent states with "UP" (stalledUP, pausedUP, queuedUP…).
        val finishedTorrentState = s.endsWith("up") && s != "up"
        return when {
            s.contains("fail") || s.contains("error") || s.contains("missing") || s.contains("invalid") -> TransferState.FAILED
            s == "uploading" || s == "seeding" || finishedTorrentState ->
                if (active && !s.startsWith("paused")) TransferState.SEEDING else TransferState.READY
            s.startsWith("stalled") -> TransferState.STALLED
            s.contains("paused") || s == "stopped" -> TransferState.PAUSED
            s.contains("queued") -> TransferState.QUEUED
            s.contains("meta") || s.contains("checking") || s.contains("moving") || s.contains("allocating") ||
                s == "processing" || s.contains("repair") || s.contains("extract") || s.contains("verif") -> TransferState.PREPARING
            s == "downloading" || s.contains("download") -> if (finished && present) TransferState.READY else TransferState.DOWNLOADING
            finished || present || s == "completed" || s == "cached" || s == "finished" -> TransferState.READY
            progress >= 1.0 -> TransferState.READY
            else -> TransferState.PREPARING
        }
    }

    fun file(dto: FileDto): TransferFile {
        val path = dto.name.ifBlank { dto.shortName.orEmpty() }
        return TransferFile(
            id = dto.id,
            path = path,
            name = dto.shortName?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'),
            size = dto.size,
            mimeType = dto.mimetype?.takeIf { it.isNotBlank() },
            infected = dto.infected,
        )
    }

    fun torrent(dto: TorrentDto) = Transfer(
        kind = TransferKind.TORRENT,
        id = dto.id,
        name = dto.name.ifBlank { "Untitled torrent" },
        hash = dto.hash,
        size = dto.size,
        progress = dto.progress.coerceIn(0.0, 1.0),
        state = state(dto.downloadState, dto.downloadFinished, dto.downloadPresent, dto.active, dto.progress),
        rawState = dto.downloadState,
        downloadSpeed = dto.downloadSpeed,
        uploadSpeed = dto.uploadSpeed,
        etaSeconds = dto.eta,
        seeds = dto.seeds,
        peers = dto.peers,
        ratio = dto.ratio,
        availability = dto.availability,
        createdAt = Timestamps.parse(dto.createdAt),
        updatedAt = Timestamps.parse(dto.updatedAt),
        expiresAt = Timestamps.parse(dto.expiresAt),
        files = dto.files.orEmpty().map(::file),
        cached = dto.cached,
        downloadReady = dto.downloadPresent,
        tracker = dto.tracker,
        originalUrl = null,
        magnet = dto.magnet,
        totalUploaded = dto.totalUploaded,
    )

    fun usenet(dto: UsenetDto) = Transfer(
        kind = TransferKind.USENET,
        id = dto.id,
        name = dto.name.ifBlank { "Untitled usenet download" },
        hash = dto.hash,
        size = dto.size,
        progress = dto.progress.coerceIn(0.0, 1.0),
        state = state(dto.downloadState, dto.downloadFinished, dto.downloadPresent, dto.active, dto.progress),
        rawState = dto.downloadState,
        downloadSpeed = dto.downloadSpeed,
        uploadSpeed = 0,
        etaSeconds = dto.eta,
        seeds = 0,
        peers = 0,
        ratio = 0.0,
        availability = dto.availability,
        createdAt = Timestamps.parse(dto.createdAt),
        updatedAt = Timestamps.parse(dto.updatedAt),
        expiresAt = Timestamps.parse(dto.expiresAt),
        files = dto.files.orEmpty().map(::file),
        cached = dto.cached,
        downloadReady = dto.downloadPresent,
        tracker = null,
        originalUrl = dto.originalUrl,
        magnet = null,
        totalUploaded = 0,
    )

    fun web(dto: WebDownloadDto) = Transfer(
        kind = TransferKind.WEB,
        id = dto.id,
        name = dto.name.ifBlank { dto.originalUrl ?: "Untitled web download" },
        hash = dto.hash,
        size = dto.size,
        progress = dto.progress.coerceIn(0.0, 1.0),
        state = if (!dto.error.isNullOrBlank()) TransferState.FAILED
        else state(dto.downloadState, dto.downloadFinished, dto.downloadPresent, dto.active, dto.progress),
        rawState = dto.downloadState,
        downloadSpeed = dto.downloadSpeed,
        uploadSpeed = 0,
        etaSeconds = dto.eta,
        seeds = 0,
        peers = 0,
        ratio = 0.0,
        availability = dto.availability,
        createdAt = Timestamps.parse(dto.createdAt),
        updatedAt = Timestamps.parse(dto.updatedAt),
        expiresAt = Timestamps.parse(dto.expiresAt),
        files = dto.files.orEmpty().map(::file),
        cached = dto.cached,
        downloadReady = dto.downloadPresent,
        tracker = null,
        originalUrl = dto.originalUrl,
        magnet = null,
        totalUploaded = 0,
        errorMessage = dto.error?.takeIf { it.isNotBlank() },
    )
}
