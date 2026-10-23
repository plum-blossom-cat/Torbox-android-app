package io.github.plumblossomcat.torbox.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Looping animations (pulsing dots, shimmer, spinners) are switched off when the person has
 * turned off animations in Android's accessibility settings, and in UI tests.
 */
object Motion {
    @Volatile
    var reduced: Boolean = false
}

@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier) {
    if (Motion.reduced) {
        CircularProgressIndicator(progress = { 0.7f }, modifier = modifier, color = color, strokeWidth = 2.dp, trackColor = Color.Transparent)
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = 2.dp)
    }
}
