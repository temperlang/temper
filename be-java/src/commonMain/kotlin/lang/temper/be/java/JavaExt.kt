package lang.temper.be.java

import lang.temper.ast.OutTree
import lang.temper.ast.deepCopy
import lang.temper.ast.toLispy
import lang.temper.be.java.JavaBackend.Companion.moduleFileName
import lang.temper.be.java.JavaBackend.Companion.packageFileName
import lang.temper.be.java.JavaBackend.Companion.sourceFileExtension
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.isPureVirtual
import lang.temper.common.allIndexed
import lang.temper.common.dequeIterable
import lang.temper.common.toHexPadded
import lang.temper.common.toStringViaBuilder
import lang.temper.format.CodeFormatter
import lang.temper.format.TokenSink
import lang.temper.format.escapeSlashStars
import lang.temper.format.toAppenderViaTokenSink
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.plus
import lang.temper.log.resolveFile
import lang.temper.log.unknownPos
import lang.temper.name.ModuleName
import lang.temper.name.Name
import lang.temper.name.OutName
import lang.temper.type2.TypeContext2
import kotlin.reflect.KProperty
import lang.temper.be.java.Java as J

fun checkCommentText(comment: String) {
    require("*/" !in comment) { "Comment contains closing comment sequence" }
}

// Modifier emit methods. These are to mitigate the problem with, e.g. MethodModifiers, where there were 2^9 format
// string branches. Productions that don't have this issue are done with regular AST conditionals.

fun J.ModAbstract.emit(sink: TokenSink) = when (this) {
    J.ModAbstract.Concrete -> {}
    J.ModAbstract.Abstract -> sink.word("abstract")
}

fun J.ModAccess.emit(sink: TokenSink) = when (this) {
    J.ModAccess.Private -> sink.word("private")
    J.ModAccess.PackagePrivate -> {}
    J.ModAccess.Protected -> sink.word("protected")
    J.ModAccess.Public -> sink.word("public")
}

fun J.ModFinal.emit(sink: TokenSink) = when (this) {
    J.ModFinal.Open -> {}
    J.ModFinal.Final -> sink.word("final")
}

fun J.ModNative.emit(sink: TokenSink) = when (this) {
    J.ModNative.Java -> {}
    J.ModNative.Native -> sink.word("native")
}

fun J.ModSealedFinal.emit(sink: TokenSink) = when (this) {
    J.ModSealedFinal.Open -> {}
    J.ModSealedFinal.NonSealed -> sink.word("non-sealed")
    J.ModSealedFinal.Sealed -> sink.word("sealed")
    J.ModSealedFinal.Final -> sink.word("final")
}

fun J.ModSealed.emit(sink: TokenSink) = when (this) {
    J.ModSealed.Open -> {}
    J.ModSealed.NonSealed -> sink.word("non-sealed")
    J.ModSealed.Sealed -> sink.word("sealed")
}

fun J.ModStatic.emit(sink: TokenSink) = when (this) {
    J.ModStatic.Dynamic -> {}
    J.ModStatic.Static -> sink.word("static")
}

fun J.ModSynchronized.emit(sink: TokenSink) = when (this) {
    J.ModSynchronized.Unsynchronized -> {}
    J.ModSynchronized.Synchronized -> sink.word("synchronized")
}

fun J.ModTransient.emit(sink: TokenSink) = when (this) {
    J.ModTransient.Persistent -> {}
    J.ModTransient.Transient -> sink.word("transient")
}

fun J.ModVolatile.emit(sink: TokenSink) = when (this) {
    J.ModVolatile.Stable -> {}
    J.ModVolatile.Volatile -> sink.word("volatile")
}

private val mainJavaPath = dirPath("src", "main", "java")
private val testJavaPath = dirPath("src", "test", "java")

val J.SourceDirectory.filePath get() = when (this) {
    J.SourceDirectory.MainJava -> mainJavaPath
    J.SourceDirectory.TestJava -> testJavaPath
}

val J.SourceDirectory.mavenScope get() = when (this) {
    J.SourceDirectory.MainJava -> "compile"
    J.SourceDirectory.TestJava -> "test"
}

/** Emit a long with an L specifier if needed. */
fun Long.emit(sink: TokenSink) {
    sink.number("$this")
    if (this < Integer.MIN_VALUE || this > Integer.MAX_VALUE) {
        sink.word("L")
    }
}

fun Number.emit(sink: TokenSink, precision: J.Precision) {
    when (precision) {
        J.Precision.Single -> sink.number("${this.toFloat()}")
        J.Precision.Double -> {
            sink.number("${this.toDouble()}")
            sink.word("D")
        }
    }
}

fun Char.emit(sink: TokenSink) {
    val escaped = when (this) {
        '\'' -> "\\'"
        in ' '..'~' -> escapes[code] ?: this.toString()
        else -> escapes[code] ?: uEscape(code)
    }
    sink.quoted("'$escaped'")
}

fun String.emit(sink: TokenSink) {
    sink.quoted(escapeString(this))
}

@Suppress("MagicNumber") // magic numbers are Unicode ranges
private fun escapeString(str: String): String = toStringViaBuilder(str.length + 2) { sb ->
    sb.append('"')
    str.chars().forEachOrdered { c ->
        sb.append(
            when (c) {
                '\n'.code -> "\\n"
                '\r'.code -> "\\r"
                '"'.code -> "\\\""
                '\\'.code -> "\\\\"
                in 0..0x1f -> escapes[c] ?: uEscape(c)
                in 0x20..0x7e -> escapes[c] ?: c.toChar()
                in 0x7f..0xffff -> uEscape(c)
                else -> throw IllegalArgumentException("Decoding produced an invalid codepoint")
            },
        )
    }
    sb.append('"')
}

@Suppress("MagicNumber") // four nibbles for a slash u escape
private fun uEscape(code: Int) = "\\u${code.toHexPadded(4)}"

/** Escapes per JLS 3.10.7 */
@Suppress("MagicNumber")
private val escapes: Map<Int, String> = mapOf(
    0x08 to "\\b",
    0x09 to "\\t",
    0x0c to "\\f",
    0x0a to "\\n",
    0x0d to "\\r",
    0x5c to "\\\\",
)

/** Consumes a value doing nothing with it. */
fun doNothing(expr: J.Expression, pos: Position = expr.pos): J.ExpressionStatementExpr =
    temperDoNothing.staticMethod(listOf(expr.asArgument()), pos)

/** Check whether an operation makes a standalone statement, e.g. `++` and `--`. */
fun J.Operation.makesStatement(): Boolean = operator.operator.makesStatement

/** Ensures an expression is valid as a standalone statement. */
fun J.Expression.asExprStmtExpr(pos: Position = this.pos): J.ExpressionStatementExpr = when (this) {
    is J.Operation -> if (makesStatement()) this else doNothing(this, pos)
    is J.ExpressionStatementExpr -> this
    else -> doNothing(this, pos)
}

/** Create a string to represent a garbage node. Note that this generally won't create compilable Java. */
fun garbageExpr(pos: Position, src: String, diagnostic: String?): J.ExpressionStatementExpr =
    temperMistranslation.staticMethod(
        listOf(
            J.StringLiteral(pos, escapeSlashStars("$src: $diagnostic")).asArgument(),
        ),
        pos,
    )

fun garbageComment(pos: Position, src: String, diagnostic: String?): J.CommentLine =
    J.CommentLine(pos, escapeSlashStars("$src: $diagnostic"))

/** Create a comment to stand in for a statement. */
fun garbageComment(t: TmpL.Garbage) =
    garbageComment(t.pos, "TmpL", t.diagnostic?.text ?: "Garbage")
fun metaComment(pos: Position, t: TmpL.DeclarationMetadata) =
    J.CommentLine(pos.leftEdge, t.toLispy().replace('\n', ' '))

/** Construct an Identifier from an OutName. */
fun OutName.toIdentifier(pos: Position) = J.Identifier(pos, this)

/** Construct a NameExpr from an OutName. */
fun OutName.toNameExpr(pos: Position) = J.NameExpr(pos, listOf(this.toIdentifier(pos)))

/** Construct a NameExpr from an OutName. */
fun List<OutName>.toNameExpr(pos: Position) = J.NameExpr(pos, this.map { it.toIdentifier(pos) })

/** Construct a Qualified name from a single OutName part. */
fun OutName.toQualName() = QualifiedName.make(listOf(this))

/** Quickly create a name expression for a well known name, e.g. java.lang.String */
operator fun J.NameExpr.Companion.invoke(pos: Position, vararg parts: String) =
    J.NameExpr(pos, parts.toList())

/** Quickly create a name expression for a well known name, e.g. java.lang.String */
operator fun J.NameExpr.Companion.invoke(pos: Position, parts: Iterable<String>) =
    J.NameExpr(
        pos,
        ident = parts.map { txt ->
            J.Identifier(pos, OutName(txt.assertSafe(), null))
        },
    )

/** Convenience method for creating blocks. */
operator fun J.BlockStatement.Companion.invoke(
    vararg stmts: J.BlockLevelStatement,
    pos: Position = stmts.last().pos,
): J.BlockStatement =
    J.BlockStatement(pos, stmts.toList())

/** A regular block may not contain explicit constructor invocations. */
fun J.BlockStatement?.regularBlock() = this == null || body.all { it !is J.AlternateConstructorInvocation }

/** A constructor block may contain a single explicit constructor invocation. */
fun J.BlockStatement.constructorBlock() =
    body.allIndexed { ix, stmt -> stmt !is J.AlternateConstructorInvocation || ix == 0 }

/** Insert a preamble before a block statement. Doesn't defensively copy. */
fun J.BlockLevelStatement.preface(preamble: List<J.BlockLevelStatement>) = when {
    preamble.isEmpty() -> this
    this is J.BlockStatement -> J.BlockStatement(pos, preamble + body)
    else -> J.BlockStatement(pos, preamble.deepCopy() + listOf(this.deepCopy()))
}

/** Insert a preamble before a block statement. Doesn't defensively copy. */
fun J.BlockStatement.preface(preamble: List<J.BlockLevelStatement>) = when {
    preamble.isEmpty() -> this
    else -> J.BlockStatement(pos, preamble.deepCopy() + body.deepCopy())
}

/** Convenience method for creating field accesses. */
fun J.Expression.field(fieldName: String, source: Name? = null, pos: Position = this.pos) =
    field(OutName(fieldName, source), pos = pos)

/** Convenience method for creating field accesses. */
fun J.Expression.field(outName: OutName, pos: Position = this.pos) =
    field(outName.toIdentifier(pos))

/** Convenience method for creating field accesses. */
fun J.Expression.field(fieldName: J.Identifier, pos: Position = fieldName.pos) =
    J.FieldAccessExpr(
        pos,
        expr = this,
        field = fieldName,
    )

/** Wrap an expression to be an argument. */
fun J.Expression.asArgument(pos: Position = this.pos): J.Argument = J.Argument(pos, this)

val TypedArg<J.Expression>.arg get() = expr.asArgument()
val TypedArg<J.Expression>.isNullable get() = TypeContext2().admitsNull(type)

/** Convenience method for creating method invocations. */
fun J.Expression.method(methodName: String, vararg args: J.Expression, source: Name? = null, pos: Position = this.pos) =
    method(methodName, args.map(J.Expression::asArgument), source, pos)

/** Convenience method for creating method invocations. */
fun J.Expression.method(methodName: String, args: List<J.Argument>, source: Name? = null, pos: Position = this.pos) =
    J.InstanceMethodInvocationExpr(
        pos,
        expr = this,
        method = J.Identifier(pos, OutName(methodName, source)),
        args = args,
    )

fun QualifiedName.staticMethod(args: List<J.Argument>, pos: Position): J.StaticMethodInvocationExpr {
    val (type, method) = this.split()
    return J.StaticMethodInvocationExpr(
        pos = pos,
        type = if (type.isNotEmpty()) type.toQualIdent(pos) else null,
        method = J.Identifier(pos, method),
        args = args,
    )
}

fun QualifiedName.staticMethod(vararg args: J.Expression, pos: Position): J.StaticMethodInvocationExpr {
    val (type, method) = this.split()
    return J.StaticMethodInvocationExpr(
        pos = pos,
        type = if (type.isNotEmpty()) type.toQualIdent(pos.leftEdge) else null,
        method = J.Identifier(pos.leftEdge, method),
        args = args.map { it.asArgument() },
    )
}

fun QualifiedName.staticField(pos: Position): J.StaticFieldAccessExpr {
    val (type, field) = this.split()
    return J.StaticFieldAccessExpr(
        pos = pos,
        type = type.toQualIdent(pos.leftEdge),
        field = J.Identifier(pos.leftEdge, field),
    )
}

/** Convenience method to wrap an expression in an ExpressionStatement. */
fun J.ExpressionStatementExpr.exprStatement(pos: Position = this.pos): J.ExpressionStatement =
    J.ExpressionStatement(pos, if (this is J.Operation && !makesStatement()) doNothing(this, pos) else this)

/** Convenience method to wrap an expression in an ExpressionStatement. */
fun J.ExpressionStatementExpr.exprOrReturnStatement(shouldReturn: Boolean, pos: Position = this.pos): J.Statement =
    if (shouldReturn) {
        J.ReturnStatement(pos, this)
    } else {
        this.exprStatement(pos)
    }

/** Convenience method to test if an expression is not null. */
fun J.Expression.testNotNull(pos: Position = this.pos) =
    JavaOperator.NotEquals.infix(this, J.NullLiteral(this.pos), pos = pos)

/** Convenience method to test if an expression is null. */
fun J.Expression.testNull(pos: Position = this.pos) =
    JavaOperator.Equals.infix(this, J.NullLiteral(this.pos), pos = pos)

/** Convenience method to cast to a type. */
fun J.Type.cast(expr: J.Expression, pos: Position = this.pos) =
    J.CastExpr(pos, type = this, expr = expr)

/** Forward to the operator enum. */
fun J.Operator.isPrefix() = operator.isPrefix()

/** Forward to the operator enum. */
fun J.Operator.isPostfix() = operator.isPostfix()

/** Forward to the operator enum. */
fun J.Operator.isInfix() = operator.isInfix()

/** Convenience method to construct infix binary operations. */
fun JavaOperator.infix(left: J.Expression, right: J.Expression, pos: Position = right.pos) =
    J.InfixExpr(pos, left, J.Operator(pos, this), right)

/** Convenience method to construct prefix unary operations. */
fun JavaOperator.prefix(value: J.Expression, pos: Position = value.pos) =
    J.PrefixExpr(pos, J.Operator(pos, this), value)

/** Take the complement of `value` and simplify if possible. */
fun simplifiedComplement(value: J.Expression, pos: Position = value.pos): J.Expression {
    if (value is J.Operation) {
        when (val oper = value.operator.operator) {
            JavaOperator.GreaterEquals, JavaOperator.LessEquals, JavaOperator.GreaterThan, JavaOperator.LessThan,
            JavaOperator.NotEquals, JavaOperator.Equals,
            ->
                return (value as J.InfixExpr).let { infix ->
                    val operComplement = when (oper) {
                        JavaOperator.GreaterEquals -> JavaOperator.LessThan
                        JavaOperator.LessEquals -> JavaOperator.GreaterThan
                        JavaOperator.GreaterThan -> JavaOperator.LessEquals
                        JavaOperator.LessThan -> JavaOperator.GreaterEquals
                        JavaOperator.NotEquals -> JavaOperator.Equals
                        JavaOperator.Equals -> JavaOperator.NotEquals
                        else -> error("Unexpected: $oper")
                    }
                    operComplement.infix(infix.left, infix.right, pos)
                }
            JavaOperator.BoolComplement -> return (value as J.PrefixExpr).expr
            else -> {}
        }
    }
    return JavaOperator.BoolComplement.prefix(value, pos)
}

/** Convenience method to construct prefix unary operations. */
fun JavaOperator.postfix(value: J.Expression, pos: Position = value.pos) =
    J.PostfixExpr(pos, value, J.Operator(pos, this))

/** Convenience method to construct inline assignments. */
fun JavaOperator.assign(left: J.LeftHandSide, right: J.Expression, pos: Position = left.pos) =
    J.AssignmentExpr(pos, left, J.Operator(pos, this), right)

/**
 * Construct a concatenation.
 * Note: in all Javas we target, a simple concatenation is optimized to invoking a StringBuilder.
 */
fun concat(first: J.StringLiteral, vararg exprs: J.Expression): J.Expression =
    exprs.fold(first as J.Expression) {
            a, b ->
        JavaOperator.Addition.infix(a, b)
    }

/** Check that a list of parameters has no arity parameter or exactly one arity parameter in the last position. */
fun Iterable<J.MethodParameter>.validArity(): Boolean =
    when (indexOfFirst { it is J.VariableArityParameter }) {
        -1, lastIndex -> true
        else -> false
    }

/** Check that a list of parameters has no arity parameter or exactly one arity parameter in the last position. */
fun Iterable<J.LambdaParameter>.lambdaValidArity(): Boolean =
    when (indexOfFirst { it is J.LambdaVarParam }) {
        -1, lastIndex -> true
        else -> false
    }

/** Convenience factory; create an identifier from a known output string and optional source. */
operator fun J.Identifier.Companion.invoke(pos: Position, text: String, source: Name? = null) =
    J.Identifier(pos, OutName(text, source))

fun J.Identifier.asNameExpr(pos: Position = this.pos) = J.NameExpr(pos, listOf(this.deepCopy()))

/** Find the path to a top-level AST. */
fun J.Program.filePathTo(): FilePath = programMeta.sourceDirectory.filePath + when (this) {
    is J.ModuleDeclaration -> filePath(moduleFileName)
    is J.PackageDeclaration -> this.packageStatement.asDirPath().resolveFile(packageFileName)
    is J.TopLevelClassDeclaration -> (this.packageStatement?.asDirPath() ?: FilePath.emptyPath)
        .resolveFile(this.classDef.name.outName.outputNameText + sourceFileExtension)
}

val J.Program.className get(): QualifiedName? = when (this) {
    is J.TopLevelClassDeclaration -> (packageName ?: QualifiedName.empty).qualify(classDef.name.outName)
    else -> null
}

val J.Program.packageName get(): QualifiedName? =
    packageStatement?.packageName?.let { QualifiedName.fromAst(it) }

/** Find a relative path implied by the package statement. */
fun J.PackageStatement.asDirPath(): FilePath = dirPath(packageName.ident.map { it.outName.outputNameText })

/** Wrap a statement in a block if needed. */
fun J.BlockLevelStatement.asBlock(pos: Position = this.pos): J.BlockStatement = when (this) {
    is J.BlockStatement -> this
    else -> J.BlockStatement(pos, listOf(this))
}

/** Wrap a statement in a block if needed, discarding an empty block. */
fun J.BlockLevelStatement.asBlockNullable(pos: Position = this.pos): J.BlockStatement? = when (this) {
    is J.BlockStatement -> if (body.isNotEmpty()) this else null
    is J.CommentLine -> null
    else -> J.BlockStatement(pos, listOf(this))
}

/** Wrap a list of statements in a block if needed, discarding an empty block. */
fun List<J.BlockLevelStatement>.asBlockNullable(pos: Position = firstOrNull()?.pos ?: unknownPos): J.BlockStatement? =
    when {
        isEmpty() -> null
        size == 1 -> first().asBlockNullable(pos)
        else -> J.BlockStatement(pos, this)
    }

/**
 * Removes the statements from this block and returns them.
 * Because they are removed, they may be reused in another Java AST node
 * because their parent pointer is null.
 */
fun J.BlockStatement.takeBody(): List<J.BlockLevelStatement> {
    val stmts = this.body.toList()
    this.body = emptyList() // Release from parent
    return stmts
}

/** Identifies if the given type is raw, meaning it has no type arguments or annotations. */
fun J.Type.isRaw(): Boolean = when (this) {
    is J.ClassType -> this.args == null && this.anns.isEmpty()
    is J.PrimitiveType -> true
    is J.ArrayType -> this.type.isRaw()
}

/** Pipe AST writing to an appendable using standard options. */
fun J.Tree.toAppendable(app: Appendable) {
    app.toAppenderViaTokenSink { sink ->
        CodeFormatter(sink).format(this@toAppendable, false)
    }
}

fun TmpL.BlockStatement?.isPureVirtual(): Boolean =
    isPureVirtual { it is TmpL.FnReference && it.id.name.simpleText() == temperPureVirtualStr }

/** Generic walk for ASTs. */
fun <T : OutTree<T>> T.walk() =
    dequeIterable { deque ->
        val node = deque.removeFirst()
        for (i in 0 until node.childCount) {
            deque.addLast(node.childOrNull(i) ?: continue)
        }
        node
    }

/**
 * Delegate class for [receiver].
 */
class Over<A, T>(private val block: A.() -> T) {
    private val map = mutableMapOf<Pair<A, String>, T>()

    operator fun getValue(thisRef: A, property: KProperty<*>) = synchronized(this) {
        map.computeIfAbsent(thisRef to property.name) { thisRef.block() }
    }
}

/**
 * Used like `by lazy`, but `this` is visible.
 *
 * Create lazy globals based on some configuration.
 *
 * ```kotlin
 * val MyEnum.someValue by receiver { "someValue(${this.name})" }
 * ```
 */
fun <A, T> receiver(initializer: A.() -> T): Over<A, T> = Over(initializer)

val <T> Iterable<T>.lastIndex get() = count() - 1

val testModuleName = ModuleName(filePath("test.temper"), 0, isPreface = true)
