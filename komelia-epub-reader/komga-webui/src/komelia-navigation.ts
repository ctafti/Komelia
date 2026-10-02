/**
 * Connects this reader to Komelia's swipe navigation and remote buttons.
 *
 * Komelia binds two functions on `window` (see EpubNavigationBridge.kt):
 * - `komeliaAwaitPageTurn()` resolves with "next" or "previous" when Komelia wants to turn the page,
 *   or "closed" when this page should stop listening.
 * - `komeliaSetScrollAxis(axis)` tells Komelia which way the reader scrolls, so swipes in that
 *   direction keep scrolling instead of turning pages.
 */

export interface KomeliaPageTurner {
    nextPage(): void

    previousPage(): void
}

export type KomeliaScrollAxis = 'vertical' | 'horizontal' | 'none'

/**
 * Turns pages whenever Komelia asks. Returns a function that stops listening.
 */
export function startKomeliaPageTurns(getPageTurner: () => KomeliaPageTurner | undefined): () => void {
    let active = true
    const listen = async () => {
        const w = window as any
        while (active) {
            if (typeof w.komeliaAwaitPageTurn !== 'function') {
                await delay(500)
                continue
            }
            let action: string | undefined
            try {
                action = (await w.komeliaAwaitPageTurn())?.result
            } catch (e) {
                return
            }
            if (!active || action === 'closed') return
            const pageTurner = getPageTurner()
            if (action === 'next') pageTurner?.nextPage()
            else if (action === 'previous') pageTurner?.previousPage()
        }
    }
    listen()
    return () => {
        active = false
    }
}

let latestScrollAxis: KomeliaScrollAxis = 'none'
let scrollAxisReportPending = false

/**
 * Tells Komelia which way the reader scrolls. Retries until Komelia's functions are available.
 */
export function reportKomeliaScrollAxis(axis: KomeliaScrollAxis) {
    latestScrollAxis = axis
    const w = window as any
    if (typeof w.komeliaSetScrollAxis === 'function') {
        Promise.resolve(w.komeliaSetScrollAxis(axis)).catch(() => {})
        return
    }
    if (scrollAxisReportPending) return
    scrollAxisReportPending = true
    setTimeout(() => {
        scrollAxisReportPending = false
        reportKomeliaScrollAxis(latestScrollAxis)
    }, 500)
}

function delay(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms))
}
