package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plumblossomcat.torbox.core.model.TransferState
import io.github.plumblossomcat.torbox.ui.theme.Tb

enum class ButtonStyle { Primary, Signal, Secondary, Ghost, Danger }

/** Tactile press feedback shared by every custom control. */
@Composable
fun Modifier.pressable(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(120), label = "press")
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun TbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val c = Tb.colors
    val (bg, fg, border) = when (style) {
        ButtonStyle.Primary -> Triple(c.ink, c.background, Color.Transparent)
        ButtonStyle.Signal -> Triple(c.signal, c.onSignal, Color.Transparent)
        ButtonStyle.Secondary -> Triple(c.raised, c.ink, c.hairlineStrong)
        ButtonStyle.Ghost -> Triple(Color.Transparent, c.ink, Color.Transparent)
        ButtonStyle.Danger -> Triple(c.dangerSoft, c.danger, Color.Transparent)
    }
    val interaction = remember { MutableInteractionSource() }
    val active = enabled && !loading
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) 38.dp else 50.dp)
            .clip(shape)
            .background(if (enabled) bg else c.sunken)
            .border(1.dp, if (enabled) border else Color.Transparent, shape)
            .pressable(interaction, enabled = active, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) fg else c.inkFaint
        if (loading) {
            Spinner(tint, Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = Tb.type.button.copy(fontSize = if (compact) Tb.type.label.fontSize else Tb.type.button.fontSize),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TbIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tb.colors.ink,
    enabled: Boolean = true,
    filled: Boolean = false,
    badge: Boolean = false,
    size: Dp = 44.dp,
) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(c.raised).border(1.dp, c.hairline, CircleShape) else Modifier)
            .pressable(interaction, enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else c.inkFaint, modifier = Modifier.size(20.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = size * 0.2f, end = size * 0.2f)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.background)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(c.danger),
            )
        }
    }
}

/** A text link: underlined on press, optional trailing arrow for external destinations. */
@Composable
fun TbLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Tb.colors.ink) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .pressable(interaction, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Tb.type.label, color = color)
        if (icon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun TbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            error != null -> c.danger
            focused -> c.ink
            else -> c.hairlineStrong
        },
        label = "fieldBorder",
    )
    val textStyle: TextStyle = (if (mono) Tb.type.mono.copy(fontSize = Tb.type.body.fontSize) else Tb.type.body).copy(color = c.ink)
    Column(modifier) {
        if (label != null) {
            Text(label, style = Tb.type.label, color = c.inkMuted)
            Spacer(Modifier.height(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle,
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.raised)
                        .border(if (focused) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (leading != null) {
                        Icon(
                            leading, null, tint = c.inkFaint,
                            modifier = Modifier
                                .padding(top = if (singleLine) 0.dp else 15.dp, end = 10.dp)
                                .size(18.dp),
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp),
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = c.inkFaint, maxLines = if (singleLine) 1 else maxLines, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
                    }
                }
            },
        )
        val note = error ?: supporting
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(note, style = Tb.type.bodySmall, color = if (error != null) c.danger else c.inkMuted)
        }
    }
}

/** Thin progress bar. A null [progress] animates as indeterminate. */
@Composable
fun Meter(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = Tb.colors.signalInk,
    track: Color = Tb.colors.hairline,
    height: Dp = 3.dp,
) {
    val animated by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(500), label = "meter")
    val sweep = if (progress == null && Motion.reduced) {
        0.35f
    } else if (progress == null) {
        val t = rememberInfiniteTransition(label = "indeterminate")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sweep").value
    } else {
        0f
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                if (progress == null) {
                    val w = size.width * 0.3f
                    val start = ((size.width + w) * sweep - w).coerceAtLeast(0f)
                    val end = ((size.width + w) * sweep).coerceAtMost(size.width)
                    if (end > start) {
                        drawRoundRect(color, topLeft = Offset(start, 0f), size = Size(end - start, size.height), cornerRadius = r)
                    }
                } else if (animated > 0f) {
                    drawRoundRect(color, size = Size(size.width * animated, size.height), cornerRadius = r)
                }
            },
    )
}

@Composable
fun StateBadge(state: TransferState, modifier: Modifier = Modifier, text: String = state.label) {
    val color = Tb.colors.forState(state)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if ((state == TransferState.DOWNLOADING || state == TransferState.PREPARING) && !Motion.reduced) {
            PulsingDot(color)
        } else {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), style = Tb.type.eyebrow, color = color, maxLines = 1)
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
}

/** Small uppercase tag, e.g. INSTANT, OWNED, PRO. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, emphasis: Boolean = false, color: Color? = null) {
    val c = Tb.colors
    val shape = RoundedCornerShape(5.dp)
    val bg = when {
        emphasis -> c.signal
        else -> Color.Transparent
    }
    val fg = color ?: if (emphasis) c.onSignal else c.inkMuted
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (emphasis) Modifier else Modifier.border(1.dp, color?.copy(alpha = 0.5f) ?: c.hairlineStrong, shape))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text.uppercase(), style = Tb.type.eyebrow, color = fg, maxLines = 1)
    }
}

/** Equal-width segmented control with a raised thumb on the selected option. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tb.colors
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.sunken)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) c.raised else Color.Transparent, label = "seg")
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .then(if (isSelected) Modifier.border(1.dp, c.hairline, RoundedCornerShape(9.dp)) else Modifier)
                    .clickable(interaction, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Tb.type.label,
                    color = if (isSelected) c.ink else c.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Text tabs with a tabular count and an underline under the current tab. */
@Composable
fun UnderlineTabs(
    tabs: List<Pair<String, Int?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tb.colors
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(c.hairline),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            tabs.forEachIndexed { index, (label, count) ->
                val isSelected = index == selected
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .clickable(interaction, indication = null, role = Role.Tab) { onSelect(index) }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(label, style = Tb.type.subtitle, color = if (isSelected) c.ink else c.inkFaint)
                        if (count != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(count.toString(), style = Tb.type.monoSmall, color = if (isSelected) c.inkMuted else c.inkFaint)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val lineAlpha by animateFloatAsState(if (isSelected) 1f else 0f, label = "tabLine")
                    Box(
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(c.ink.copy(alpha = lineAlpha)),
                    )
                }
            }
        }
    }
}

/** Rounded filter chips in a single row. */
@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tb.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val interaction = remember { MutableInteractionSource() }
            val bg by animateColorAsState(if (isSelected) c.ink else Color.Transparent, label = "chipBg")
            val fg by animateColorAsState(if (isSelected) c.background else c.inkMuted, label = "chipFg")
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (isSelected) c.ink else c.hairlineStrong, CircleShape)
                    .pressable(interaction, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(label, style = Tb.type.label, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
fun TbSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val c = Tb.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.background,
            checkedTrackColor = c.ink,
            checkedBorderColor = c.ink,
            uncheckedThumbColor = c.inkFaint,
            uncheckedTrackColor = c.sunken,
            uncheckedBorderColor = c.hairlineStrong,
        ),
    )
}

/** Vertical rule used between inline metadata. */
@Composable
fun Divot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(10.dp)
            .background(Tb.colors.hairlineStrong),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Tb.colors.hairline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(Tb.colors.hairline),
    )
}
