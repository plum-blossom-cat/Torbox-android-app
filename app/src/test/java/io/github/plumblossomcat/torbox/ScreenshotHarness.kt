package io.github.plumblossomcat.torbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Toaster
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.ThemeMode
import io.github.plumblossomcat.torbox.ui.theme.TorBoxTheme

/** Screenshots land in the repository's docs folder so they can be reviewed and linked from the README. */
private const val DIR = "../docs/screenshots"

fun ComposeContentTestRule.shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
    setContent {
        TorBoxTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            val scope = rememberCoroutineScope()
            val toaster = remember { Toaster(SnackbarHostState(), scope) }
            CompositionLocalProvider(LocalToaster provides toaster) {
                Box(Modifier.fillMaxSize().background(Tb.colors.background)) { content() }
            }
        }
    }
    waitForIdle()
    onRoot().captureRoboImage("$DIR/${name}_${if (dark) "dark" else "light"}.png")
}
