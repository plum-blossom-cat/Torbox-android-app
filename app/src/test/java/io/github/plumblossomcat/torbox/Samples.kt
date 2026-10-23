package io.github.plumblossomcat.torbox

import io.github.plumblossomcat.torbox.core.api.ChangelogDto
import io.github.plumblossomcat.torbox.core.api.HosterDto
import io.github.plumblossomcat.torbox.core.api.IntegrationJobDto
import io.github.plumblossomcat.torbox.core.api.NotificationDto
import io.github.plumblossomcat.torbox.core.api.QueuedDto
import io.github.plumblossomcat.torbox.core.api.RssFeedDto
import io.github.plumblossomcat.torbox.core.api.SearchMetadataDto
import io.github.plumblossomcat.torbox.core.api.SearchResultDto
import io.github.plumblossomcat.torbox.core.api.SearchTorrentDto
import io.github.plumblossomcat.torbox.core.api.UserDto
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.model.TransferState
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Realistic, legally redistributable sample content for screenshots. */
object Samples {
    private val now: Instant = Instant.now()
    private fun ago(minutes: Long): Instant = now.minus(minutes, ChronoUnit.MINUTES)
    private fun iso(minutes: Long) = ago(minutes).toString()
    private const val GB = 1024L * 1024 * 1024
    private const val MB = 1024L * 1024

    val user = UserDto(
        id = 48213,
        email = "maya.okafor@example.com",
        plan = 2,
        totalDownloaded = 3_420L * GB,
        isSubscribed = true,
        premiumExpiresAt = now.plus(214, ChronoUnit.DAYS).toString(),
        createdAt = "2024-02-11T09:12:00Z",
        server = 7,
        userReferral = "8f14e45f-ceea-467a-9c4e-a6b1c3d5e2f0",
    )

    private fun file(id: Long, path: String, size: Long, mime: String? = null) =
        TransferFile(id, path, path.substringAfterLast('/'), size, mime, infected = false)

    private fun transfer(
        id: Long,
        name: String,
        state: TransferState,
        size: Long,
        progress: Double = if (state.isDone) 1.0 else 0.0,
        kind: TransferKind = TransferKind.TORRENT,
        files: List<TransferFile> = emptyList(),
        addedMinutesAgo: Long = 60,
        speed: Long = 0,
        upload: Long = 0,
        eta: Long = 0,
        seeds: Long = 0,
        peers: Long = 0,
        ratio: Double = 0.0,
        raw: String = state.label.lowercase(),
        cached: Boolean = false,
        error: String? = null,
    ) = Transfer(
        kind = kind, id = id, name = name, hash = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c".take(40), size = size,
        progress = progress, state = state, rawState = raw, downloadSpeed = speed, uploadSpeed = upload, etaSeconds = eta,
        seeds = seeds, peers = peers, ratio = ratio, availability = if (kind == TransferKind.TORRENT) 1.0 else 0.0,
        createdAt = ago(addedMinutesAgo), updatedAt = ago(2), expiresAt = now.plus(29, ChronoUnit.DAYS), files = files,
        cached = cached, downloadReady = state.isDone, tracker = if (kind == TransferKind.TORRENT) "udp://tracker.opentrackr.org:1337/announce" else null,
        originalUrl = if (kind != TransferKind.TORRENT) "https://example.org/files/$id" else null, magnet = null,
        totalUploaded = (size * ratio).toLong(), errorMessage = error,
    )

    val bunnyFiles = listOf(
        file(1, "Big Buck Bunny/Big Buck Bunny (2008) 1080p.mkv", 885 * MB, "video/x-matroska"),
        file(2, "Big Buck Bunny/Subs/English.srt", 42_000),
        file(3, "Big Buck Bunny/Subs/Français.srt", 44_100),
        file(4, "Big Buck Bunny/Extras/Making of.mp4", 214 * MB, "video/mp4"),
        file(5, "Big Buck Bunny/poster.jpg", 1_240_000),
        file(6, "Big Buck Bunny/README.txt", 2_400),
    )

    val torrents = listOf(
        transfer(4021, "Ubuntu 26.04 LTS Desktop (amd64)", TransferState.DOWNLOADING, 6_210 * MB, progress = 0.62, speed = 19_300_000, eta = 3_840, seeds = 214, peers = 38, addedMinutesAgo = 12, raw = "downloading"),
        transfer(4020, "Big Buck Bunny (2008) [1080p] Blender Open Movie", TransferState.READY, 1_101 * MB, files = bunnyFiles, addedMinutesAgo = 180, cached = true, raw = "cached"),
        transfer(4019, "Tears of Steel (2012) 4K Remaster", TransferState.SEEDING, 7_480 * MB, upload = 2_400_000, ratio = 1.37, seeds = 12, peers = 9, addedMinutesAgo = 1_500, raw = "uploading"),
        transfer(4018, "Sintel (2010) Open Movie — Director's Cut", TransferState.STALLED, 1_290 * MB, progress = 0.18, peers = 2, addedMinutesAgo = 2_900, raw = "stalled (no seeds)"),
        transfer(4017, "Arch Linux 2026.10.01 x86_64", TransferState.PREPARING, 0, addedMinutesAgo = 3, raw = "metaDL"),
        transfer(4016, "Debian 13.2 netinst & firmware collection", TransferState.READY, 1_540 * MB, files = listOf(file(9, "debian-13.2.0-amd64-netinst.iso", 1_540 * MB)), addedMinutesAgo = 6_000),
    )

    val usenet = listOf(
        transfer(901, "Cosmos.Laundromat.2015.1080p.WEB", TransferState.DOWNLOADING, 2_200 * MB, progress = 0.34, kind = TransferKind.USENET, speed = 61_000_000, eta = 31, addedMinutesAgo = 4),
        transfer(900, "Spring.2019.Blender.Studio.2160p", TransferState.FAILED, 3_100 * MB, kind = TransferKind.USENET, addedMinutesAgo = 400, raw = "Failed (missing articles)"),
    )

    val web = listOf(
        transfer(77, "agent-327-operation-barbershop.mp4", TransferState.READY, 412 * MB, kind = TransferKind.WEB, files = listOf(file(1, "agent-327-operation-barbershop.mp4", 412 * MB)), addedMinutesAgo = 90),
    )

    val search = SearchResultDto(
        metadata = SearchMetadataDto(
            title = "Sintel", description = "A lonely young woman, Sintel, helps and befriends a dragon, whom she calls Scales. When he is kidnapped by an adult dragon, she sets out on a dangerous quest.",
            type = "movie", releaseYears = "2010", rating = 7.4, genres = listOf("Animation", "Fantasy", "Short"), runtime = "15 min",
        ),
        torrents = listOf(
            SearchTorrentDto(hash = "a1", rawTitle = "Sintel.2010.2160p.UHD.BluRay.x265-OPEN", size = 4_300 * MB, lastKnownSeeders = 312, age = "2y", tracker = "OpenTracker", cached = true),
            SearchTorrentDto(hash = "a2", rawTitle = "Sintel.2010.1080p.BluRay.x264-BLENDER", size = 1_290 * MB, lastKnownSeeders = 1_204, age = "6y", tracker = "Archive", cached = true, owned = true),
            SearchTorrentDto(hash = "a3", rawTitle = "Sintel (2010) 720p WEB-DL AAC", size = 690 * MB, lastKnownSeeders = 48, age = "8y", tracker = "PublicHD"),
            SearchTorrentDto(hash = "a4", rawTitle = "Sintel.Soundtrack.FLAC.24bit", size = 380 * MB, lastKnownSeeders = 9, age = "3y", tracker = "Music"),
        ),
    )

    val queued = listOf(
        QueuedDto(id = 3, name = "Elephants Dream (2006) 1080p", type = "torrent", createdAt = iso(25)),
        QueuedDto(id = 4, name = "Caminandes.Llamigos.2016.NZB", type = "usenet", createdAt = iso(300)),
    )

    val notifications = listOf(
        NotificationDto(id = 12, title = "Download ready", message = "Big Buck Bunny (2008) [1080p] finished and is ready to download.", createdAt = iso(170)),
        NotificationDto(id = 11, title = "Usenet download failed", message = "Spring.2019.Blender.Studio.2160p is missing articles on every provider.", createdAt = iso(400)),
        NotificationDto(id = 10, title = "Welcome to Pro", message = "Your plan was upgraded. Enjoy 10 active slots and RSS feeds.", createdAt = iso(20_000)),
    )

    val hosters = listOf(
        HosterDto(name = "1fichier", domains = listOf("1fichier.com", "alterupload.com", "cjoint.net"), status = true, dailyLinkLimit = 25, dailyLinkUsed = 3),
        HosterDto(name = "Google Drive", domains = listOf("drive.google.com", "docs.google.com"), status = true),
        HosterDto(name = "MediaFire", domains = listOf("mediafire.com"), status = true, dailyBandwidthLimit = 50 * GB, dailyBandwidthUsed = 6 * GB),
        HosterDto(name = "Rapidgator", domains = listOf("rapidgator.net", "rg.to"), status = false, note = "Temporarily unavailable while the host rotates its API."),
    )

    val changelog = listOf(
        ChangelogDto(id = "1", name = "v7.4 — Faster cache checks", markdown = "## Improvements\n- Cache checks are **3× faster** for large batches\n- Usenet post-processing now reports progress\n- Fixed `zip_link` for downloads with one file", createdAt = iso(4_000)),
        ChangelogDto(id = "2", name = "v7.3 — Cloud uploads", markdown = "- Send finished downloads to [Pixeldrain](https://pixeldrain.com)\n- OneDrive uploads support folders over 250 GB", createdAt = iso(30_000)),
    )

    val jobs = listOf(
        IntegrationJobDto(id = 1, integration = "gofile", type = "torrent", status = "uploading", progress = 0.46, createdAt = iso(6)),
        IntegrationJobDto(id = 2, integration = "googledrive", type = "torrent", zip = true, status = "completed", progress = 1.0, downloadUrl = "https://drive.google.com/x", createdAt = iso(900)),
        IntegrationJobDto(id = 3, integration = "onedrive", type = "usenet", status = "failed", detail = "OneDrive token expired. Reconnect your account.", createdAt = iso(3_000)),
    )

    val feeds = listOf(
        RssFeedDto(id = 1, name = "Blender Studio releases", url = "https://studio.blender.org/films/feed.xml", status = "active", state = "idle", scanInterval = 60, rssType = "torrent", lastChecked = iso(14)),
        RssFeedDto(id = 2, name = "Linux ISOs (weekly)", url = "https://distrowatch.example/torrents.rss", status = "paused", scanInterval = 720, rssType = "torrent", lastChecked = iso(9_000)),
        RssFeedDto(id = 3, name = "Indexer — documentaries", url = "https://indexer.example/rss?t=5080", status = "error", statusMessage = "Feed returned HTTP 403. Check the API key in the URL.", scanInterval = 30, rssType = "usenet"),
    )
}
