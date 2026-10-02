package snd.komelia

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import snd.komelia.ui.platform.CapturedPointerGesture
import snd.komelia.ui.platform.CapturedPointerInput
import snd.komelia.ui.platform.PointerVisibility

/**
 * Root layout of the activity that implements app-wide pointer handling:
 *
 * - Hides the mouse pointer while [PointerVisibility.isHidden]. Android asks the view hierarchy for
 *   the pointer icon from the root down, so answering here keeps the pointer hidden even when Compose
 *   updates its own icon (which it does on every press), and over embedded views such as the EPUB
 *   reader's WebView.
 * - Provides [CapturedPointerInput]: while a screen listens, it requests pointer capture, so mouse- and
 *   touchpad-like devices send their input to the app instead of moving the cursor. Captured events
 *   are delivered along the focused view chain, so they are intercepted here before reaching Compose
 *   or a WebView.
 */
class PointerInputRootLayout(context: Context) : FrameLayout(context) {
    private val gestureTracker = CapturedGestureTracker(CapturedPointerInput::dispatch)
    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = MainScope().also { scope ->
            scope.launch { CapturedPointerInput.isActive.collect { updatePointerCapture() } }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Android releases pointer capture when the window loses focus
        updatePointerCapture()
    }

    private fun updatePointerCapture() {
        val requested = CapturedPointerInput.isActive.value
        if (requested && hasWindowFocus() && !hasPointerCapture()) {
            // Captured events are dispatched along the focused view chain, so some view must have focus.
            // Children are tried first; this layout only takes focus if none of them can.
            if (findFocus() == null) {
                isFocusableInTouchMode = true
                requestFocus()
            }
            requestPointerCapture()
        } else if (!requested && hasPointerCapture()) {
            releasePointerCapture()
        }
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        CapturedPointerInput.onCaptureChanged(hasCapture)
    }

    override fun dispatchCapturedPointerEvent(event: MotionEvent): Boolean {
        if (!CapturedPointerInput.isActive.value) return super.dispatchCapturedPointerEvent(event)
        gestureTracker.onEvent(event)
        return true
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        return if (PointerVisibility.isHidden) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
        else super.onResolvePointerIcon(event, pointerIndex)
    }
}

/**
 * Turns captured pointer events into presses: the movement between press and release.
 *
 * Mice are captured as [InputDevice.SOURCE_MOUSE_RELATIVE] and report movement deltas, which are summed.
 * Touchpad-like devices report absolute positions on the pad, converted to thousandths of the pad size.
 */
private class CapturedGestureTracker(private val onGesture: (CapturedPointerGesture) -> Unit) {
    private var pressed = false
    private var relativeX = 0f
    private var relativeY = 0f
    private var startX = 0f
    private var startY = 0f

    fun onEvent(event: MotionEvent) {
        val relative = event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                relativeX = 0f
                relativeY = 0f
                startX = event.x
                startY = event.y
                if (relative) addRelativeMovement(event)
            }

            MotionEvent.ACTION_MOVE -> if (pressed && relative) addRelativeMovement(event)

            MotionEvent.ACTION_UP -> if (pressed) {
                pressed = false
                if (relative) {
                    addRelativeMovement(event)
                    onGesture(CapturedPointerGesture(relativeX, relativeY))
                } else {
                    onGesture(
                        CapturedPointerGesture(
                            deltaX = (event.x - startX) * 1000f / axisRange(event, MotionEvent.AXIS_X),
                            deltaY = (event.y - startY) * 1000f / axisRange(event, MotionEvent.AXIS_Y),
                        )
                    )
                }
            }

            // A press is a single pointer; anything more is not a remote button
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> pressed = false
        }
    }

    private fun addRelativeMovement(event: MotionEvent) {
        for (i in 0 until event.historySize) {
            relativeX += event.getHistoricalX(i)
            relativeY += event.getHistoricalY(i)
        }
        relativeX += event.x
        relativeY += event.y
    }

    private fun axisRange(event: MotionEvent, axis: Int): Float {
        val range = event.device?.getMotionRange(axis, event.source)?.range
        return if (range == null || range <= 0f) 1000f else range
    }
}
