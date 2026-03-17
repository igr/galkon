package galkon.common

/** UI color constants shared across client and server. */
object Colors {
    const val ACCENT = "#00ff88"
    const val ACCENT_ALPHA = "#00ff8844"
    const val DANGER = "#ff4444"
    const val WARN = "#ffaa00"

    /** Vivid player colors indexed by player position (0-7). */
    val PLAYER = listOf(
        "#00ff88",  // green
        "#ff4444",  // red
        "#ffcc00",  // yellow
        "#ff6600",  // orange
        "#ff00ff",  // magenta
        "#00ffcc",  // cyan
        "#aa44ff",  // purple
        "#ff6688",  // pink
    )
    const val TEXT = "#e0e0e0"
    const val TEXT_MUTED = "#aaa"
    const val TEXT_DIM = "#888"
    const val WHITE = "#ffffff"
    const val BLACK = "#000000"
    const val BG_DARK = "#0a0a2e"
    const val BG_PANEL = "#1a1a4e"
    const val BORDER = "#333"
    const val BORDER_LIGHT = "#555"
    const val GRID_LINE = "#1a1a3e"
    const val NEUTRAL = "#888888"
    const val BG_BATTLE = "#2a0a1a"
}
