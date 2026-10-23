package io.github.plumblossomcat.torbox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.plumblossomcat.torbox.data.AppSettings
import io.github.plumblossomcat.torbox.data.IncomingIntent
import io.github.plumblossomcat.torbox.data.SessionState
import io.github.plumblossomcat.torbox.ui.AppRoot
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TorBoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val container get() = (application as TorBoxApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { container.session.state.value == SessionState.Loading }
        if (savedInstanceState == null) handle(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            TorBoxTheme(settings.theme) {
                val dark = Tb.colors.isDark
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                CompositionLocalProvider(LocalContainer provides container) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        IncomingIntent.open(intent)?.let { container.pendingOpen.value = it }
        lifecycleScope.launch {
            val add = withContext(Dispatchers.IO) { IncomingIntent.parse(intent, contentResolver) }
            if (add != null) container.pendingAdd.value = add
        }
    }
}
