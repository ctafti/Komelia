package snd.komelia.ui.settings.imagereader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_details_hide
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_details_show
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_button_action
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_buttons
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_back_gesture
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_matching
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_methods
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_orientation
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_outside
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_purpose
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_details_reserved
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learn
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_none
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_orientation_any
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_orientation_both
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_orientation_landscape
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_orientation_portrait
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_remove
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_remove_all
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_tap
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_action_next_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_action_none
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_action_previous_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_details_epub
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_details_ignored
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_details_readers
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_details_remote_only
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_details_scrolling
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_down
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_left
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_navigation
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_right
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_swipe_up
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.ReaderSwipeAction
import snd.komelia.settings.model.ReaderSwipeActions
import snd.komelia.settings.model.ReaderSwipeDirection
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.reader.image.common.RemoteButtonKind
import snd.komelia.ui.reader.image.common.isCaptured
import snd.komelia.ui.reader.image.common.kind

/** Actions for swipes in the readers (see readerSwipeGestures). */
@Composable
internal fun SwipeNavigationSettings(
    swipeActions: ReaderSwipeActions,
    onSwipeActionsChange: (ReaderSwipeActions) -> Unit,
) {
    val options = ReaderSwipeAction.entries.map { LabeledEntry(it, it.label()) }
    val directions = listOf(
        ReaderSwipeDirection.LEFT to Res.string.settings_image_swipe_left,
        ReaderSwipeDirection.RIGHT to Res.string.settings_image_swipe_right,
        ReaderSwipeDirection.UP to Res.string.settings_image_swipe_up,
        ReaderSwipeDirection.DOWN to Res.string.settings_image_swipe_down,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SectionHeader(
            title = stringResource(Res.string.settings_image_swipe_navigation),
            details = listOf(
                stringResource(Res.string.settings_image_swipe_details_readers),
                stringResource(Res.string.settings_image_swipe_details_scrolling),
                stringResource(Res.string.settings_image_swipe_details_ignored),
                stringResource(Res.string.settings_image_swipe_details_epub),
                stringResource(Res.string.settings_image_swipe_details_remote_only),
            ),
        )
        directions.forEach { (direction, label) ->
            val current = swipeActions.actionFor(direction)
            DropdownChoiceMenu(
                selectedOption = options.firstOrNull { it.value == current },
                options = options,
                onOptionChange = { onSwipeActionsChange(swipeActions.withAction(direction, it.value)) },
                label = { Text(stringResource(label)) },
                inputFieldModifier = Modifier.fillMaxWidth(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Learned buttons of Bluetooth page-turner remotes, and the entry point to learn new ones. */
@Composable
internal fun RemoteButtonSettings(
    swipeActions: ReaderSwipeActions,
    onSwipeActionsChange: (ReaderSwipeActions) -> Unit,
    onLearnClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SectionHeader(
            title = stringResource(Res.string.settings_image_remote_buttons),
            details = listOf(
                stringResource(Res.string.settings_image_remote_details_purpose),
                stringResource(Res.string.settings_image_remote_details_methods),
                stringResource(Res.string.settings_image_remote_details_matching),
                stringResource(Res.string.settings_image_remote_details_orientation),
                stringResource(Res.string.settings_image_remote_details_back_gesture),
                stringResource(Res.string.settings_image_remote_details_reserved),
                stringResource(Res.string.settings_image_remote_details_outside),
            ),
        )

        // One row per physical button, covering every orientation it was learned in
        val buttons = swipeActions.learnedRemoteButtons.groupBy { Triple(it.pointerType, it.kind(), it.action) }
        if (buttons.isEmpty()) {
            Text(stringResource(Res.string.settings_image_remote_none), style = MaterialTheme.typography.bodyMedium)
        }
        buttons.forEach { (key, entries) ->
            val (_, kind, action) = key
            val hasPortrait = entries.any { !it.landscape }
            val hasLandscape = entries.any { it.landscape }
            val orientations = when {
                entries.any { it.isCaptured } -> Res.string.settings_image_remote_orientation_any
                hasPortrait && hasLandscape -> Res.string.settings_image_remote_orientation_both
                hasLandscape -> Res.string.settings_image_remote_orientation_landscape
                else -> Res.string.settings_image_remote_orientation_portrait
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_image_remote_button_action, kind.label(), action.label()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(orientations), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    onSwipeActionsChange(
                        swipeActions.copy(learnedRemoteButtons = swipeActions.learnedRemoteButtons - entries.toSet())
                    )
                }) { Text(stringResource(Res.string.settings_image_remote_remove)) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onLearnClick) { Text(stringResource(Res.string.settings_image_remote_learn)) }
            if (buttons.isNotEmpty()) {
                TextButton(onClick = { onSwipeActionsChange(swipeActions.copy(learnedRemoteButtons = emptyList())) }) {
                    Text(stringResource(Res.string.settings_image_remote_remove_all))
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
}

/** Section title with a toggle that shows where the feature works and its limits. */
@Composable
private fun SectionHeader(title: String, details: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) Res.string.settings_image_details_hide else Res.string.settings_image_details_show
                    )
                )
            }
        }
        if (expanded) {
            details.forEach { detail ->
                Text(
                    "\u2022 $detail",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                )
            }
        }
    }
}


@Composable
internal fun ReaderSwipeAction.label(): String = stringResource(
    when (this) {
        ReaderSwipeAction.NONE -> Res.string.settings_image_swipe_action_none
        ReaderSwipeAction.NEXT_PAGE -> Res.string.settings_image_swipe_action_next_page
        ReaderSwipeAction.PREVIOUS_PAGE -> Res.string.settings_image_swipe_action_previous_page
    }
)

@Composable
internal fun RemoteButtonKind.label(): String = stringResource(
    when (this) {
        RemoteButtonKind.TAP -> Res.string.settings_image_remote_tap
        RemoteButtonKind.SWIPE_LEFT -> Res.string.settings_image_swipe_left
        RemoteButtonKind.SWIPE_RIGHT -> Res.string.settings_image_swipe_right
        RemoteButtonKind.SWIPE_UP -> Res.string.settings_image_swipe_up
        RemoteButtonKind.SWIPE_DOWN -> Res.string.settings_image_swipe_down
    }
)
