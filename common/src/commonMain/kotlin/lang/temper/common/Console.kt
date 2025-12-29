package lang.temper.common

import lang.temper.common.structure.Structured
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.Synchronized
import kotlin.math.max
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.toDuration

internal expect fun getDiagnosticTrace(description: String?): Any

/** A best effort minimal "at <wherever>" message if possible. */
internal expect fun traceStart(error: Throwable): String?

inline fun <T> (Console).benchmarkIf(cond: Boolean, message: String, f: () -> T): T =
    if (cond) {
        val result: T
        val duration = TimeSource.Monotonic.measureTime {
            result = f()
        }
        if (duration > benchmarkQuietThreshold && this.logs(Log.Fine)) {
            this.warn("$message took $duration")
        }
        result
    } else {
        f()
    }

inline fun (Console).logIf(
    cond: Boolean,
    level: Log.Level = Log.Info,
    message: () -> String,
) {
    if (cond) {
        log(level = level, str = message())
    }
}

inline fun (Console).doIfLogs(level: Log.Level = Log.Info, toRunIfVerbose: (Console) -> Unit) {
    if (logs(level)) {
        toRunIfVerbose(this)
    }
}

/** Mimics bits of the Web Console API. */
@OptIn(ExperimentalContracts::class)
open class Console(
    textOutput: TextOutput,
    logLevel: Log.LevelFilter = Log.Info,
    val snapshotter: Snapshotter? = null,
) {
    private val _textOutput = IndentingTextOutput(textOutput)
    val textOutput: TextOutput get() = _textOutput

    /** Without indenting */
    val rawTextOutput: TextOutput get() = _textOutput.textOutput

    var level: Log.LevelFilter = logLevel
        protected set

    fun setLogLevel(newLevel: Log.LevelFilter) { this.level = newLevel }

    private val _deferredUntilWrite = mutableListOf<() -> Unit>()
    private fun write(level: Log.Level, str: String) {
        val beforeWriting = _deferredUntilWrite.toList()
        _deferredUntilWrite.clear()
        for (f in beforeWriting) { f() }

        val style = level.style
        _textOutput.atomicallyWithLevelAndStyle(level, style) {
            _textOutput.emitLine(str)
        }
    }

    /**
     * Use [group] or a related method instead of calling this directly.
     * Indent following lines until a corresponding call to [carefullyDedent].
     */
    open fun carefullyIndent() {
        _textOutput.indent()
    }

    /**
     * Use [group] or a related method instead of calling this directly.
     * Undoes a previous call to [carefullyIndent]
     */
    open fun carefullyDedent() {
        _textOutput.dedent()
    }

    /**
     * This is public so that it can be called by `inline` methods
     * but is not meant to be used by code outside this source file.
     *
     * Queues [f] to happen before the next writing operation.
     *
     * @return a cancellation function that will dequeue [f].
     */
    fun deferUntilWrite(f: () -> Unit): () -> Unit {
        _deferredUntilWrite.add(f)
        return {
            val i = _deferredUntilWrite.indexOfLast { it === f }
            if (i >= 0) {
                _deferredUntilWrite.removeAt(i)
            }
        }
    }

    /**
     * This is public so that it can be called by `inline` methods
     * but is not meant to be used by code outside this source file.
     *
     * Like [log] but bypasses the [logs] check.
     */
    fun logBypassingLevelCheck(str: String, level: Log.Level) {
        write(level, str)
    }

    fun logs(level: Log.Level): Boolean = level >= this.level

    @Suppress("unused") // Usually uses are not commit.
    fun trace(str: String? = null) {
        if (logs(Log.Warn)) {
            write(Log.Warn, "${ getDiagnosticTrace(str) }")
        }
    }

    fun log(str: String, level: Log.Level = Log.Info) {
        if (logs(level)) {
            write(level, str)
        }
    }
    fun log(level: Log.Level = Log.Info, f: () -> String) {
        if (logs(level)) {
            write(level, f())
        }
    }
    fun logMulti(lines: MultilineOutput, level: Log.Level = Log.Info) {
        if (logs(level)) {
            write(
                level,
                toStringViaBuilder {
                    val lineTexts = mutableListOf<String>()
                    lines.addOutputLines(lineTexts)
                    if (lineTexts.isNotEmpty()) {
                        write(level, lineTexts.joinToString("\n"))
                    }
                },
            )
        }
    }
    fun logMulti(level: Log.Level = Log.Info, f: () -> MultilineOutput) {
        if (logs(level)) {
            write(
                level,
                toStringViaBuilder {
                    val lineTexts = mutableListOf<String>()
                    f().addOutputLines(lineTexts)
                    if (lineTexts.isNotEmpty()) {
                        write(level, lineTexts.joinToString("\n"))
                    }
                },
            )
        }
    }

    fun info(str: String) = log(str, Log.Info)
    fun warn(str: String) = log(str, Log.Warn)
    fun error(str: String) = log(str, Log.Error)

    @Suppress("unused") // For consistency with Web Console API
    fun info(f: () -> String) = log(Log.Info, f)

    @Suppress("unused") // For consistency with Web Console API
    fun warn(f: () -> String) = log(Log.Warn, f)
    fun error(f: () -> String) = log(Log.Error, f)

    // Throwable-formatting functions are open for extensive customization as desired.
    open fun formatError(err: Throwable) = err.stackTraceToString()
    open fun formatMessageError(str: String?, err: Throwable) = run {
        val errText = formatError(err)
        when (str) {
            null -> errText
            // This implementation requires two buffers to get the job done,
            // but we don't expect to log errors often, so it should be ok.
            else -> "$str\n$errText"
        }
    }
    fun error(err: Throwable) = error(null, err)
    fun error(str: String?, err: Throwable) = error { formatMessageError(str, err) }

    fun errorDense(err: Throwable) = error {
        val loc = traceStart(err)?.let { " $it" } ?: ""
        "${err.message ?: "$err"}$loc"
    }

    operator fun invoke(str: String, level: Log.Level) = log(str, level)
    operator fun invoke(level: Log.Level = Log.Info, f: () -> String) = log(level, f)

    @Suppress("unused") // For consistency with Web Console API
    fun <T> echo(x: T, level: Log.Level = Log.Info, f: (x: T) -> String?): T {
        val str = f(x)
        if (str != null) {
            log(str, level)
        }
        return x
    }

    @Suppress("unused") // For consistency with Web Console API
    fun assert(b: Boolean, str: String) {
        if (b) {
            warn(str)
        }
    }

    /**
     * Emits [str] as a header and then executes [f]\() making sure that anything
     * it logs is indented under that header.
     */
    inline fun <T> group(str: String, level: Log.Level = Log.Info, f: () -> T): T {
        contract {
            callsInPlace(f, InvocationKind.EXACTLY_ONCE)
        }
        log(str, level)
        val x: T

        carefullyIndent()
        try {
            x = f()
        } finally {
            carefullyDedent()
        }

        return x
    }

    /**
     * Like [group] but only emits the header if [level] is logged or if [f] writes something.
     *
     * This is handy when we only want to emit a header describing an operation
     * if there are errors or warnings that happen during that operation.
     */
    inline fun <T> groupSoft(str: String, level: Log.Level = Log.Info, f: () -> T): T {
        contract {
            callsInPlace(f, InvocationKind.EXACTLY_ONCE)
        }

        var didIndent = false
        val writeHeaderAndIndent = {
            logBypassingLevelCheck(str, level)
            carefullyIndent()
            didIndent = true
        }
        val cancelDeferral = if (logs(level)) {
            writeHeaderAndIndent()
            null
        } else {
            deferUntilWrite(writeHeaderAndIndent)
        }

        val x: T
        try {
            x = f()
        } finally {
            if (didIndent) {
                carefullyDedent()
            } else {
                cancelDeferral?.invoke()
            }
        }

        return x
    }

    inline fun <T> groupIf(cond: Boolean, str: String, level: Log.Level = Log.Info, f: () -> T): T {
        contract {
            callsInPlace(f, InvocationKind.EXACTLY_ONCE)
        }
        return if (cond) {
            group(str, level, f)
        } else {
            f()
        }
    }

    /**
     * Not part of the Web console API.
     *
     * When refining an intermediate representation, we may want to periodically snapshot the state.
     * This allows the REPL to record processing steps and play them back to the user on demand, but
     * is expensive during normal operation, so this is normally a no-op.
     *
     * A tool like the REPL may register a [Snapshotter] with a [Console], typically one configured
     * via [lang.temper.log.Debug].
     *
     * A [Snapshotter] may assume that [currentState] is not mutated while this call is in progress.
     */
    fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, currentState: IR) {
        snapshotter?.snapshot(key, stepId, currentState)
    }
}

class PrefixingTextOutput(
    private val textOutput: TextOutput,

    /** Typically configured at the top of an app before kicking things off. */
    var emitPrefix: (TextOutput) -> Unit = {},
) : TextOutput() {
    private var freshLine = true

    override val isTtyLike: Boolean
        get() = textOutput.isTtyLike

    override fun emitLineChunk(text: CharSequence) {
        maybeEmitPrefix()
        textOutput.emitLineChunk(text)
    }

    override fun endLine() {
        maybeEmitPrefix()
        textOutput.endLine()
        freshLine = true
    }

    override fun flush() {
        textOutput.flush()
    }

    private fun maybeEmitPrefix() {
        if (freshLine) {
            emitPrefix(textOutput)
            freshLine = false
        }
    }
}

/**
 * Performs a [Console.group] even if the [Console] reference is null but always performs [f]
 * unlike `console?.group` which does not execute the block when the console is null.
 *
 * This allows using a nullable console to enable debugging trace when desired.
 */
inline fun <T> Console?.groupAlwaysRunning(str: String, level: Log.Level = Log.Info, f: () -> T): T =
    if (this != null) {
        this.group(str, level, f)
    } else {
        f()
    }

private const val MILLISECONDS_DIGITS = 3
private val timestampFormatter = DateTimeFormatterBuilder().appendInstant(MILLISECONDS_DIGITS).toFormatter()

fun prefixTimestamp(textOutput: TextOutput) {
    textOutput.emitLineChunk(timestampFormatter.format(Instant.now()))
    textOutput.emitLineChunk(" ")
}

private object SimpleConsoleOutput : TextOutput() {
    // Err on the side of pretty output.
    override val isTtyLike = isatty(1)

    private val line = StringBuilder()

    @Synchronized
    override fun emitLineChunk(text: CharSequence) {
        line.append(text)
    }

    override fun endLine() {
        printErr(consumeLine())
    }

    override fun flush() {
        printErrNoEol(consumeLine())
    }

    @Synchronized
    private fun consumeLine(): String {
        val line = this.line.toString()
        this.line.clear()
        return line
    }
}

val consoleOutput = PrefixingTextOutput(SimpleConsoleOutput)

fun trimLine(line: String, max: Int): String {
    val ellipsis = "..."
    if (max < ellipsis.length) {
        // No more space left.
        return ""
    }
    // Prevent overflow because that makes erasing harder.
    // But only bother with code points if we seem likely over the max.
    return if (line.length <= max) {
        line
    } else {
        // Reserve space for ellipsis at the end. Sometimes this will be pessimistic, but eh.
        val buildMax = max - ellipsis.length + max(0, min(max - line.length, ellipsis.length))
        // This still is ignorant of graphemes, etc., but at least it shouldn't split code points.
        buildString(line.length) {
            for (code in CodePoints(line)) {
                // Be pessimistic still.
                if (length + 2 <= buildMax) {
                    encodeUtf16(code, this)
                } else {
                    append(ellipsis)
                    break
                }
            }
        }
    }
}

/**
 * A [Console] that dumps to stderr with ANSI formatting.
 */
val console = Console(consoleOutput)

private class IndentingTextOutput(val textOutput: TextOutput) : TextOutput() {
    private var indentation: Int = 0
    private var atLineStart = true
    private var style: Style? = null
    private var indent: String? = null

    @Synchronized
    fun indent() {
        indentation += 1
        indent = null
    }

    @Synchronized
    fun dedent() {
        require(indentation > 0)
        indentation -= 1
        indent = null
    }

    @Synchronized // Won't deadlock because we don't have cycles in TextOutput delegation
    override fun atomicallyWithLevelAndStyle(newLevel: Log.LevelFilter, newStyle: Style, f: () -> Unit) {
        textOutput.atomicallyWithLevelAndStyle(newLevel, newStyle, f)
    }

    override fun flush() {
        textOutput.flush()
    }

    override fun endStyle() {
        if (style != null) {
            textOutput.endStyle()
        }
        style = null
    }

    override fun startStyle(style: Style) {
        if (style != this.style) {
            endStyle()
            this.style = style
            if (!atLineStart) {
                textOutput.startStyle(style)
            }
        }
    }

    override val isTtyLike get() = textOutput.isTtyLike

    override fun withLevel(level: Log.LevelFilter, action: () -> Unit) {
        textOutput.withLevel(level, action)
    }

    @Synchronized
    override fun emitLineChunk(text: CharSequence) {
        val n = text.length
        var lineStart = 0
        var i = 0
        while (i < n) {
            val c = text[i]
            i += 1
            if (c == '\n' || c == '\r') {
                val lineEnd = i - 1
                emit(text, lineStart, lineEnd)
                if (style != null) {
                    textOutput.endStyle()
                }
                textOutput.endLine()
                atLineStart = true
                if (c == '\r' && i < n && text[i] == '\n') { // crlf
                    i += 1
                }
                lineStart = i
            }
        }
        emit(text, lineStart, n)
    }

    @Synchronized
    private fun emit(text: CharSequence, left: Int, right: Int) {
        if (left == right) {
            return
        }
        val style = this.style
        if (atLineStart) {
            atLineStart = false
            var indent = this.indent
            if (indent == null) {
                indent = "  ".repeat(indentation)
                this.indent = indent
            }
            if (indent.isNotEmpty()) {
                textOutput.emitLineChunk(indent)
            }
            if (style != null) {
                textOutput.startStyle(style)
            }
        }
        textOutput.emitLineChunk(text.substring(left, right))
    }
}

@Suppress("MagicNumber")
val benchmarkQuietThreshold = 50.toDuration(DurationUnit.MILLISECONDS)

val Log.Level.style: Style get() = when (this) {
    Log.Error, Log.Fatal -> Style.LogErrorOutput
    Log.Warn -> Style.LogWarningOutput
    Log.Info, Log.Fine -> Style.LogInfoOutput
}
