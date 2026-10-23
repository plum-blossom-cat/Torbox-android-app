package io.github.plumblossomcat.torbox.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.theme.Tb

@Composable
fun MainNav() {
    Box(Modifier.fillMaxSize().background(Tb.colors.background)) {
        EmptyState("Signed in", "The library is on its way.")
    }
}
