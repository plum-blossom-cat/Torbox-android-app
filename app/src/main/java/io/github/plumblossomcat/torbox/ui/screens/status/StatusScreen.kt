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
import androidx.lifecycle.viewModelScope
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.Meter
import io.github.plumblossomcat.torbox.ui.components.TbButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

data class ServiceStatus(val up: Boolean, val stats: List<Stat>, val checkedAt: Instant)

sealed interface SpeedTest {
    data object Idle : SpeedTest
    data object Finding : SpeedTest
    data class Running(val server: String, val mbps: Double, val fraction: Float) : SpeedTest
    data class Done(val server: String, val mbps: Double, val bytes: Long, val seconds: Double) : SpeedTest
    data class Failed(val message: String) : SpeedTest
}

class StatusViewModel(container: AppContainer) : LoadViewModel<ServiceStatus>(container, {
    val up = runCatching { client.isUp() }.getOrDefault(false)
    val stats = runCatching { client.stats() }.getOrNull()?.let(::readStats).orEmpty()
    ServiceStatus(up, stats, Instant.now())
}) {
    private val _speed = MutableStateFlow<SpeedTest>(SpeedTest.Idle)
    val speed: StateFlow<SpeedTest> = _speed.asStateFlow()
    private var job: Job? = null

    fun toggleSpeedTest() {
        if (job?.isActive == true) {
            job?.cancel()
            _speed.value = SpeedTest.Idle
            return
        }
        job = viewModelScope.launch {
            _speed.value = SpeedTest.Finding
            _speed.value = try {
                val server = container.client.speedtestServers().firstOrNull()
                    ?: throw IllegalStateException("TorBox didn't offer a test server right now.")
                val label = listOfNotNull(server.name, server.region?.takeIf { it != server.name }).joinToString(" · ")
                measure(server.url, label)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SpeedTest.Failed(e.friendly())
            }
        }
    }

    /** Streams the test file for up to [limitSeconds], reporting throughput as it goes. */
    private suspend fun measure(url: String, label: String, limitSeconds: Double = 12.0): SpeedTest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        container.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The test server answered HTTP ${response.code}.")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            val buffer = ByteArray(64 * 1024)
            val started = System.nanoTime()
            var bytes = 0L
            var lastReport = started
            body.byteStream().use { stream ->
                while (isActive) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    bytes += read
                    val now = System.nanoTime()
                    val elapsed = (now - started) / 1e9
                    if (now - lastReport > 200_000_000L) {
                        lastReport = now
                        val fraction = maxOf(total?.let { bytes.toFloat() / it } ?: 0f, (elapsed / limitSeconds).toFloat()).coerceIn(0f, 1f)
                        _speed.value = SpeedTest.Running(label, bytes * 8 / elapsed / 1e6, fraction)
                    }
                    if (elapsed >= limitSeconds) break
                }
            }
            val seconds = ((System.nanoTime() - started) / 1e9).coerceAtLeast(0.001)
            SpeedTest.Done(label, bytes * 8 / seconds / 1e6, bytes, seconds)
        }
    }
}

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
    val speed by vm.speed.collectAsStateWithLifecycle()
    StatusContent(load, refreshing, onBack, vm::refresh, onChangelog, speed, vm::toggleSpeedTest)
}

@Composable
fun StatusContent(
    load: Load<ServiceStatus>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onChangelog: () -> Unit,
    speed: SpeedTest = SpeedTest.Idle,
    onSpeedTest: () -> Unit = {},
) {
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
        item(key = "speedHeader") {
            Spacer(Modifier.height(28.dp))
            SectionHeader("Download speed")
        }
        item(key = "speed") { SpeedCard(speed, onSpeedTest) }
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

@Composable
private fun SpeedCard(speed: SpeedTest, onToggle: () -> Unit) {
    val c = Tb.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape)
            .padding(20.dp),
    ) {
        val mbps = when (speed) {
            is SpeedTest.Running -> speed.mbps
            is SpeedTest.Done -> speed.mbps
            else -> null
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                mbps?.let { if (it >= 100) "%.0f".format(java.util.Locale.US, it) else "%.1f".format(java.util.Locale.US, it) } ?: "—",
                style = Tb.type.figure.copy(fontSize = Tb.type.display.fontSize, lineHeight = Tb.type.display.lineHeight),
            )
            Spacer(Modifier.width(8.dp))
            Text("Mbps", style = Tb.type.mono.copy(color = c.inkMuted), modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (speed) {
                SpeedTest.Idle -> "Measures how fast you can download from TorBox's nearest CDN."
                SpeedTest.Finding -> "Finding the closest server…"
                is SpeedTest.Running -> "Testing against ${speed.server}…"
                is SpeedTest.Done -> "${speed.server}  ·  ${Format.bytes(speed.bytes)} in ${"%.1f".format(java.util.Locale.US, speed.seconds)}s"
                is SpeedTest.Failed -> speed.message
            },
            style = Tb.type.bodySmall.copy(color = if (speed is SpeedTest.Failed) c.danger else c.inkMuted),
        )
        if (speed is SpeedTest.Running || speed is SpeedTest.Finding) {
            Spacer(Modifier.height(14.dp))
            Meter((speed as? SpeedTest.Running)?.fraction, color = c.meterFor(io.github.plumblossomcat.torbox.core.model.TransferState.DOWNLOADING))
        }
        Spacer(Modifier.height(16.dp))
        TbButton(
            text = when (speed) {
                SpeedTest.Idle -> "Run speed test"
                SpeedTest.Finding, is SpeedTest.Running -> "Stop"
                else -> "Run again"
            },
            onClick = onToggle,
            style = if (speed is SpeedTest.Running || speed is SpeedTest.Finding) ButtonStyle.Secondary else ButtonStyle.Primary,
            icon = if (speed is SpeedTest.Running || speed is SpeedTest.Finding) TbIcons.Close else TbIcons.Gauge,
            compact = true,
        )
    }
}
