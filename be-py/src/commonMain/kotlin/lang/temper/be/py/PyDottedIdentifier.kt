package lang.temper.be.py

import lang.temper.be.py.PyIdentifierGrammar.parseDottedIdentifier
import lang.temper.common.toStringViaBuilder
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.TemperName
import lang.temper.name.name

/**
 * Handles python module references and decorator names.
 */
class PyDottedIdentifier private constructor(
    internal val parts: List<DiPart>,
) : TokenSerializable, Comparable<PyDottedIdentifier> {

    val text: String
        by lazy {
            toStringViaBuilder {
                var prior = ""
                for (part in this.parts) {
                    it.append(prior)
                    it.append(part.text)
                    prior = part.prior
                }
            }
        }

    /** Returns the (possibly relative) path. */
    fun modulePath(): List<FilePathSegmentOrPseudoSegment> =
        parts.map { part ->
            when (part) {
                DiPart.RelDot -> ParentPseudoFilePathSegment
                is DiPart.Module -> FilePathSegment(part.outName.outputNameText)
            }
        }

    /** Returns the path, asserting it is absolute. */
    fun absModulePath(isPkg: Boolean = false): FilePath {
        return FilePath(
            parts.map { part ->
                require(part is DiPart.Module)
                FilePathSegment(part.outName.outputNameText)
            },
            isDir = isPkg,
        )
    }

    override fun toString(): String = text
    override fun hashCode(): Int = parts.hashCode()
    override fun equals(other: Any?): Boolean = other is PyDottedIdentifier && this.parts == other.parts

    val isRelative: Boolean
        get() = parts.getOrNull(0) is DiPart.RelDot

    init {
        var next = 0
        var issue = "No module found"

        for (part in parts) {
            when {
                next == 0 && part is DiPart.Module -> {
                    next = 1
                    issue = ""
                }
                next == 1 && part == DiPart.RelDot -> {
                    issue = "Must lead with RelDot parts"
                    break
                }
            }
        }
        require(issue.isEmpty()) { issue }
    }

    /** Attempt to convert a single part dotted identifier to a simple OutName. */
    fun asSimpleName(): OutName? = when (parts.size) {
        1 -> (parts[0] as? DiPart.Module)?.outName
        else -> null
    }

    fun asAst(pos: Position): Py.ImportDotted = Py.ImportDotted(pos, this)

    override fun renderTo(tokenSink: TokenSink) {
        var needsDot = false
        for (part in parts) {
            if (part is DiPart.Module) {
                if (needsDot) {
                    tokenSink.punctuation(".")
                }
                needsDot = true
            }
            part.renderTo(tokenSink)
        }
    }

    /**
     * Given this id is `foo.bar`, and the target is `foo.bar.qux.wat`,
     * find the next level of descent `qux`.
     */
    fun find(target: PyDottedIdentifier): DiDescent {
        // Alas, zip isn't a zipLongest.
        val thisIter = this.parts.iterator()
        val tgtIter = target.parts.iterator()
        while (thisIter.hasNext() && tgtIter.hasNext()) {
            val thisPart = thisIter.next()
            val tgtPart = tgtIter.next()
            if (thisPart !is DiPart.Module || tgtPart !is DiPart.Module) {
                return DiDescent.None
            }
            if (thisPart.outName.outputNameText != tgtPart.outName.outputNameText) {
                return DiDescent.None
            }
        }
        if (thisIter.hasNext()) {
            return DiDescent.None
        }
        return if (tgtIter.hasNext()) {
            val tgtPart = tgtIter.next()
            if (tgtPart !is DiPart.Module) {
                return DiDescent.None
            }
            DiDescent.Into(tgtPart.outName)
        } else {
            DiDescent.Same
        }
    }

    fun dot(name: OutName): PyDottedIdentifier {
        val parts = this.parts.toMutableList()
        parts.add(DiPart.Module(name))
        return PyDottedIdentifier(parts)
    }

    companion object {
        fun dotted(part: String, sourceName: TemperName? = null): PyDottedIdentifier =
            dotted(OutName(part, sourceName))
        fun dotted(vararg parts: OutName): PyDottedIdentifier =
            PyDottedIdentifier(parts.map { p -> DiPart.Module(p) })
        fun dotted(parts: Iterable<String>): PyDottedIdentifier =
            PyDottedIdentifier(parts.map { p -> DiPart.Module(OutName(p, null)) })

        operator fun invoke(text: String) = PyDottedIdentifier(parseDottedIdentifier(text))
        fun fromPath(filePath: FilePath): PyDottedIdentifier =
            PyDottedIdentifier(
                filePath.segments
                    .mapNotNull {
                        if (it.baseName == DUNDER_INIT) {
                            null
                        } else {
                            DiPart.Module(OutName(safeModuleFileName(it).baseName, null))
                        }
                    },
            )
    }

    override fun compareTo(other: PyDottedIdentifier): Int = this.toString().compareTo(other.toString())
}

fun PyDottedIdentifier?.dot(name: OutName): PyDottedIdentifier =
    this?.dot(name) ?: PyDottedIdentifier.dotted(name)

fun PyDottedIdentifier?.dot(name: String, sourceName: TemperName? = null): PyDottedIdentifier =
    this.dot(OutName(name, sourceName))

sealed class DiDescent {
    class Into(val into: OutName) : DiDescent()
    object Same : DiDescent()
    object None : DiDescent()
}

/** A part of a [PyDottedIdentifier]. */
internal sealed class DiPart : TokenSerializable {
    abstract val text: String
    abstract val prior: String
    object RelDot : DiPart() {
        override val text: String get() = "."
        override val prior: String get() = ""
        override fun renderTo(tokenSink: TokenSink) = tokenSink.punctuation(".")

        override fun equals(other: Any?): Boolean = other is RelDot
        override fun hashCode(): Int = 42
    }

    class Module(val outName: OutName) : DiPart() {
        override val text: String get() = outName.outputNameText
        override val prior: String get() = "."
        constructor(name: String, source: TemperName?) : this(OutName(name, source))

        override fun renderTo(tokenSink: TokenSink) =
            tokenSink.name(outName, inOperatorPosition = false)

        override fun equals(other: Any?): Boolean =
            other is Module && this.text == other.text
        override fun hashCode(): Int = this.text.hashCode()
    }
}
