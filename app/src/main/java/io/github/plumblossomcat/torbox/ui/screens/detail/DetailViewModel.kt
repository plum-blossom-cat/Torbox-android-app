package io.github.plumblossomcat.torbox.ui.screens.detail

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.ControlOp
import io.github.plumblossomcat.torbox.core.api.IntegrationProvider
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.data.AppSettings
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.ui.components.guessMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What to do with a link once TorBox hands it over. */
enum class LinkUse { SAVE, OPEN, COPY, SHARE }

data class DetailUi(
    val loading: Boolean = true,
    val error: String? = null,
    val collapsed: Set<String> = emptySet(),
    /** Key of the file (or "zip") whose link is being prepared. */
    val preparing: String? = null,
    val busy: Boolean = false,
    val deleted: Boolean = false,
)

data class DetailState(
    val item: Transfer? = null,
    val ui: DetailUi = DetailUi(),
    val rows: List<TreeRow> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val cloudTokens: Map<IntegrationProvider, String> = emptyMap(),
)

class DetailViewModel(
    private val container: AppContainer,
    val kind: TransferKind,
    val id: Long,
) : ViewModel() {
    private val ui = MutableStateFlow(DetailUi(loading = container.library.find(kind, id) == null))

    val state: StateFlow<DetailState> = combine(
        container.library.state,
        ui,
        container.settings.settings,
        container.settings.integrationTokens(),
    ) { library, ui, settings, tokens ->
        val item = library.getValue(kind).items.firstOrNull { it.id == id }
        DetailState(item, ui, item?.let { FileTree.rows(it.files, ui.collapsed) } ?: emptyList(), settings, tokens)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState(container.library.find(kind, id)))

    /** One-shot messages for the snackbar. */
    val messages = MutableStateFlow<Message?>(null)

    data class Message(val text: String, val action: String? = null, val kind: MessageAction? = null)
    enum class MessageAction { CLOUD }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                container.library.fetch(kind, id)
                ui.update { it.copy(loading = false, error = null) }
            } catch (e: Exception) {
                ui.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }

    suspend fun poll() {
        runCatching { container.library.fetch(kind, id) }
    }

    fun toggleFolder(key: String) = ui.update { it.copy(collapsed = if (key in it.collapsed) it.collapsed - key else it.collapsed + key) }

    fun control(op: ControlOp, done: String) {
        viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            try {
                container.library.control(kind, id, op)
                say(done)
            } catch (e: Exception) {
                say(e.friendly())
            }
            ui.update { it.copy(busy = false) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            try {
                container.library.delete(kind, listOf(id))
                ui.update { it.copy(busy = false, deleted = true) }
            } catch (e: Exception) {
                ui.update { it.copy(busy = false) }
                say(e.friendly())
            }
        }
    }

    /** Requests a CDN link for [file] (or the whole download zipped) and uses it. */
    fun link(file: TransferFile?, use: LinkUse) {
        val item = state.value.item ?: return
        val key = file?.let { "f${it.id}" } ?: "zip"
        if (ui.value.preparing != null) return
        ui.update { it.copy(preparing = key) }
        viewModelScope.launch {
            try {
                val url = container.client.requestLink(kind, id, file?.id)
                val name = file?.name ?: "${item.name}.zip"
                val device = container.device
                when (use) {
                    LinkUse.SAVE -> say(if (device.save(url, name)) "Downloading “$name” — check your notifications" else "Android's download manager isn't available")
                    LinkUse.OPEN -> if (!device.openWith(url, file?.guessMime() ?: "application/zip", "Open “$name” with")) {
                        say("No installed app can open this file")
                    }
                    LinkUse.COPY -> {
                        device.copy(url, name)
                        if (!device.systemConfirmsCopy) say("Link copied. It works for a few hours.")
                    }
                    LinkUse.SHARE -> device.share(url, name)
                }
            } catch (e: Exception) {
                container.session.inspect(e)
                say(e.friendly())
            }
            ui.update { it.copy(preparing = null) }
        }
    }

    fun copy(text: String, what: String) {
        container.device.copy(text, what)
        if (!container.device.systemConfirmsCopy) say("$what copied")
    }

    fun copyMagnet() {
        val item = state.value.item ?: return
        val existing = item.magnet?.takeIf { it.startsWith("magnet:") }
        if (existing != null) return copy(existing, "Magnet link")
        viewModelScope.launch {
            try {
                copy(container.client.exportMagnet(id), "Magnet link")
            } catch (e: Exception) {
                say(e.friendly())
            }
        }
    }

    fun exportTorrent(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val bytes = container.client.exportTorrentFile(id)
                withContext(Dispatchers.IO) { resolver.openOutputStream(uri)?.use { it.write(bytes) } }
                say("Saved .torrent file")
            } catch (e: Exception) {
                say(e.friendly())
            }
        }
    }

    fun sendToCloud(provider: IntegrationProvider, file: TransferFile?) {
        val token = state.value.cloudTokens[provider]
        if (provider.tokenRequired && token == null) {
            messages.value = Message("Connect ${provider.label} first", "Set up", MessageAction.CLOUD)
            return
        }
        viewModelScope.launch {
            try {
                container.client.queueIntegration(provider, kind, id, file?.id, token)
                messages.value = Message("Upload to ${provider.label} queued", "View", MessageAction.CLOUD)
            } catch (e: Exception) {
                say(e.friendly())
            }
        }
    }

    private fun say(text: String) {
        messages.value = Message(text)
    }
}
