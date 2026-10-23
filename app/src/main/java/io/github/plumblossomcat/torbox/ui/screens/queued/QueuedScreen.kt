package io.github.plumblossomcat.torbox.ui.screens.queued

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.QueuedDto
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Magnet
import io.github.plumblossomcat.torbox.core.util.Timestamps
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ConfirmDialog
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LoadScaffold
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Queued items from all three download types, newest first. */
class QueuedViewModel(container: AppContainer) : LoadViewModel<List<QueuedDto>>(container, {
    coroutineScope {
        TransferKind.entries.map { kind -> async { client.queued(kind) } }.flatMap { it.await() }
    }.sortedByDescending { Timestamps.parse(it.createdAt) }
}) {
    fun start(item: QueuedDto) {
        val kind = TransferKind.fromApiType(item.type) ?: TransferKind.TORRENT
        update { list -> list.filterNot { it.id == item.id && it.type == item.type } }
        mutate("Started “${item.displayName}”") {
            client.controlQueued(kind, item.id, "start")
            library.refresh(kind)
        }
    }

    fun delete(item: QueuedDto) {
        val kind = TransferKind.fromApiType(item.type) ?: TransferKind.TORRENT
        update { list -> list.filterNot { it.id == item.id && it.type == item.type } }
        mutate("Removed from queue") { client.controlQueued(kind, item.id, "delete") }
    }

    fun clearAll() {
        update { emptyList() }
        mutate("Queue cleared") {
            TransferKind.entries.forEach { kind -> runCatching { client.controlQueued(kind, null, "delete") } }
        }
    }
}

val QueuedDto.displayName: String
    get() = name?.takeIf { it.isNotBlank() }
        ?: magnet?.let { Magnet.displayName(it) }
        ?: hash?.takeIf { it.isNotBlank() }
        ?: "Queued download #$id"

@Composable
fun QueuedScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: QueuedViewModel = viewModel { QueuedViewModel(container) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    LaunchedEffect(message) {
        message?.let {
            toaster.show(it)
            vm.message.value = null
        }
    }
    QueuedContent(load, refreshing, onBack, vm::refresh, vm::start, vm::delete, vm::clearAll)
}

@Composable
fun QueuedContent(
    load: Load<List<QueuedDto>>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStart: (QueuedDto) -> Unit,
    onDelete: (QueuedDto) -> Unit,
    onClearAll: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    LoadScaffold(
        title = "Queued",
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { it.isEmpty() },
        empty = {
            EmptyState(
                "Nothing queued",
                "Downloads you add with “Add to queue”, and ones waiting for a free slot on your plan, wait here until they start.",
            )
        },
        actions = {
            TbIconButton(TbIcons.Delete, "Clear queue", { confirmClear = true }, enabled = (load as? Load.Ready)?.value?.isNotEmpty() == true)
        },
    ) { list ->
        items(list, key = { "${it.type}:${it.id}" }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .padding(start = Gutter, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, style = Tb.type.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(TransferKind.fromApiType(item.type)?.label ?: item.type.orEmpty())
                        Text("  Queued " + Format.relative(Timestamps.parse(item.createdAt)), style = Tb.type.monoSmall)
                    }
                }
                TbIconButton(TbIcons.Play, "Start now", { onStart(item) })
                TbIconButton(TbIcons.Delete, "Remove from queue", { onDelete(item) }, tint = Tb.colors.inkMuted)
            }
            Hairline(Modifier.padding(horizontal = Gutter))
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear the whole queue?",
            body = "Every queued torrent, usenet and web download will be removed. Nothing that has already started is affected.",
            confirmLabel = "Clear queue",
            danger = true,
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
