package io.github.plumblossomcat.torbox.ui.screens.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.ListRow
import io.github.plumblossomcat.torbox.ui.components.LoadScaffold
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.Stat
import io.github.plumblossomcat.torbox.ui.components.StatGrid
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

data class ServiceStatus(val up: Boolean, val stats: List<Stat>, val checkedAt: Instant)

class StatusViewModel(container: AppContainer) : LoadViewModel<ServiceStatus>(container, {
    val up = runCatching { client.isUp() }.getOrDefault(false)
    val stats = runCatching { client.stats() }.getOrNull()?.let(::readStats).orEmpty()
    ServiceStatus(up, stats, Instant.now())
})

/** Turns TorBox's stats object into labelled figures without hard-coding its keys. */
fun readStats(json: JsonObject): List<Stat> = json.entries.mapNotNull { (key, value) ->
    val p = value as? JsonPrimitive ?: return@mapNotNull null
    val number = p.longOrNull ?: p.doubleOrNull?.toLong() ?: return@mapNotNull null
    val label = key.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        .replace("Webdl", "Web")
    val text = when {
        key.contains("bytes") || key.contains("size") || key.contains("downloaded") && number > 1_000_000 -> Format.bytes(number)
        else -> Format.compact(number)
    }
    Stat(label, text)
}

@Composable
fun StatusScreen(onBack: () -> Unit, onChangelog: () -> Unit) {
    val container = LocalContainer.current
    val vm: StatusViewModel = viewModel { StatusViewModel(container) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    StatusContent(load, refreshing, onBack, vm::refresh, onChangelog)
}

@Composable
fun StatusContent(load: Load<ServiceStatus>, refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onChangelog: () -> Unit) {
    LoadScaffold(
        title = "Service status",
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { false },
        empty = {},
    ) { status ->
        item(key = "status") { StatusCard(status) }
        item(key = "statsHeader") {
            Spacer(Modifier.height(28.dp))
            SectionHeader("TorBox in numbers")
        }
        if (status.stats.isEmpty()) {
            item(key = "noStats") { EmptyState("No figures right now", "TorBox didn't share its network statistics. Pull to try again.") }
        } else {
            item(key = "stats") {
                StatGrid(status.stats, Modifier.padding(horizontal = Gutter))
                Hairline(Modifier.padding(horizontal = Gutter))
            }
        }
        item(key = "links") {
            Spacer(Modifier.height(20.dp))
            ListRow("What's new in TorBox", subtitle = "The official changelog", icon = TbIcons.Newspaper, onClick = onChangelog)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusCard(status: ServiceStatus) {
    val c = Tb.colors
    val shape = RoundedCornerShape(18.dp)
    val tone = if (status.up) c.signalInk else c.danger
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 8.dp)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tone))
            Spacer(Modifier.width(10.dp))
            Eyebrow(if (status.up) "Operational" else "Unreachable", color = tone)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (status.up) "TorBox is up and answering." else "TorBox isn't answering right now.",
            style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.72f, lineHeight = Tb.type.headline.lineHeight * 0.76f),
        )
        Spacer(Modifier.height(8.dp))
        Text("Checked ${Format.relative(status.checkedAt)} from this device.", style = Tb.type.bodySmall)
    }
}
