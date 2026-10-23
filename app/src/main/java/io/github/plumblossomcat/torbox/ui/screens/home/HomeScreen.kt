package io.github.plumblossomcat.torbox.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.UserDto
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.data.expiresAt
import io.github.plumblossomcat.torbox.data.planLabel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ActionTile
import io.github.plumblossomcat.torbox.ui.components.AddFab
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.Notice
import io.github.plumblossomcat.torbox.ui.components.ScreenHeader
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonBlock
import io.github.plumblossomcat.torbox.ui.components.SkeletonRows
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TransferRow
import io.github.plumblossomcat.torbox.ui.components.VerticalHairline
import io.github.plumblossomcat.torbox.ui.components.WhileVisible
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeState(
    val user: UserDto? = null,
    val userError: String? = null,
    val active: List<Transfer> = emptyList(),
    val recent: List<Transfer> = emptyList(),
    val total: Int = 0,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val libraryError: String? = null,
    val unread: Int = 0,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        container.account.user,
        container.account.error,
        container.library.state,
        container.account.unread,
    ) { user, userError, library, unread ->
        val all = library.values.flatMap { it.items }
        HomeState(
            user = user,
            userError = userError,
            active = all.filter { it.state.isActive }.sortedByDescending { it.progress },
            // Anything in progress is already listed above, so "recent" shows the rest.
            recent = all.filterNot { it.state.isActive }.sortedByDescending { it.createdAt }.take(6),
            total = all.size,
            loaded = library.values.all { it.loaded },
            refreshing = library.values.any { it.refreshing },
            libraryError = library.values.firstNotNullOfOrNull { it.error },
            unread = unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { container.account.refresh() }
        viewModelScope.launch { container.library.refreshAll() }
        viewModelScope.launch { container.account.refreshUnread() }
    }

    suspend fun poll() {
        val activeKinds = state.value.active.map { it.kind }.distinct()
        activeKinds.forEach { container.library.refresh(it) }
    }
}

@Composable
fun HomeScreen(
    onOpen: (Transfer) -> Unit,
    onAdd: (TransferKind?) -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onNotifications: () -> Unit,
) {
    val container = LocalContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    WhileVisible((settings?.refreshSeconds ?: 5) * 1000L, enabled = state.active.isNotEmpty()) { vm.poll() }
    HomeContent(state, vm::refresh, onOpen, onAdd, onSearch, onLibrary, onNotifications)
}

private fun greeting(now: LocalDateTime = LocalDateTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Up late"
}

@Composable
fun HomeContent(
    state: HomeState,
    onRefresh: () -> Unit,
    onOpen: (Transfer) -> Unit,
    onAdd: (TransferKind?) -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onNotifications: () -> Unit,
    now: LocalDateTime = LocalDateTime.now(),
) {
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing && state.loaded, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    ScreenHeader(
                        title = greeting(now),
                        eyebrow = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)),
                        actions = {
                            TbIconButton(TbIcons.Search, "Search", onSearch)
                            TbIconButton(TbIcons.Bell, "Notifications", onNotifications, badge = state.unread > 0)
                        },
                    )
                }
                item(key = "plan") {
                    Spacer(Modifier.height(12.dp))
                    PlanPanel(state)
                }
                item(key = "quick") {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Add to TorBox")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionTile("Magnet link", "Paste or type", TbIcons.Magnet, { onAdd(TransferKind.TORRENT) })
                        ActionTile("Usenet", "NZB link or file", TbIcons.Newspaper, { onAdd(TransferKind.USENET) })
                        ActionTile("Web link", "Hosters & direct", TbIcons.Link, { onAdd(TransferKind.WEB) })
                        ActionTile("Search", "Torrents & NZBs", TbIcons.Search, onSearch)
                    }
                }
                state.libraryError?.let { message ->
                    item(key = "error") {
                        Spacer(Modifier.height(20.dp))
                        Notice(message, action = "Retry", onAction = onRefresh)
                    }
                }
                if (!state.loaded) {
                    item(key = "skeleton") {
                        Spacer(Modifier.height(28.dp))
                        SkeletonRows(3)
                    }
                } else if (state.total == 0) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Nothing here yet",
                            body = "Add a magnet link, an NZB or a web link and it will show up here the moment TorBox starts on it.",
                            action = "Add your first download",
                            onAction = { onAdd(null) },
                        )
                    }
                } else {
                    item(key = "activeHeader") {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader(if (state.active.isEmpty()) "In progress" else "In progress · ${state.active.size}")
                    }
                    if (state.active.isEmpty()) {
                        item(key = "idle") {
                            Text(
                                "Nothing is downloading right now.",
                                style = Tb.type.body.copy(color = Tb.colors.inkMuted),
                                modifier = Modifier.padding(horizontal = Gutter, vertical = 10.dp),
                            )
                        }
                    } else {
                        items(state.active, key = { "a" + it.key }) { item ->
                            TransferRow(item, onClick = { onOpen(item) }, showKind = true)
                            Hairline(Modifier.padding(horizontal = Gutter))
                        }
                    }
                    item(key = "recentHeader") {
                        Spacer(Modifier.height(24.dp))
                        SectionHeader("Recently added", action = "Library", onAction = onLibrary)
                    }
                    items(state.recent, key = { "r" + it.key }) { item ->
                        TransferRow(item, onClick = { onOpen(item) }, showKind = true)
                        Hairline(Modifier.padding(horizontal = Gutter))
                    }
                }
                item(key = "end") { Spacer(Modifier.height(96.dp)) }
            }
        }
        AddFab(onClick = { onAdd(null) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PlanPanel(state: HomeState) {
    val c = Tb.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape),
    ) {
        val user = state.user
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Plan")
                Spacer(Modifier.height(4.dp))
                if (user == null) {
                    if (state.userError != null) {
                        Text("Unavailable", style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.8f, color = c.inkFaint))
                    } else {
                        SkeletonBlock(Modifier.width(96.dp).height(30.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.planLabel, style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.8f))
                        if (user.isSubscribed) {
                            Spacer(Modifier.width(10.dp))
                            Tag("Subscribed")
                        }
                    }
                }
            }
            if (user != null) {
                Column(horizontalAlignment = Alignment.End) {
                    val expires = user.expiresAt
                    val days = Format.daysUntil(expires)
                    Eyebrow(if (user.isSubscribed) "Renews" else "Expires")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            expires == null -> "—"
                            days == 0L -> "Today"
                            days == 1L -> "Tomorrow"
                            else -> "in $days days"
                        },
                        style = Tb.type.mono.copy(fontSize = Tb.type.body.fontSize, color = if (days != null && days <= 7) c.caution else c.ink),
                    )
                    Text(Format.date(expires), style = Tb.type.monoSmall)
                }
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Figure("Active", if (state.loaded) state.active.size.toString() else "–", Modifier.weight(1f))
            VerticalHairline()
            Figure("In library", if (state.loaded) state.total.toString() else "–", Modifier.weight(1f))
            VerticalHairline()
            Figure("Downloaded", user?.let { Format.bytes(it.totalDownloaded) } ?: "–", Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = Tb.type.figure, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
