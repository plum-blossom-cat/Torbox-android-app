package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.delay

/**
 * Runs [tick] every [intervalMillis] while the screen is visible. Stops in the background so
 * the app never polls TorBox when nobody is looking.
 */
@Composable
fun WhileVisible(intervalMillis: Long, enabled: Boolean = true, tick: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentTick by rememberUpdatedState(tick)
    LaunchedEffect(owner, intervalMillis, enabled) {
        if (!enabled) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(intervalMillis)
                currentTick()
            }
        }
    }
}

/** The floating "Add" action. */
@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Add") {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .padding(end = Gutter, bottom = 16.dp)
            .shadow(if (c.isDark) 0.dp else 10.dp, shape, ambientColor = c.ink.copy(alpha = 0.2f), spotColor = c.ink.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.signal)
            .pressable(interaction, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TbIcons.Add, null, tint = c.onSignal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = Tb.type.button, color = c.onSignal)
    }
}
