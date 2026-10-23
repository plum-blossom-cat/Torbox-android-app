package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.plumblossomcat.torbox.data.Load

/**
 * Pushed screen with a top bar and a pull-to-refresh list, handling the loading and failure
 * states uniformly so every secondary screen behaves the same way.
 */
@Composable
fun <T> LoadScaffold(
    title: String,
    onBack: () -> Unit,
    load: Load<T>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    isEmpty: (T) -> Boolean,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    content: LazyListScope.(T) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopBar(title, onBack, actions = actions)
        Box(Modifier.fillMaxSize()) {
            when (load) {
                Load.Loading -> SkeletonRows(6)
                is Load.Failed -> ErrorState(load.message, onRefresh)
                is Load.Ready -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                        if (isEmpty(load.value)) {
                            item { empty() }
                        } else {
                            content(load.value)
                        }
                    }
                }
            }
            overlay()
        }
    }
}
