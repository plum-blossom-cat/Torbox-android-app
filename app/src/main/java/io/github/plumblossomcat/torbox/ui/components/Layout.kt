package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.plumblossomcat.torbox.ui.theme.LightColors
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Standard horizontal gutter for every screen. */
val Gutter = 20.dp

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Tb.colors.inkMuted) {
    Text(text.uppercase(), style = Tb.type.eyebrow, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The top of a primary tab: an eyebrow, a large serif title, and optional actions aligned to
 * the title's baseline row.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Gutter, end = Gutter - 8.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (eyebrow != null) Eyebrow(eyebrow, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Text(title, style = Tb.type.headline, modifier = Modifier.padding(end = 8.dp))
    }
}

/** Compact bar for pushed screens: back, a title, actions. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TbIconButton(TbIcons.Back, "Back", onBack)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = Tb.type.title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(title, Modifier.weight(1f))
        if (action != null && onAction != null) {
            TbLink(action, onAction, icon = TbIcons.ChevronRight, color = Tb.colors.inkMuted)
        }
    }
}

/** The workhorse list row. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Tb.colors.ink,
    titleColor: Color = Tb.colors.ink,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = {
        if (onClick != null) Icon(TbIcons.ChevronRight, null, tint = Tb.colors.inkFaint, modifier = Modifier.size(18.dp))
    },
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(interaction, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tb.colors.sunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Tb.type.subtitle, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Tb.type.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** A titled group of rows on a raised panel, separated by hairlines. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(RoundedCornerShape(16.dp))
            .background(Tb.colors.raised)
            .border(1.dp, Tb.colors.hairline, RoundedCornerShape(16.dp)),
        content = content,
    )
}

/** Rows inside a [Panel] use a tighter gutter. */
@Composable
fun PanelRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    titleColor: Color = Tb.colors.ink,
    iconTint: Color = Tb.colors.ink,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = {
        if (onClick != null) Icon(TbIcons.ChevronRight, null, tint = Tb.colors.inkFaint, modifier = Modifier.size(18.dp))
    },
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(interaction, onClick = onClick) else Modifier)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Tb.type.subtitle, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Tb.type.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun PanelDivider() = Hairline(Modifier.padding(start = 16.dp))

data class Stat(val label: String, val value: String, val mono: Boolean = true)

/** A grid of labelled values separated by hairlines, read like a spec sheet. */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier.fillMaxWidth()) {
        stats.chunked(columns).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Hairline()
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, stat ->
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(top = 14.dp, bottom = 14.dp, start = if (i == 0) 0.dp else 16.dp, end = 8.dp),
                    ) {
                        Eyebrow(stat.label)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stat.value,
                            style = if (stat.mono) Tb.type.mono.copy(fontSize = Tb.type.body.fontSize) else Tb.type.subtitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Calm, typographic empty state. No clip-art. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter + 8.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.78f, lineHeight = Tb.type.headline.lineHeight * 0.8f, fontStyle = FontStyle.Italic),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(body, style = Tb.type.body.copy(color = Tb.colors.inkMuted), textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            TbButton(action, onAction, style = ButtonStyle.Secondary, compact = true)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter + 8.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(TbIcons.Alert, null, tint = Tb.colors.danger, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(14.dp))
        Text(message, style = Tb.type.body, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            TbButton("Try again", onRetry, style = ButtonStyle.Secondary, icon = TbIcons.Refresh, compact = true)
        }
    }
}

/** Inline notice used at the top of lists, e.g. when a refresh failed but cached data is shown. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: Color = Tb.colors.danger, icon: ImageVector = TbIcons.Alert, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.08f))
            .border(1.dp, tone.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Tb.type.bodySmall.copy(color = Tb.colors.ink), modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TbLink(action, onAction, color = tone)
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val alpha by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Tb.colors.sunken.copy(alpha = alpha)),
    )
}

/** Placeholder rows shaped like real transfer rows. */
@Composable
fun SkeletonRows(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(count) { i ->
            Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(if (i % 2 == 0) 0.82f else 0.64f).height(15.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.42f).height(11.dp))
            }
            Hairline(Modifier.padding(horizontal = Gutter))
        }
    }
}

@Composable
fun TbSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Tb.colors.surface,
        contentColor = Tb.colors.ink,
        scrimColor = Tb.colors.scrim,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Tb.colors.hairlineStrong),
            )
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), content = content)
    }
}

/** Header used at the top of sheets. */
@Composable
fun SheetHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 12.dp)) {
        Text(title, style = Tb.type.headline.copy(fontSize = Tb.type.headline.fontSize * 0.75f, lineHeight = Tb.type.headline.lineHeight * 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = Tb.type.monoSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row inside an action sheet. */
@Composable
fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit, detail: String? = null, danger: Boolean = false, enabled: Boolean = true) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(interaction, enabled = enabled, onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            !enabled -> c.inkFaint
            danger -> c.danger
            else -> c.ink
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Tb.type.subtitle, color = tint)
            if (detail != null) Text(detail, style = Tb.type.bodySmall)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tb.colors.raised,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, style = Tb.type.title.copy(fontSize = Tb.type.title.fontSize * 1.1f)) },
        text = { Text(body, style = Tb.type.body.copy(color = Tb.colors.inkMuted)) },
        confirmButton = {
            TbButton(confirmLabel, onConfirm, style = if (danger) ButtonStyle.Danger else ButtonStyle.Primary, compact = true)
        },
        dismissButton = {
            TbButton("Cancel", onDismiss, style = ButtonStyle.Ghost, compact = true)
        },
    )
}

/** App-wide transient messages. */
@Stable
class Toaster(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = action, withDismissAction = false, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalToaster = staticCompositionLocalOf<Toaster> { error("No Toaster provided") }

@Composable
fun TbSnackbar(data: SnackbarData) {
    Snackbar(
        modifier = Modifier.padding(horizontal = 12.dp),
        containerColor = Tb.colors.ink,
        contentColor = Tb.colors.background,
        actionColor = Tb.colors.signal,
        shape = RoundedCornerShape(14.dp),
        action = data.visuals.actionLabel?.let { label ->
            {
                TbLink(label, { data.performAction() }, color = if (Tb.colors.isDark) LightColors.signalInk else Tb.colors.signal)
            }
        },
    ) {
        Text(data.visuals.message, style = Tb.type.body.copy(color = Tb.colors.background))
    }
}

@Composable
fun FieldGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}
