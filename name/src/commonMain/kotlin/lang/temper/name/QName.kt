package lang.temper.name

import lang.temper.common.C_DOT
import lang.temper.common.C_HASH
import lang.temper.common.C_SLASH
import lang.temper.common.C_SPACE
import lang.temper.common.RResult
import lang.temper.common.ignore
import lang.temper.common.inverse
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.bannedPathSegmentNames

/**
 * A fully qualified name, *QName*, is a label associated with a
 * declaration so that we can persist information about it across
 * versions of a library.
 *
 * Being able to associate a declaration in version 1.2.3 with
 * the corresponding declaration in 1.3.0 allows us to alert
 * the developer to backwards-compatibility troubles and to
 * associate backend-specific decisions like renames to avoid
 * name collisions via external annotations.
 *
 * A fully qualified name includes several parts:
 *
 * - a library name
 * - a relative file path to the declaring module
 * - the [ParsedName] texts of nesting declaration elements.
 *
 * [QName]s need to be serialized out to metadata files or stored
 * in declaration decorators, so there is a non-lossy conversion to
 * and from strings.
 *
 * [QName]s also need to be identifiable as they may appear in
 * error messages and debugging artifacts.
 *
 * To that end, the textual form of an [QName] uses several
 * meta-characters to delimit segments, and allows for `\` escaping.
 * A meta-character must be escaped regardless of where it appears.
 *
 * | Meta-character     | Purpose                                                         |
 * | ------------------ | --------------------------------------------------------------- |
 * | Solidus (`/`)      | Indicates that the **next** element is a relative path element  |
 * | Dot     (`.`)      | Indicates that the **next** element is a parsed name text       |
 * | Angles  (`<`, `>`) | Indicate that the bracketed identifier is a type formal         |
 * | Parens  (`(`, `)`) | Indicates that the associated identifier is part of a function  |
 * | Equals  (`=`)      | Indicates that the associated identifier is a local variable    |
 * | Space   (` `)      | Follows a kind-identifying keyword (see below)                  |
 * | Hash    (`#`)      | Indicates that the **next** element is a disambiguation index   |
 * | Rev Solidus  (`\`) | Disables treatment of the **next* character as a meta-character |
 *
 * Each identifier part has a kind associated with it.  Some of these kinds are indicated by
 * punctuation meta-characters and some by a keyword followed by a single space (` `):
 *
 * - `x` is a [module top level or property name declaration][QName.PartKind.Decl]
 * - `x=` is a [local variable name][QName.PartKind.Local]
 * - `f()` is a [function or method name][QName.PartKind.FunctionOrMethod]
 * - `(x)` is a [function input][QName.PartKind.Input] as distinct from a local variable in a function body.
 * - `<T>` is a [type formal][QName.PartKind.TypeFormal]
 * - `constructor` is a [constructor name][QName.PartKind.Constructor]
 * - `get x()` is a [getter method][QName.PartKind.Getter] for an abstract parameter named `x`
 * - `set x()` is a [setter method][QName.PartKind.Setter] for an abstract parameter named `x`
 * - `type C` indicates a [named class or interface type][QName.PartKind.Type]
 *
 * These kinding conventions allow us to be stricter about name overlaps for top-levels and type
 * members than, for example, local variables.
 *
 * A disambiguation index, like `#0`, is added to the end of any [QName] that would
 * otherwise be ambiguous in its module.
 * An exported name always gets the disambiguation index `#0` and other ambiguous declarations
 * get the name in lexical declaration order.
 *
 * The declarations in the example below are in a library named `my-library` in a file
 * under the directory `foo/bar` relative to the library root.
 * The [QName]s appear in comments next to the declarations.
 *
 * **Note**: the first dot (`.`) in the [QName]s below ends the path.   Temper module
 * relative paths are directories, so they tend not to include dots or file extensions, but
 * if a dot did appear in a file path segment it would need to be escaped.
 *
 * ```temper inert
 * let x = 123;             // my-library/foo/bar.x
 * let f(                   // my-library/foo/bar.f()
 *   x: Int                 // my-library/foo/bar.f().(x)
 * ): Int {
 *   let y = x + 1;         // my-library/foo/bar.f().y=
 *   return y;
 * }
 *
 * class C<T> {             // my-library/foo/bar.type C          and   my-library/foo/bar.type C.<T>
 *   public var x: Int;     // my-library/foo/bar.type C.x
 *
 *   public f<T>(           // my-library/foo/bar.type C.f()      and   my-library/foo/bar.type C.f().<T>
 *     x: T                 // my-library/foo/bar.type C.f().(x)
 *   ): T {
 *     x
 *   }
 *
 *   public get y(): Int {  // my-library/foo/bar.type C.get y()
 *     x + 1
 *   }
 *   public set y(          // my-library/foo/bar.type C.set y()
 *     newY: Int            // my-library/foo/bar.type C.set y().(newY)
 *   ) {
 *     x = newY - 1;
 *   }
 *
 *   public static fromY(   // my-library/foo/bar.type C.fromY()
 *     y: Int               // my-library/foo/bar.type C.fromY().(y)
 *   ): C {
 *     let c =              // my-library/foo/bar.type C.fromY().c=
 *       new C(x = y - 1);
 *     return c;
 *   }
 * }
 *
 * do {
 *   let x = 1;             // my-library/foo/bar.x=#0
 *   let x = x + 1;         // my-library/foo/bar.x=#1
 * }
 *
 * // When constructs like type's nest, names nest.
 * let f(): Void {          // my-library/foo/bar.f()
 *   class Helper { ... }   // my-library/foo/bar.f().type Helper
 *   let helper() { ... }   // my-library/foo/bar.f().helper()
 *   ...
 * }
 * ```
 *
 * In addition to `\` escaping meta-characters, control characters and double-quotes
 * may be escaped for readability:
 *
 * | Escape sequence | Value     |
 * | --------------- | --------- |
 * | `\0`            | 0x00      |
 * | `\t`            | 0x09      |
 * | `\n`            | 0x0A      |
 * | `\r`            | 0x0D      |
 * | `\ `            | 0x20      |
 * | `\"`            | 0x22 `"`  |
 * | `\#`            | 0x23 `#`  |
 * | `\.`            | 0x2E `.`  |
 * | `\/`            | 0x2F `/`  |
 * | `\<`            | 0x3C `<`  |
 * | `\=`            | 0x3C `=`  |
 * | `\>`            | 0x3E `>`  |
 * | `\\`            | 0x5C `\`  |
 *
 * That escape set allows presenting [QName]s in `raw "..."` string form without introducing
 * multiple, possibly confusing layers of escaping.
 * Other character must not be escaped.
 *
 * Not every declaration has an [QName].  Examples of declarations that might not:
 *
 * - temporary variables
 * - auto-generated constructors
 * - getters and setters generated for backed properties
 * - abstract properties generated based on the presence of explicit getters or setters
 */
data class QName(
    val libraryName: DashedIdentifier,
    val relativePath: FilePath,
    val parts: List<Part>,
    val disambiguationIndex: Int?,
) : TokenSerializable {
    enum class PartKind {
        Type,
        TypeFormal,
        Getter,
        Setter,
        Constructor,
        FunctionOrMethod,
        Input,
        Decl,
        Local,
    }

    data class Part(
        val parsedName: ParsedName,
        val kind: PartKind,
    )

    override fun toString(): String = buildString {
        unParseOnto(this@QName, this@buildString)
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken(toString(), OutputTokenType.Name))
    }

    class Builder(
        val libraryName: DashedIdentifier,
        val relativePath: FilePath,
    ) {
        private val parts = mutableListOf<Part>()
        private var disambiguationIndex: Int? = null

        constructor(name: QName) : this(name.libraryName, name.relativePath) {
            this.parts.addAll(name.parts)
            this.disambiguationIndex = name.disambiguationIndex
        }

        fun toQName(): QName = QName(
            libraryName = libraryName,
            relativePath = relativePath,
            parts = parts.toList(),
            disambiguationIndex = disambiguationIndex,
        )

        /** Appends at the end a [part][QName.parts] for the built [QName] */
        fun part(name: ParsedName, kind: PartKind) {
            parts.add(Part(name, kind))
        }

        /** Specifies the [QName.disambiguationIndex] for the built [QName]. */
        fun disambiguate(newDisambiguationIndex: Int) {
            this.disambiguationIndex = newDisambiguationIndex
        }

        /** adds a [part] that is a [PartKind.Decl] */
        fun decl(name: ParsedName) = part(name, PartKind.Decl)

        /** adds a [part] that is a [PartKind.FunctionOrMethod] */
        fun fn(name: ParsedName) = part(name, PartKind.FunctionOrMethod)

        /** adds a [part] that is a [PartKind.Input] */
        fun input(name: ParsedName) = part(name, PartKind.Input)

        /** adds a [part] that is a [PartKind.FunctionOrMethod] */
        fun formal(name: ParsedName) = part(name, PartKind.TypeFormal)

        /** adds a [part] that is a [PartKind.Local] */
        fun local(name: ParsedName) = part(name, PartKind.Local)

        /** adds a [part] that is a [PartKind.Type] */
        fun type(name: ParsedName) = part(name, PartKind.Type)

        /** adds a [part] that is a [PartKind.Getter] */
        fun constructor(name: ParsedName) = part(name, PartKind.Constructor)

        /** adds a [part] that is a [PartKind.Getter] */
        fun getter(name: ParsedName) = part(name, PartKind.Getter)

        /** adds a [part] that is a [PartKind.Getter] */
        fun setter(name: ParsedName) = part(name, PartKind.Setter)

        val partCount: Int get() = parts.size

        fun resetPartCount(newCount: Int) {
            val partCount = parts.size
            check(newCount <= partCount)
            parts.subList(newCount, partCount).clear()
        }
    }

    companion object {
        fun fromString(string: String) = parse(string)
    }
}

private val escapeMap = mapOf(
    "\\0" to '\u0000',
    "\\t" to '\t',
    "\\n" to '\n',
    "\\r" to '\r',
    "\\ " to ' ',
    "\\\"" to '"',
    "\\#" to '#',
    "\\." to '.',
    "\\/" to '/',
    "\\<" to '<',
    "\\=" to '=',
    "\\>" to '>',
    "\\\\" to '\\',
)

private const val N_ASCII = 128
private val revEscapeMap = run {
    val escapes = Array<String?>(N_ASCII) { null }
    for ((s, c) in escapeMap) {
        escapes[c.code] = s
    }
    escapes
}

private val affixesToKind = mapOf(
    ("" to "") to QName.PartKind.Decl,
    ("" to "=") to QName.PartKind.Local,
    ("" to "()") to QName.PartKind.FunctionOrMethod,
    ("(" to ")") to QName.PartKind.Input,
    ("<" to ">") to QName.PartKind.TypeFormal,
    ("constructor" to "()") to QName.PartKind.Constructor,
    ("get" to "()") to QName.PartKind.Getter,
    ("set" to "()") to QName.PartKind.Setter,
    ("type" to "") to QName.PartKind.Type,
)

private val kindToAffixes = affixesToKind.inverse()

private val metacharacters = run {
    val bits = BooleanArray(N_ASCII)
    bits['/'.code] = true
    bits['.'.code] = true
    bits['<'.code] = true
    bits['>'.code] = true
    bits['('.code] = true
    bits[')'.code] = true
    bits['='.code] = true
    bits[' '.code] = true
    bits['#'.code] = true
    bits['\\'.code] = true
    bits
}

private fun parse(
    s: String,
): RResult<QName, IllegalArgumentException> {
    // The last code-unit read
    var read = -1
    // Cursors into s
    val limit = s.length
    var offset = 0
    var nextOffset = 0 // The offset after consuming read
    var isEscaped = false

    fun peek(): Int {
        if (offset == limit) {
            read = -1
        } else {
            val c = s[offset]
            isEscaped = c == '\\'
            if (!isEscaped) {
                read = c.code
                nextOffset = offset + 1
            } else {
                require(offset + 1 != limit) { "Incomplete escape sequence at end: `$s`" }
                val esc = s.substring(offset, offset + 2)
                val escaped = escapeMap[esc]
                    ?: throw IllegalArgumentException("Invalid escape sequence `$esc` at offset $offset in `$s`")
                read = escaped.code
                nextOffset = offset + 2
            }
        }
        return read
    }

    fun consume() {
        offset = nextOffset
    }

    // Accumulates characters read
    val sb = StringBuilder()

    // Drain a run of metacharacters or non-metacharacters onto sb
    fun drain(wantMeta: Boolean): String {
        sb.clear()
        while (true) {
            peek()
            if (read < 0 || !isEscaped && (C_DOT == read || C_SLASH == read || C_HASH == read)) {
                // Stop at end or a part separator
                break
            }
            val gotMeta = !isEscaped && metacharacters.getOrNull(read) == true
            if (wantMeta == gotMeta) {
                sb.append(read.toChar())
                consume()
            } else {
                break
            }
        }
        return "$sb"
    }

    return RResult.of(IllegalArgumentException::class) {
        // First, we're looking for a library name.
        val libraryNameText = drain(wantMeta = false)
        val problemIndex = DashedIdentifier.firstProblemCharIndex(libraryNameText)
        require(problemIndex < 0) { "Malformed library name at offset $problemIndex in `$s`" }
        val libraryName = DashedIdentifier(libraryNameText)
        val pathSegments = mutableListOf<FilePathSegment>()
        while (peek() == C_SLASH && !isEscaped) {
            consume()
            offset = nextOffset
            drain(wantMeta = false)
            val pathSegmentText = "$sb"
            require(pathSegmentText !in bannedPathSegmentNames) {
                "Bad path segment `$pathSegmentText` in `$s`"
            }
            pathSegments.add(FilePathSegment(pathSegmentText))
        }

        val b = QName.Builder(libraryName, FilePath(pathSegments, isDir = true))
        while (offset < limit) {
            val separator = peek()
            if (isEscaped) { break } // Not a separator
            consume()
            if (separator == C_DOT) {
                val offsetBefore = offset

                var metaPrefix = drain(wantMeta = true)
                var idText = drain(wantMeta = false)
                if (metaPrefix == "" && peek() == C_SPACE && !isEscaped) {
                    // Shift a keyword into the meta prefix.
                    metaPrefix = idText
                    consume()
                    idText = drain(wantMeta = false)
                }
                val metaSuffix = drain(wantMeta = true)

                require(idText.isNotEmpty()) { "Empty name at offset $offset in `$s`" }
                val name = ParsedName(idText)
                val kind = affixesToKind[metaPrefix to metaSuffix]
                require(kind != null) {
                    "Could not determine kind of `${
                        s.substring(offsetBefore, offset)
                    }` at offset $offsetBefore in `$s`"
                }
                b.part(name, kind)
            } else if (separator == C_HASH) {
                val indexText = s.substring(offset, limit)
                var index: Int = -1
                try {
                    index = indexText.toInt(radix = 10)
                } catch (e: NumberFormatException) {
                    ignore(e) // Merge negative index and invalid text handling below
                }
                require(index >= 0) {
                    "Bad disambiguation index at offset $offset in `$s`"
                }
                b.disambiguate(index)
                offset = limit
                break
            } else {
                throw IllegalArgumentException(
                    "Expected `.` or `#` not `${separator.toChar()}` at offset $offset in `$s`",
                )
            }
        }

        require(offset == limit) {
            "Unparsed content at offset $offset in `$s`"
        }
        b.toQName()
    }
}

private fun unParseOnto(
    n: QName,
    sb: StringBuilder,
) {
    fun escape(s: String) {
        var emitted = 0
        for (i in s.indices) {
            val c = s[i]
            val esc = revEscapeMap.getOrNull(c.code)
            if (esc != null) {
                sb.append(s, emitted, i)
                sb.append(esc)
                emitted = i + 1
            }
        }
        sb.append(s, emitted, s.length)
    }

    escape(n.libraryName.text)
    for (segment in n.relativePath.segments) {
        sb.append('/')
        escape(segment.fullName)
    }
    for ((partName, partKind) in n.parts) {
        val (before, after) = kindToAffixes.getValue(partKind)
        sb.append('.')
        sb.append(before)
        if (before.isNotEmpty() && before.last() in 'a'..'z') {
            // Keywords are followed by spaces
            sb.append(' ')
        }
        escape(partName.nameText)
        sb.append(after)
    }
    n.disambiguationIndex?.let {
        sb.append('#')
        sb.append(it)
    }
}
