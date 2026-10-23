package io.github.plumblossomcat.torbox.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.plumblossomcat.torbox.core.api.SeedPreference
import io.github.plumblossomcat.torbox.data.AppSettings
import io.github.plumblossomcat.torbox.data.CompletionWorker
import io.github.plumblossomcat.torbox.data.FileAction
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.Labeled
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.OptionRow
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.Segmented
import io.github.plumblossomcat.torbox.ui.components.ToggleRow
import io.github.plumblossomcat.torbox.ui.components.TopBar
import io.github.plumblossomcat.torbox.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private val REFRESH_OPTIONS = listOf(3, 5, 10, 30)

class SettingsActions(
    val onTheme: (ThemeMode) -> Unit = {},
    val onSeed: (SeedPreference) -> Unit = {},
    val onAllowZip: (Boolean) -> Unit = {},
    val onFileAction: (FileAction) -> Unit = {},
    val onRefresh: (Int) -> Unit = {},
    val onNotify: (Boolean) -> Unit = {},
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val store = container.settings

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { store.setNotifyOnComplete(granted) }
        if (!granted) toaster.show("Notifications are blocked. Allow them in Android settings to get alerts.")
    }

    SettingsContent(
        settings = settings,
        onBack = onBack,
        actions = SettingsActions(
            onTheme = { scope.launch { store.setTheme(it) } },
            onSeed = { scope.launch { store.setSeed(it) } },
            onAllowZip = { scope.launch { store.setAllowZip(it) } },
            onFileAction = { scope.launch { store.setFileAction(it) } },
            onRefresh = { scope.launch { store.setRefreshSeconds(it) } },
            onNotify = { enable ->
                if (enable && Build.VERSION.SDK_INT >= 33 && !CompletionWorker.canNotify(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { store.setNotifyOnComplete(enable) }
                }
            },
        ),
    )
}

@Composable
fun SettingsContent(settings: AppSettings, onBack: () -> Unit, actions: SettingsActions) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            SectionHeader("Appearance")
            Segmented(
                options = listOf("System", "Light", "Dark"),
                selected = ThemeMode.entries.indexOf(settings.theme),
                onSelect = { actions.onTheme(ThemeMode.entries[it]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
            SectionHeader("When you tap a file")
            Column(Modifier.padding(horizontal = Gutter)) {
                FileAction.entries.forEach { action ->
                    OptionRow(
                        title = action.label,
                        selected = settings.fileAction == action,
                        onClick = { actions.onFileAction(action) },
                        subtitle = when (action) {
                            FileAction.ASK -> "Choose between saving, streaming, copying or sharing"
                            FileAction.SAVE -> "Downloads to your phone's Downloads/TorBox folder"
                            FileAction.OPEN -> "Streams in VLC, MX Player or any app that can"
                            FileAction.COPY -> "Copies a temporary download link"
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("New torrents")
            Column(Modifier.padding(horizontal = Gutter)) {
                Labeled("Seeding") {
                    Segmented(
                        options = listOf("Default", "Always", "Never"),
                        selected = SeedPreference.entries.indexOf(settings.seed),
                        onSelect = { actions.onSeed(SeedPreference.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Allow zipping", settings.allowZip, actions.onAllowZip, subtitle = "Lets you download a whole torrent as one zip.")
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Live updates")
            Column(Modifier.padding(horizontal = Gutter)) {
                Labeled("Refresh active downloads every", supporting = "Only while the app is open and something is downloading.") {
                    Segmented(
                        options = REFRESH_OPTIONS.map { "${it}s" },
                        selected = REFRESH_OPTIONS.indexOf(settings.refreshSeconds).coerceAtLeast(0),
                        onSelect = { actions.onRefresh(REFRESH_OPTIONS[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Hairline()
                ToggleRow(
                    "Notify when downloads finish",
                    settings.notifyOnComplete,
                    actions.onNotify,
                    subtitle = "Checks about every 15 minutes in the background.",
                )
                Hairline()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
