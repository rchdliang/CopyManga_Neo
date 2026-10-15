package com.rchdliang.copymanga_neo.multiplatform.ui.reader

/** Opt-in capability: mobile/browser hosts retain their existing input behavior. */
interface DesktopReaderKeyboard

internal enum class ReaderNavigationKey { LEFT, RIGHT, UP, DOWN, PAGE_UP, PAGE_DOWN, SPACE, OTHER }
internal enum class ReaderKeyboardMovement { PAGE, SMALL_SCROLL, VIEWPORT_SCROLL }
internal data class ReaderKeyboardCommand(val forward: Boolean, val movement: ReaderKeyboardMovement)

/** Logical page order is independent of the physical arrow direction in RTL mode. */
internal fun readerKeyboardCommand(
    key: ReaderNavigationKey,
    direction: ReadingDirection,
    ownsFocus: Boolean,
    blocked: Boolean,
    shift: Boolean = false,
    control: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false
): ReaderKeyboardCommand? {
    if (!ownsFocus || blocked || control || alt || meta || (shift && key != ReaderNavigationKey.SPACE)) return null
    val continuous = direction == ReadingDirection.TOP_TO_BOTTOM
    val forward = when (key) {
        ReaderNavigationKey.LEFT -> if (continuous) return null else direction == ReadingDirection.RIGHT_TO_LEFT
        ReaderNavigationKey.RIGHT -> if (continuous) return null else direction == ReadingDirection.LEFT_TO_RIGHT
        ReaderNavigationKey.UP -> if (continuous) false else return null
        ReaderNavigationKey.DOWN -> if (continuous) true else return null
        ReaderNavigationKey.PAGE_UP -> false
        ReaderNavigationKey.PAGE_DOWN -> true
        ReaderNavigationKey.SPACE -> !shift
        ReaderNavigationKey.OTHER -> return null
    }
    val movement = if (!continuous) ReaderKeyboardMovement.PAGE
        else if (key == ReaderNavigationKey.UP || key == ReaderNavigationKey.DOWN) ReaderKeyboardMovement.SMALL_SCROLL
        else ReaderKeyboardMovement.VIEWPORT_SCROLL
    return ReaderKeyboardCommand(forward, movement)
}
