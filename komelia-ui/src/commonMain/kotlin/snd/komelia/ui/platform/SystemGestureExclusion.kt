package snd.komelia.ui.platform

import androidx.compose.ui.Modifier

/** Asks the platform not to use this area for system gestures, such as Android's edge-swipe back gesture. */
expect fun Modifier.excludeFromSystemGestures(): Modifier
