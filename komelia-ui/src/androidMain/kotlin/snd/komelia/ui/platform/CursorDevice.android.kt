package snd.komelia.ui.platform

import android.view.InputDevice
import androidx.compose.ui.input.pointer.PointerEvent

actual fun PointerEvent.isFromCursorDevice(): Boolean {
    return motionEvent?.isFromSource(InputDevice.SOURCE_MOUSE) ?: false
}
