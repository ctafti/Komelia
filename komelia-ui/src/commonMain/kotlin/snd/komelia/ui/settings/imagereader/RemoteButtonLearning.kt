package snd.komelia.ui.settings.imagereader

import kotlinx.coroutines.flow.MutableStateFlow
import snd.komelia.settings.model.LearnedRemoteButton

/**
 * Steps of learning a remote button: press it and choose its action.
 *
 * Buttons learned from screen input are then pressed once more after rotating the screen. Buttons
 * learned through pointer capture work in any orientation.
 */
sealed interface RemoteButtonLearningStep {
    data object Idle : RemoteButtonLearningStep

    /**
     * Waiting for a button press.
     *
     * @param firstPress the press already saved in the other orientation, or null for the first press.
     * @param problem why the last press wasn't used.
     */
    data class WaitingForPress(
        val firstPress: LearnedRemoteButton? = null,
        val problem: Problem? = null,
    ) : RemoteButtonLearningStep

    /**
     * First press recorded; waiting for the user to choose what the button does.
     *
     * @param needsRotatedPress whether the button must also be pressed in the other orientation.
     */
    data class ChooseAction(
        val press: LearnedRemoteButton,
        val needsRotatedPress: Boolean,
    ) : RemoteButtonLearningStep

    enum class Problem {
        /** The press started too close to the edge of the screen, or ran into it. */
        CURSOR_NEAR_EDGE,

        /** Second press arrived before the screen was rotated. */
        NOT_ROTATED,
    }
}

/**
 * Current learning step. Kept for the app process instead of the screen model because on Android
 * rotating the device (part of the flow) recreates the activity.
 */
internal object RemoteButtonLearningSession {
    val step = MutableStateFlow<RemoteButtonLearningStep>(RemoteButtonLearningStep.Idle)
}
