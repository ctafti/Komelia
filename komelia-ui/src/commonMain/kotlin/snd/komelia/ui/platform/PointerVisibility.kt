package snd.komelia.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Screens can ask the platform to hide the mouse pointer, e.g. readers, where the cursor of a
 * Bluetooth page-turner remote would otherwise be drawn over the page.
 *
 * Requests are counted, so screens that overlap during navigation don't undo each other.
 * Currently honored on Android (see MainActivity).
 */
object PointerVisibility {
    private val hideRequests = MutableStateFlow(0)

    val isHidden: Boolean
        get() = hideRequests.value > 0

    internal fun requestHide() = hideRequests.update { it + 1 }

    internal fun releaseHide() = hideRequests.update { (it - 1).coerceAtLeast(0) }
}

/** Hides the mouse pointer while this is in the composition and [hide] is true. */
@Composable
fun HidePointerEffect(hide: Boolean) {
    DisposableEffect(hide) {
        if (hide) PointerVisibility.requestHide()
        onDispose { if (hide) PointerVisibility.releaseHide() }
    }
}
