package io.github.plumblossomcat.torbox.ui.screens.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.NotificationDto
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Timestamps
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ConfirmDialog
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LoadScaffold
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.launch

class NotificationsViewModel(container: AppContainer) :
    LoadViewModel<List<NotificationDto>>(container, { client.notifications().sortedByDescending { it.id } }) {

    fun markSeen(list: List<NotificationDto>) {
        val latest = list.maxOfOrNull { it.id } ?: return
        viewModelScope.launch { container.account.markSeen(latest) }
    }

    fun dismiss(id: Long) {
        update { list -> list.filterNot { it.id == id } }
        mutate(null) { client.clearNotification(id) }
    }

    fun clearAll() {
        update { emptyList() }
        mutate("Cleared all notifications") { client.clearNotification(null) }
    }

    fun sendTest() = mutate("Test notification sent") { client.sendTestNotification() }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: NotificationsViewModel = viewModel { NotificationsViewModel(container) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    LaunchedEffect(message) {
        message?.let {
            toaster.show(it)
            vm.message.value = null
        }
    }
    LaunchedEffect(load) { (load as? Load.Ready)?.value?.let(vm::markSeen) }
    NotificationsContent(load, refreshing, onBack, vm::refresh, vm::dismiss, vm::clearAll, vm::sendTest)
}

@Composable
fun NotificationsContent(
    load: Load<List<NotificationDto>>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: (Long) -> Unit,
    onClearAll: () -> Unit,
    onSendTest: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    LoadScaffold(
        title = "Notifications",
        onBack = onBack,
        load = load,
        refreshing = refreshing,
        onRefresh = onRefresh,
        isEmpty = { it.isEmpty() },
        empty = {
            EmptyState(
                "You're all caught up",
                "TorBox posts here when downloads finish, fail, or something about your account changes.",
                action = "Send a test notification",
                onAction = onSendTest,
            )
        },
        actions = {
            TbIconButton(TbIcons.Send, "Send a test notification", onSendTest)
            val hasAny = (load as? Load.Ready)?.value?.isNotEmpty() == true
            TbIconButton(TbIcons.Delete, "Clear all", { confirmClear = true }, enabled = hasAny)
        },
    ) { list ->
        items(list, key = { it.id }) { n ->
            NotificationRow(n, onDismiss = { onDismiss(n.id) }, modifier = Modifier.animateItem())
            Hairline(Modifier.padding(horizontal = Gutter))
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all notifications?",
            body = "They'll be removed from your TorBox account everywhere.",
            confirmLabel = "Clear all",
            danger = true,
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun NotificationRow(n: NotificationDto, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Eyebrow(Format.relative(Timestamps.parse(n.createdAt)))
            Spacer(Modifier.height(6.dp))
            Text(n.title.ifBlank { "Notification" }, style = Tb.type.subtitle)
            if (n.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(n.message, style = Tb.type.body.copy(color = Tb.colors.inkMuted))
            }
        }
        Spacer(Modifier.width(8.dp))
        TbIconButton(TbIcons.Close, "Dismiss", onDismiss, tint = Tb.colors.inkFaint, size = 40.dp)
    }
}
