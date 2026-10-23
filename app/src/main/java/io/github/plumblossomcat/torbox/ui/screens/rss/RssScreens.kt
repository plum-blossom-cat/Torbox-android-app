package io.github.plumblossomcat.torbox.ui.screens.rss

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.RssFeedDto
import io.github.plumblossomcat.torbox.core.api.RssFeedInput
import io.github.plumblossomcat.torbox.core.api.RssItemDto
import io.github.plumblossomcat.torbox.core.api.SeedPreference
import io.github.plumblossomcat.torbox.core.api.TorBoxException
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Timestamps
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.AddFab
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.ChipRow
import io.github.plumblossomcat.torbox.ui.components.ConfirmDialog
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.Labeled
import io.github.plumblossomcat.torbox.ui.components.LoadScaffold
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Segmented
import io.github.plumblossomcat.torbox.ui.components.SheetAction
import io.github.plumblossomcat.torbox.ui.components.SheetHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonRows
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbSheet
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.components.ToggleRow
import io.github.plumblossomcat.torbox.ui.components.TopBar
import io.github.plumblossomcat.torbox.ui.components.pressable
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ list */

class RssViewModel(container: AppContainer) : LoadViewModel<List<RssFeedDto>>(container, { client.rssFeeds() }) {
    fun control(feed: RssFeedDto, op: String) {
        if (op == "delete") update { list -> list.filterNot { it.id == feed.id } }
        val done = when (op) {
            "update" -> "Checking “${feed.name}” now"
            "pause" -> "Paused “${feed.name}”"
            "resume" -> "Resumed “${feed.name}”"
            else -> "Deleted “${feed.name}”"
        }
        mutate(done) { client.controlRss(feed.id, op) }
    }
}

private fun RssFeedDto.kind(): TransferKind = TransferKind.fromApiType(rssType.ifBlank { type }) ?: TransferKind.TORRENT

@Composable
fun RssScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onItems: (RssFeedDto) -> Unit, onPlans: () -> Unit) {
    val container = LocalContainer.current
    val vm: RssViewModel = viewModel { RssViewModel(container) }
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
    RssContent(load, refreshing, onBack, vm::refresh, onEdit, onItems, vm::control, onPlans)
}

@Composable
fun RssContent(
    load: Load<List<RssFeedDto>>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: (Long) -> Unit,
    onItems: (RssFeedDto) -> Unit,
    onControl: (RssFeedDto, String) -> Unit,
    onPlans: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<RssFeedDto?>(null) }
    var confirmDelete by remember { mutableStateOf<RssFeedDto?>(null) }
    val planLocked = (load as? Load.Failed)?.message?.contains("plan", ignoreCase = true) == true

    if (planLocked) {
        Column(Modifier.fillMaxSize()) {
            TopBar("RSS feeds", onBack)
            EmptyState(
                "RSS needs TorBox Pro",
                "Feeds watch your favourite sources and add new releases automatically. They're part of the Pro plan.",
                action = "See plans",
                onAction = onPlans,
            )
        }
        return
    }

    LoadScaffold(
        title = "RSS feeds",
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { it.isEmpty() },
        empty = {
            EmptyState(
                "No feeds yet",
                "Add an RSS feed and TorBox will check it on a schedule, grabbing anything that matches your filters.",
                action = "Add a feed",
                onAction = { onEdit(-1) },
            )
        },
        overlay = {
            if (load is Load.Ready) AddFab(onClick = { onEdit(-1) }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding(), label = "New feed")
        },
    ) { feeds ->
        items(feeds, key = { it.id }) { feed ->
            FeedRow(feed, onClick = { onItems(feed) }, onLongClick = { menuFor = feed })
        }
        item(key = "end") { Spacer(Modifier.height(96.dp)) }
    }

    menuFor?.let { feed ->
        TbSheet({ menuFor = null }) {
            SheetHeader(feed.name, feed.url)
            val close: (() -> Unit) -> () -> Unit = { action -> { menuFor = null; action() } }
            SheetAction("View scraped items", TbIcons.Inbox, close { onItems(feed) })
            SheetAction("Check now", TbIcons.Refresh, close { onControl(feed, "update") })
            if (feed.status.equals("paused", true)) {
                SheetAction("Resume", TbIcons.Play, close { onControl(feed, "resume") })
            } else {
                SheetAction("Pause", TbIcons.Pause, close { onControl(feed, "pause") })
            }
            SheetAction("Edit", TbIcons.Pencil, close { onEdit(feed.id) })
            SheetAction("Delete", TbIcons.Delete, close { confirmDelete = feed }, danger = true)
        }
    }
    confirmDelete?.let { feed ->
        ConfirmDialog(
            title = "Delete “${feed.name}”?",
            body = "The feed and its scraped items are removed. Downloads it already added stay in your library.",
            confirmLabel = "Delete",
            danger = true,
            onConfirm = {
                confirmDelete = null
                onControl(feed, "delete")
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun FeedRow(feed: RssFeedDto, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Tb.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val tone = when (feed.status.lowercase()) {
        "active" -> c.signalInk
        "error" -> c.danger
        else -> c.inkFaint
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(interaction, onClick = onClick)
            .padding(start = Gutter, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(feed.name.ifBlank { "Untitled feed" }, style = Tb.type.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Tag(feed.status.ifBlank { "unknown" }, color = tone)
            }
            Spacer(Modifier.height(4.dp))
            Text(feed.url, style = Tb.type.monoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    feed.kind().label,
                    "every ${feed.scanInterval} min",
                    feed.state?.takeIf { it.isNotBlank() && it != "idle" },
                    (feed.lastChecked ?: feed.updatedAt)?.let { "checked " + Format.relative(Timestamps.parse(it)) },
                ).joinToString("  ·  "),
                style = Tb.type.monoSmall,
            )
            feed.statusMessage?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = Tb.type.bodySmall.copy(color = if (feed.status.equals("error", true)) c.danger else c.inkMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        TbIconButton(TbIcons.More, "Feed options", onLongClick, tint = c.inkMuted)
    }
    Hairline(Modifier.padding(horizontal = Gutter))
}

/* ------------------------------------------------------------------ edit */

data class RssForm(
    val loading: Boolean = false,
    val name: String = "",
    val url: String = "",
    val doRegex: String = "",
    val dontRegex: String = "",
    val olderThanDays: String = "0",
    val intervalMinutes: String = "60",
    val kind: TransferKind = TransferKind.TORRENT,
    val seed: SeedPreference = SeedPreference.AUTO,
    val passCheck: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class RssEditViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    private val _form = MutableStateFlow(RssForm(loading = id > 0))
    val form = _form.asStateFlow()
    val isNew get() = id <= 0

    init {
        if (id > 0) viewModelScope.launch {
            try {
                val feed = container.client.rssFeeds().first { it.id == id }
                _form.value = RssForm(
                    name = feed.name,
                    url = feed.url,
                    doRegex = feed.doRegex.orEmpty(),
                    dontRegex = feed.dontRegex.orEmpty(),
                    olderThanDays = feed.dontOlderThan.toString(),
                    intervalMinutes = feed.scanInterval.toString(),
                    kind = feed.kind(),
                    seed = SeedPreference.from(feed.torrentSeeding),
                    passCheck = feed.passCheck,
                )
            } catch (e: Exception) {
                _form.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }

    fun edit(transform: RssForm.() -> RssForm) = _form.update { it.transform().copy(error = null) }

    fun save() {
        val f = _form.value
        val problem = when {
            f.name.isBlank() -> "Give the feed a name."
            isNew && !(f.url.startsWith("http://") || f.url.startsWith("https://")) -> "Enter the feed's full address, starting with https://"
            (f.intervalMinutes.toIntOrNull() ?: 0) < 10 -> "Check at most every 10 minutes."
            f.olderThanDays.toIntOrNull() == null -> "Use a whole number of days (0 for no limit)."
            listOf(f.doRegex, f.dontRegex).any { it.isNotBlank() && runCatching { Regex(it) }.isFailure } -> "One of the filters isn't a valid regular expression."
            else -> null
        }
        if (problem != null) {
            _form.update { it.copy(error = problem) }
            return
        }
        _form.update { it.copy(saving = true) }
        val input = RssFeedInput(
            name = f.name,
            url = f.url,
            doRegex = f.doRegex,
            dontRegex = f.dontRegex,
            dontOlderThan = f.olderThanDays.toInt(),
            passCheck = f.passCheck,
            scanIntervalMinutes = f.intervalMinutes.toInt(),
            type = f.kind,
            seeding = f.seed,
        )
        viewModelScope.launch {
            try {
                if (isNew) container.client.addRss(input) else container.client.modifyRss(id, input)
                _form.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                container.session.inspect(e)
                val message = if (e is TorBoxException.Api && e.code == "PLAN_RESTRICTED_FEATURE") "RSS feeds need a Pro plan." else e.friendly()
                _form.update { it.copy(saving = false, error = message) }
            }
        }
    }
}

@Composable
fun RssEditScreen(id: Long, onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: RssEditViewModel = viewModel(key = "rss-$id") { RssEditViewModel(container, id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    LaunchedEffect(form.saved) {
        if (form.saved) {
            toaster.show(if (vm.isNew) "Feed added" else "Feed updated")
            onBack()
        }
    }
    RssEditContent(form, vm.isNew, onBack, vm::edit, vm::save)
}

@Composable
fun RssEditContent(form: RssForm, isNew: Boolean, onBack: () -> Unit, onEdit: (RssForm.() -> RssForm) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (isNew) "New feed" else "Edit feed", onBack)
        if (form.loading) {
            SkeletonRows(4)
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TbTextField(form.name, { v -> onEdit { copy(name = v) } }, label = "Name", placeholder = "e.g. Weekly releases")
            TbTextField(
                form.url, { v -> onEdit { copy(url = v.trim()) } },
                label = "Feed address", placeholder = "https://", mono = true, enabled = isNew,
                supporting = if (isNew) null else "A feed's address can't be changed. Create a new feed instead.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Labeled("Adds items as") {
                Segmented(TransferKind.entries.map { it.pluralLabel }, TransferKind.entries.indexOf(form.kind), { i -> onEdit { copy(kind = TransferKind.entries[i]) } }, Modifier.fillMaxWidth())
            }
            if (form.kind == TransferKind.TORRENT) {
                Labeled("Seeding") {
                    Segmented(listOf("Default", "Always", "Never"), SeedPreference.entries.indexOf(form.seed), { i -> onEdit { copy(seed = SeedPreference.entries[i]) } }, Modifier.fillMaxWidth())
                }
            }
            TbTextField(form.doRegex, { v -> onEdit { copy(doRegex = v) } }, label = "Only grab titles matching", placeholder = "Regular expression, e.g. 1080p", mono = true, supporting = "Leave empty to accept everything.")
            TbTextField(form.dontRegex, { v -> onEdit { copy(dontRegex = v) } }, label = "Skip titles matching", placeholder = "e.g. CAM|TS", mono = true)
            Row {
                TbTextField(
                    form.intervalMinutes, { v -> onEdit { copy(intervalMinutes = v.filter(Char::isDigit)) } },
                    label = "Check every (min)", mono = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(12.dp))
                TbTextField(
                    form.olderThanDays, { v -> onEdit { copy(olderThanDays = v.filter(Char::isDigit)) } },
                    label = "Ignore older (days)", mono = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Column {
                Hairline()
                ToggleRow("Skip duplicate check", form.passCheck, { v -> onEdit { copy(passCheck = v) } }, subtitle = "Add matching items even if something similar was grabbed before.")
                Hairline()
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.navigationBarsPadding()) {
            Hairline()
            form.error?.let {
                Text(it, style = Tb.type.bodySmall.copy(color = Tb.colors.danger), modifier = Modifier.padding(start = Gutter, end = Gutter, top = 12.dp))
            }
            TbButton(
                if (isNew) "Add feed" else "Save changes", onSave,
                loading = form.saving, style = ButtonStyle.Signal,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
            )
        }
    }
}

/* ----------------------------------------------------------------- items */

class RssItemsViewModel(container: AppContainer, feedId: Long) :
    LoadViewModel<List<RssItemDto>>(container, { client.rssItems(feedId).sortedByDescending { Timestamps.parse(it.createdAt) } })

@Composable
fun RssItemsScreen(id: Long, name: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: RssItemsViewModel = viewModel(key = "rssItems-$id") { RssItemsViewModel(container, id) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    RssItemsContent(name, load, refreshing, onBack, vm::refresh) { link ->
        container.device.copy(link, "Link")
        if (!container.device.systemConfirmsCopy) toaster.show("Link copied")
    }
}

@Composable
fun RssItemsContent(name: String, load: Load<List<RssItemDto>>, refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onCopy: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val statuses = listOf("All", "Downloaded", "New", "Ignored", "Error")
    LoadScaffold(
        title = name,
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { it.isEmpty() },
        empty = { EmptyState("Nothing scraped yet", "Items show up after the feed's next check.") },
    ) { all ->
        item(key = "filter") {
            ChipRow(
                statuses, filter, { filter = it },
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Gutter, vertical = 8.dp),
            )
        }
        val shown = if (filter == 0) all else all.filter { it.status.equals(statuses[filter], ignoreCase = true) }
        if (shown.isEmpty()) item(key = "none") { EmptyState("None here", "No items are ${statuses[filter].lowercase()}.") }
        items(shown, key = { it.id }) { item ->
            val c = Tb.colors
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .pressable(interaction, enabled = !item.link.isNullOrBlank()) { item.link?.let(onCopy) }
                    .padding(horizontal = Gutter, vertical = 12.dp),
            ) {
                Text(item.title.ifBlank { "Untitled" }, style = Tb.type.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(item.status.ifBlank { "unknown" }, color = when (item.status.lowercase()) {
                        "downloaded" -> c.signalInk
                        "error" -> c.danger
                        else -> null
                    })
                    Spacer(Modifier.width(8.dp))
                    Text(Format.relative(Timestamps.parse(item.createdAt)), style = Tb.type.monoSmall)
                }
                item.statusMessage?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = Tb.type.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Hairline(Modifier.padding(horizontal = Gutter))
        }
    }
}
