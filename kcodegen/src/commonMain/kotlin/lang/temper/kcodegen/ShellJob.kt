package lang.temper.kcodegen

import lang.temper.kcodegen.TaskStatus.SOURCE_MISSING
import lang.temper.kcodegen.TaskStatus.TARGET_MISSING
import lang.temper.kcodegen.TaskStatus.TARGET_OLD
import lang.temper.kcodegen.TaskStatus.UP_TO_DATE

/** Generally, java.io.File on JVM. The expected contract is that we can convert these to and from Strings. */
interface Path {
    override fun toString(): String

    companion object {
        fun of(subproject: String, vararg path: String): Path = packPartsAsPath(subproject, path.asIterable())
    }
}

class JobReportsFailure(msg: String) : Exception(msg)

/** Some kind of interpreter that can run a job. It will raise an exception if the job doesn't complete. */
interface Interpreter {
    fun run(st: SrcTgt)
}

/**
 * Identifies the task state and, implicitly, the action to take.
 */
enum class TaskStatus {
    /** The task is up to date, no action needs to be taken. */
    UP_TO_DATE,

    /** The target is missing and needs to be regenerated. */
    TARGET_MISSING,

    /** The source is missing and the target should be removed. */
    SOURCE_MISSING,

    /** The target is old and needs to be regenerated. */
    TARGET_OLD,
}

/** Build a path from the subproject root. */
expect fun packPartsAsPath(subproject: String, parts: Iterable<String>): Path

/** Delete the given paths. */
expect fun rm(paths: Iterable<Path>)

/** Ensure a directory exists for the given path. */
expect fun ensureDir(path: Path, isFile: Boolean)

/** Look up an interpreter by name. */
expect fun which(name: String): Interpreter

/**
 * A source-target is roughly the script that will be invoked, the source it's reading from, and the target
 * to generate. We expect that these are self-contained scripts.
 */
class SrcTgt(val script: Path, val source: Path, val target: Path) {
    val sources: Iterable<Path> = listOf(source, script)
    fun status(): TaskStatus {
        return checkOutdated(this)
    }
}

/** Check whether a job is outdated or not based on a last modified check. */
expect fun checkOutdated(st: SrcTgt): TaskStatus

abstract class ShellJob {
    abstract val sourceTargets: Iterable<SrcTgt>

    abstract val interpreter: Interpreter

    fun run() {
        val runs: MutableList<SrcTgt> = mutableListOf()
        val removes: MutableList<Path> = mutableListOf()
        for (st in sourceTargets) {
            when (st.status()) {
                UP_TO_DATE -> {}
                TARGET_MISSING, TARGET_OLD -> runs.add(st)
                SOURCE_MISSING -> removes.add(st.target)
            }
        }
        cleanUp(removes)
        for (st in runs) {
            ensureDir(st.target, isFile = true)
            interpreter.run(st)
        }
    }

    private fun cleanUp(paths: Iterable<Path>) = rm(paths)
}
