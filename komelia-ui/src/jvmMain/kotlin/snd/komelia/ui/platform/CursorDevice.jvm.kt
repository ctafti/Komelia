package snd.komelia.ui.platform

import androidx.compose.ui.input.pointer.PointerEvent

actual fun PointerEvent.isFromCursorDevice(): Boolean = false
