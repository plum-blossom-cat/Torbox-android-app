package io.github.plumblossomcat.torbox.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.plumblossomcat.torbox.BuildConfig
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Panel
import io.github.plumblossomcat.torbox.ui.components.PanelDivider
import io.github.plumblossomcat.torbox.ui.components.PanelRow
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.SheetHeader
import io.github.plumblossomcat.torbox.ui.components.TbSheet
import io.github.plumblossomcat.torbox.ui.components.TopBar
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

const val SOURCE_URL = "https://github.com/plum-blossom-cat/Torbox-android-app"
const val API_DOCS_URL = "https://api-docs.torbox.app"

private data class License(val name: String, val detail: String, val asset: String?)

private val LICENSES = listOf(
    License("Geist & Geist Mono", "SIL Open Font License 1.1 · Vercel", "licenses/OFL-Geist.txt"),
    License("Instrument Serif", "SIL Open Font License 1.1 · Instrument", "licenses/OFL-InstrumentSerif.txt"),
    License("Lucide icons", "ISC License · Lucide contributors", null),
    License("Jetpack Compose & AndroidX", "Apache License 2.0 · Google", null),
    License("OkHttp", "Apache License 2.0 · Square", null),
    License("Coil", "Apache License 2.0 · Coil contributors", null),
    License("kotlinx.serialization & coroutines", "Apache License 2.0 · JetBrains", null),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val dark = Tb.colors.isDark
    val context = LocalContext.current
    var license by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("About", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = Gutter, vertical = 12.dp)) {
                Eyebrow("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Spacer(Modifier.height(10.dp))
                Text(
                    buildAnnotatedString {
                        append("A calm, fast home for your ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("TorBox") }
                        append(" library.")
                    },
                    style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.8f, lineHeight = Tb.type.headline.lineHeight * 0.84f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Torrents, usenet and web downloads, search, RSS, cloud uploads and your account — everything the TorBox website does, built for your phone. It talks only to TorBox's official API.",
                    style = Tb.type.body.copy(color = Tb.colors.inkMuted),
                )
            }
            Spacer(Modifier.height(20.dp))
            SectionHeader("Links")
            Panel {
                PanelRow("TorBox website", icon = TbIcons.Globe, onClick = { container.device.browse("https://torbox.app", dark) }, trailing = null)
                PanelDivider()
                PanelRow("TorBox API documentation", icon = TbIcons.FileCode, onClick = { container.device.browse(API_DOCS_URL, dark) }, trailing = null)
                PanelDivider()
                PanelRow("Source code", subtitle = "plum-blossom-cat/Torbox-android-app", icon = TbIcons.Github, onClick = { container.device.browse(SOURCE_URL, dark) }, trailing = null)
            }
            Spacer(Modifier.height(28.dp))
            SectionHeader("Open source")
            Panel {
                LICENSES.forEachIndexed { i, l ->
                    if (i > 0) PanelDivider()
                    PanelRow(
                        title = l.name,
                        subtitle = l.detail,
                        onClick = l.asset?.let { path ->
                            {
                                val text = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("License text unavailable.")
                                license = l.name to text
                            }
                        },
                        trailing = null,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    license?.let { (name, text) ->
        TbSheet({ license = null }) {
            SheetHeader(name, "SIL Open Font License 1.1")
            SelectionContainer {
                Text(
                    text,
                    style = Tb.type.monoSmall,
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Gutter),
                )
            }
        }
    }
}
