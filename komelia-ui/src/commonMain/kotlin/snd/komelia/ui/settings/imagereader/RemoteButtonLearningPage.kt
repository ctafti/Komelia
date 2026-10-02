package snd.komelia.ui.settings.imagereader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.dialog_cancel
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_box_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_choose_action
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_detected
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_direct_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_direct_unavailable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_method_direct
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_method_screen
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_press_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_problem_edge
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_problem_not_rotated
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_rotate_landscape
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_rotate_portrait
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn_skip
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.ReaderSwipeAction
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.CapturedPointerEffect
import snd.komelia.ui.platform.CapturedPointerGesture
import snd.komelia.ui.platform.CapturedPointerInput
import snd.komelia.ui.reader.image.common.REMOTE_BUTTON_LEARNING_ZONE
import snd.komelia.ui.reader.image.common.RemoteButtonInput
import snd.komelia.ui.reader.image.common.kind
import snd.komelia.ui.reader.image.common.rememberRemoteCursorState
import snd.komelia.ui.reader.image.common.remoteButtonLearningInput
import snd.komelia.ui.reader.image.common.trackRemoteCursor
import kotlin.math.roundToInt

/** How a remote button is learned and later recognized. */
private enum class LearningMethod {
    /** From the remote's screen input. Works with most remotes. */
    SCREEN,

    /** Through pointer capture: no cursor and no screen edges, but not supported by every remote. */
    DIRECT,
}

/**
 * Full-screen page for learning a remote button.
 *
 * With the screen method, presses only count when they start inside the center box, so other remote
 * buttons can be used to move the remote's cursor there first. The direct method reads the remote
 * through pointer capture (see [CapturedPointerInput]).
 *
 * Shown as a non-focusable popup rather than a dialog: pointer capture needs the app window to keep focus.
 */
@Composable
internal fun RemoteButtonLearningPage(
    step: RemoteButtonLearningStep,
    onInput: (RemoteButtonInput) -> Unit,
    onCapturedInput: (CapturedPointerGesture) -> Unit,
    onActionChosen: (ReaderSwipeAction) -> Unit,
    onSkipRotation: () -> Unit,
    onCancel: () -> Unit,
) {
    if (step == RemoteButtonLearningStep.Idle) return
    var method by remember { mutableStateOf(LearningMethod.SCREEN) }
    val waiting = step as? RemoteButtonLearningStep.WaitingForPress
    val choosingMethod = waiting != null && waiting.firstPress == null
    val direct = choosingMethod && method == LearningMethod.DIRECT
    CapturedPointerEffect(enabled = direct, onGesture = onCapturedInput)
    val captured = CapturedPointerInput.isCaptured.collectAsState().value
    BackPressHandler(onCancel)

    // Android often hides the remote's cursor (e.g. after the screen is touched), so the page shows its own
    val cursor = rememberRemoteCursorState()
    val screenInput = waiting != null && !direct

    Popup(
        popupPositionProvider = FullScreenPopupPositionProvider,
        properties = PopupProperties(focusable = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // Screen input area. The buttons around it are drawn on top, so tapping them isn't recorded.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .trackRemoteCursor(cursor, enabled = screenInput)
                    .remoteButtonLearningInput(enabled = screenInput, onInput = onInput),
            ) {
                if (screenInput) {
                    val position = cursor.position
                    val width = constraints.maxWidth.toFloat()
                    val height = constraints.maxHeight.toFloat()
                    val cursorInBox = position != null &&
                            position.x / width in REMOTE_BUTTON_LEARNING_ZONE &&
                            position.y / height in REMOTE_BUTTON_LEARNING_ZONE
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(REMOTE_BUTTON_LEARNING_ZONE.endInclusive - REMOTE_BUTTON_LEARNING_ZONE.start)
                            .background(
                                if (cursorInBox) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    )
                    if (position != null) CursorDot(position)
                }
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 480.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (step) {
                        RemoteButtonLearningStep.Idle -> {}
                        is RemoteButtonLearningStep.WaitingForPress -> WaitingForPressText(step, direct, captured)
                        is RemoteButtonLearningStep.ChooseAction -> {
                            val kind = step.press.kind().label()
                            LearningTitle(stringResource(Res.string.settings_image_remote_learn_detected, kind))
                            Text(
                                stringResource(Res.string.settings_image_remote_learn_choose_action),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (choosingMethod) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = method == LearningMethod.SCREEN,
                        onClick = { method = LearningMethod.SCREEN },
                        label = { Text(stringResource(Res.string.settings_image_remote_learn_method_screen)) },
                    )
                    FilterChip(
                        selected = method == LearningMethod.DIRECT,
                        onClick = { method = LearningMethod.DIRECT },
                        label = { Text(stringResource(Res.string.settings_image_remote_learn_method_direct)) },
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (step) {
                    RemoteButtonLearningStep.Idle -> {}
                    is RemoteButtonLearningStep.WaitingForPress ->
                        if (step.firstPress == null) {
                            TextButton(onClick = onCancel) { Text(stringResource(Res.string.dialog_cancel)) }
                        } else {
                            TextButton(onClick = onSkipRotation) {
                                Text(stringResource(Res.string.settings_image_remote_learn_skip))
                            }
                        }

                    is RemoteButtonLearningStep.ChooseAction -> {
                        TextButton(onClick = onCancel) { Text(stringResource(Res.string.dialog_cancel)) }
                        FilledTonalButton(onClick = { onActionChosen(ReaderSwipeAction.PREVIOUS_PAGE) }) {
                            Text(ReaderSwipeAction.PREVIOUS_PAGE.label())
                        }
                        FilledTonalButton(onClick = { onActionChosen(ReaderSwipeAction.NEXT_PAGE) }) {
                            Text(ReaderSwipeAction.NEXT_PAGE.label())
                        }
                    }
                }
            }
        }
    }
}

private val CURSOR_DOT_SIZE = 20.dp

/** Marks where the remote's cursor is. */
@Composable
private fun CursorDot(position: Offset) {
    Box(
        Modifier
            .offset {
                val radius = CURSOR_DOT_SIZE.toPx() / 2
                IntOffset((position.x - radius).roundToInt(), (position.y - radius).roundToInt())
            }
            .size(CURSOR_DOT_SIZE)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
    )
}

private object FullScreenPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ) = IntOffset.Zero
}

@Composable
private fun WaitingForPressText(step: RemoteButtonLearningStep.WaitingForPress, direct: Boolean, captured: Boolean) {
    val firstPress = step.firstPress
    val title = when {
        firstPress == null -> Res.string.settings_image_remote_learn_press_title
        firstPress.landscape -> Res.string.settings_image_remote_learn_rotate_portrait
        else -> Res.string.settings_image_remote_learn_rotate_landscape
    }
    LearningTitle(stringResource(title))

    val body = when {
        !direct -> Res.string.settings_image_remote_learn_box_body
        captured -> Res.string.settings_image_remote_learn_direct_body
        else -> Res.string.settings_image_remote_learn_direct_unavailable
    }
    Text(stringResource(body), textAlign = TextAlign.Center)

    val problem = when (step.problem) {
        RemoteButtonLearningStep.Problem.CURSOR_NEAR_EDGE -> Res.string.settings_image_remote_learn_problem_edge
        RemoteButtonLearningStep.Problem.NOT_ROTATED -> Res.string.settings_image_remote_learn_problem_not_rotated
        null -> null
    }
    if (problem != null && !direct) {
        Text(stringResource(problem), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LearningTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}
