package io.github.plumblossomcat.torbox.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.model.TransferState
import io.github.plumblossomcat.torbox.data.KindState
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.AddFab
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.ChipRow
import io.github.plumblossomcat.torbox.ui.components.ConfirmDialog
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.ErrorState
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Notice
import io.github.plumblossomcat.torbox.ui.components.ScreenHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonRows
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.components.TransferRow
import io.github.plumblossomcat.torbox.ui.components.UnderlineTabs
import io.github.plumblossomcat.torbox.ui.components.WhileVisible
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    READY("Ready"),
    SEEDING("Seeding"),
    FAILED("Issues"),
    ;

    fun matches(t: Transfer): Boolean = when (this) {
        ALL -> true
        ACTIVE -> t.state.isActive || t.state == TransferState.QUEUED || t.state == TransferState.PAUSED && t.progress < 1.0
        READY -> t.state.isDone
        SEEDING -> t.state == TransferState.SEEDING
        FAILED -> t.state == TransferState.FAILED || t.state == TransferState.STALLED
    }

    companion object {
        fun forKind(kind: TransferKind): List<LibraryFilter> =
            if (kind == TransferKind.TORRENT) entries else entries - SEEDING
    }
}

enum class LibrarySort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name"),
    SIZE("Largest first"),
    PROGRESS("Least complete"),
}

data class LibraryUi(
    val kind: TransferKind = TransferKind.TORRENT,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.NEWEST,
    val query: String = "",
    val searching: Boolean = false,
    val selection: Set<Long> = emptySet(),
    val deleting: Boolean = false,
)

data class LibraryState(
    val ui: LibraryUi = LibraryUi(),
    val items: List<Transfer> = emptyList(),
    val kindState: KindState = KindState(),
    val counts: Map<TransferKind, Int> = emptyMap(),
) {
    val selecting: Boolean get() = ui.selection.isNotEmpty()
    val hasActive: Boolean get() = kindState.items.any { it.state.isActive }
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val ui = MutableStateFlow(LibraryUi())

    val state: StateFlow<LibraryState> = combine(ui, container.library.state) { ui, library ->
        val ks = library.getValue(ui.kind)
        val q = ui.query.trim()
        val filtered = ks.items
            .filter { ui.filter.matches(it) }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.hash.startsWith(q, ignoreCase = true) }
        val sorted = when (ui.sort) {
            LibrarySort.NEWEST -> filtered.sortedByDescending { it.createdAt }
            LibrarySort.OLDEST -> filtered.sortedBy { it.createdAt }
            LibrarySort.NAME -> filtered.sortedBy { it.name.lowercase() }
            LibrarySort.SIZE -> filtered.sortedByDescending { it.size }
            LibrarySort.PROGRESS -> filtered.sortedBy { it.progress }
        }
        LibraryState(ui, sorted, ks, library.mapValues { it.value.items.size })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { container.library.refresh(ui.value.kind) }
    }

    suspend fun poll() = container.library.refresh(ui.value.kind)

    fun selectKind(kind: TransferKind) {
        ui.update { it.copy(kind = kind, selection = emptySet(), filter = if (it.filter in LibraryFilter.forKind(kind)) it.filter else LibraryFilter.ALL) }
        if (!container.library.state.value.getValue(kind).loaded) refresh()
        else viewModelScope.launch { container.library.refresh(kind) }
    }

    fun setFilter(filter: LibraryFilter) = ui.update { it.copy(filter = filter) }
    fun setSort(sort: LibrarySort) = ui.update { it.copy(sort = sort) }
    fun setQuery(q: String) = ui.update { it.copy(query = q) }
    fun setSearching(on: Boolean) = ui.update { it.copy(searching = on, query = if (on) it.query else "") }
    fun toggle(id: Long) = ui.update { it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id) }
    fun selectAll() = ui.update { it.copy(selection = state.value.items.map { t -> t.id }.toSet()) }
    fun clearSelection() = ui.update { it.copy(selection = emptySet()) }

    fun deleteSelected(onDone: (String) -> Unit) {
        val ids = ui.value.selection
        val kind = ui.value.kind
        if (ids.isEmpty()) return
        ui.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val message = try {
                container.library.delete(kind, ids)
                if (ids.size == 1) "Deleted" else "Deleted ${ids.size} items"
            } catch (e: Exception) {
                e.friendly()
            }
            ui.update { it.copy(deleting = false, selection = emptySet()) }
            onDone(message)
        }
    }
}

@Composable
fun LibraryScreen(onOpen: (Transfer) -> Unit, onAdd: (TransferKind) -> Unit, onQueued: () -> Unit) {
    val container = LocalContainer.current
    val vm: LibraryViewModel = viewModel { LibraryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val toaster = LocalToaster.current
    val interval = if (state.hasActive) (settings?.refreshSeconds ?: 5) * 1000L else 60_000L
    WhileVisible(interval) { vm.poll() }
    BackHandler(state.selecting) { vm.clearSelection() }
    BackHandler(state.ui.searching && !state.selecting) { vm.setSearching(false) }
    LibraryContent(
        state = state,
        actions = LibraryActions(
            onKind = vm::selectKind,
            onFilter = vm::setFilter,
            onSort = vm::setSort,
            onQuery = vm::setQuery,
            onSearching = vm::setSearching,
            onRefresh = vm::refresh,
            onOpen = onOpen,
            onToggle = vm::toggle,
            onSelectAll = vm::selectAll,
            onClearSelection = vm::clearSelection,
            onDelete = { vm.deleteSelected { toaster.show(it) } },
            onAdd = { onAdd(state.ui.kind) },
            onQueued = onQueued,
        ),
    )
}

class LibraryActions(
    val onKind: (TransferKind) -> Unit = {},
    val onFilter: (LibraryFilter) -> Unit = {},
    val onSort: (LibrarySort) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSearching: (Boolean) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpen: (Transfer) -> Unit = {},
    val onToggle: (Long) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onQueued: () -> Unit = {},
)

@Composable
fun LibraryContent(state: LibraryState, actions: LibraryActions) {
    val c = Tb.colors
    val ui = state.ui
    var confirmDelete by remember { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(ui.kind, ui.filter, ui.sort) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state.selecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "libraryHeader",
            ) { selecting ->
                if (selecting) {
                    SelectionBar(
                        count = ui.selection.size,
                        onClose = actions.onClearSelection,
                        onSelectAll = actions.onSelectAll,
                        onDelete = { confirmDelete = true },
                    )
                } else {
                    ScreenHeader(
                        title = "Library",
                        eyebrow = state.counts.values.sum().let { if (it == 1) "1 item" else "$it items" },
                        actions = {
                            TbIconButton(TbIcons.Search, "Search library", { actions.onSearching(!ui.searching) })
                            Box {
                                TbIconButton(TbIcons.Sort, "Sort", { sortOpen = true })
                                SortMenu(sortOpen, ui.sort, onDismiss = { sortOpen = false }, onSort = {
                                    actions.onSort(it)
                                    sortOpen = false
                                })
                            }
                            TbIconButton(TbIcons.Queue, "Queued downloads", actions.onQueued)
                        },
                    )
                }
            }
            AnimatedVisibility(ui.searching && !state.selecting, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
                TbTextField(
                    value = ui.query,
                    onValueChange = actions.onQuery,
                    placeholder = "Filter by name or hash",
                    leading = TbIcons.Search,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 8.dp),
                    trailing = {
                        TbIconButton(TbIcons.Close, "Close search", { actions.onSearching(false) }, tint = c.inkMuted, size = 40.dp)
                    },
                )
            }
            UnderlineTabs(
                tabs = TransferKind.entries.map { it.pluralLabel to (state.counts[it] ?: 0) },
                selected = TransferKind.entries.indexOf(ui.kind),
                onSelect = { actions.onKind(TransferKind.entries[it]) },
                modifier = Modifier.padding(horizontal = Gutter),
            )
            val filters = LibraryFilter.forKind(ui.kind)
            ChipRow(
                options = filters.map { it.label },
                selected = filters.indexOf(ui.filter).coerceAtLeast(0),
                onSelect = { actions.onFilter(filters[it]) },
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Gutter, vertical = 14.dp),
            )
            Hairline()
            PullToRefreshBox(
                isRefreshing = state.kindState.refreshing && state.kindState.loaded,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.kindState.loaded -> SkeletonRows(7)
                    state.kindState.items.isEmpty() && state.kindState.error != null ->
                        ErrorState(state.kindState.error, actions.onRefresh)
                    state.items.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item { EmptyFor(state, actions) }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        state.kindState.error?.let { message ->
                            item(key = "error") {
                                Spacer(Modifier.height(12.dp))
                                Notice("Couldn't refresh: $message", action = "Retry", onAction = actions.onRefresh)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        items(state.items, key = { it.key }) { item ->
                            TransferRow(
                                item = item,
                                onClick = { if (state.selecting) actions.onToggle(item.id) else actions.onOpen(item) },
                                onLongClick = { actions.onToggle(item.id) },
                                selecting = state.selecting,
                                selected = item.id in ui.selection,
                                modifier = Modifier.animateItem(),
                            )
                            Hairline(Modifier.padding(horizontal = Gutter))
                        }
                        item(key = "end") { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
        AnimatedVisibility(!state.selecting, Modifier.align(Alignment.BottomEnd), enter = fadeIn(), exit = fadeOut()) {
            AddFab(onClick = actions.onAdd)
        }
    }

    if (confirmDelete) {
        val n = ui.selection.size
        ConfirmDialog(
            title = if (n == 1) "Delete this download?" else "Delete $n downloads?",
            body = "They'll be removed from your TorBox account along with their files. This can't be undone.",
            confirmLabel = "Delete",
            danger = true,
            onConfirm = {
                confirmDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EmptyFor(state: LibraryState, actions: LibraryActions) {
    val ui = state.ui
    when {
        ui.query.isNotBlank() -> EmptyState("No matches", "Nothing in ${ui.kind.pluralLabel.lowercase()} matches “${ui.query.trim()}”.")
        state.kindState.items.isEmpty() -> EmptyState(
            title = when (ui.kind) {
                TransferKind.TORRENT -> "No torrents yet"
                TransferKind.USENET -> "No usenet downloads yet"
                TransferKind.WEB -> "No web downloads yet"
            },
            body = when (ui.kind) {
                TransferKind.TORRENT -> "Add a magnet link or a .torrent file. Cached torrents are ready in seconds."
                TransferKind.USENET -> "Add an NZB link or file and TorBox will fetch it from usenet for you."
                TransferKind.WEB -> "Paste a link from a supported file host, or any direct download link."
            },
            action = "Add ${ui.kind.label.lowercase()}",
            onAction = actions.onAdd,
        )
        else -> EmptyState("All clear", "Nothing here is ${ui.filter.label.lowercase()} right now.", action = "Show everything", onAction = { actions.onFilter(LibraryFilter.ALL) })
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 108.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TbIconButton(TbIcons.Close, "Cancel selection", onClose)
        Text("$count selected", style = Tb.type.title, modifier = Modifier.weight(1f).padding(start = 4.dp))
        TbButton("All", onSelectAll, style = ButtonStyle.Ghost, compact = true)
        Spacer(Modifier.width(6.dp))
        TbButton("Delete", onDelete, style = ButtonStyle.Danger, icon = TbIcons.Delete, compact = true)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun SortMenu(expanded: Boolean, current: LibrarySort, onDismiss: () -> Unit, onSort: (LibrarySort) -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Tb.colors.raised,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Sort by", style = Tb.type.eyebrow, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LibrarySort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(sort.label, style = Tb.type.body) },
                onClick = { onSort(sort) },
                trailingIcon = {
                    if (sort == current) Icon(TbIcons.Check, null, tint = Tb.colors.ink)
                },
            )
        }
    }
}
