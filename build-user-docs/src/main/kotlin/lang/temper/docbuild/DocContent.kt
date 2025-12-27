package lang.temper.docbuild

import lang.temper.common.ContentHash
import lang.temper.common.WrappedByteArray
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder

/**
 * Content of a documentation related file or snippet.
 */
sealed class DocContent : Structured {
    abstract fun hash(): ContentHash

    companion object {
        const val ALGORITHM_NAME = "SHA-256"
    }
}

data class TextDocContent(
    val text: String,
) : DocContent() {
    override fun hash(): ContentHash =
        ContentHash.fromChars(ALGORITHM_NAME, "text:$text").result!!

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("type", Hints.u) { value("TextDocContent") }
        key("text", Hints.s) { value(text) }
    }
}

data class ByteDocContent(
    val bytes: WrappedByteArray,
) : DocContent() {
    override fun hash(): ContentHash {
        val prefix = "bytes:".toByteArray()
        val bytesToHash = ByteArray(prefix.size + bytes.size)
        prefix.copyInto(bytesToHash)
        this.bytes.copyInto(
            destination = bytesToHash,
            destinationOffset = prefix.size,
            startIndex = 0,
            endIndex = this.bytes.size,
        )
        return ContentHash.fromBytes(ALGORITHM_NAME, bytesToHash).result!!
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("type", Hints.u) { value("ByteDocContent") }
        key("bytes", Hints.s) { value(bytes.hexEncode()) }
    }
}

/**
 * A shell command that would generate, on stdout, the snippet content.
 * This allows us to hash the specification for snippet content, for example, a DOT generated image,
 * before running the command, which allows testing whether snippets are up-to-date in a GitHub
 * command action without downloading shell commands and running them on every PR.
 *
 * ## Command should be canonical
 * [command] should be a simple command name resolved via the PATH environment variable, not
 * an absolute or OS-specific path.
 * - GOOD: `dot`
 * - BAD:  `/usr/local/bin/dot`
 * - BAD:  `dot.exe`
 * It is OK to refer to a script under `scripts/` as long as there are equivalent `.bat` versions
 * for Windows and an extensionless one for UNIX.
 *
 * Paths in [args] must be `/` separated and relative to the project root.
 * - GOOD: `foo/bar/baz.dot`
 * - BAD:  `/path/to/user/workspace/temper/foo/bar/baz.dot`
 * - BAD:  `foo\bar\baz.dot`
 * - BAD:  `$TMPDIR/baz.dot`
 *
 * Commands should not include complex logic.  Put that in `.bat` and bash-compatible scripts under
 * `scripts/` instead.
 * - GOOD: `dot foo/bar/baz.dot`
 * - BAD:  `dot <(echo 'graph G { }' | tr "'" '"')`
 *
 * ## Commands may batch together inputs
 * Sometimes, a command may have significant startup time, so spawning 50 sub-processes to produce
 * 50 snippets is inefficient.  We might be able to avoid 49/50ths of the overhead by sending
 * all snippet args together.
 * If [groupTogether] is true then this command will be grouped with others with the same
 * [command] that also have [groupTogether] set, and arguments will be passed using an
 * alternate convention.
 *
 * Instead of emitting the output to stdout, which can only be redirected to one file, the output
 * will be specified on the command line thus:
 * - `--args`
 * - followed by the [args] as a JSON list
 * - `--out`
 * - path to the snippet output file
 *
 * So these commands:
 *
 * ```bash
 * my-command arg0 arg1 > build-user-docs/build/snippet/foo/snippet.txt
 * my-command arg2 arg3 > build-user-docs/build/snippet/bar/snippet.txt
 * ```
 *
 * could be considered thus:
 *
 * ```bash
 * my-command \
 *      --args '["arg0", "arg1"]' --out build-user-docs/build/snippet/foo/snippet.txt \
 *      --args '["arg2", "arg3"]' --out build-user-docs/build/snippet/bar/snippet.txt
 * ```
 */
data class ShellCommandDocContent(
    val command: String,
    val args: List<String>,
    val groupTogether: Boolean = false,
) : DocContent() {
    override fun hash(): ContentHash {
        val textToHash = toStringViaBuilder { sb ->
            sb.append("shell:")
            FormattingStructureSink(sb, indent = false).arr {
                value(command)
                args.forEach { value(it) }
            }
        }
        return ContentHash.fromChars(ALGORITHM_NAME, textToHash).result!!
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("type", Hints.u) { value("ShellCommandDocContent") }
        key("command") { value(command) }
        key("args") { value(args) }
        key("groupTogether", isDefault = !groupTogether) { value(groupTogether) }
    }
}
