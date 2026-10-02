package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.LearnedRemoteButton
import snd.komelia.settings.model.ReaderSwipeAction
import snd.komelia.settings.model.ReaderSwipeActions
import snd.komelia.settings.model.ReaderSwipeDirection
import snd.komelia.ui.platform.CapturedPointerEffect
import snd.komelia.ui.platform.isFromCursorDevice
import snd.komelia.ui.reader.image.ScreenScaleState
import kotlin.math.abs
import kotlin.math.max

/** Swipes slower than this are treated as deliberate drags. */
private const val MAX_SWIPE_DURATION_MS = 1000L

/** Minimum travel for a swipe. */
private const val MIN_SWIPE_DISTANCE_DP = 40

/** The main axis of a swipe must be at least this many times longer than the other axis. */
private const val DIRECTION_RATIO = 1.5f

/** A swipe that moved a zoomed page by more than this fraction of its length was panning, not swiping. */
private const val MAX_PAN_FRACTION = 0.25f

/** Inputs ending this close to the edge of the screen may have been cut short by it. */
private const val EDGE_MARGIN_DP = 16

/** Axis a reader scrolls along. Swipes along it are left to the reader, so they keep scrolling. */
enum class ReaderScrollAxis { VERTICAL, HORIZONTAL }

/**
 * Turns swipes and learned remote buttons into reader actions.
 *
 * Finger input is only observed, so tap zones, panning, scrolling and zooming keep working. The final
 * "up" event is consumed when an action runs, so the reader doesn't also handle it as a tap or its own swipe.
 *
 * Remote buttons learned from screen input are matched against complete inputs from the same kind of
 * device. Buttons that swipe in a perfectly straight line already match while the swipe is in
 * progress. Input from a device with learned buttons is reserved: nothing else in the reader sees it,
 * and it only performs learned actions. Fingers are never reserved.
 *
 * Remote buttons learned through pointer capture are matched against captured presses (see
 * [snd.komelia.ui.platform.CapturedPointerInput]). Capture stays on while [enabled] is false, so the
 * remote's cursor doesn't reappear when e.g. the reader menu opens; presses are ignored then.
 *
 * @param screenScaleState when set, swipes that zoom or pan the page are ignored.
 * @param scrollAxis when set, swipes along this axis are ignored so the reader keeps scrolling.
 */
fun Modifier.readerSwipeGestures(
    swipeActions: ReaderSwipeActions,
    onAction: (ReaderSwipeAction) -> Unit,
    enabled: Boolean = true,
    screenScaleState: ScreenScaleState? = null,
    scrollAxis: ReaderScrollAxis? = null,
): Modifier = composed {
    val currentSwipeActions by rememberUpdatedState(swipeActions)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentScrollAxis by rememberUpdatedState(scrollAxis)

    CapturedPointerEffect(enabled = swipeActions.learnedRemoteButtons.any { it.isCaptured }) { gesture ->
        if (!currentEnabled) return@CapturedPointerEffect
        val button = currentSwipeActions.learnedRemoteButtons.findMatch(gesture.toRemoteButtonInput())
        if (button != null) currentOnAction(button.action)
    }
    if (!enabled || !swipeActions.isEnabled) return@composed Modifier

    Modifier.pointerInput(screenScaleState) {
        val minSwipeDistancePx = MIN_SWIPE_DISTANCE_DP.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerType = pointerTypeOf(down)
            val buttons = currentSwipeActions.learnedRemoteButtons.filter {
                it.action != ReaderSwipeAction.NONE && it.pointerType == pointerType
            }
            // Finger input is never reserved
            val reserved = pointerType != TOUCH_POINTER_TYPE && buttons.isNotEmpty()
            val landscape = size.width > size.height
            val startTransform = screenScaleState?.transformation?.value

            val straightButtons = buttons.filter { it.straight }

            val gesture = trackGesture(down, pointerType, reserve = reserved) { progress ->
                if (straightButtons.isEmpty()) return@trackGesture false
                val button = straightButtons.findStraightMatch(
                    pointerType = pointerType,
                    landscape = landscape,
                    deltaX = progress.delta.x.toDp().value,
                    deltaY = progress.delta.y.toDp().value,
                    maxOffsetX = progress.maxOffset.x.toDp().value,
                    maxOffsetY = progress.maxOffset.y.toDp().value,
                ) ?: return@trackGesture false
                currentOnAction(button.action)
                true
            } ?: return@awaitEachGesture
            if (gesture.claimed) return@awaitEachGesture

            if (buttons.isNotEmpty()) {
                val button = buttons.findMatch(toRemoteButtonInput(gesture))
                if (button != null) {
                    gesture.consumeUp()
                    currentOnAction(button.action)
                    return@awaitEachGesture
                }
            }
            val actions = currentSwipeActions
            if (reserved || !actions.swipesEnabled) return@awaitEachGesture
            if (gesture.durationMillis > MAX_SWIPE_DURATION_MS) return@awaitEachGesture

            val delta = gesture.endPosition - down.position
            val direction = swipeDirection(delta, minSwipeDistancePx) ?: return@awaitEachGesture
            if (currentScrollAxis == direction.axis()) return@awaitEachGesture

            if (screenScaleState != null && startTransform != null) {
                val endTransform = screenScaleState.transformation.value
                if (endTransform.scale != startTransform.scale) return@awaitEachGesture
                val panned = (endTransform.offset - startTransform.offset).getDistance()
                if (panned > delta.getDistance() * MAX_PAN_FRACTION) return@awaitEachGesture
            }

            val action = actions.actionFor(direction)
            if (action != ReaderSwipeAction.NONE) {
                gesture.consumeUp()
                currentOnAction(action)
            }
        }
    }
}

/**
 * Reports every complete screen input in this area, for learning remote buttons. All input is consumed.
 */
fun Modifier.remoteButtonLearningInput(
    enabled: Boolean,
    onInput: (RemoteButtonInput) -> Unit,
): Modifier = composed {
    val currentOnInput by rememberUpdatedState(onInput)
    if (!enabled) return@composed Modifier

    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val gesture = trackGesture(down, pointerTypeOf(down), reserve = true) ?: return@awaitEachGesture
            currentOnInput(toRemoteButtonInput(gesture))
        }
    }
}

private fun ReaderSwipeDirection.axis(): ReaderScrollAxis = when (this) {
    ReaderSwipeDirection.LEFT, ReaderSwipeDirection.RIGHT -> ReaderScrollAxis.HORIZONTAL
    ReaderSwipeDirection.UP, ReaderSwipeDirection.DOWN -> ReaderScrollAxis.VERTICAL
}

private fun swipeDirection(delta: Offset, minDistancePx: Float): ReaderSwipeDirection? {
    val absX = abs(delta.x)
    val absY = abs(delta.y)
    return when {
        absX >= minDistancePx && absX >= absY * DIRECTION_RATIO ->
            if (delta.x < 0) ReaderSwipeDirection.LEFT else ReaderSwipeDirection.RIGHT

        absY >= minDistancePx && absY >= absX * DIRECTION_RATIO ->
            if (delta.y < 0) ReaderSwipeDirection.UP else ReaderSwipeDirection.DOWN

        else -> null
    }
}

/**
 * Pointer type of the gesture that [down] starts: Compose's pointer type, or [CURSOR_POINTER_TYPE] for
 * devices that move a cursor. Must be called right after the down event.
 */
private fun AwaitPointerEventScope.pointerTypeOf(down: PointerInputChange): String {
    return if (currentEvent.isFromCursorDevice()) CURSOR_POINTER_TYPE else down.type.toString()
}

/**
 * State of a gesture in progress, in px.
 *
 * @param delta movement from the start.
 * @param maxOffset largest horizontal and vertical distance from the start so far.
 */
private class GestureProgress(val delta: Offset, val maxOffset: Offset)

private class TrackedGesture(
    val down: PointerInputChange,
    val pointerType: String,
    val endPosition: Offset,
    val maxOffset: Offset,
    val durationMillis: Long,
    /** An action already ran while the gesture was in progress; the rest of it was consumed. */
    val claimed: Boolean,
    private val upEvent: PointerEvent,
) {
    /** Stops other handlers from treating the end of this gesture as a tap or swipe. */
    fun consumeUp() = upEvent.changes.forEach { it.consume() }
}

/**
 * Follows a gesture from [down] until all pointers are up. Returns null for multi-touch gestures.
 *
 * With [reserve], every event is consumed so nothing else handles the gesture. [onProgress] is called
 * for every movement until it returns true, which claims the gesture: its remaining events are consumed.
 */
private suspend fun AwaitPointerEventScope.trackGesture(
    down: PointerInputChange,
    pointerType: String,
    reserve: Boolean,
    onProgress: ((GestureProgress) -> Boolean)? = null,
): TrackedGesture? {
    if (reserve) down.consume()
    var endPosition = down.position
    var endTime = down.uptimeMillis
    var maxOffset = Offset.Zero
    var multiTouch = false
    var claimed = false

    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (reserve || claimed) event.changes.forEach { it.consume() }
        if (event.changes.count { it.pressed } > 1) multiTouch = true

        event.changes.firstOrNull { it.id == down.id }?.let {
            endPosition = it.position
            endTime = it.uptimeMillis
            val offset = it.position - down.position
            maxOffset = Offset(max(maxOffset.x, abs(offset.x)), max(maxOffset.y, abs(offset.y)))
            if (!claimed && !multiTouch && onProgress != null && onProgress(GestureProgress(offset, maxOffset))) {
                claimed = true
                event.changes.forEach { change -> change.consume() }
            }
        }
        if (event.changes.none { it.pressed }) {
            return if (multiTouch && !claimed) null
            else TrackedGesture(
                down = down,
                pointerType = pointerType,
                endPosition = endPosition,
                maxOffset = maxOffset,
                durationMillis = endTime - down.uptimeMillis,
                claimed = claimed,
                upEvent = event,
            )
        }
    }
}

private fun AwaitPointerEventScope.toRemoteButtonInput(gesture: TrackedGesture): RemoteButtonInput {
    val width = size.width.toFloat().coerceAtLeast(1f)
    val height = size.height.toFloat().coerceAtLeast(1f)
    val start = gesture.down.position
    val end = gesture.endPosition
    val deltaX = (end.x - start.x).toDp().value
    val deltaY = (end.y - start.y).toDp().value
    val margin = EDGE_MARGIN_DP.dp.toPx()
    val endEdges = buildSet {
        if (end.x <= margin) add(ReaderSwipeDirection.LEFT)
        if (end.x >= width - margin) add(ReaderSwipeDirection.RIGHT)
        if (end.y <= margin) add(ReaderSwipeDirection.UP)
        if (end.y >= height - margin) add(ReaderSwipeDirection.DOWN)
    }
    return RemoteButtonInput(
        button = LearnedRemoteButton(
            pointerType = gesture.pointerType,
            deltaX = deltaX,
            deltaY = deltaY,
            landscape = width > height,
            action = ReaderSwipeAction.NONE,
            straight = isStraightMovement(
                deltaX = deltaX,
                deltaY = deltaY,
                maxOffsetX = gesture.maxOffset.x.toDp().value,
                maxOffsetY = gesture.maxOffset.y.toDp().value,
            ),
        ),
        startFractionX = start.x / width,
        startFractionY = start.y / height,
        endEdges = endEdges,
    )
}
