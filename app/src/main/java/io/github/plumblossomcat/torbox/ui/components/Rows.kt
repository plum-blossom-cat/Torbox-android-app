package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.plumblossomcat.torbox.ui.theme.Tb

/** Title + explanation + switch. The whole row toggles. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressable(interaction, enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Tb.type.subtitle, color = if (enabled) Tb.colors.ink else Tb.colors.inkFaint)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Tb.type.bodySmall)
            }
        }
        Spacer(Modifier.width(16.dp))
        TbSwitch(checked, null, enabled)
    }
}

/** A radio-style choice with optional explanation. */
@Composable
fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) c.ink else c.hairlineStrong, label = "ring")
    Row(
        modifier
            .fillMaxWidth()
            .pressable(interaction, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(if (selected) 6.dp else 1.5.dp, ring, CircleShape)
                .background(if (selected) c.background else Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Tb.type.subtitle)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Tb.type.bodySmall)
            }
        }
    }
}

/** Label above an arbitrary control, matching [TbTextField]'s label. */
@Composable
fun Labeled(label: String, modifier: Modifier = Modifier, supporting: String? = null, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = Tb.type.label, color = Tb.colors.inkMuted)
        Spacer(Modifier.height(8.dp))
        content()
        if (supporting != null) {
            Spacer(Modifier.height(6.dp))
            Text(supporting, style = Tb.type.bodySmall)
        }
    }
}
