package snd.komelia.settings.model

import kotlinx.serialization.Serializable

/**
 * Action performed by a reader swipe or a learned remote button.
 *
 * Actions are literal: [NEXT_PAGE] always moves forward in the book regardless of reading direction.
 */
enum class ReaderSwipeAction {
    NONE,
    NEXT_PAGE,
    PREVIOUS_PAGE,
}

enum class ReaderSwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

/**
 * A button of a Bluetooth page-turner remote, learned from the input it sends.
 *
 * Many of these remotes act as a touchpad or stylus and press-and-drag from wherever their cursor
 * currently is, so a button is identified by the shape of its input, not by where on screen it starts.
 * Remotes can report movement differently per screen orientation, so screen-input buttons are learned
 * separately for portrait and [landscape].
 *
 * @param pointerType kind of device: a Compose pointer type name such as "Touch", or a Komelia type
 *   such as "Cursor" (screen input from a cursor-moving device) or "Captured" (read through pointer capture).
 * @param deltaX horizontal movement (negative = left), in dp for screen input. Near zero for a tap.
 * @param deltaY vertical movement (negative = up), in dp for screen input. Near zero for a tap.
 * @param straight the swipe moved in a perfectly straight line, as remotes do and fingers practically
 *   never do. Such buttons are recognized as soon as an input starts moving the same way.
 */
@Serializable
data class LearnedRemoteButton(
    val pointerType: String,
    val deltaX: Float,
    val deltaY: Float,
    val landscape: Boolean,
    val action: ReaderSwipeAction,
    val straight: Boolean = false,
)

/**
 * Reader navigation by swipe gestures and learned remote buttons.
 * Everything is off by default.
 */
@Serializable
data class ReaderSwipeActions(
    val swipeLeft: ReaderSwipeAction = ReaderSwipeAction.NONE,
    val swipeRight: ReaderSwipeAction = ReaderSwipeAction.NONE,
    val swipeUp: ReaderSwipeAction = ReaderSwipeAction.NONE,
    val swipeDown: ReaderSwipeAction = ReaderSwipeAction.NONE,
    val learnedRemoteButtons: List<LearnedRemoteButton> = emptyList(),
) {
    fun actionFor(direction: ReaderSwipeDirection): ReaderSwipeAction = when (direction) {
        ReaderSwipeDirection.LEFT -> swipeLeft
        ReaderSwipeDirection.RIGHT -> swipeRight
        ReaderSwipeDirection.UP -> swipeUp
        ReaderSwipeDirection.DOWN -> swipeDown
    }

    fun withAction(direction: ReaderSwipeDirection, action: ReaderSwipeAction): ReaderSwipeActions = when (direction) {
        ReaderSwipeDirection.LEFT -> copy(swipeLeft = action)
        ReaderSwipeDirection.RIGHT -> copy(swipeRight = action)
        ReaderSwipeDirection.UP -> copy(swipeUp = action)
        ReaderSwipeDirection.DOWN -> copy(swipeDown = action)
    }

    val swipesEnabled: Boolean
        get() = ReaderSwipeDirection.entries.any { actionFor(it) != ReaderSwipeAction.NONE }

    val remoteButtonsEnabled: Boolean
        get() = learnedRemoteButtons.any { it.action != ReaderSwipeAction.NONE }

    val isEnabled: Boolean
        get() = swipesEnabled || remoteButtonsEnabled
}
