package io.github.plumblossomcat.torbox.core.api

import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.model.TransferMapper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val TorBoxJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/** Seeding preference accepted by `createtorrent` and RSS feeds. */
enum class SeedPreference(val apiValue: Int, val label: String) {
    AUTO(1, "Account default"),
    ALWAYS(2, "Always seed"),
    NEVER(3, "Don't seed");

    companion object {
        fun from(value: Long): SeedPreference = entries.firstOrNull { it.apiValue.toLong() == value } ?: AUTO
    }
}

/** Usenet post-processing levels, see the TorBox docs for `createusenetdownload`. */
enum class PostProcessing(val apiValue: Int, val label: String, val detail: String) {
    DEFAULT(-1, "Default", "Repair, extract, and keep only the wanted files"),
    NONE(0, "None", "Download every file as-is, PAR2 included"),
    REPAIR(1, "Repair", "Verify with PAR2 and repair when needed"),
    UNPACK(2, "Repair + unpack", "Repair and extract, keep the archives"),
    UNPACK_DELETE(3, "Repair, unpack, delete", "Repair, extract and remove the archives"),
}

/** A file attached to a create request (a .torrent or .nzb picked by the person). */
class UploadFile(val fileName: String, val bytes: ByteArray, val mimeType: String)

sealed interface ControlOp {
    val apiValue: String

    enum class Torrent(override val apiValue: String) : ControlOp { REANNOUNCE("reannounce"), DELETE("delete"), RESUME("resume"), PAUSE("pause") }
    enum class Usenet(override val apiValue: String) : ControlOp { DELETE("delete"), PAUSE("pause"), RESUME("resume") }
    enum class Web(override val apiValue: String) : ControlOp { DELETE("delete") }
}

enum class IntegrationProvider(val path: String, val tokenKey: String, val label: String, val tokenRequired: Boolean, val oauthProvider: String?) {
    GOOGLE_DRIVE("googledrive", "google_token", "Google Drive", true, "google"),
    ONEDRIVE("onedrive", "onedrive_token", "OneDrive", true, "onedrive"),
    DROPBOX("dropbox", "dropbox_token", "Dropbox", true, "dropbox"),
    GOFILE("gofile", "gofile_token", "GoFile", false, null),
    ONEFICHIER("1fichier", "onefichier_token", "1fichier", false, null),
    PIXELDRAIN("pixeldrain", "pixeldrain_token", "Pixeldrain", false, null),
}

data class RssFeedInput(
    val name: String,
    val url: String,
    val doRegex: String,
    val dontRegex: String,
    val dontOlderThan: Int,
    val passCheck: Boolean,
    val scanIntervalMinutes: Int,
    val type: TransferKind,
    val seeding: SeedPreference,
)

/**
 * Thin, typed client for the TorBox Main API (`api.torbox.app/v1/api`) and Search API
 * (`search-api.torbox.app`). All calls are main-safe suspending functions.
 */
class TorBoxClient(
    private val http: OkHttpClient,
    private val token: () -> String?,
    private val apiBase: HttpUrl = DEFAULT_API_BASE.toHttpUrl(),
    private val searchBase: HttpUrl = DEFAULT_SEARCH_BASE.toHttpUrl(),
    private val rootBase: HttpUrl = DEFAULT_ROOT.toHttpUrl(),
) {

    /* ------------------------------------------------------------------ user */

    suspend fun me(withSettings: Boolean = true): UserDto =
        get(api("user/me") { addQueryParameter("settings", withSettings.toString()) }, UserDto.serializer())

    /** Validates a key without storing it. */
    suspend fun meWithToken(candidate: String): UserDto =
        decode(execute(Request.Builder().url(api("user/me")).header("Authorization", "Bearer $candidate").build()), UserDto.serializer())

    suspend fun addReferral(code: String) {
        post(api("user/addreferral") { addQueryParameter("referral", code.trim()) }, EMPTY_JSON)
    }

    /* ------------------------------------------------------------- transfers */

    suspend fun list(kind: TransferKind, bypassCache: Boolean = false): List<Transfer> {
        val url = api("${kind.segment}/mylist") {
            addQueryParameter("bypass_cache", bypassCache.toString())
            addQueryParameter("limit", "1000")
        }
        return when (kind) {
            TransferKind.TORRENT -> getList(url, TorrentDto.serializer()).map(TransferMapper::torrent)
            TransferKind.USENET -> getList(url, UsenetDto.serializer()).map(TransferMapper::usenet)
            TransferKind.WEB -> getList(url, WebDownloadDto.serializer()).map(TransferMapper::web)
        }
    }

    suspend fun item(kind: TransferKind, id: Long): Transfer {
        val url = api("${kind.segment}/mylist") {
            addQueryParameter("id", id.toString())
            addQueryParameter("bypass_cache", "true")
        }
        val data = envelope(execute(authed(url).get().build()))
        // A single-id request returns an object; be tolerant of a one-element list too.
        val element = (data as? JsonArray)?.firstOrNull() ?: data
        if (element == null || element is JsonNull) throw TorBoxException.Api("ITEM_NOT_FOUND", null, 404)
        return parse {
            when (kind) {
                TransferKind.TORRENT -> TransferMapper.torrent(TorBoxJson.decodeFromJsonElement(TorrentDto.serializer(), element))
                TransferKind.USENET -> TransferMapper.usenet(TorBoxJson.decodeFromJsonElement(UsenetDto.serializer(), element))
                TransferKind.WEB -> TransferMapper.web(TorBoxJson.decodeFromJsonElement(WebDownloadDto.serializer(), element))
            }
        }
    }

    suspend fun addTorrent(
        magnet: String?,
        file: UploadFile?,
        name: String?,
        seed: SeedPreference,
        allowZip: Boolean,
        asQueued: Boolean,
    ): CreatedDto {
        require(magnet != null || file != null) { "A magnet link or .torrent file is required" }
        val body = multipart {
            magnet?.let { addFormDataPart("magnet", it.trim()) }
            file?.let { addFormDataPart("file", it.fileName, it.bytes.toRequestBody(it.mimeType.toMediaType())) }
            name?.takeIf { it.isNotBlank() }?.let { addFormDataPart("name", it.trim()) }
            addFormDataPart("seed", seed.apiValue.toString())
            addFormDataPart("allow_zip", allowZip.toString())
            addFormDataPart("as_queued", asQueued.toString())
        }
        return post(api("torrents/createtorrent"), body, CreatedDto.serializer())
    }

    suspend fun addUsenet(
        link: String?,
        file: UploadFile?,
        name: String?,
        password: String?,
        postProcessing: PostProcessing,
        asQueued: Boolean,
    ): CreatedDto {
        require(link != null || file != null) { "A link or .nzb file is required" }
        val body = multipart {
            link?.let { addFormDataPart("link", it.trim()) }
            file?.let { addFormDataPart("file", it.fileName, it.bytes.toRequestBody(it.mimeType.toMediaType())) }
            name?.takeIf { it.isNotBlank() }?.let { addFormDataPart("name", it.trim()) }
            password?.takeIf { it.isNotBlank() }?.let { addFormDataPart("password", it) }
            addFormDataPart("post_processing", postProcessing.apiValue.toString())
            addFormDataPart("as_queued", asQueued.toString())
        }
        return post(api("usenet/createusenetdownload"), body, CreatedDto.serializer())
    }

    suspend fun addWeb(link: String, name: String?, password: String?, asQueued: Boolean): CreatedDto {
        val body = multipart {
            addFormDataPart("link", link.trim())
            name?.takeIf { it.isNotBlank() }?.let { addFormDataPart("name", it.trim()) }
            password?.takeIf { it.isNotBlank() }?.let { addFormDataPart("password", it) }
            addFormDataPart("as_queued", asQueued.toString())
        }
        return post(api("webdl/createwebdownload"), body, CreatedDto.serializer())
    }

    /** Runs [op] on one item, or on every item of that kind when [id] is null. */
    suspend fun control(kind: TransferKind, id: Long?, op: ControlOp) {
        val json = buildJsonObject {
            if (id != null) put(kind.controlIdKey, id) else put("all", true)
            put("operation", op.apiValue)
        }
        post(api("${kind.segment}/${kind.controlPath}"), json.toBody())
    }

    /** A short-lived CDN link for one file, or for the whole download zipped when [fileId] is null. */
    suspend fun requestLink(kind: TransferKind, id: Long, fileId: Long?): String {
        val key = token() ?: throw notSignedIn()
        val url = api("${kind.segment}/requestdl") {
            addQueryParameter("token", key)
            addQueryParameter(kind.linkIdParam, id.toString())
            if (fileId != null) addQueryParameter("file_id", fileId.toString()) else addQueryParameter("zip_link", "true")
        }
        val data = envelope(execute(Request.Builder().url(url).get().build()))
        return (data as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }
            ?: throw TorBoxException.Malformed(IllegalStateException("No link in response"))
    }

    /** A permanent redirecting link, safe to hand to media players. Invalidated by resetting the API key. */
    fun permalink(kind: TransferKind, id: Long, fileId: Long?): String? {
        val key = token() ?: return null
        return api("${kind.segment}/requestdl") {
            addQueryParameter("token", key)
            addQueryParameter(kind.linkIdParam, id.toString())
            if (fileId != null) addQueryParameter("file_id", fileId.toString()) else addQueryParameter("zip_link", "true")
            addQueryParameter("redirect", "true")
        }.toString()
    }

    /** Returns the subset of [hashes] that TorBox already has cached, keyed by lower-case hash. */
    suspend fun checkCached(kind: TransferKind, hashes: List<String>): Map<String, CachedEntryDto> {
        if (hashes.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, CachedEntryDto>()
        hashes.distinct().chunked(90).forEach { chunk ->
            val url = api("${kind.segment}/checkcached") {
                addQueryParameter("hash", chunk.joinToString(","))
                addQueryParameter("format", "object")
            }
            val data = envelope(execute(authed(url).get().build()))
            if (data is JsonObject) {
                data.forEach { (hash, value) ->
                    if (value is JsonObject) {
                        result[hash.lowercase()] = parse { TorBoxJson.decodeFromJsonElement(CachedEntryDto.serializer(), value) }
                    }
                }
            } else if (data is JsonArray) {
                data.filterIsInstance<JsonObject>().forEach { obj ->
                    val entry = parse { TorBoxJson.decodeFromJsonElement(CachedEntryDto.serializer(), obj) }
                    entry.hash?.let { result[it.lowercase()] = entry }
                }
            }
        }
        return result
    }

    suspend fun exportMagnet(torrentId: Long): String {
        val url = api("torrents/exportdata") {
            addQueryParameter("torrent_id", torrentId.toString())
            addQueryParameter("type", "magnet")
        }
        val data = envelope(execute(authed(url).get().build()))
        return (data as? JsonPrimitive)?.contentOrNull
            ?: throw TorBoxException.Malformed(IllegalStateException("No magnet in response"))
    }

    suspend fun exportTorrentFile(torrentId: Long): ByteArray {
        val url = api("torrents/exportdata") {
            addQueryParameter("torrent_id", torrentId.toString())
            addQueryParameter("type", "file")
        }
        val response = execute(authed(url).get().build())
        response.use {
            if (!it.isSuccessful) envelope(it)
            return it.body.bytes()
        }
    }

    suspend fun torrentInfo(hash: String, timeoutSeconds: Int = 10): TorrentInfoDto {
        val url = api("torrents/torrentinfo") {
            addQueryParameter("hash", hash)
            addQueryParameter("timeout", timeoutSeconds.toString())
        }
        return get(url, TorrentInfoDto.serializer())
    }

    /* ---------------------------------------------------------------- queued */

    suspend fun queued(kind: TransferKind): List<QueuedDto> =
        getList(api("queued/getqueued") {
            addQueryParameter("type", kind.apiType)
            addQueryParameter("bypass_cache", "true")
        }, QueuedDto.serializer()).map { if (it.type.isNullOrBlank()) it.copy(type = kind.apiType) else it }

    suspend fun controlQueued(kind: TransferKind, id: Long?, operation: String) {
        val json = buildJsonObject {
            if (id != null) put("queued_id", id) else put("all", true)
            put("operation", operation)
            put("type", kind.apiType)
        }
        post(api("queued/controlqueued"), json.toBody())
    }

    /* ------------------------------------------------------------------- rss */

    suspend fun rssFeeds(): List<RssFeedDto> = getList(api("rss/getfeeds"), RssFeedDto.serializer())

    suspend fun rssItems(feedId: Long): List<RssItemDto> =
        getList(api("rss/getfeeditems") { addQueryParameter("rss_feed_id", feedId.toString()) }, RssItemDto.serializer())

    suspend fun addRss(input: RssFeedInput) {
        post(api("rss/addrss"), rssJson(input, includeUrl = true).toBody())
    }

    suspend fun modifyRss(id: Long, input: RssFeedInput) {
        val json = JsonObject(rssJson(input, includeUrl = false) + ("rss_feed_id" to JsonPrimitive(id)))
        post(api("rss/modifyrss"), json.toBody())
    }

    /** [operation] is one of `update`, `delete`, `pause`, `resume`. */
    suspend fun controlRss(id: Long, operation: String) {
        post(api("rss/controlrss"), buildJsonObject {
            put("rss_feed_id", id)
            put("operation", operation)
        }.toBody())
    }

    private fun rssJson(input: RssFeedInput, includeUrl: Boolean) = buildJsonObject {
        if (includeUrl) put("url", input.url.trim())
        put("name", input.name.trim())
        put("do_regex", input.doRegex)
        put("dont_regex", input.dontRegex)
        put("dont_older_than", input.dontOlderThan)
        put("pass_check", input.passCheck)
        put("scan_interval", input.scanIntervalMinutes)
        put("rss_type", input.type.apiType)
        put("torrent_seeding", input.seeding.apiValue)
    }

    /* ---------------------------------------------------------- integrations */

    suspend fun integrationJobs(): List<IntegrationJobDto> = getList(api("integration/jobs"), IntegrationJobDto.serializer())

    suspend fun cancelIntegrationJob(jobId: Long) {
        val response = execute(authed(api("integration/job/$jobId")).delete().build())
        envelope(response)
    }

    suspend fun queueIntegration(
        provider: IntegrationProvider,
        kind: TransferKind,
        id: Long,
        fileId: Long?,
        providerToken: String?,
    ) {
        val json = buildJsonObject {
            put("id", id)
            if (fileId != null) put("file_id", fileId)
            put("zip", fileId == null)
            put("type", kind.apiType)
            providerToken?.takeIf { it.isNotBlank() }?.let { put(provider.tokenKey, it.trim()) }
        }
        post(api("integration/${provider.path}"), json.toBody())
    }

    /** Where to send a person to authorise a cloud provider. TorBox finishes on `torbox.app/{provider}/success?token=…`. */
    fun oauthUrl(provider: IntegrationProvider): String? =
        provider.oauthProvider?.let { api("integration/oauth/$it").toString() }

    /* --------------------------------------------------------- notifications */

    suspend fun notifications(): List<NotificationDto> =
        getList(api("notifications/mynotifications"), NotificationDto.serializer())

    suspend fun clearNotification(id: Long?) {
        post(api(if (id == null) "notifications/clear" else "notifications/clear/$id"), EMPTY_JSON)
    }

    suspend fun sendTestNotification() {
        post(api("notifications/test"), EMPTY_JSON)
    }

    /* --------------------------------------------------------------- general */

    suspend fun hosters(): List<HosterDto> = getList(api("webdl/hosters"), HosterDto.serializer())

    /** True when the API root answers successfully. */
    suspend fun isUp(): Boolean {
        val response = execute(Request.Builder().url(rootBase).get().build())
        response.use {
            if (!it.isSuccessful) return false
            val body = runCatching { TorBoxJson.parseToJsonElement(it.body.string()) }.getOrNull() as? JsonObject
            return (body?.get("success") as? JsonPrimitive)?.booleanOrNull ?: true
        }
    }

    suspend fun stats(): JsonObject {
        val data = envelope(execute(Request.Builder().url(api("stats")).get().build()))
        return data as? JsonObject ?: JsonObject(emptyMap())
    }

    suspend fun changelog(): List<ChangelogDto> {
        val data = envelope(execute(Request.Builder().url(api("changelogs/json")).get().build()))
        return parse { TorBoxJson.decodeFromJsonElement(ListSerializer(ChangelogDto.serializer()), data.asArray()) }
    }

    /** CDN test files, closest region first. */
    suspend fun speedtestServers(long: Boolean = false): List<SpeedServer> {
        val url = api("speedtest") { addQueryParameter("test_length", if (long) "long" else "short") }
        return SpeedtestParser.parse(envelope(execute(Request.Builder().url(url).get().build())))
    }

    /* ---------------------------------------------------------------- search */

    suspend fun searchTorrents(query: String): SearchResultDto = search("torrents", query)

    suspend fun searchUsenet(query: String): SearchResultDto = search("usenet", query)

    private suspend fun search(kind: String, query: String): SearchResultDto {
        val url = searchBase.newBuilder()
            .addPathSegment(kind)
            .addPathSegment("search")
            .addPathSegment(query.trim())
            .addQueryParameter("metadata", "true")
            .addQueryParameter("check_cache", "true")
            .addQueryParameter("check_owned", "true")
            .addQueryParameter("search_user_engines", "true")
            .build()
        return get(url, SearchResultDto.serializer())
    }

    /* -------------------------------------------------------------- plumbing */

    private fun api(path: String, block: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        apiBase.newBuilder().addPathSegments(path).apply(block).build()

    private fun authed(url: HttpUrl): Request.Builder {
        val key = token() ?: throw notSignedIn()
        return Request.Builder().url(url).header("Authorization", "Bearer $key")
    }

    private fun notSignedIn() = TorBoxException.Api("NO_AUTH", null, 401)

    private suspend fun <T> get(url: HttpUrl, serializer: DeserializationStrategy<T>): T =
        decode(execute(authed(url).get().build()), serializer)

    private suspend fun <T> getList(url: HttpUrl, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        val data = envelope(execute(authed(url).get().build()))
        return parse { TorBoxJson.decodeFromJsonElement(ListSerializer(serializer), data.asArray()) }
    }

    private suspend fun post(url: HttpUrl, body: RequestBody): JsonElement? =
        envelope(execute(authed(url).post(body).build()))

    private suspend fun <T> post(url: HttpUrl, body: RequestBody, serializer: DeserializationStrategy<T>): T =
        decode(execute(authed(url).post(body).build()), serializer)

    private fun <T> decode(response: Response, serializer: DeserializationStrategy<T>): T {
        val data = envelope(response) ?: JsonObject(emptyMap())
        val element = if (data is JsonNull) JsonObject(emptyMap()) else data
        return parse { TorBoxJson.decodeFromJsonElement(serializer, element) }
    }

    /** Unwraps `{ success, error, detail, data }`, throwing [TorBoxException.Api] on failure. */
    private fun envelope(response: Response): JsonElement? = response.use {
        val text = try {
            it.body.string()
        } catch (e: IOException) {
            throw TorBoxException.Network(e)
        }
        val json = runCatching { TorBoxJson.parseToJsonElement(text) }.getOrNull()
        val obj = json as? JsonObject
        val success = (obj?.get("success") as? JsonPrimitive)?.booleanOrNull
        if (!it.isSuccessful || success == false) {
            throw TorBoxException.Api(
                code = (obj?.get("error") as? JsonPrimitive)?.contentOrNull,
                detail = (obj?.get("detail") as? JsonPrimitive)?.contentOrNull,
                httpStatus = if (it.isSuccessful) 400 else it.code,
            )
        }
        if (json == null) throw TorBoxException.Malformed(IllegalStateException("Response was not JSON"))
        obj?.get("data") ?: json
    }

    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: TorBoxException) {
        throw e
    } catch (e: Exception) {
        throw TorBoxException.Malformed(e)
    }

    private fun JsonElement?.asArray(): JsonArray = when (this) {
        is JsonArray -> this
        is JsonObject -> JsonArray(listOf(this))
        else -> JsonArray(emptyList())
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(TorBoxException.Network(e))
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun multipart(block: MultipartBody.Builder.() -> Unit): RequestBody =
        MultipartBody.Builder().setType(MultipartBody.FORM).apply(block).build()

    private fun JsonObject.toBody(): RequestBody = toString().toRequestBody(JSON)

    companion object {
        const val DEFAULT_API_BASE = "https://api.torbox.app/v1/api/"
        const val DEFAULT_SEARCH_BASE = "https://search-api.torbox.app/"
        const val DEFAULT_ROOT = "https://api.torbox.app/"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_JSON = "{}".toRequestBody(JSON)
    }
}
