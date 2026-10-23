package io.github.plumblossomcat.torbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.plumblossomcat.torbox.data.SessionState
import io.github.plumblossomcat.torbox.ui.nav.MainNav
import io.github.plumblossomcat.torbox.ui.screens.login.LoginScreen
import io.github.plumblossomcat.torbox.ui.theme.Tb

@Composable
fun AppRoot() {
    val container = LocalContainer.current
    val session by container.session.state.collectAsStateWithLifecycle()
    when (val s = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(Tb.colors.background))
        is SessionState.SignedOut -> {
            LaunchedEffect(Unit) {
                container.library.clear()
                container.account.clear()
            }
            LoginScreen(s.reason)
        }
        SessionState.SignedIn -> MainNav()
    }
}
