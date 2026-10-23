package io.github.plumblossomcat.torbox.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.PostProcessing
import io.github.plumblossomcat.torbox.core.api.SearchMetadataDto
import io.github.plumblossomcat.torbox.core.api.SearchResultDto
import io.github.plumblossomcat.torbox.core.api.SearchTorrentDto
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Magnet
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.ChipRow
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.ErrorState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.ListRow
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.ScreenHeader
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.SheetHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonRows
import io.github.plumblossomcat.torbox.ui.components.Stat
import io.github.plumblossomcat.torbox.ui.components.StatGrid
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbLink
import io.github.plumblossomcat.torbox.ui.components.TbSheet
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.components.pressable
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchMode(val label: String) { TORRENTS("Torrents"), USENET("Usenet") }
enum class SearchSort(val label: String) { RELEVANCE("Best match"), SEEDERS("Most seeders"), SIZE("Largest"), CACHED("Instant first") }

data class SearchUi(
    val query: String = "",
    val submitted: String = "",
    val mode: SearchMode = SearchMode.TORRENTS,
    val sort: SearchSort = SearchSort.RELEVANCE,
    val result: Load<SearchResultDto>? = null,
    val adding: Set<String> = emptySet(),
    val added: Map<String, Long> = emptyMap(),
) {
    val rows: List<SearchTorrentDto>
        get() {
            val data = (result as? Load.Ready)?.value ?: return emptyList()
            val list = if (mode == SearchMode.USENET) data.nzbs.ifEmpty { data.torrents } else data.torrents
            return when (sort) {
                SearchSort.RELEVANCE -> list
                SearchSort.SEEDERS -> list.sortedByDescending { it.lastKnownSeeders }
                SearchSort.SIZE -> list.sortedByDescending { it.size }
                SearchSort.CACHED -> list.sortedByDescending { it.cached }
            }
        }
}

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()
    val recent: StateFlow<List<String>> = container.settings.settings.map { it.recentSearches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var job: Job? = null

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setSort(s: SearchSort) = _ui.update { it.copy(sort = s) }

    fun setMode(mode: SearchMode) {
        if (mode == _ui.value.mode) return
        _ui.update { it.copy(mode = mode) }
        if (_ui.value.submitted.isNotBlank()) search(_ui.value.submitted)
    }

    fun clear() {
        job?.cancel()
        _ui.update { it.copy(query = "", submitted = "", result = null) }
    }

    fun search(query: String = _ui.value.query) {
        val q = query.trim()
        if (q.length < 2) return
        job?.cancel()
        _ui.update { it.copy(query = q, submitted = q, result = Load.Loading) }
        job = viewModelScope.launch {
            container.settings.rememberSearch(q)
            val result = try {
                val data = if (_ui.value.mode == SearchMode.USENET) container.client.searchUsenet(q) else container.client.searchTorrents(q)
                Load.Ready(data)
            } catch (e: Exception) {
                container.session.inspect(e)
                Load.Failed(e.friendly())
            }
            _ui.update { it.copy(result = result) }
        }
    }

    fun clearRecent() {
        viewModelScope.launch { container.settings.clearSearches() }
    }

    fun add(row: SearchTorrentDto, onDone: (String, TransferKind?, Long?) -> Unit) {
        val key = row.hash.ifBlank { row.rawTitle ?: row.title.orEmpty() }
        if (key in _ui.value.adding) return
        _ui.update { it.copy(adding = it.adding + key) }
        viewModelScope.launch {
            val settings = container.settings.current()
            try {
                val (kind, id) = if (_ui.value.mode == SearchMode.USENET) {
                    val link = row.nzb ?: error("This result has no NZB link")
                    TransferKind.USENET to container.client.addUsenet(link, null, null, null, PostProcessing.DEFAULT, false).usenetdownloadId
                } else {
                    val magnet = row.magnet?.takeIf { it.startsWith("magnet:") } ?: Magnet.fromHash(row.hash, row.rawTitle)
                    TransferKind.TORRENT to container.client.addTorrent(magnet, null, null, settings.seed, settings.allowZip, false).torrentId
                }
                _ui.update { it.copy(adding = it.adding - key, added = it.added + (key to id)) }
                launch { container.library.refresh(kind) }
                onDone("Added “${row.title ?: row.rawTitle}”", kind, id.takeIf { it > 0 })
            } catch (e: Exception) {
                container.session.inspect(e)
                _ui.update { it.copy(adding = it.adding - key) }
                onDone(e.friendly(), null, null)
            }
        }
    }
}

val SearchTorrentDto.key: String get() = hash.ifBlank { rawTitle ?: title.orEmpty() }

@Composable
fun SearchScreen(onOpen: (TransferKind, Long) -> Unit) {
    val container = LocalContainer.current
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val focus = LocalFocusManager.current
    SearchContent(
        ui = ui,
        recent = recent,
        onQuery = vm::setQuery,
        onSearch = {
            focus.clearFocus()
            vm.search(it)
        },
        onClear = vm::clear,
        onMode = vm::setMode,
        onSort = vm::setSort,
        onClearRecent = vm::clearRecent,
        onAdd = { row ->
            vm.add(row) { message, kind, id ->
                if (kind != null && id != null) toaster.show(message, "Open") { onOpen(kind, id) } else toaster.show(message)
            }
        },
        onOpenAdded = { row ->
            ui.added[row.key]?.let { id -> onOpen(if (ui.mode == SearchMode.USENET) TransferKind.USENET else TransferKind.TORRENT, id) }
        },
    )
}

@Composable
fun SearchContent(
    ui: SearchUi,
    recent: List<String>,
    onQuery: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onMode: (SearchMode) -> Unit,
    onSort: (SearchSort) -> Unit,
    onClearRecent: () -> Unit,
    onAdd: (SearchTorrentDto) -> Unit,
    onOpenAdded: (SearchTorrentDto) -> Unit,
) {
    val c = Tb.colors
    var detail by remember { mutableStateOf<SearchTorrentDto?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search", eyebrow = "TorBox search")
        TbTextField(
            value = ui.query,
            onValueChange = onQuery,
            placeholder = "Title, show, album, release…",
            leading = TbIcons.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(ui.query) }),
            modifier = Modifier.padding(horizontal = Gutter),
            trailing = if (ui.query.isNotEmpty()) {
                { TbIconButton(TbIcons.Close, "Clear", onClear, tint = c.inkMuted, size = 40.dp) }
            } else {
                null
            },
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipRow(SearchMode.entries.map { it.label }, SearchMode.entries.indexOf(ui.mode), { onMode(SearchMode.entries[it]) })
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(20.dp).background(c.hairlineStrong))
            ChipRow(SearchSort.entries.map { it.label }, SearchSort.entries.indexOf(ui.sort), { onSort(SearchSort.entries[it]) })
        }
        Hairline()
        when (val r = ui.result) {
            null -> RecentSearches(recent, onSearch, onClearRecent)
            Load.Loading -> SkeletonRows(6)
            is Load.Failed -> ErrorState(r.message, { onSearch(ui.submitted) })
            is Load.Ready -> {
                val rows = ui.rows
                LazyColumn(Modifier.fillMaxSize()) {
                    r.value.metadata?.takeIf { !it.title.isNullOrBlank() }?.let { meta ->
                        item(key = "meta") { MetadataCard(meta) }
                    }
                    item(key = "count") {
                        SectionHeader(
                            when (rows.size) {
                                0 -> "No results"
                                1 -> "1 result"
                                else -> "${rows.size} results · ${rows.count { it.cached }} instant"
                            },
                            Modifier.padding(top = 12.dp),
                        )
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            EmptyState("Nothing found", "Try a shorter title, drop the year, or switch to ${if (ui.mode == SearchMode.TORRENTS) "Usenet" else "Torrents"}.")
                        }
                    }
                    items(rows, key = { it.key + it.size }) { row ->
                        ResultRow(
                            row = row,
                            adding = row.key in ui.adding,
                            added = row.key in ui.added,
                            onClick = { detail = row },
                            onAdd = { if (row.key in ui.added) onOpenAdded(row) else onAdd(row) },
                        )
                        Hairline(Modifier.padding(horizontal = Gutter))
                    }
                    item(key = "end") { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    detail?.let { row ->
        TbSheet(onDismiss = { detail = null }) {
            SheetHeader(row.title ?: row.rawTitle ?: "Result", row.rawTitle?.takeIf { it != row.title })
            StatGrid(
                listOfNotNull(
                    Stat("Size", if (row.size > 0) Format.bytes(row.size) else "—"),
                    Stat("Availability", if (row.cached) "Instant" else "Needs download", mono = false),
                    if (ui.mode == SearchMode.TORRENTS) Stat("Seeders", if (row.lastKnownSeeders >= 0) row.lastKnownSeeders.toString() else "—") else null,
                    if (ui.mode == SearchMode.TORRENTS) Stat("Peers", if (row.lastKnownPeers >= 0) row.lastKnownPeers.toString() else "—") else null,
                    Stat("Age", row.age ?: "—"),
                    Stat("Source", row.tracker ?: "—"),
                    if (row.files > 0) Stat("Files", row.files.toString()) else null,
                    if (row.categories.isNotEmpty()) Stat("Category", row.categories.joinToString(", "), mono = false) else null,
                ),
                Modifier.padding(horizontal = Gutter),
            )
            if (row.hash.isNotBlank()) {
                Hairline(Modifier.padding(horizontal = Gutter))
                Column(Modifier.padding(horizontal = Gutter, vertical = 14.dp)) {
                    Eyebrow("Hash")
                    Spacer(Modifier.height(6.dp))
                    Text(row.hash, style = Tb.type.mono)
                }
            }
            Spacer(Modifier.height(12.dp))
            val added = row.key in ui.added
            TbButton(
                text = when {
                    added -> "Open in library"
                    row.cached -> "Add — ready instantly"
                    else -> "Add to TorBox"
                },
                onClick = {
                    detail = null
                    if (added) onOpenAdded(row) else onAdd(row)
                },
                style = if (added) ButtonStyle.Secondary else ButtonStyle.Signal,
                icon = if (added) TbIcons.ChevronRight else TbIcons.Add,
                loading = row.key in ui.adding,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
            )
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onSearch: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        EmptyState(
            title = "Find anything",
            body = "Search TorBox's indexers by title. Results marked Instant are already cached and start immediately.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Recent", Modifier.weight(1f))
                TbLink("Clear", onClear, color = Tb.colors.inkMuted, modifier = Modifier.padding(end = Gutter))
            }
        }
        items(recent, key = { it }) { q ->
            ListRow(q, icon = TbIcons.History, iconTint = Tb.colors.inkMuted, onClick = { onSearch(q) })
        }
    }
}

@Composable
private fun MetadataCard(meta: SearchMetadataDto) {
    val c = Tb.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 16.dp)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape)
            .padding(14.dp),
    ) {
        if (!meta.image.isNullOrBlank()) {
            AsyncImage(
                model = meta.image,
                contentDescription = meta.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 74.dp, height = 110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.sunken),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(meta.title.orEmpty(), style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.66f, lineHeight = Tb.type.headline.lineHeight * 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val facts = listOfNotNull(
                meta.releaseYears ?: meta.releasedDate?.take(4),
                meta.type?.replaceFirstChar { it.uppercase() },
                meta.rating.takeIf { it > 0 }?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) },
                meta.runtime,
            )
            if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = Tb.type.monoSmall)
            if (meta.genres.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(meta.genres.take(4).joinToString(", "), style = Tb.type.bodySmall)
            }
            meta.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = Tb.type.bodySmall.copy(color = c.ink), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultRow(row: SearchTorrentDto, adding: Boolean, added: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(interaction, onClick = onClick)
            .padding(start = Gutter, end = Gutter - 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.rawTitle ?: row.title ?: "Untitled", style = Tb.type.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.cached) Tag("Instant", emphasis = true)
                if (row.owned) Tag("In library")
                Text(
                    listOfNotNull(
                        Format.bytes(row.size).takeIf { row.size > 0 },
                        row.lastKnownSeeders.takeIf { it >= 0 }?.let { "↑ $it" },
                        row.age,
                        row.tracker,
                    ).joinToString("  ·  "),
                    style = Tb.type.monoSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when {
                adding -> CircularProgressIndicator(Modifier.size(18.dp), color = c.ink, strokeWidth = 2.dp)
                added -> Icon(TbIcons.Check, "Added", tint = c.signalInk, modifier = Modifier.size(20.dp))
                else -> TbIconButton(TbIcons.Add, "Add to TorBox", onAdd, filled = true, size = 38.dp)
            }
        }
    }
}
