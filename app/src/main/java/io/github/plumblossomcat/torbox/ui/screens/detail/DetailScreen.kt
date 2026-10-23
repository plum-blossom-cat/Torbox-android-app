package io.github.plumblossomcat.torbox.ui.screens.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.core.api.ControlOp
import io.github.plumblossomcat.torbox.core.api.IntegrationProvider
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.model.TransferState
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.data.FileAction
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ConfirmDialog
import io.github.plumblossomcat.torbox.ui.components.ErrorState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Meter
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.SheetAction
import io.github.plumblossomcat.torbox.ui.components.SheetHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonBlock
import io.github.plumblossomcat.torbox.ui.components.Stat
import io.github.plumblossomcat.torbox.ui.components.StatGrid
import io.github.plumblossomcat.torbox.ui.components.StateBadge
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbSheet
import io.github.plumblossomcat.torbox.ui.components.TopBar
import io.github.plumblossomcat.torbox.ui.components.VerticalHairline
import io.github.plumblossomcat.torbox.ui.components.WhileVisible
import io.github.plumblossomcat.torbox.ui.components.icon
import io.github.plumblossomcat.torbox.ui.components.isStreamable
import io.github.plumblossomcat.torbox.ui.components.kind
import io.github.plumblossomcat.torbox.ui.components.liveLine
import io.github.plumblossomcat.torbox.ui.components.pressable
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

@Composable
fun DetailScreen(kind: TransferKind, id: Long, onBack: () -> Unit, onCloud: () -> Unit) {
    val container = LocalContainer.current
    val vm: DetailViewModel = viewModel(key = "$kind:$id") { DetailViewModel(container, kind, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let { m ->
            toaster.show(m.text, m.action, if (m.kind == DetailViewModel.MessageAction.CLOUD) onCloud else null)
            vm.messages.value = null
        }
    }
    LaunchedEffect(state.ui.deleted) {
        if (state.ui.deleted) {
            toaster.show("Deleted “${state.item?.name ?: "download"}”")
            onBack()
        }
    }
    val item = state.item
    val live = item?.state?.isActive == true || item?.state == TransferState.SEEDING
    WhileVisible(state.settings.refreshSeconds * 1000L, enabled = live) { vm.poll() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-bittorrent")) { uri ->
        if (uri != null) vm.exportTorrent(uri, context.contentResolver)
    }

    DetailContent(
        state = state,
        kind = kind,
        onBack = onBack,
        onRetry = vm::refresh,
        onToggleFolder = vm::toggleFolder,
        onLink = vm::link,
        onControl = vm::control,
        onDelete = vm::delete,
        onCopy = vm::copy,
        onCopyMagnet = vm::copyMagnet,
        onExportTorrent = { exportLauncher.launch("${item?.name ?: "torrent"}.torrent") },
        onCloud = vm::sendToCloud,
        onShareOriginal = { url -> container.device.share(url) },
    )
}

@Composable
fun DetailContent(
    state: DetailState,
    kind: TransferKind,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onLink: (TransferFile?, LinkUse) -> Unit,
    onControl: (ControlOp, String) -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit,
    onCopyMagnet: () -> Unit,
    onExportTorrent: () -> Unit,
    onCloud: (IntegrationProvider, TransferFile?) -> Unit,
    onShareOriginal: (String) -> Unit,
) {
    val item = state.item
    var menuOpen by remember { mutableStateOf(false) }
    var sheetFile by remember { mutableStateOf<TransferFile?>(null) }
    var zipSheet by remember { mutableStateOf(false) }
    var cloudTarget by remember { mutableStateOf<CloudTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun tapFile(file: TransferFile) {
        when (state.settings.fileAction) {
            FileAction.ASK -> sheetFile = file
            FileAction.SAVE -> onLink(file, LinkUse.SAVE)
            FileAction.OPEN -> onLink(file, LinkUse.OPEN)
            FileAction.COPY -> onLink(file, LinkUse.COPY)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = kind.label,
            onBack = onBack,
            actions = {
                if (item != null) {
                    Box {
                        TbIconButton(TbIcons.More, "More actions", { menuOpen = true })
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = Tb.colors.raised,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            val close: (() -> Unit) -> () -> Unit = { action -> { menuOpen = false; action() } }
                            MenuEntry("Refresh", TbIcons.Refresh, close(onRetry))
                            if (item.hash.isNotBlank()) MenuEntry("Copy hash", TbIcons.Hash, close { onCopy(item.hash, "Hash") })
                            if (kind == TransferKind.TORRENT) {
                                MenuEntry("Copy magnet link", TbIcons.Magnet, close(onCopyMagnet))
                                MenuEntry("Save .torrent file", TbIcons.Download, close(onExportTorrent))
                            }
                            item.originalUrl?.takeIf { it.startsWith("http") }?.let { url ->
                                MenuEntry("Copy original link", TbIcons.Link, close { onCopy(url, "Original link") })
                                MenuEntry("Share original link", TbIcons.Share, close { onShareOriginal(url) })
                            }
                        }
                    }
                }
            },
        )
        when {
            item == null && state.ui.loading -> DetailSkeleton()
            item == null -> ErrorState(state.ui.error ?: "This download isn't in your library anymore.", onRetry)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item(key = "head") { Header(item) }
                item(key = "actions") {
                    ActionStrip(
                        item = item,
                        busy = state.ui.busy,
                        preparingZip = state.ui.preparing == "zip",
                        onDownload = {
                            val single = item.files.singleOrNull()
                            if (single != null) tapFile(single) else zipSheet = true
                        },
                        onControl = onControl,
                        onCloud = { cloudTarget = CloudTarget(null) },
                        onDelete = { confirmDelete = true },
                    )
                }
                item(key = "stats") {
                    Spacer(Modifier.height(8.dp))
                    StatGrid(stats(item), Modifier.padding(horizontal = Gutter))
                    if (item.hash.isNotBlank()) {
                        Hairline(Modifier.padding(horizontal = Gutter))
                        HashRow(item.hash) { onCopy(item.hash, "Hash") }
                    }
                    Hairline(Modifier.padding(horizontal = Gutter))
                }
                if (state.rows.isNotEmpty()) {
                    item(key = "filesHeader") {
                        Spacer(Modifier.height(24.dp))
                        SectionHeader("Files · ${item.files.size}")
                        Spacer(Modifier.height(4.dp))
                    }
                    items(state.rows, key = { it.key }) { row ->
                        when (row) {
                            is TreeRow.Folder -> FolderRow(row) { onToggleFolder(row.key) }
                            is TreeRow.Leaf -> FileRow(
                                row = row,
                                ready = item.downloadReady || item.state.isDone,
                                preparing = state.ui.preparing == row.key,
                                onClick = { tapFile(row.file) },
                                onLongClick = { sheetFile = row.file },
                            )
                        }
                    }
                } else if (!item.state.isDone) {
                    item(key = "noFiles") {
                        Text(
                            "Files appear here once TorBox has the metadata.",
                            style = Tb.type.body.copy(color = Tb.colors.inkMuted),
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 24.dp),
                        )
                    }
                }
                item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
            }
        }
    }

    sheetFile?.let { file ->
        TbSheet(onDismiss = { sheetFile = null }) {
            SheetHeader(file.name, "${Format.bytes(file.size)}  ·  ${file.path}")
            val dismissThen: (() -> Unit) -> () -> Unit = { action -> { sheetFile = null; action() } }
            SheetAction("Save to device", TbIcons.Download, dismissThen { onLink(file, LinkUse.SAVE) }, "Downloads/TorBox")
            SheetAction(
                if (file.kind().isStreamable) "Stream in another app" else "Open in another app",
                if (file.kind().isStreamable) TbIcons.Stream else TbIcons.External,
                dismissThen { onLink(file, LinkUse.OPEN) },
                if (file.kind().isStreamable) "VLC, MX Player, mpv…" else null,
            )
            SheetAction("Copy download link", TbIcons.Copy, dismissThen { onLink(file, LinkUse.COPY) }, "Works for a few hours")
            SheetAction("Share link", TbIcons.Share, dismissThen { onLink(file, LinkUse.SHARE) })
            SheetAction("Send to cloud storage", TbIcons.Cloud, dismissThen { cloudTarget = CloudTarget(file) })
        }
    }

    if (zipSheet && item != null) {
        TbSheet(onDismiss = { zipSheet = false }) {
            SheetHeader("Everything as one zip", "${item.files.size} files  ·  ${Format.bytes(item.size)}")
            val dismissThen: (() -> Unit) -> () -> Unit = { action -> { zipSheet = false; action() } }
            SheetAction("Save zip to device", TbIcons.Download, dismissThen { onLink(null, LinkUse.SAVE) }, "Downloads/TorBox")
            SheetAction("Copy zip link", TbIcons.Copy, dismissThen { onLink(null, LinkUse.COPY) }, "Works for a few hours")
            SheetAction("Share zip link", TbIcons.Share, dismissThen { onLink(null, LinkUse.SHARE) })
            SheetAction("Send zip to cloud storage", TbIcons.Cloud, dismissThen { cloudTarget = CloudTarget(null) })
        }
    }

    cloudTarget?.let { target ->
        TbSheet(onDismiss = { cloudTarget = null }) {
            SheetHeader("Send to cloud", target.file?.name ?: "The whole download, zipped")
            IntegrationProvider.entries.forEach { provider ->
                val connected = state.cloudTokens.containsKey(provider)
                SheetAction(
                    label = provider.label,
                    icon = TbIcons.Cloud,
                    detail = when {
                        connected -> "Connected"
                        provider.tokenRequired -> "Not connected — set up in Cloud uploads"
                        else -> "Uploads anonymously unless you add a key"
                    },
                    onClick = {
                        cloudTarget = null
                        onCloud(provider, target.file)
                    },
                )
            }
        }
    }

    if (confirmDelete && item != null) {
        ConfirmDialog(
            title = "Delete this download?",
            body = "“${item.name}” and its files will be removed from your TorBox account. This can't be undone.",
            confirmLabel = "Delete",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private data class CloudTarget(val file: TransferFile?)

@Composable
private fun MenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Tb.type.body) },
        leadingIcon = { Icon(icon, null, tint = Tb.colors.ink, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun Header(item: Transfer) {
    val c = Tb.colors
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 20.dp)) {
        Eyebrow(listOfNotNull(item.kind.label, "#${item.id}", item.createdAt?.let { "Added " + Format.relative(it) }).joinToString("  ·  "))
        Spacer(Modifier.height(10.dp))
        Text(
            item.name,
            style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.8f, lineHeight = Tb.type.headline.lineHeight * 0.82f),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StateBadge(item.state)
            if (item.cached) Tag("Cached")
            if (item.rawState.isNotBlank() && !item.rawState.equals(item.state.label, ignoreCase = true)) {
                Text(item.rawState, style = Tb.type.monoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val showMeter = item.state.isActive || (item.state == TransferState.PAUSED && item.progress < 1.0)
        if (showMeter) {
            Spacer(Modifier.height(16.dp))
            Meter(
                progress = if (item.state == TransferState.PREPARING && item.progress <= 0.0) null else item.progress.toFloat(),
                color = c.meterFor(item.state),
                height = 4.dp,
            )
        }
        item.liveLine()?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Tb.type.mono.copy(color = if (item.state == TransferState.FAILED) c.danger else c.inkMuted))
        }
    }
}

@Composable
private fun ActionStrip(
    item: Transfer,
    busy: Boolean,
    preparingZip: Boolean,
    onDownload: () -> Unit,
    onControl: (ControlOp, String) -> Unit,
    onCloud: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Tb.colors
    val ready = item.downloadReady || item.state.isDone
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape),
    ) {
        StripButton("Download", TbIcons.Download, onDownload, Modifier.weight(1f), enabled = ready && item.files.isNotEmpty(), loading = preparingZip, emphasis = true)
        VerticalHairline()
        when (item.kind) {
            TransferKind.TORRENT -> {
                if (item.state == TransferState.PAUSED) {
                    StripButton("Resume", TbIcons.Play, { onControl(ControlOp.Torrent.RESUME, "Resumed") }, Modifier.weight(1f), enabled = !busy)
                } else {
                    StripButton(
                        "Pause", TbIcons.Pause, { onControl(ControlOp.Torrent.PAUSE, "Paused") }, Modifier.weight(1f),
                        enabled = !busy && (item.state.isActive || item.state == TransferState.SEEDING),
                    )
                }
                VerticalHairline()
                StripButton(
                    "Reannounce", TbIcons.Repeat, { onControl(ControlOp.Torrent.REANNOUNCE, "Asked trackers for more peers") }, Modifier.weight(1f),
                    enabled = !busy && (item.state.isActive || item.state == TransferState.SEEDING),
                )
            }
            TransferKind.USENET -> {
                if (item.state == TransferState.PAUSED) {
                    StripButton("Resume", TbIcons.Play, { onControl(ControlOp.Usenet.RESUME, "Resumed") }, Modifier.weight(1f), enabled = !busy)
                } else {
                    StripButton("Pause", TbIcons.Pause, { onControl(ControlOp.Usenet.PAUSE, "Paused") }, Modifier.weight(1f), enabled = !busy && item.state.isActive)
                }
            }
            TransferKind.WEB -> Unit
        }
        if (item.kind != TransferKind.WEB) VerticalHairline()
        StripButton("Cloud", TbIcons.Cloud, onCloud, Modifier.weight(1f), enabled = ready && item.files.isNotEmpty())
        VerticalHairline()
        StripButton("Delete", TbIcons.Delete, onDelete, Modifier.weight(1f), enabled = !busy, danger = true)
    }
}

@Composable
private fun StripButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    emphasis: Boolean = false,
    danger: Boolean = false,
) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> c.inkFaint.copy(alpha = 0.6f)
        danger -> c.danger
        else -> c.ink
    }
    Column(
        modifier
            .pressable(interaction, enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (emphasis && enabled) c.signal else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = if (emphasis) c.onSignal else tint, strokeWidth = 2.dp)
            } else {
                Icon(icon, null, tint = if (emphasis && enabled) c.onSignal else tint, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = Tb.type.label.copy(fontSize = Tb.type.eyebrow.fontSize * 1.05f), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun stats(item: Transfer): List<Stat> = buildList {
    add(Stat("Size", if (item.size > 0) Format.bytes(item.size) else "—"))
    add(Stat("Files", if (item.files.isEmpty()) "—" else Format.count(item.files.size.toLong())))
    if (item.state.isActive) {
        add(Stat("Speed", Format.speed(item.downloadSpeed)))
        add(Stat("Time left", Format.eta(item.etaSeconds)))
    }
    if (item.kind == TransferKind.TORRENT) {
        add(Stat("Seeds", item.seeds.toString()))
        add(Stat("Peers", item.peers.toString()))
        add(Stat("Ratio", Format.ratio(item.ratio)))
        add(Stat("Uploaded", if (item.totalUploaded > 0) Format.bytes(item.totalUploaded) else "—"))
        if (item.state == TransferState.SEEDING) add(Stat("Upload speed", Format.speed(item.uploadSpeed)))
        if (item.availability > 0) add(Stat("Availability", String.format(java.util.Locale.US, "%.2f", item.availability)))
    }
    add(Stat("Added", Format.dateTime(item.createdAt)))
    add(Stat("Updated", Format.relative(item.updatedAt)))
    item.expiresAt?.let { add(Stat("Expires", Format.date(it))) }
    item.tracker?.takeIf { it.isNotBlank() }?.let { add(Stat("Tracker", it.substringAfter("://").substringBefore('/'))) }
}

@Composable
private fun HashRow(hash: String, onCopy: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(interaction, onClick = onCopy)
            .padding(horizontal = Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow("Hash")
            Spacer(Modifier.height(6.dp))
            Text(hash, style = Tb.type.mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Icon(TbIcons.Copy, "Copy hash", tint = Tb.colors.inkFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun FolderRow(row: TreeRow.Folder, onToggle: () -> Unit) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(interaction, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(start = Gutter + (row.depth * 18).dp, end = Gutter, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (row.expanded) TbIcons.ChevronDown else TbIcons.ChevronRight, null, tint = c.inkFaint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Icon(if (row.expanded) TbIcons.FolderOpen else TbIcons.Folder, null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(row.name, style = Tb.type.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("${row.fileCount}  ·  ${Format.bytes(row.size)}", style = Tb.type.monoSmall, maxLines = 1)
    }
}

@Composable
private fun FileRow(row: TreeRow.Leaf, ready: Boolean, preparing: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Tb.colors
    val file = row.file
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = ready,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "More options",
            )
            .heightIn(min = 52.dp)
            .padding(start = Gutter + (row.depth * 18).dp + if (row.depth > 0) 22.dp else 0.dp, end = Gutter - 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(file.kind().icon(), null, tint = if (file.infected) c.danger else c.inkMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = Tb.type.body.copy(color = if (ready) c.ink else c.inkMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(Format.bytes(file.size))
                    if (file.extension.isNotBlank()) append("  ·  ").append(file.extension.uppercase())
                    if (file.infected) append("  ·  flagged as infected")
                },
                style = Tb.type.monoSmall.copy(color = if (file.infected) c.danger else c.inkMuted),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (preparing) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.ink, strokeWidth = 2.dp)
            } else {
                Icon(TbIcons.Download, if (ready) "Download ${file.name}" else null, tint = if (ready) c.ink else c.inkFaint.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
        SkeletonBlock(Modifier.width(160.dp).height(11.dp))
        Spacer(Modifier.height(14.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(28.dp))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(28.dp))
        Spacer(Modifier.height(28.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(84.dp))
        Spacer(Modifier.height(24.dp))
        repeat(4) {
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}
