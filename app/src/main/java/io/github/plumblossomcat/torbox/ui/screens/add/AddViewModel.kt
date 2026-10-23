package io.github.plumblossomcat.torbox.ui.screens.add

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.CreatedDto
import io.github.plumblossomcat.torbox.core.api.PostProcessing
import io.github.plumblossomcat.torbox.core.api.SeedPreference
import io.github.plumblossomcat.torbox.core.api.UploadFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Magnet
import io.github.plumblossomcat.torbox.data.IncomingIntent
import io.github.plumblossomcat.torbox.data.PendingAdd
import io.github.plumblossomcat.torbox.data.friendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CacheStatus {
    data object Idle : CacheStatus
    data object Checking : CacheStatus
    data class Cached(val count: Int, val of: Int, val name: String?, val size: Long) : CacheStatus
    data class NotCached(val of: Int) : CacheStatus
}

data class AddForm(
    val kind: TransferKind = TransferKind.TORRENT,
    val text: String = "",
    val file: UploadFile? = null,
    val name: String = "",
    val password: String = "",
    val seed: SeedPreference = SeedPreference.AUTO,
    val allowZip: Boolean = true,
    val asQueued: Boolean = false,
    val postProcessing: PostProcessing = PostProcessing.DEFAULT,
    val cache: CacheStatus = CacheStatus.Idle,
    val submitting: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
) {
    /** Non-empty lines of the text field: each one is a separate download. */
    val entries: List<String> get() = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    val canSubmit: Boolean get() = !submitting && (file != null || entries.isNotEmpty())

    val isBatch: Boolean get() = file == null && entries.size > 1
}

sealed interface AddResult {
    data class Opened(val kind: TransferKind, val id: Long) : AddResult
    data class Many(val kind: TransferKind, val added: Int, val failed: Int, val firstError: String?) : AddResult
    data class Queued(val count: Int) : AddResult
}

@OptIn(FlowPreview::class)
class AddViewModel(private val container: AppContainer, initialKind: TransferKind?) : ViewModel() {
    private val _form = MutableStateFlow(AddForm(kind = initialKind ?: TransferKind.TORRENT))
    val form: StateFlow<AddForm> = _form.asStateFlow()

    val result = MutableStateFlow<AddResult?>(null)
    private var cacheJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            _form.update { it.copy(seed = settings.seed, allowZip = settings.allowZip) }
        }
        viewModelScope.launch {
            container.pendingAdd.filterNotNull().collect { pending ->
                apply(pending)
                container.pendingAdd.value = null
            }
        }
    }

    private fun apply(pending: PendingAdd) {
        _form.update {
            it.copy(kind = pending.kind, text = pending.text ?: "", file = pending.file, error = null, cache = CacheStatus.Idle)
        }
        scheduleCacheCheck()
    }

    fun setKind(kind: TransferKind) {
        if (kind == _form.value.kind) return
        _form.update { it.copy(kind = kind, file = null, error = null, cache = CacheStatus.Idle) }
        scheduleCacheCheck()
    }

    fun setText(text: String) {
        _form.update { it.copy(text = text, error = null) }
        scheduleCacheCheck()
    }

    fun setName(v: String) = _form.update { it.copy(name = v) }
    fun setPassword(v: String) = _form.update { it.copy(password = v) }
    fun setSeed(v: SeedPreference) = _form.update { it.copy(seed = v) }
    fun setAllowZip(v: Boolean) = _form.update { it.copy(allowZip = v) }
    fun setQueued(v: Boolean) = _form.update { it.copy(asQueued = v) }
    fun setPostProcessing(v: PostProcessing) = _form.update { it.copy(postProcessing = v) }
    fun clearFile() = _form.update { it.copy(file = null) }

    fun pickFile(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { IncomingIntent.fromUri(uri, null, resolver) }
            if (picked?.file == null) {
                _form.update { it.copy(error = "That file couldn't be read.") }
                return@launch
            }
            val kind = _form.value.kind
            val file = picked.file
            val expected = if (kind == TransferKind.USENET) ".nzb" else ".torrent"
            if (!file.fileName.endsWith(expected, ignoreCase = true) && picked.kind != kind) {
                _form.update { it.copy(error = "Choose a $expected file.") }
                return@launch
            }
            _form.update { it.copy(file = file, error = null) }
        }
    }

    private fun scheduleCacheCheck() {
        cacheJob?.cancel()
        val f = _form.value
        val hashes = when (f.kind) {
            TransferKind.TORRENT -> f.entries.mapNotNull { Magnet.infoHash(it) }
            TransferKind.USENET, TransferKind.WEB -> f.entries.filter { it.startsWith("http", ignoreCase = true) }.map { Magnet.md5(it) }
        }.distinct()
        if (hashes.isEmpty() || f.file != null) {
            _form.update { it.copy(cache = CacheStatus.Idle) }
            return
        }
        cacheJob = viewModelScope.launch {
            delay(450)
            _form.update { it.copy(cache = CacheStatus.Checking) }
            val status = try {
                val cached = container.client.checkCached(f.kind, hashes)
                if (cached.isEmpty()) {
                    CacheStatus.NotCached(hashes.size)
                } else {
                    val first = cached.values.first()
                    CacheStatus.Cached(cached.size, hashes.size, first.name, first.size)
                }
            } catch (_: Exception) {
                CacheStatus.Idle
            }
            _form.update { it.copy(cache = status) }
        }
    }

    fun submit() {
        val f = _form.value
        if (!f.canSubmit) {
            _form.update { it.copy(error = emptyMessage(it.kind)) }
            return
        }
        validate(f)?.let { problem ->
            _form.update { it.copy(error = problem) }
            return
        }
        _form.update { it.copy(submitting = true, error = null, progress = null) }
        viewModelScope.launch {
            val items: List<String?> = if (f.file != null) listOf(null) else f.entries
            var added = 0
            var firstError: String? = null
            var last: CreatedDto? = null
            items.forEachIndexed { index, entry ->
                if (items.size > 1) _form.update { it.copy(progress = "Adding ${index + 1} of ${items.size}…") }
                try {
                    last = create(f, entry)
                    added++
                } catch (e: Exception) {
                    container.session.inspect(e)
                    if (firstError == null) firstError = e.friendly()
                }
            }
            viewModelScope.launch { container.library.refresh(f.kind) }
            when {
                added == 0 -> _form.update { it.copy(submitting = false, progress = null, error = firstError) }
                f.asQueued -> result.value = AddResult.Queued(added)
                items.size == 1 -> {
                    val created = last!!
                    val id = when (f.kind) {
                        TransferKind.TORRENT -> created.torrentId
                        TransferKind.USENET -> created.usenetdownloadId
                        TransferKind.WEB -> created.webdownloadId
                    }
                    result.value = if (id > 0) AddResult.Opened(f.kind, id) else AddResult.Many(f.kind, 1, 0, null)
                }
                else -> result.value = AddResult.Many(f.kind, added, items.size - added, firstError)
            }
        }
    }

    private suspend fun create(f: AddForm, entry: String?): CreatedDto {
        val name = f.name.takeIf { !f.isBatch && it.isNotBlank() }
        return when (f.kind) {
            TransferKind.TORRENT -> {
                val magnet = entry?.let { if (Magnet.isMagnet(it)) it else Magnet.fromHash(Magnet.infoHash(it) ?: it) }
                container.client.addTorrent(magnet, f.file, name, f.seed, f.allowZip, f.asQueued)
            }
            TransferKind.USENET -> container.client.addUsenet(entry, f.file, name, f.password, f.postProcessing, f.asQueued)
            TransferKind.WEB -> container.client.addWeb(entry!!, name, f.password, f.asQueued)
        }
    }

    private fun validate(f: AddForm): String? {
        if (f.file != null) return null
        val bad = when (f.kind) {
            TransferKind.TORRENT -> f.entries.firstOrNull { !Magnet.isMagnet(it) && Magnet.infoHash(it) == null }
            TransferKind.USENET, TransferKind.WEB -> f.entries.firstOrNull { !it.startsWith("http://", true) && !it.startsWith("https://", true) }
        } ?: return null
        val shown = if (bad.length > 48) bad.take(45) + "…" else bad
        return when (f.kind) {
            TransferKind.TORRENT -> "“$shown” isn't a magnet link or info hash."
            else -> "“$shown” isn't a web link. Links start with https://"
        }
    }

    private fun emptyMessage(kind: TransferKind) = when (kind) {
        TransferKind.TORRENT -> "Paste a magnet link or choose a .torrent file."
        TransferKind.USENET -> "Paste an NZB link or choose an .nzb file."
        TransferKind.WEB -> "Paste at least one link."
    }

    companion object {
        fun describe(status: CacheStatus.Cached): String = buildString {
            if (status.of > 1) append("${status.count} of ${status.of} are cached") else append("Cached on TorBox")
            append(" — ready almost instantly")
            if (status.of == 1 && status.size > 0) append("  ·  ").append(Format.bytes(status.size))
        }
    }
}
