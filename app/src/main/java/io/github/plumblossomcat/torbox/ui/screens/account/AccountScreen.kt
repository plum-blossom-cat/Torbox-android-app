package io.github.plumblossomcat.torbox.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.plumblossomcat.torbox.core.api.UserDto
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.data.expiresAt
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.data.memberSince
import io.github.plumblossomcat.torbox.data.planLabel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.ConfirmDialog
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Notice
import io.github.plumblossomcat.torbox.ui.components.Panel
import io.github.plumblossomcat.torbox.ui.components.PanelDivider
import io.github.plumblossomcat.torbox.ui.components.PanelRow
import io.github.plumblossomcat.torbox.ui.components.ScreenHeader
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonBlock
import io.github.plumblossomcat.torbox.ui.components.Stat
import io.github.plumblossomcat.torbox.ui.components.StatGrid
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.launch

class AccountNav(
    val onNotifications: () -> Unit = {},
    val onQueued: () -> Unit = {},
    val onRss: () -> Unit = {},
    val onCloud: () -> Unit = {},
    val onHosters: () -> Unit = {},
    val onStatus: () -> Unit = {},
    val onChangelog: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onAbout: () -> Unit = {},
)

@Composable
fun AccountScreen(nav: AccountNav) {
    val container = LocalContainer.current
    val user by container.account.user.collectAsStateWithLifecycle()
    val error by container.account.error.collectAsStateWithLifecycle()
    val unread by container.account.unread.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var refreshing by remember { mutableStateOf(false) }
    val dark = Tb.colors.isDark

    AccountContent(
        user = user,
        error = error,
        unread = unread,
        refreshing = refreshing,
        nav = nav,
        onRefresh = {
            scope.launch {
                refreshing = true
                container.account.refresh()
                refreshing = false
            }
        },
        onCopy = { text, what ->
            container.device.copy(text, what)
            if (!container.device.systemConfirmsCopy) toaster.show("$what copied")
        },
        onShareReferral = { code -> container.device.share("Join TorBox with my referral code: https://torbox.app/subscription?referral=$code", "TorBox referral") },
        onRedeem = { code, done ->
            scope.launch {
                try {
                    container.client.addReferral(code)
                    toaster.show("Referral code added")
                    container.account.refresh()
                    done(null)
                } catch (e: Exception) {
                    done(e.friendly())
                }
            }
        },
        onManage = { container.device.browse("https://torbox.app/subscription", dark) },
        onWebsite = { container.device.browse("https://torbox.app/dashboard", dark) },
        onSignOut = {
            scope.launch {
                container.session.signOut()
            }
        },
    )
}

@Composable
fun AccountContent(
    user: UserDto?,
    error: String?,
    unread: Int,
    refreshing: Boolean,
    nav: AccountNav,
    onRefresh: () -> Unit,
    onCopy: (String, String) -> Unit,
    onShareReferral: (String) -> Unit,
    onRedeem: (String, (String?) -> Unit) -> Unit,
    onManage: () -> Unit,
    onWebsite: () -> Unit,
    onSignOut: () -> Unit,
) {
    val c = Tb.colors
    var confirmSignOut by remember { mutableStateOf(false) }
    var redeemOpen by remember { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader("Account", eyebrow = user?.let { "${it.planLabel} plan" } ?: "Your TorBox")
            if (error != null && user == null) {
                Notice(error, action = "Retry", onAction = onRefresh)
                Spacer(Modifier.height(16.dp))
            }

            ProfileCard(user, onManage)

            Spacer(Modifier.height(28.dp))
            SectionHeader("Details")
            if (user != null) {
                StatGrid(
                    listOf(
                        Stat("Member since", Format.date(user.memberSince)),
                        Stat("Downloaded", Format.bytes(user.totalDownloaded)),
                        Stat("Server", if (user.server > 0) "#${user.server}" else "Auto"),
                        Stat("Account ID", user.id.toString()),
                    ),
                    Modifier.padding(horizontal = Gutter),
                )
                Hairline(Modifier.padding(horizontal = Gutter))
            } else {
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Spacer(Modifier.height(14.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(14.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("Referrals")
            Panel {
                val code = user?.userReferral?.takeIf { it.isNotBlank() }
                PanelRow(
                    title = "Your referral code",
                    subtitle = code ?: "Not available",
                    subtitleMono = code != null,
                    icon = TbIcons.Gift,
                    trailing = if (code != null) {
                        {
                            TbIconButton(TbIcons.Copy, "Copy referral code", { onCopy(code, "Referral code") }, tint = c.inkMuted, size = 40.dp)
                            TbIconButton(TbIcons.Share, "Share referral link", { onShareReferral(code) }, tint = c.inkMuted, size = 40.dp)
                        }
                    } else {
                        null
                    },
                )
                PanelDivider()
                PanelRow("Redeem a referral code", subtitle = "Only works if your account doesn't have one yet", icon = TbIcons.Key, onClick = { redeemOpen = true })
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("Library tools")
            Panel {
                PanelRow("Notifications", icon = TbIcons.Bell, onClick = nav.onNotifications, trailing = {
                    if (unread > 0) Tag(unread.toString())
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Icon(TbIcons.ChevronRight, null, tint = c.inkFaint, modifier = Modifier.size(18.dp))
                })
                PanelDivider()
                PanelRow("Queued downloads", subtitle = "Waiting to start", icon = TbIcons.Queue, onClick = nav.onQueued)
                PanelDivider()
                PanelRow("RSS feeds", subtitle = "Automatically grab new releases", icon = TbIcons.Rss, onClick = nav.onRss)
                PanelDivider()
                PanelRow("Cloud uploads", subtitle = "Google Drive, OneDrive, Dropbox, GoFile…", icon = TbIcons.Cloud, onClick = nav.onCloud)
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("TorBox")
            Panel {
                PanelRow("Supported hosts", icon = TbIcons.Globe, onClick = nav.onHosters)
                PanelDivider()
                PanelRow("Service status & stats", icon = TbIcons.Activity, onClick = nav.onStatus)
                PanelDivider()
                PanelRow("What's new", subtitle = "TorBox changelog", icon = TbIcons.Newspaper, onClick = nav.onChangelog)
                PanelDivider()
                PanelRow("Open the TorBox dashboard", icon = TbIcons.External, onClick = onWebsite, trailing = null)
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("App")
            Panel {
                PanelRow("Settings", subtitle = "Appearance, downloads, notifications", icon = TbIcons.Settings, onClick = nav.onSettings)
                PanelDivider()
                PanelRow("About", icon = TbIcons.Info, onClick = nav.onAbout)
            }

            Spacer(Modifier.height(28.dp))
            TbButton(
                "Sign out",
                { confirmSignOut = true },
                style = ButtonStyle.Danger,
                icon = TbIcons.Logout,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Your API key and cloud connections will be removed from this device. Your TorBox library isn't affected.",
            confirmLabel = "Sign out",
            danger = true,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (redeemOpen) {
        RedeemDialog(onDismiss = { redeemOpen = false }, onRedeem = onRedeem)
    }
}

@Composable
private fun ProfileCard(user: UserDto?, onManage: () -> Unit) {
    val c = Tb.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.hairline, shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(c.ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (user?.email?.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"),
                    style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.62f, color = c.background),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (user == null) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp))
                } else {
                    Text(user.email ?: "TorBox user", style = Tb.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(if (user.isSubscribed) "${user.planLabel} · subscribed" else user.planLabel, style = Tb.type.bodySmall)
                }
            }
        }
        if (user != null) {
            Spacer(Modifier.height(18.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(if (user.isSubscribed) "Renews" else "Premium until")
                    Spacer(Modifier.height(4.dp))
                    val days = Format.daysUntil(user.expiresAt)
                    Text(
                        Format.date(user.expiresAt) + (days?.let { "  ·  ${it}d left" } ?: ""),
                        style = Tb.type.mono.copy(color = if (days != null && days <= 7) c.caution else c.ink),
                    )
                }
                TbButton("Manage", onManage, style = ButtonStyle.Secondary, compact = true, icon = TbIcons.External)
            }
        }
    }
}

@Composable
private fun RedeemDialog(onDismiss: () -> Unit, onRedeem: (String, (String?) -> Unit) -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tb.colors.raised,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Redeem a referral code", style = Tb.type.title) },
        text = {
            Column {
                Text("Paste a code from a friend. It can only be set once per account.", style = Tb.type.bodySmall)
                Spacer(Modifier.height(16.dp))
                TbTextField(code, { code = it.trim(); error = null }, placeholder = "xxxxxxxx-xxxx-…", mono = true, error = error)
            }
        },
        confirmButton = {
            TbButton("Redeem", {
                if (code.isBlank()) {
                    error = "Enter a code"
                } else {
                    busy = true
                    onRedeem(code) { problem ->
                        busy = false
                        if (problem == null) onDismiss() else error = problem
                    }
                }
            }, compact = true, loading = busy)
        },
        dismissButton = { TbButton("Cancel", onDismiss, style = ButtonStyle.Ghost, compact = true) },
    )
}
