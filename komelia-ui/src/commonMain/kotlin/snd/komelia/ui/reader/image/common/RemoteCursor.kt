package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import snd.komelia.ui.platform.excludeFromSystemGestures
import snd.komelia.ui.platform.isFromCursorDevice
import kotlin.math.roundToInt

/** Android excludes at most 200dp of each screen edge from system gestures. */
private val GESTURE_EXCLUSION_HEIGHT = 200.dp

/**
 * Position of the cursor of a cursor-moving device, such as a Bluetooth page-turner remote, in the
 * coordinates of the area tracking it. Null until the cursor has been seen there.
 */
@Stable
class RemoteCursorState {
    var position by mutableStateOf<Offset?>(null)
        internal set
}

@Composable
fun rememberRemoteCursorState() = remember { RemoteCursorState() }

/** Follows the cursor of cursor-moving devices over this area. Input is only observed, never consumed. */
fun Modifier.trackRemoteCursor(state: RemoteCursorState, enabled: Boolean): Modifier {
    if (!enabled) return this
    return pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.isFromCursorDevice()) {
                    event.changes.firstOrNull()?.let { state.position = it.position }
                }
            }
        }
    }
}

/**
 * Keeps the system's edge-swipe back gesture away from the remote's cursor. Without this, a remote
 * swipe that starts next to the left or right edge of the screen can be taken over as "back".
 *
 * Excludes a band of the screen centered on the cursor, as tall as the system allows.
 */
@Composable
fun BoxScope.RemoteCursorGestureExclusion(state: RemoteCursorState, enabled: Boolean) {
    val cursor = state.position
    if (!enabled || cursor == null) return
    Box(
        Modifier
            .offset { IntOffset(0, (cursor.y - GESTURE_EXCLUSION_HEIGHT.toPx() / 2).roundToInt()) }
            .fillMaxWidth()
            .height(GESTURE_EXCLUSION_HEIGHT)
            .excludeFromSystemGestures()
    )
}
