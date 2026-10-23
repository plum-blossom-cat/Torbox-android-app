package io.github.plumblossomcat.torbox.ui.screens.hosters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.HosterDto
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LoadScaffold
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

class HostersViewModel(container: AppContainer) :
    LoadViewModel<List<HosterDto>>(container, { client.hosters().sortedWith(compareByDescending<HosterDto> { it.status }.thenBy { it.name.lowercase() }) })

@Composable
fun HostersScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: HostersViewModel = viewModel { HostersViewModel(container) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    HostersContent(load, refreshing, onBack, vm::refresh)
}

@Composable
fun HostersContent(load: Load<List<HosterDto>>, refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    LoadScaffold(
        title = "Supported hosts",
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { it.isEmpty() },
        empty = { EmptyState("No hosts listed", "TorBox didn't return any supported hosts right now.") },
    ) { all ->
        val q = query.trim()
        val list = if (q.isEmpty()) all else all.filter { h -> h.name.contains(q, true) || h.domains.any { it.contains(q, true) } }
        item(key = "search") {
            TbTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Find a host or domain",
                leading = TbIcons.Search,
                modifier = Modifier.padding(start = Gutter, end = Gutter, bottom = 8.dp),
            )
        }
        item(key = "count") {
            SectionHeader("${list.count { it.status }} online · ${list.count { !it.status }} offline")
        }
        if (list.isEmpty()) item(key = "none") { EmptyState("No matches", "No supported host matches “$q”.") }
        items(list, key = { it.name + it.domains.firstOrNull() }) { HosterRow(it) }
    }
}

@Composable
private fun HosterRow(h: HosterDto) {
    val c = Tb.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.sunken), contentAlignment = Alignment.Center) {
            if (!h.icon.isNullOrBlank()) {
                AsyncImage(h.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            } else {
                Text(h.name.take(1).uppercase(), style = Tb.type.label)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.name, style = Tb.type.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (h.status) c.signalInk else c.danger))
                Spacer(Modifier.width(6.dp))
                Text(if (h.status) "ONLINE" else "OFFLINE", style = Tb.type.eyebrow.copy(color = if (h.status) c.inkMuted else c.danger))
            }
            if (h.domains.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(h.domains.take(4).joinToString(", ") + if (h.domains.size > 4) " +${h.domains.size - 4}" else "", style = Tb.type.monoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val limits = buildList {
                if (h.dailyLinkLimit > 0) add("${h.dailyLinkUsed}/${h.dailyLinkLimit} links today")
                if (h.dailyBandwidthLimit > 0) add("${Format.bytes(h.dailyBandwidthUsed)} of ${Format.bytes(h.dailyBandwidthLimit)} today")
            }
            if (limits.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(limits.joinToString("  ·  "), style = Tb.type.monoSmall)
            }
            h.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = Tb.type.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Hairline(Modifier.padding(horizontal = Gutter))
}
