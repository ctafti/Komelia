package snd.komelia.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A press of a captured pointer device: its movement between press and release.
 *
 * Units depend on the device but are consistent for the same device: touchpad-like devices report
 * thousandths of the pad's size, mice report raw movement counts.
 */
data class CapturedPointerGesture(val deltaX: Float, val deltaY: Float)

/**
 * Captured pointer input. While a screen listens, the platform captures mouse- and touchpad-like
 * devices (such as some Bluetooth page-turner remotes) and delivers their presses here, instead of
 * moving the cursor. The cursor is hidden, and screen edges and system edge gestures no longer
 * interfere. Touchscreen input is unaffected.
 *
 * Not every device supports this: on recent Android versions, some touch-style devices stop sending
 * input entirely while captured.
 *
 * Currently provided on Android (see PointerInputRootLayout); elsewhere nothing is delivered.
 */
object CapturedPointerInput {
    private val listeners = mutableListOf<(CapturedPointerGesture) -> Unit>()
    private val mutableIsActive = MutableStateFlow(false)
    private val mutableIsCaptured = MutableStateFlow(false)

    /** True while any screen listens. The platform keeps pointer capture on while this is true. */
    val isActive: StateFlow<Boolean> = mutableIsActive.asStateFlow()

    /**
     * True while the platform actually has pointer capture. Capture can be refused or lost (e.g. when
     * another window takes focus), in which case devices keep moving their cursor as usual.
     */
    val isCaptured: StateFlow<Boolean> = mutableIsCaptured.asStateFlow()

    /** Called by the platform for each completed press. Only the most recent listener receives it. */
    fun dispatch(gesture: CapturedPointerGesture) {
        listeners.lastOrNull()?.invoke(gesture)
    }

    /** Called by the platform when pointer capture is granted or lost. */
    fun onCaptureChanged(captured: Boolean) {
        mutableIsCaptured.value = captured
    }

    internal fun addListener(listener: (CapturedPointerGesture) -> Unit) {
        listeners.add(listener)
        mutableIsActive.value = true
    }

    internal fun removeListener(listener: (CapturedPointerGesture) -> Unit) {
        listeners.remove(listener)
        mutableIsActive.value = listeners.isNotEmpty()
    }
}

/** Receives captured pointer presses while this is in the composition and [enabled] is true. */
@Composable
fun CapturedPointerEffect(enabled: Boolean, onGesture: (CapturedPointerGesture) -> Unit) {
    val currentOnGesture by rememberUpdatedState(onGesture)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val listener: (CapturedPointerGesture) -> Unit = { currentOnGesture(it) }
        CapturedPointerInput.addListener(listener)
        onDispose { CapturedPointerInput.removeListener(listener) }
    }
}
