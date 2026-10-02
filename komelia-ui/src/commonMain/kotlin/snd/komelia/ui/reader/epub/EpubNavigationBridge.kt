package snd.komelia.ui.reader.epub

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import snd.komelia.settings.model.ReaderSwipeAction
import snd.komelia.ui.reader.image.common.ReaderScrollAxis
import snd.webview.KomeliaWebview

/**
 * Connects an EPUB web reader to Komelia's swipe navigation and learned remote buttons.
 *
 * Functions bound in the web view (used by `komelia-navigation.ts` in each web reader):
 * - `komeliaAwaitPageTurn()` waits until Komelia requests a page turn and resolves with "next" or
 *   "previous". It resolves with "closed" when the reader closes or the web view is recreated
 *   (e.g. after rotating), so the old page stops waiting.
 * - `komeliaSetScrollAxis(axis)` reports the reader's scroll direction ("vertical", "horizontal" or
 *   "none"), so swipes along it are left to the reader.
 */
class EpubNavigationBridge {
    private var pageTurns = newChannel()
    private val mutableScrollAxis = MutableStateFlow<ReaderScrollAxis?>(null)
    val scrollAxis: StateFlow<ReaderScrollAxis?> = mutableScrollAxis.asStateFlow()

    suspend fun bindTo(webview: KomeliaWebview) {
        pageTurns.close()
        pageTurns = newChannel()
        mutableScrollAxis.value = null

        webview.bind<Unit, String>("komeliaAwaitPageTurn") {
            val channel = pageTurns
            channel.receiveCatching().getOrNull() ?: CLOSED
        }
        webview.bind<String, Unit>("komeliaSetScrollAxis") { axis ->
            mutableScrollAxis.value = when (axis) {
                "vertical" -> ReaderScrollAxis.VERTICAL
                "horizontal" -> ReaderScrollAxis.HORIZONTAL
                else -> null
            }
        }
    }

    fun turnPage(action: ReaderSwipeAction) {
        when (action) {
            ReaderSwipeAction.NONE -> {}
            ReaderSwipeAction.NEXT_PAGE -> pageTurns.trySend(NEXT)
            ReaderSwipeAction.PREVIOUS_PAGE -> pageTurns.trySend(PREVIOUS)
        }
    }

    fun close() {
        pageTurns.close()
    }

    private fun newChannel() = Channel<String>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val CLOSED = "closed"
    }
}
