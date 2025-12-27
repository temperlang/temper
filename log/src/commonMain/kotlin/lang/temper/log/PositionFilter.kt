package lang.temper.log

import lang.temper.common.Log

/**
 * Tracks ranges to retain only unique positions or higher log levels.
 */
class PositionFilter {
    fun allow(level: Log.Level, pos: Position): Boolean {
        val currentLevel = levels[pos]
        val result = currentLevel == null || level > currentLevel
        if (result) {
            levels[pos] = level
        }
        return result
    }

    private val levels = mutableMapOf<Position, Log.Level>()

    fun reset() {
        levels.clear()
    }
}
