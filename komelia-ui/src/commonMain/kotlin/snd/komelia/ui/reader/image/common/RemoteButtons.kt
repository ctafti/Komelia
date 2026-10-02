package snd.komelia.ui.reader.image.common

import snd.komelia.settings.model.LearnedRemoteButton
import snd.komelia.settings.model.ReaderSwipeAction
import snd.komelia.settings.model.ReaderSwipeDirection
import snd.komelia.ui.platform.CapturedPointerGesture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** Compose name of finger input. */
const val TOUCH_POINTER_TYPE = "Touch"

/** Pointer type of screen input from a device that moves a cursor (see isFromCursorDevice). */
const val CURSOR_POINTER_TYPE = "Cursor"

/** Pointer type of remote buttons learned through captured pointer input. */
const val CAPTURED_POINTER_TYPE = "Captured"

/** Movement shorter than this is a tap (dp for screen input, device units for captured input). */
private const val TAP_THRESHOLD = 16f

/** Max angle between an input's direction and a learned button's direction. */
private const val MAX_ANGLE_DEGREES = 25f

/** Allowed length difference between an input and a learned button. */
private const val MIN_LENGTH_RATIO = 0.5f
private const val MAX_LENGTH_RATIO = 2f

/** Max sideways movement (dp) of a perfectly straight swipe. Fingers practically always exceed this. */
private const val STRAIGHT_TOLERANCE = 0.5f

/** A perfectly straight input matches a straight button once it has moved this far (dp). */
private const val STRAIGHT_MATCH_DISTANCE = 12f

/**
 * While learning from screen input, a swipe must start in this middle part of the screen along its
 * direction (as a fraction of the screen's width or height).
 */
val REMOTE_BUTTON_LEARNING_ZONE = 0.25f..0.75f

/** Kind of input a remote button sends. */
enum class RemoteButtonKind { TAP, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN }

/**
 * One complete input that may come from a remote button.
 *
 * @param button the input's shape, with action [ReaderSwipeAction.NONE].
 * @param startFractionX start position as a fraction of the input area width (0.5 for captured input).
 * @param startFractionY start position as a fraction of the input area height (0.5 for captured input).
 * @param endEdges screen edges the input ended at. A swipe that runs into an edge may have been cut short.
 */
data class RemoteButtonInput(
    val button: LearnedRemoteButton,
    val startFractionX: Float,
    val startFractionY: Float,
    val endEdges: Set<ReaderSwipeDirection>,
)

/** True if this input came from a finger on the touchscreen (or a remote that acts exactly like one). */
val LearnedRemoteButton.isTouch: Boolean
    get() = pointerType == TOUCH_POINTER_TYPE

/** True if this button was learned through captured pointer input. It then works in any orientation. */
val LearnedRemoteButton.isCaptured: Boolean
    get() = pointerType == CAPTURED_POINTER_TYPE

fun CapturedPointerGesture.toRemoteButtonInput() = RemoteButtonInput(
    button = LearnedRemoteButton(
        pointerType = CAPTURED_POINTER_TYPE,
        deltaX = deltaX,
        deltaY = deltaY,
        landscape = false,
        action = ReaderSwipeAction.NONE,
    ),
    startFractionX = 0.5f,
    startFractionY = 0.5f,
    endEdges = emptySet(),
)

/**
 * True if a movement of [deltaX], [deltaY] (dp) whose largest sideways offsets from its start were
 * [maxOffsetX], [maxOffsetY] (dp) went in a perfectly straight horizontal or vertical line.
 */
fun isStraightMovement(deltaX: Float, deltaY: Float, maxOffsetX: Float, maxOffsetY: Float): Boolean {
    val sideways = if (abs(deltaX) >= abs(deltaY)) maxOffsetY else maxOffsetX
    return hypot(deltaX, deltaY) >= TAP_THRESHOLD && sideways <= STRAIGHT_TOLERANCE
}

/**
 * True if this input can be learned reliably: a swipe has to start in the middle part of the screen
 * along its direction, and no input may end at an edge of the screen, which could have cut it short.
 */
val RemoteButtonInput.isReliableForLearning: Boolean
    get() {
        val kind = button.kind()
        if (endEdges.isNotEmpty() && (kind == RemoteButtonKind.TAP || kind.edge() in endEdges)) return false
        return when (kind) {
            RemoteButtonKind.TAP -> true
            RemoteButtonKind.SWIPE_UP, RemoteButtonKind.SWIPE_DOWN -> startFractionY in REMOTE_BUTTON_LEARNING_ZONE
            RemoteButtonKind.SWIPE_LEFT, RemoteButtonKind.SWIPE_RIGHT -> startFractionX in REMOTE_BUTTON_LEARNING_ZONE
        }
    }

fun LearnedRemoteButton.kind(): RemoteButtonKind = kindOf(deltaX, deltaY)

/** True if [other] is the same button: same device, orientation and input shape. */
fun LearnedRemoteButton.isSameButtonAs(other: LearnedRemoteButton): Boolean {
    return pointerType == other.pointerType && landscape == other.landscape && matchesShape(other)
}

/** Finds the learned button that sent the complete [input], or null. */
fun List<LearnedRemoteButton>.findMatch(input: RemoteButtonInput): LearnedRemoteButton? {
    val candidates = filter {
        it.action != ReaderSwipeAction.NONE &&
                it.pointerType == input.button.pointerType &&
                it.landscape == input.button.landscape
    }
    candidates.filter { it.matchesShape(input.button) }
        .minByOrNull { it.angleTo(input.button) }
        ?.let { return it }

    // A remote's cursor can be near the edge of the screen, which cuts its swipes short.
    // Accept a shorter movement in the button's direction that ended at that edge.
    return candidates.filter { it.matchesCutOffInput(input) }.minByOrNull { it.angleTo(input.button) }
}

/**
 * Finds a straight-swiping learned button matching an input that is still in progress: it has moved
 * at least [STRAIGHT_MATCH_DISTANCE] in the button's direction without any sideways movement.
 *
 * Remotes swipe in perfectly straight lines and fingers practically never do, so this recognizes a
 * remote's swipe early and at any length, including swipes cut short by the edge of the screen.
 *
 * @param deltaX movement so far in dp.
 * @param deltaY movement so far in dp.
 * @param maxOffsetX largest horizontal offset from the start so far, in dp.
 * @param maxOffsetY largest vertical offset from the start so far, in dp.
 */
fun List<LearnedRemoteButton>.findStraightMatch(
    pointerType: String,
    landscape: Boolean,
    deltaX: Float,
    deltaY: Float,
    maxOffsetX: Float,
    maxOffsetY: Float,
): LearnedRemoteButton? {
    val horizontal = abs(deltaX) >= abs(deltaY)
    val distance = if (horizontal) abs(deltaX) else abs(deltaY)
    val sideways = if (horizontal) maxOffsetY else maxOffsetX
    if (distance < STRAIGHT_MATCH_DISTANCE || sideways > STRAIGHT_TOLERANCE) return null

    val kind = kindOf(deltaX, deltaY)
    return firstOrNull {
        it.straight &&
                it.action != ReaderSwipeAction.NONE &&
                it.pointerType == pointerType &&
                it.landscape == landscape &&
                it.kind() == kind
    }
}

private fun kindOf(deltaX: Float, deltaY: Float): RemoteButtonKind = when {
    hypot(deltaX, deltaY) < TAP_THRESHOLD -> RemoteButtonKind.TAP
    abs(deltaX) >= abs(deltaY) -> if (deltaX < 0) RemoteButtonKind.SWIPE_LEFT else RemoteButtonKind.SWIPE_RIGHT
    else -> if (deltaY < 0) RemoteButtonKind.SWIPE_UP else RemoteButtonKind.SWIPE_DOWN
}

private fun LearnedRemoteButton.matchesShape(other: LearnedRemoteButton): Boolean {
    val isTap = kind() == RemoteButtonKind.TAP
    val otherIsTap = other.kind() == RemoteButtonKind.TAP
    if (isTap || otherIsTap) return isTap && otherIsTap

    val ratio = other.length() / length()
    if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) return false
    return angleTo(other) <= MAX_ANGLE_DEGREES
}

private fun LearnedRemoteButton.matchesCutOffInput(input: RemoteButtonInput): Boolean {
    val edge = kind().edge() ?: return false
    if (edge !in input.endEdges) return false
    // An input that didn't move can't be told apart from a press in another direction, so it never matches
    val other = input.button
    if (other.kind() == RemoteButtonKind.TAP) return false
    return other.length() <= length() * MAX_LENGTH_RATIO && angleTo(other) <= MAX_ANGLE_DEGREES
}

private fun LearnedRemoteButton.length() = hypot(deltaX, deltaY)

/** Angle in degrees between the movement directions of two inputs (0 if either is a tap). */
private fun LearnedRemoteButton.angleTo(other: LearnedRemoteButton): Float {
    val length = length()
    val otherLength = other.length()
    if (length == 0f || otherLength == 0f) return 0f
    val cos = ((deltaX * other.deltaX + deltaY * other.deltaY) / (length * otherLength)).coerceIn(-1f, 1f)
    return acos(cos) * 180f / PI.toFloat()
}

private fun RemoteButtonKind.edge(): ReaderSwipeDirection? = when (this) {
    RemoteButtonKind.TAP -> null
    RemoteButtonKind.SWIPE_LEFT -> ReaderSwipeDirection.LEFT
    RemoteButtonKind.SWIPE_RIGHT -> ReaderSwipeDirection.RIGHT
    RemoteButtonKind.SWIPE_UP -> ReaderSwipeDirection.UP
    RemoteButtonKind.SWIPE_DOWN -> ReaderSwipeDirection.DOWN
}
