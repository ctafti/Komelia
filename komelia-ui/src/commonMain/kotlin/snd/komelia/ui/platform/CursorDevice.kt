package snd.komelia.ui.platform

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * True if this event came from a device that moves a cursor (a mouse, or a touchpad-like device such
 * as a Bluetooth page-turner remote) rather than the touchscreen. Pointer types alone can't tell:
 * Android reports touchpad input as finger input.
 */
expect fun PointerEvent.isFromCursorDevice(): Boolean
