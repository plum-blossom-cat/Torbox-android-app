package io.github.plumblossomcat.torbox.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * Wire models. Field names are camelCase here and mapped to the API's snake_case by the
 * JSON naming strategy configured in [TorBoxJson]. Every field has a default so a missing key
 * never breaks parsing.
 */

@Serializable
data class UserDto(
    val id: LLong = 0,
    val authId: LString? = null,
    val email: LString? = null,
    val plan: LLong = 0,
    val totalDownloaded: LLong = 0,
    val customer: LString? = null,
    val isSubscribed: LBool = false,
    val premiumExpiresAt: LString? = null,
    val cooldownUntil: LString? = null,
    val createdAt: LString? = null,
    val updatedAt: LString? = null,
    val server: LLong = 0,
    val userReferral: LString? = null,
    val baseEmail: LString? = null,
    val purchasesReferred: LLong = 0,
    val additionalConcurrentSlots: LLong = 0,
    val longTermSeeding: LBool = false,
    val longTermStorage: LBool = false,
    val isVendor: LBool = false,
    val settings: JsonObject? = null,
)

@Serializable
data class FileDto(
    val id: LLong = 0,
    val name: LString = "",
    val shortName: LString? = null,
    val absolutePath: LString? = null,
    val size: LLong = 0,
    val mimetype: LString? = null,
    val md5: LString? = null,
    val hash: LString? = null,
    val s3Path: LString? = null,
    val zipped: LBool = false,
    val infected: LBool = false,
    val opensubtitlesHash: LString? = null,
)

@Serializable
data class TorrentDto(
    val id: LLong = 0,
    val hash: LString = "",
    val name: LString = "",
    val magnet: LString? = null,
    val size: LLong = 0,
    val active: LBool = false,
    val authId: LString? = null,
    val downloadState: LString = "",
    val seeds: LLong = 0,
    val peers: LLong = 0,
    val ratio: LDouble = 0.0,
    val progress: LDouble = 0.0,
    val downloadSpeed: LLong = 0,
    val uploadSpeed: LLong = 0,
    val eta: LLong = 0,
    val torrentFile: LBool = false,
    val expiresAt: LString? = null,
    val downloadPresent: LBool = false,
    val downloadFinished: LBool = false,
    val files: List<FileDto>? = null,
    val inactiveCheck: LLong = 0,
    val availability: LDouble = 0.0,
    val createdAt: LString? = null,
    val updatedAt: LString? = null,
    val server: LLong = 0,
    val tracker: LString? = null,
    val totalUploaded: LLong = 0,
    val totalDownloaded: LLong = 0,
    val cached: LBool = false,
    val seedTorrent: LBool = false,
    val allowZipped: LBool = true,
    val longTermSeeding: LBool = false,
)

@Serializable
data class UsenetDto(
    val id: LLong = 0,
    val hash: LString = "",
    val name: LString = "",
    val size: LLong = 0,
    val active: LBool = false,
    val authId: LString? = null,
    val downloadState: LString = "",
    val progress: LDouble = 0.0,
    val downloadSpeed: LLong = 0,
    val eta: LLong = 0,
    val originalUrl: LString? = null,
    val expiresAt: LString? = null,
    val downloadPresent: LBool = false,
    val downloadFinished: LBool = false,
    val files: List<FileDto>? = null,
    val createdAt: LString? = null,
    val updatedAt: LString? = null,
    val server: LLong = 0,
    val cached: LBool = false,
    val availability: LDouble = 0.0,
)

@Serializable
data class WebDownloadDto(
    val id: LLong = 0,
    val hash: LString = "",
    val name: LString = "",
    val size: LLong = 0,
    val active: LBool = false,
    val authId: LString? = null,
    val downloadState: LString = "",
    val progress: LDouble = 0.0,
    val downloadSpeed: LLong = 0,
    val eta: LLong = 0,
    val originalUrl: LString? = null,
    val expiresAt: LString? = null,
    val downloadPresent: LBool = false,
    val downloadFinished: LBool = false,
    val files: List<FileDto>? = null,
    val createdAt: LString? = null,
    val updatedAt: LString? = null,
    val server: LLong = 0,
    val cached: LBool = false,
    val error: LString? = null,
    val availability: LDouble = 0.0,
)

/** Response of the three create endpoints. Only the id matters for navigation. */
@Serializable
data class CreatedDto(
    val hash: LString? = null,
    val torrentId: LLong = 0,
    val usenetdownloadId: LLong = 0,
    val webdownloadId: LLong = 0,
    val queuedId: LLong = 0,
    val authId: LString? = null,
)

@Serializable
data class QueuedDto(
    val id: LLong = 0,
    val name: LString? = null,
    val hash: LString? = null,
    val magnet: LString? = null,
    val torrentFile: LString? = null,
    val type: LString? = null,
    val createdAt: LString? = null,
)

@Serializable
data class NotificationDto(
    val id: LLong = 0,
    val title: LString = "",
    val message: LString = "",
    val createdAt: LString? = null,
)

@Serializable
data class RssFeedDto(
    val id: LLong = 0,
    val name: LString = "",
    val url: LString = "",
    val doRegex: LString? = null,
    val dontRegex: LString? = null,
    val dontOlderThan: LLong = 0,
    val passCheck: LBool = false,
    val scanInterval: LLong = 60,
    val rssType: LString = "torrent",
    val type: LString? = null,
    val torrentSeeding: LLong = 1,
    val status: LString = "",
    val statusMessage: LString? = null,
    val state: LString? = null,
    val createdAt: LString? = null,
    val updatedAt: LString? = null,
    val lastChecked: LString? = null,
)

@Serializable
data class RssItemDto(
    val id: LLong = 0,
    val title: LString = "",
    val link: LString? = null,
    val status: LString = "",
    val statusMessage: LString? = null,
    val type: LString? = null,
    val createdAt: LString? = null,
)

@Serializable
data class IntegrationJobDto(
    val id: LLong = 0,
    val hash: LString? = null,
    val integration: LString? = null,
    val type: LString? = null,
    val fileId: LLong = 0,
    val zip: LBool = false,
    val progress: LDouble = 0.0,
    val status: LString = "",
    val detail: LString? = null,
    val downloadUrl: LString? = null,
    val createdAt: LString? = null,
    val updatedAt: LString? = null,
)

@Serializable
data class HosterDto(
    val name: LString = "",
    val domains: List<String> = emptyList(),
    val url: LString? = null,
    val icon: LString? = null,
    val status: LBool = false,
    val type: LString? = null,
    val note: LString? = null,
    val dailyLinkLimit: LLong = 0,
    val dailyLinkUsed: LLong = 0,
    val dailyBandwidthLimit: LLong = 0,
    val dailyBandwidthUsed: LLong = 0,
)

@Serializable
data class ChangelogDto(
    val id: LString = "",
    val name: LString = "",
    val markdown: LString? = null,
    val html: LString? = null,
    val link: LString? = null,
    val createdAt: LString? = null,
)

@Serializable
data class TorrentInfoDto(
    val name: LString = "",
    val hash: LString = "",
    val size: LLong = 0,
    val peers: LLong = 0,
    val seeds: LLong = 0,
    val trackers: List<JsonElement> = emptyList(),
    val files: List<InfoFileDto> = emptyList(),
)

@Serializable
data class InfoFileDto(
    val name: LString = "",
    val size: LLong = 0,
)

@Serializable
data class CachedEntryDto(
    val name: LString? = null,
    val size: LLong = 0,
    val hash: LString? = null,
)

/* ---- Search API (search-api.torbox.app) ---- */

@Serializable
data class SearchMetadataDto(
    val id: LString? = null,
    val globalId: LString? = null,
    val title: LString? = null,
    val description: LString? = null,
    val image: LString? = null,
    val backdrop: LString? = null,
    val type: LString? = null,
    val releasedDate: LString? = null,
    val releaseYears: LString? = null,
    val rating: LDouble = 0.0,
    val genres: List<String> = emptyList(),
    val runtime: LString? = null,
    val imdbId: LString? = null,
)

@Serializable
data class SearchTorrentDto(
    val hash: LString = "",
    val rawTitle: LString? = null,
    val title: LString? = null,
    val magnet: LString? = null,
    val torrent: LString? = null,
    val nzb: LString? = null,
    val lastKnownSeeders: LLong = -1,
    val lastKnownPeers: LLong = -1,
    val size: LLong = 0,
    val tracker: LString? = null,
    val categories: List<String> = emptyList(),
    val files: LLong = 0,
    val type: LString? = null,
    val age: LString? = null,
    val cached: LBool = false,
    val owned: LBool = false,
    val titleParsedData: JsonObject? = null,
)

@Serializable
data class SearchResultDto(
    val metadata: SearchMetadataDto? = null,
    val torrents: List<SearchTorrentDto> = emptyList(),
    val nzbs: List<SearchTorrentDto> = emptyList(),
)
