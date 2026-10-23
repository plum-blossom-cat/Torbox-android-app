package io.github.plumblossomcat.torbox.ui.screens.changelog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.ChangelogDto
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Timestamps
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LoadScaffold
import io.github.plumblossomcat.torbox.ui.components.TbLink
import io.github.plumblossomcat.torbox.ui.components.pressable
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

class ChangelogViewModel(container: AppContainer) :
    LoadViewModel<List<ChangelogDto>>(container, { client.changelog().sortedByDescending { Timestamps.parse(it.createdAt) } })

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: ChangelogViewModel = viewModel { ChangelogViewModel(container) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val dark = Tb.colors.isDark
    ChangelogContent(load, refreshing, onBack, vm::refresh) { container.device.browse(it, dark) }
}

@Composable
fun ChangelogContent(load: Load<List<ChangelogDto>>, refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    LoadScaffold(
        title = "What's new",
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { it.isEmpty() },
        empty = { EmptyState("No entries", "TorBox's changelog is empty right now.") },
    ) { list ->
        items(list, key = { it.id.ifBlank { it.name } }) { entry -> Entry(entry, onOpen) }
    }
}

@Composable
private fun Entry(entry: ChangelogDto, onOpen: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val body = remember(entry.id) { renderMarkdown(entry.markdown ?: stripHtml(entry.html.orEmpty())) }
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(interaction) { expanded = !expanded }
            .padding(horizontal = Gutter, vertical = 18.dp),
    ) {
        Eyebrow(Format.date(Timestamps.parse(entry.createdAt)))
        Spacer(Modifier.height(6.dp))
        Text(entry.name, style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.7f, lineHeight = Tb.type.headline.lineHeight * 0.74f))
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                body,
                style = Tb.type.body.copy(color = Tb.colors.inkMuted),
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val link = entry.link?.takeIf { it.startsWith("http") }
        if (expanded && link != null) {
            Spacer(Modifier.height(4.dp))
            TbLink("Read on TorBox", { onOpen(link) }, icon = TbIcons.External)
        }
    }
    Hairline(Modifier.padding(horizontal = Gutter))
}

private fun stripHtml(html: String): String =
    html.replace(Regex("<(br|/p|/li|/h\\d)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "- ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")

/** Just enough Markdown for changelog notes: headings, bullets, bold, inline code and links. */
fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var first = true
    var paragraphBreak = false
    text.lines().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) {
            if (!first) paragraphBreak = true
            return@forEach
        }
        if (!first) append(if (paragraphBreak) "\n\n" else "\n")
        first = false
        paragraphBreak = false
        when {
            line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { inline(line.trimStart('#').trim()) }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                append("•  ")
                inline(line.drop(2))
            }
            else -> inline(line)
        }
    }
}

private val INLINE = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\[([^\\]]+)]\\(([^)]+)\\)")

private fun AnnotatedString.Builder.inline(text: String) {
    var last = 0
    INLINE.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        val (bold1, bold2, code, linkText) = m.destructured
        when {
            bold1.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold1) }
            bold2.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold2) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = io.github.plumblossomcat.torbox.ui.theme.GeistMono)) { append(code) }
            linkText.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(linkText) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
