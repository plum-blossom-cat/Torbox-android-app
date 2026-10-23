package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.model.TransferState
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

/** TorBox passes through raw client states; these read better as words. */
fun humanState(raw: String): String? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    return when (s.lowercase()) {
        "metadl", "metadata" -> "Fetching metadata"
        "checkingresumedata", "checkingdl", "checkingup" -> "Checking data"
        "moving" -> "Moving files"
        "allocating" -> "Allocating space"
        "queueddl" -> "Queued"
        "processing" -> "Processing"
        else -> s.replaceFirstChar { it.uppercase() }
    }
}

/** "1.24 GB · 12 files · 2d ago" */
fun Transfer.metaLine(): String {
    val bytes = size
    val fileCount = files.size
    return buildList {
        add(if (bytes > 0) Format.bytes(bytes) else "Size unknown")
        if (fileCount > 0) add(if (fileCount == 1) "1 file" else "$fileCount files")
        add(Format.relative(createdAt))
    }.joinToString("  ·  ")
}

/** "42%  ·  ↓ 12.3 MB/s  ·  4m 05s left" */
fun Transfer.liveLine(): String? = when (state) {
    TransferState.DOWNLOADING -> buildList {
        add(Format.percent(progress))
        if (downloadSpeed > 0) add("↓ ${Format.speed(downloadSpeed)}")
        if (etaSeconds > 0 && Format.eta(etaSeconds) != "—") add("${Format.eta(etaSeconds)} left")
    }.joinToString("  ·  ")
    TransferState.PREPARING -> (humanState(rawState) ?: "Preparing").let { if (progress > 0) "${Format.percent(progress)}  ·  $it" else it }
    TransferState.STALLED -> "${Format.percent(progress)}  ·  waiting for peers" + if (peers > 0) " ($peers connected)" else ""
    TransferState.SEEDING -> buildList {
        if (uploadSpeed > 0) add("↑ ${Format.speed(uploadSpeed)}")
        add("ratio ${Format.ratio(ratio)}")
        if (seeds > 0 || peers > 0) add("$peers peers")
    }.joinToString("  ·  ")
    TransferState.PAUSED -> if (progress in 0.0..0.999) "${Format.percent(progress)}  ·  paused" else null
    TransferState.QUEUED -> "Waiting for a free slot"
    TransferState.FAILED -> errorMessage ?: rawState.takeIf { it.isNotBlank() && !it.equals("failed", true) }?.let(::humanState)
    TransferState.READY -> null
}

private val Transfer.showsMeter: Boolean
    get() = state == TransferState.DOWNLOADING || state == TransferState.STALLED ||
        state == TransferState.PREPARING || (state == TransferState.PAUSED && progress < 1.0)

@Composable
fun TransferRow(
    item: Transfer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    showKind: Boolean = false,
) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) c.signal.copy(alpha = if (c.isDark) 0.08f else 0.18f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Select",
            )
            .padding(horizontal = Gutter, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AnimatedVisibility(selecting, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Row {
                Icon(
                    if (selected) TbIcons.SquareCheck else TbIcons.Square,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) c.ink else c.inkFaint,
                    modifier = Modifier.padding(top = 1.dp).size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.name,
                    style = Tb.type.subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                StateBadge(item.state, Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showKind) Tag(item.kind.label)
                if (item.cached && item.kind == TransferKind.TORRENT && item.state == TransferState.READY) Tag("Cached")
                Text(item.metaLine(), style = Tb.type.monoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.showsMeter) {
                Spacer(Modifier.height(12.dp))
                Meter(
                    progress = if (item.state == TransferState.PREPARING && item.progress <= 0.0) null else item.progress.toFloat(),
                    color = c.meterFor(item.state),
                )
            }
            item.liveLine()?.let { line ->
                Spacer(Modifier.height(8.dp))
                Text(
                    line,
                    style = Tb.type.monoSmall.copy(color = if (item.state == TransferState.FAILED) c.danger else c.inkMuted),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Big tappable tile used for quick actions on Home. */
@Composable
fun ActionTile(label: String, detail: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .width(148.dp)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape)
            .pressable(interaction, onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (c.isDark) c.hairline else c.sunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.ink, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(label, style = Tb.type.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = Tb.type.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
