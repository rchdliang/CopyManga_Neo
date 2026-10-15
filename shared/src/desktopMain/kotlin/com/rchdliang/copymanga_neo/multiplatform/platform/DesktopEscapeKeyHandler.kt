package com.rchdliang.copymanga_neo.multiplatform.platform

enum class DesktopEscapeEvent { DOWN, UP, OTHER }

/** Child-first fallback: an orphan release (for example after a popup closes) never navigates. */
class DesktopEscapeKeyHandler {
    private var pressed = false
    private var owned = false
    private var freshDown = false
    private var releasedOwned = false

    /** Observe before children, without consuming or navigating. A consumed down still starts a press. */
    fun observe(event: DesktopEscapeEvent) {
        when (event) {
            DesktopEscapeEvent.DOWN -> { freshDown = !pressed; pressed = true; releasedOwned = false }
            DesktopEscapeEvent.UP -> {
                releasedOwned = owned
                pressed = false; owned = false; freshDown = false
            }
            DesktopEscapeEvent.OTHER -> Unit
        }
    }

    /** Called only if the focused child did not consume this event. */
    fun handle(event: DesktopEscapeEvent, modified: Boolean, dispatchBack: () -> Boolean): Boolean {
        if (event == DesktopEscapeEvent.UP) {
            val consumed = releasedOwned
            releasedOwned = false
            return consumed
        }
        if (event != DesktopEscapeEvent.DOWN) return false
        if (!freshDown) return owned
        freshDown = false
        owned = !modified && dispatchBack()
        return owned
    }

    fun reset() { pressed = false; owned = false; freshDown = false; releasedOwned = false }
}
