package io.github.plumblossomcat.torbox.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.plumblossomcat.torbox.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
