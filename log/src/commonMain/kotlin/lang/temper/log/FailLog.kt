package lang.temper.log

import lang.temper.common.Log
import lang.temper.common.truncateTo

/**
 * Analogous to a call stack, keeps a log of reasons that can be truncated when it turns out an
 * operation succeeded so that the eventual state of the log relates to the reasons for failure.
 */
interface FailLog {
    fun explain(
        messageTemplate: MessageTemplateI,
        pos: Position,
        values: List<Any> = emptyList(),
        pinned: Boolean = false,
    )

    fun explain(problem: LogEntry, pinned: Boolean = false) = explain(
        messageTemplate = problem.template,
        pos = problem.pos,
        values = problem.values,
        pinned = pinned,
    )

    fun fail(
        messageTemplate: MessageTemplateI,
        pos: Position,
        values: List<Any> = emptyList(),
    )

    fun markBeforeRecoverableFailure(): FailMark

    interface FailMark {
        fun rollback()
    }

    val logSink: LogSink

    fun logReasonForFailure(ls: LogSink = logSink)

    companion object {
        operator fun invoke(logSink: LogSink): FailLog = FailLogImpl(logSink = logSink)
    }

    /**
     * A stateless, side effect free fail log implementation that silently drops all explanations.
     */
    object NullFailLog : FailLog {
        override fun explain(
            messageTemplate: MessageTemplateI,
            pos: Position,
            values: List<Any>,
            pinned: Boolean,
        ) = Unit

        override fun fail(
            messageTemplate: MessageTemplateI,
            pos: Position,
            values: List<Any>,
        ) = Unit

        override fun markBeforeRecoverableFailure(): FailMark = NullFailMark

        override val logSink: LogSink get() = LogSink.devNull

        override fun logReasonForFailure(ls: LogSink) = Unit
    }

    private object NullFailMark : FailMark {
        override fun rollback() = Unit
    }
}

private class FailLogImpl(override val logSink: LogSink) : FailLog {
    private val events = mutableListOf<InterpreterEvent>()

    override fun explain(
        messageTemplate: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        pinned: Boolean,
    ) {
        events.add(InterpreterEvent(messageTemplate, pos, values, pinned))
    }

    override fun fail(
        messageTemplate: MessageTemplateI,
        pos: Position,
        values: List<Any>,
    ) {
        explain(messageTemplate = messageTemplate, pinned = true, pos = pos, values = values)
        logSink.log(level = Log.Error, template = messageTemplate, pos = pos, values = values)
    }

    override fun markBeforeRecoverableFailure() = FailMarkImpl()

    inner class FailMarkImpl : FailLog.FailMark {
        private val sizeBefore = events.size

        override fun rollback() {
            var sizeAfterPinned = sizeBefore
            // Coalesce pinned events left
            for (i in sizeBefore until events.size) {
                val e = events[i]
                if (e.pinned) {
                    events[sizeAfterPinned] = e
                    sizeAfterPinned += 1
                }
            }
            // and discard the unpinned ones.
            events.truncateTo(sizeAfterPinned)
        }
    }

    override fun logReasonForFailure(ls: LogSink) {
        for (e in events) {
            ls.log(level = Log.Error, template = e.messageTemplate, pos = e.pos, values = e.values)
        }
    }
}

internal data class InterpreterEvent(
    val messageTemplate: MessageTemplateI,
    val pos: Position,
    val values: List<Any>,
    val pinned: Boolean,
)
