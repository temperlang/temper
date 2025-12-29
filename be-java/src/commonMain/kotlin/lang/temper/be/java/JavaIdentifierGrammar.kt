package lang.temper.be.java

import lang.temper.ast.deepCopy
import lang.temper.common.CodePoints
import lang.temper.common.toHex
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.applyToSegments
import lang.temper.log.dirPath
import lang.temper.log.resolveFile
import lang.temper.name.OutName
import lang.temper.name.identifiers.IdentStyle
import lang.temper.be.java.Java as J

private val whitespace = Regex("\\s+")

/** JLS 3.9 */
val reservedKeywords = """
    abstract   continue   for          new         switch
    assert     default    if           package     synchronized
    boolean    do         goto         private     this
    break      double     implements   protected   throw
    byte       else       import       public      throws
    case       enum       instanceof   return      transient
    catch      extends    int          short       try
    char       final      interface    static      void
    class      finally    long         strictfp    volatile
    const      float      native       super       while
    _
    """.split(whitespace).toSet()

/** JLS 3.9 */
val contextualKeywords = """
    exports      opens      requires     uses
    module       permits    sealed       var
    non-sealed   provides   to           with
    open         record     transitive   yield
    """.split(whitespace).toSet()

/** JLS 3.10.3, JLS 3.10.8 */
val literals = setOf("false", "true", "null")

/** JLS 3.9 Keywords */
val keywords = reservedKeywords.union(contextualKeywords)

/** all words that are excluded as an identifier name */
val identifierExcludes = keywords.union(literals)

/** Words reserved by Temper. */
val temperReserved = setOf(
    MODULE_EXPORT_NAME,
)

/**
 * Includes valid identifiers (temper reserved words) that user identifiers will avoid.
 * This should be largely invisible to users in practice.
 */
val identifierAvoids = identifierExcludes.union(temperReserved)

/**
 * ### Notes on inconsistencies in JLS 3.8 and 3.9
 *
 * Section 3.8 defines:
 * ```
 * Identifier:
 *   IdentifierChars but not a Keyword or BooleanLiteral or NullLiteral
 * // later
 * TypeIdentifier:
 *   Identifier but not permits, record, sealed, var, or yield
 * UnqualifiedMethodIdentifier:
 *   Identifier but not yield
 * ```
 *
 * However, Section 3.9 defines Keyword:
 *
 * ```
 * Keyword:
 *   ReservedKeyword
 *   ContextualKeyword
 * ```
 *
 * Since ContextualKeyword includes all of _permits, record, sealed, var, or yield_, according to the JLS
 * both TypeIdentifier and UnqualifiedMethodIdentifier are still identical to Identifier.
 *
 * There's little utility in allowing these names in Temper, so we simply use Identifier to stand in for
 * TypeIdentifier and UnqualifiedMethodIdentifier.
 */
fun String.isIdentifier(): Boolean = isIdentifierChars() && this !in identifierExcludes

/** JLS 3.8 IdentifierChars */
fun String.isIdentifierChars(): Boolean {
    var first = true
    var result = false
    for (codepoint in this.codePoints()) {
        if (first) {
            if (!Character.isJavaIdentifierStart(codepoint)) {
                break
            }
            result = true // Need at least a starting character
            first = false
        } else {
            if (!Character.isJavaIdentifierPart(codepoint)) {
                result = false
                break
            }
        }
    }
    return result
}

fun String.escapeNonidentifierChars(): String = buildString(this.length) {
    val iter = CodePoints(this@escapeNonidentifierChars).iterator()
    if (!iter.hasNext()) {
        return ""
    }
    var codepoint = iter.next()
    if (Character.isJavaIdentifierStart(codepoint)) {
        appendCodePoint(codepoint)
    } else {
        genericEscape(codepoint, this)
    }
    while (iter.hasNext()) {
        codepoint = iter.next()
        if (Character.isJavaIdentifierPart(codepoint)) {
            appendCodePoint(codepoint)
        } else {
            genericEscape(codepoint, this)
        }
    }
}

private val escapes = listOf(
    '-' to '_',
    '#' to '_',
).associate { (e, r) -> e.code to r.code }

private fun genericEscape(cp: Int, sb: StringBuilder) {
    when (val escaped = escapes[cp]) {
        null -> sb.append('$').append(cp.toHex())
        else -> sb.appendCodePoint(escaped)
    }
}

/** Sometimes Temper names are lower or upper. */
fun String.temperToJavaClass(pascalSuffix: String) =
    IdentStyle.Pascal.convertTo(IdentStyle.Pascal, this).safeIdentifier() + pascalSuffix

fun String.assertSafe(): String {
    require(isIdentifier()) { "Manually constructed identifiers must be safe" }
    return this
}

/** Modify an identifier to avoid it being a keyword. */
fun String.safeIdentifier() =
    when {
        this.isEmpty() -> "$"
        this in identifierAvoids -> "${this}_"
        isIdentifierChars() -> this
        else -> escapeNonidentifierChars()
    }

/** For use outside of the AST, same as [J.QualIdentifier]. */
class QualifiedName private constructor(
    val parts: List<OutName>,
) : Comparable<QualifiedName> {
    val fullyQualified get() = parts.joinToString(".") { it.outputNameText }

    fun isEmpty() = parts.isEmpty()
    fun isNotEmpty() = parts.isNotEmpty()
    val lastPart get() = parts.last()
    val lastPartOrNull get() = parts.lastOrNull()
    val size get() = parts.size
    val estimateLength get() = parts.sumOf { it.outputNameText.length + 1 } - 1
    val indices get() = parts.indices
    fun startsWith(prefix: QualifiedName): Boolean = size >= prefix.size && prefix.indices.all { idx ->
        parts[idx] == prefix.parts[idx]
    }
    fun isChildOf(prefix: QualifiedName): Boolean = size == prefix.size + 1 && startsWith(prefix)

    private fun identsList(pos: Position) =
        parts.map { part -> J.Identifier(pos, outName = part) }

    /** Splits a name into leading parts and the tail part. */
    internal fun split(): Pair<QualifiedName, OutName> = QualifiedName(parts.dropLast(1)) to parts.last()

    /** Further qualify with an outname. If this is generated by methods in [JavaNames] it should be safe. */
    fun qualify(name: OutName): QualifiedName = QualifiedName(parts + listOf(name))

    /** Add a name from a raw string, but ignoring safe identifier checks. */
    fun qualifyKnownSafe(name: String): QualifiedName = qualify(OutName(name, null))

    /** Add a suffix to the last element of the name. */
    fun suffix(suffix: String): QualifiedName =
        QualifiedName(
            parts.dropLast(1) + listOf(
                OutName(parts.last().outputNameText + suffix, null),
            ),
        )

    fun toClassType(pos: Position, args: J.TypeArguments? = null, anns: List<J.Annotation> = listOf()) =
        J.ClassType(pos, type = toQualIdent(pos), args = args, anns = anns)
    fun toNameExpr(pos: Position): J.NameExpr = J.NameExpr(pos, identsList(pos))
    fun toQualIdent(pos: Position): J.QualIdentifier =
        J.QualIdentifier(pos, ident = identsList(pos))
    private fun toAnnQualIdent(pos: Position, anns: List<J.Annotation>) =
        split().let { (pkg, type) ->
            J.AnnotatedQualIdentifier(
                pos,
                pkg = pkg.identsList(pos),
                anns = anns.deepCopy(),
                type = J.Identifier(pos, type),
            )
        }
    fun toTypeArg(pos: Position, anns: List<J.Annotation> = listOf(), args: J.TypeArguments? = null) =
        J.ReferenceTypeArgument(pos, annType = toAnnQualIdent(pos, anns), args = args?.deepCopy())
    fun toStaticMethodRef(pos: Position): J.Expression {
        val (type, method) = split()
        return J.StaticMethodReferenceExpr(pos, type.toQualIdent(pos), J.Identifier(pos, method))
    }
    fun toAnnotation(pos: Position, vararg params: Pair<String, String>) =
        J.Annotation(
            pos,
            name = toQualIdent(pos),
            params = params.map { (name, value) ->
                J.AnnotationParam(
                    pos,
                    name = J.Identifier(pos, OutName(name, null)),
                    value = J.StringLiteral(pos, value),
                )
            },
        )

    override fun compareTo(other: QualifiedName): Int = fullyQualified.compareTo(other.fullyQualified)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is QualifiedName -> false
        else -> parts == other.parts
    }

    override fun hashCode(): Int = parts.hashCode()
    override fun toString(): String = fullyQualified

    /** Construct a file path pointing to this class */
    fun toFilePath(): FilePath {
        val (pkg, klass) = this.split()
        return dirPath(pkg.parts.map { it.outputNameText })
            .resolveFile(klass.outputNameText + JavaBackend.sourceFileExtension)
    }

    companion object {
        val empty = QualifiedName(listOf())

        fun make(parts: List<OutName>) =
            if (parts.isEmpty()) empty else QualifiedName(parts.toList())

        operator fun invoke(vararg parts: OutName) = make(parts.toList())

        /** Create a safe QualifiedName from parts. */
        fun safe(vararg parts: String) = make(parts.map { OutName(it.safeIdentifier(), null) })

        /** Create a safe Qualified name from parts. */
        fun safe(parts: List<String>): QualifiedName =
            make(parts.map { name -> OutName(name.safeIdentifier(), null) })

        /** Create a QualifiedName from parts already known to be safe. */
        fun knownSafe(vararg parts: String) = make(parts.map { OutName(it, null) })

        /** Create from an AST qualified identifier. */
        fun fromAst(qualIdent: J.QualIdentifier) = make(qualIdent.ident.map { it.outName })

        /** Create from an AST annotated qualified identifier. */
        fun fromAst(qualIdent: J.AnnotatedQualIdentifier) = make(
            qualIdent.pkg.map { it.outName } + qualIdent.type.outName,
        )

        /** Create from a named expression. */
        fun fromAst(name: J.NameExpr) = make(name.ident.map { it.outName })

        /** Create from a path to a Temper file, possibly `.temper` or `.temper.md`. */
        fun fromTemperPath(path: FilePath): QualifiedName {
            return safe(
                path
                    .applyToSegments(file = { it.withTemperAwareExtension("") })
                    .segments
                    .map { it.baseName },
            )
        }
    }
}

fun QualifiedName?.concat(parts: List<OutName>): QualifiedName {
    return QualifiedName.make((this?.parts ?: listOf()) + parts)
}
