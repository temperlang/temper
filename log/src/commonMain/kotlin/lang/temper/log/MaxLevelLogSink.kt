package lang.temper.log

import lang.temper.common.Log

/** For adding to a [TeeLogSink] for level tracking purposes. */
class MaxLevelLogSink : AbstractMaxLevelLogSink() {
    override fun doLog(level: Log.Level, template: MessageTemplateI, pos: Position, values: List<Any>, fyi: Boolean) {
        // No logging. Just tracking in the superclass.
    }
}

abstract class AbstractMaxLevelLogSink : LogSink {
    var maxLevel = Log.levels.first()
        private set

    override val hasFatal get() = maxLevel >= Log.Fatal

    override fun log(level: Log.Level, template: MessageTemplateI, pos: Position, values: List<Any>, fyi: Boolean) {
        if (level > maxLevel) {
            maxLevel = level
        }
        doLog(level, template, pos, values, fyi = fyi)
    }

    protected abstract fun doLog(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    )
}
