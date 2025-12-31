package lang.temper.name

import lang.temper.common.C_DASH
import lang.temper.common.C_UPPER_A
import lang.temper.common.C_UPPER_Z
import lang.temper.common.asciiLowerCase
import lang.temper.common.charCount
import lang.temper.common.decodeUtf16
import lang.temper.common.encodeUtf16
import lang.temper.common.subListToEnd
import lang.temper.common.toStringViaBuilder
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.lexer.IdParts
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.appendSegmentTo
import lang.temper.log.FilePath.Companion.joinPathTo
import lang.temper.log.FilePathSegment
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.SharedLocationContext
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR

/** A code location for a module */
sealed interface ModuleLocation : CodeLocation, Comparable<ModuleLocation>, TokenSerializable {
    /**
     * Render tokens that represent the exporter in pseudocode
     * so that we can represent exported names as *Exporter*.*exportedName*.
     */
    override fun renderTo(tokenSink: TokenSink) = renderTo(tokenSink, null)

    fun renderTo(tokenSink: TokenSink, context: SharedLocationContext?)
}

/**
 * A code location that should only be used for the Implicits module, the module that is implicitly
 * imported by all other modules.
 */
object ImplicitsCodeLocation : ModuleLocation {
    override val diagnostic = wordImplicits.text
    override fun renderTo(tokenSink: TokenSink, context: SharedLocationContext?) {
        tokenSink.emit(wordImplicits)
    }

    override fun compareTo(other: ModuleLocation): Int = when (other) {
        is ImplicitsCodeLocation -> 0
        is ModuleName -> -1
    }

    override fun toString() = wordImplicits.text
}

private val wordImplicits = OutputToken("implicits", OutputTokenType.Word)

/**
 * A module name is a cross-version identifier for a module.
 * By "cross-version" we mean that different compiler runs with small changes of a module.
 *
 * A module name consists of:
 *
 * 1. a library name as a dashed identifier like `my-library`.
 * 2. a path relative to the library root.
 * 3. a list of module parameter descriptions.
 */
data class ModuleName(
    /** The path to the module */
    override val sourceFile: FilePath,
    /** The number of segments in [sourceFile] that correspond to the library root. */
    val libraryRootSegmentCount: Int,
    /**
     * Whether the module represents the preface: shared definitions separate
     * from any instantiation of a module functor.
     */
    val isPreface: Boolean,
) : FileRelatedCodeLocation, ModuleLocation {
    init {
        require(libraryRootSegmentCount <= sourceFile.segments.size) {
            "$libraryRootSegmentCount !in $sourceFile"
        }
    }

    /** The library root. */
    fun libraryRoot(): FilePath = FilePath(
        sourceFile.segments.subList(0, libraryRootSegmentCount),
        isDir = true,
    )

    /** A path relative to the library's root directory. */
    fun relativePath(): FilePath = sourceFile.copy(
        segments = sourceFile.segments.subListToEnd(libraryRootSegmentCount),
    )

    override fun toString() = toStringViaBuilder { appendTo(it) }

    private fun appendPathPartsTo(sb: StringBuilder) {
        for (i in 0 until libraryRootSegmentCount) {
            if (i != 0) {
                sb.append(UNIX_FILE_SEGMENT_SEPARATOR)
            }
            sourceFile.segments[i].appendSegmentTo(sb)
        }
        // Make sure there is a // separator between the library root and the
        // relative path that indicates visually, which library the file is
        // part of.
        sb.append(UNIX_FILE_SEGMENT_SEPARATOR)
        sb.append(UNIX_FILE_SEGMENT_SEPARATOR)

        appendRelativePathPartsTo(sb)
    }

    private fun appendRelativePathPartsTo(sb: StringBuilder) {
        val relativePath = relativePath()
        if (relativePath.segments.isNotEmpty()) {
            relativePath.joinPathTo(sb)
        } else if (relativePath.isFile) {
            // Something odd is going on.
            // Make sure that oddity is apparent in diagnostic printouts like this.
            val lastSegment = sourceFile.lastOrNull()
                ?: FilePathSegment("???")
            // ".." is not allowed as a segment name, so this indicates something odd
            // is going on.
            sb.append("..")
            sb.append(UNIX_FILE_SEGMENT_SEPARATOR)
            lastSegment.appendSegmentTo(sb)
        }
    }

    private fun appendTo(sb: StringBuilder) {
        appendPathPartsTo(sb)
        if (isPreface) {
            sb.append(":preface")
        }
    }

    fun appendRelativePathAndSuffix(sb: StringBuilder) {
        appendRelativePathPartsTo(sb)
        if (isPreface) {
            sb.append(":preface")
        }
    }

    override fun compareTo(other: ModuleLocation): Int = when (other) {
        is ImplicitsCodeLocation -> 1 // Implicits sorts first
        is ModuleName -> {
            var delta = sourceFile.compareTo(other.sourceFile)
            if (delta == 0) {
                delta = -this.isPreface.compareTo(other.isPreface)
            }
            delta
        }
    }

    override val diagnostic: String
        get() = toString()

    override fun renderTo(tokenSink: TokenSink, context: SharedLocationContext?) {
        val libraryRoot = libraryRoot()
        val libraryName = context?.get(libraryRoot, LibraryNameLocationKey)
        if (libraryName != null) {
            libraryName.renderTo(tokenSink)
            tokenSink.emit(OutToks.dot)
            val relativePath = relativePath()
            tokenSink.name(
                ParsedName("$relativePath"),
                inOperatorPosition = false,
            )
        } else {
            tokenSink.emit(
                OutputToken(
                    buildString {
                        append('`')
                        appendPathPartsTo(this)
                        append('`')
                    },
                    OutputTokenType.QuotedValue,
                ),
            )
        }

        if (isPreface) {
            tokenSink.emit(OutToks.colon)
            tokenSink.emit(OutputToken("preface", OutputTokenType.Word))
        }
    }
}

fun FilePath.rootModuleName(): ModuleName {
    check(isDir)
    return ModuleName(
        sourceFile = this,
        libraryRootSegmentCount = this.segments.size,
        isPreface = false,
    )
}

data class DashedIdentifier(
    val text: String,
) : Comparable<DashedIdentifier>, TokenSerializable {
    init {
        require(isDashedIdentifier(text)) { text }
    }

    override fun compareTo(other: DashedIdentifier): Int =
        text.compareTo(other.text)

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("`$text`", OutputTokenType.QuotedValue))
    }

    override fun toString(): String = text

    companion object {
        private enum class DashIdState {
            Start,
            Continue,
            AfterMedial,
        }

        fun isDashedIdentifier(text: String): Boolean = firstProblemCharIndex(text) < 0

        /** -1 if [text] is a valid DashedIdentifier text or otherwise, the character offset of the index. */
        fun firstProblemCharIndex(text: String): Int {
            var i = 0
            val n = text.length
            var state = DashIdState.Start
            while (i < n) {
                val cp = decodeUtf16(text, i)
                i += charCount(cp)
                val nextState = when (state) {
                    DashIdState.Start -> when (cp) {
                        in IdParts.Start -> DashIdState.Continue
                        else -> null
                    }
                    DashIdState.Continue -> when (cp) {
                        in IdParts.Continue -> DashIdState.Continue
                        C_DASH, in IdParts.Medial -> DashIdState.AfterMedial
                        else -> null
                    }
                    DashIdState.AfterMedial -> when (cp) {
                        in IdParts.Continue -> DashIdState.Continue
                        else -> null
                    }
                }
                state = nextState ?: break
            }

            return if (i < n || state != DashIdState.Continue) {
                i // Position of error
            } else {
                -1 // No error
            }
        }

        /**
         * Does what GFM does to turn header text into a dashed identifier as explained in
         * [issue 65](https://github.com/github/cmark-gfm/issues/65#issuecomment-343433978)
         */
        fun from(text: String): DashedIdentifier? {
            val coerced = toStringViaBuilder { sb ->
                var i = 0
                val n = text.length
                var state = DashIdState.Start
                while (i < n) {
                    val cp = when (val cpAnyCase = decodeUtf16(text, i)) {
                        in C_UPPER_A..C_UPPER_Z -> cpAnyCase.toChar().asciiLowerCase().code
                        else -> cpAnyCase
                    }
                    i += charCount(cp)
                    when (state) {
                        DashIdState.Start -> when (cp) {
                            in IdParts.Start -> {
                                encodeUtf16(cp, sb)
                                state = DashIdState.Continue
                            }
                            in IdParts.Continue -> {
                                sb.append("x-")
                                encodeUtf16(cp, sb)
                                state = DashIdState.Continue
                            }
                        }
                        DashIdState.Continue -> when (cp) {
                            in IdParts.Continue -> encodeUtf16(cp, sb)
                            in IdParts.Medial -> {
                                encodeUtf16(cp, sb)
                                state = DashIdState.AfterMedial
                            }
                            else -> {
                                sb.append('-')
                                state = DashIdState.AfterMedial
                            }
                        }
                        DashIdState.AfterMedial -> when (cp) {
                            in IdParts.Continue -> {
                                encodeUtf16(cp, sb)
                                state = DashIdState.Continue
                            }
                        }
                    }
                }
                if (state == DashIdState.AfterMedial) {
                    val lastIndex = sb.lastIndex
                    if (sb[lastIndex] == '-') {
                        sb.setLength(lastIndex)
                    }
                }
            }
            return if (isDashedIdentifier(coerced)) {
                DashedIdentifier(coerced)
            } else {
                null
            }
        }

        /**
         * A name that may be used to refer to a theoretical library that defines Temper builtin
         * types and operators. Each backend should probably recognize imports of things from here
         * and reroute them to its runtime support code.
         */
        val temperCoreLibraryIdentifier = DashedIdentifier("temper-core")

        /**
         * The current version number for the core library.
         * TODO Move this and [temperCoreLibraryIdentifier] elsewhere?
         * TODO Script to update all temper-core backends and also std with this?
         */
        const val temperCoreLibraryVersion = "0.6.0"

        /** Specifically refers to the implicits library used by Temper code. */
        val temperImplicitsLibraryIdentifier = DashedIdentifier("temper-implicits")

        /** Library that ships with temper but needs to be explicitly imported when used. */
        val temperStandardLibraryIdentifier = DashedIdentifier("std")
    }
}
