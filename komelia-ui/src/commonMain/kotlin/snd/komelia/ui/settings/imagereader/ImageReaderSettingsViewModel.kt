package snd.komelia.ui.settings.imagereader

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learned
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_remote_learned_one_orientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.availableReduceKernels
import snd.komelia.image.availableUpsamplingModes
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.LearnedRemoteButton
import snd.komelia.settings.model.ReaderSwipeAction
import snd.komelia.settings.model.ReaderSwipeActions
import snd.komelia.ui.platform.CapturedPointerGesture
import snd.komelia.ui.reader.image.common.RemoteButtonInput
import snd.komelia.ui.reader.image.common.isReliableForLearning
import snd.komelia.ui.reader.image.common.isSameButtonAs
import snd.komelia.ui.reader.image.common.toRemoteButtonInput
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState
import snd.komelia.updates.OnnxModelDownloader
import snd.komelia.updates.OnnxRuntimeInstaller

class ImageReaderSettingsViewModel(
    private val settingsRepository: ImageReaderSettingsRepository,
    private val appNotifications: AppNotifications,
    private val onnxRuntimeInstaller: OnnxRuntimeInstaller?,
    private val onnxRuntime: OnnxRuntime?,
    private val upscaler: KomeliaUpscaler?,
    private val panelDetector: KomeliaPanelDetector?,
    private val onnxModelDownloader: OnnxModelDownloader?,
    private val coilMemoryCache: MemoryCache?,
    private val coilDiskCache: DiskCache?,
    private val readerDiskCache: DiskCache?,
) : ScreenModel {

    val onnxRuntimeSettingsState = OnnxRuntimeSettingsState(
        onnxRuntimeInstaller = onnxRuntimeInstaller,
        onnxModelDownloader = onnxModelDownloader,

        onnxRuntime = onnxRuntime,
        panelDetector = panelDetector,
        upscaler = upscaler,

        settingsRepository = settingsRepository,
        coroutineScope = screenModelScope
    )

    val upsamplingMode = MutableStateFlow(UpsamplingMode.NEAREST)
    val downsamplingKernel = MutableStateFlow(ReduceKernel.NEAREST)
    val linearLightDownsampling = MutableStateFlow(false)
    val loadThumbnailsPreview = MutableStateFlow(false)
    val volumeKeysNavigation = MutableStateFlow(false)
    val swipeActions = MutableStateFlow(ReaderSwipeActions())
    val remoteButtonLearningStep: StateFlow<RemoteButtonLearningStep> = RemoteButtonLearningSession.step
    val availableUpsamplingModes = availableUpsamplingModes()
    val availableDownsamplingKernels = availableReduceKernels()


    suspend fun initialize() {

        upsamplingMode.value = settingsRepository.getUpsamplingMode().first()
        downsamplingKernel.value = settingsRepository.getDownsamplingKernel().first()
        linearLightDownsampling.value = settingsRepository.getLinearLightDownsampling().first()
        loadThumbnailsPreview.value = settingsRepository.getLoadThumbnailPreviews().first()
        volumeKeysNavigation.value = settingsRepository.getVolumeKeysNavigation().first()
        swipeActions.value = settingsRepository.getSwipeActions().first()
        onnxRuntimeSettingsState.initialize()
    }

    fun onUpsamplingModeChange(mode: UpsamplingMode) {
        upsamplingMode.value = mode
        screenModelScope.launch { settingsRepository.putUpsamplingMode(mode) }
    }

    fun onDownsamplingKernelChange(kernel: ReduceKernel) {
        downsamplingKernel.value = kernel
        screenModelScope.launch { settingsRepository.putDownsamplingKernel(kernel) }
    }

    fun onLinearLightDownsamplingChange(linear: Boolean) {
        linearLightDownsampling.value = linear
        screenModelScope.launch { settingsRepository.putLinearLightDownsampling(linear) }
    }

    fun onLoadThumbnailsPreviewChange(load: Boolean) {
        loadThumbnailsPreview.value = load
        screenModelScope.launch { settingsRepository.putLoadThumbnailPreviews(load) }
    }

    fun onVolumeKeysNavigationChange(enable: Boolean) {
        volumeKeysNavigation.value = enable
        screenModelScope.launch { settingsRepository.putVolumeKeysNavigation(enable) }
    }

    fun onSwipeActionsChange(actions: ReaderSwipeActions) {
        swipeActions.value = actions
        screenModelScope.launch { settingsRepository.putSwipeActions(actions) }
    }

    private var learningStep: RemoteButtonLearningStep
        get() = RemoteButtonLearningSession.step.value
        set(value) {
            RemoteButtonLearningSession.step.value = value
        }

    fun startRemoteButtonLearning() {
        learningStep = RemoteButtonLearningStep.WaitingForPress()
    }

    fun cancelRemoteButtonLearning() {
        learningStep = RemoteButtonLearningStep.Idle
    }

    /** A press of the remote button being learned, from screen input. Learned for each orientation. */
    fun onRemoteButtonInput(input: RemoteButtonInput) {
        val current = learningStep as? RemoteButtonLearningStep.WaitingForPress ?: return
        val firstPress = current.firstPress
        learningStep = when {
            firstPress != null && input.button.landscape == firstPress.landscape ->
                current.copy(problem = RemoteButtonLearningStep.Problem.NOT_ROTATED)

            !input.isReliableForLearning -> current.copy(problem = RemoteButtonLearningStep.Problem.CURSOR_NEAR_EDGE)
            firstPress == null -> RemoteButtonLearningStep.ChooseAction(input.button, needsRotatedPress = true)
            else -> {
                saveRemoteButton(input.button.copy(action = firstPress.action))
                notify(Res.string.settings_image_remote_learned)
                RemoteButtonLearningStep.Idle
            }
        }
    }

    /** A press of the remote button being learned, through pointer capture. Works in any orientation. */
    fun onRemoteButtonCapturedInput(gesture: CapturedPointerGesture) {
        val current = learningStep as? RemoteButtonLearningStep.WaitingForPress ?: return
        if (current.firstPress != null) return
        learningStep = RemoteButtonLearningStep.ChooseAction(
            press = gesture.toRemoteButtonInput().button,
            needsRotatedPress = false,
        )
    }

    /**
     * Saves the press with [action] right away. Buttons learned from screen input then wait for a
     * press in the other orientation.
     */
    fun onRemoteButtonActionChosen(action: ReaderSwipeAction) {
        val current = learningStep as? RemoteButtonLearningStep.ChooseAction ?: return
        val button = current.press.copy(action = action)
        saveRemoteButton(button)
        learningStep = if (current.needsRotatedPress) {
            RemoteButtonLearningStep.WaitingForPress(firstPress = button)
        } else {
            notify(Res.string.settings_image_remote_learned)
            RemoteButtonLearningStep.Idle
        }
    }

    fun skipRotatedRemoteButtonPress() {
        learningStep = RemoteButtonLearningStep.Idle
        notify(Res.string.settings_image_remote_learned_one_orientation)
    }

    /** Adds [button], replacing an earlier copy of the same button. */
    private fun saveRemoteButton(button: LearnedRemoteButton) {
        val current = swipeActions.value
        val buttons = current.learnedRemoteButtons.filterNot { it.isSameButtonAs(button) } + button
        onSwipeActionsChange(current.copy(learnedRemoteButtons = buttons))
    }

    private fun notify(message: StringResource) {
        screenModelScope.launch { appNotifications.add(AppNotification.Success(getString(message))) }
    }

    fun onClearImageCache() {
        clearImageCache()
        appNotifications.add(AppNotification.Success("Cleared image cache"))
    }

    private fun clearImageCache() {
        coilMemoryCache?.clear()
        coilDiskCache?.clear()
        readerDiskCache?.clear()
        upscaler?.clearCache()
    }
}