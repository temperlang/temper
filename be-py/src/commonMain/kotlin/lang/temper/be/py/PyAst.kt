@file:Suppress("unused")

package lang.temper.be.py

import lang.temper.be.py.Py.ArgPrefix
import lang.temper.be.py.PyDottedIdentifier.Companion.dotted
import lang.temper.be.tmpl.TmpL
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.OutName
import lang.temper.value.DependencyCategory

private val noPrefix = ArgPrefix.None

operator fun Py.Arg.Companion.invoke(pos: Position, id: String, prefix: ArgPrefix = noPrefix): Py.Arg =
    Py.Arg(pos, id = PyIdentifierName(id), prefix = prefix)
operator fun Py.Arg.Companion.invoke(pos: Position, id: PyIdentifierName, prefix: ArgPrefix = noPrefix): Py.Arg =
    Py.Arg(pos, Py.Identifier(pos, id), prefix = prefix)

fun Py.Arguments.Companion.simple(pos: Position, vararg name: String): Py.Arguments =
    Py.Arguments.simple(pos, name.toList())

fun Py.Arguments.Companion.simple(pos: Position, names: List<String>): Py.Arguments =
    Py.Arguments(pos, names.map { Py.Arg(pos, it) })

fun Py.Assign.Companion.simple(name: Py.Name, init: Py.Expr) = Py.Assign(name.pos, targets = listOf(name), value = init)

fun Py.Assign.Companion.self(pos: Position, attr: String, init: Py.Expr) = Py.Assign(
    pos,
    targets = listOf(
        Py.Attribute(
            pos,
            Py.Name(pos, PyIdentifierName("self")),
            Py.Identifier(pos, PyIdentifierName(attr)),
        ),
    ),
    value = init,
)

operator fun BinaryOpEnum.invoke(left: Py.Expr, right: Py.Expr, pos: Position = left.pos) =
    Py.BinExpr(pos, left, this.atom(left.pos), right)
operator fun UnaryOpEnum.invoke(value: Py.Expr, pos: Position = value.pos) =
    Py.UnaryExpr(pos, this.atom(pos), value)

fun Py.Expr.call(vararg args: Py.Expr, pos: Position = this.pos): Py.Call =
    call(args.asIterable(), pos = pos)
fun Py.Expr.call(args: Iterable<Py.Expr>, pos: Position = this.pos) =
    Py.Call(pos, func = this, args = args.map { a -> Py.CallArg(a.pos, value = a) })

fun Py.Expr.attribute(name: String, pos: Position = this.pos) =
    this.attribute(OutName(name, sourceName = null), pos = pos)
fun Py.Expr.attribute(srcName: OutName, pos: Position = this.pos) =
    Py.Attribute(pos, value = this, attr = srcName.asPyId(this.pos))
fun Py.Expr.subscript(expr: Py.Expr, pos: Position = this.pos) = Py.Subscript(pos, value = this, slice = listOf(expr))
fun Py.Expr.method(name: String, vararg args: Py.Expr, pos: Position = this.pos) =
    Py.Call(pos, func = this.attribute(name, pos = pos), args = args.map { a -> Py.CallArg(a.pos, value = a) })
fun Py.Expr.method(name: String, args: List<Py.Expr>, pos: Position = this.pos) =
    Py.Call(pos, func = this.attribute(name, pos = pos), args = args.map { a -> Py.CallArg(a.pos, value = a) })
fun Py.Expr.stmt(pos: Position = this.pos) = Py.ExprStmt(pos, this)
fun Py.Expr.booleanNegate(pos: Position = this.pos): Py.Expr =
    when (this) {
        is Py.BinExpr -> when (this.op.opEnum) {
            // Don't do de Morgan's as it may change semantics of short-circuiting operators.
            // BinaryOpEnum.BoolOr -> BinaryOpEnum.BoolAnd(left.booleanNegate(), right.booleanNegate())
            // BinaryOpEnum.BoolAnd -> BinaryOpEnum.BoolOr(left.booleanNegate(), right.booleanNegate())
            BinaryOpEnum.Lt -> BinaryOpEnum.GtEq(left, right, pos = pos)
            BinaryOpEnum.Gt -> BinaryOpEnum.LtEq(left, right, pos = pos)
            BinaryOpEnum.Eq -> BinaryOpEnum.NotEq(left, right, pos = pos)
            BinaryOpEnum.GtEq -> BinaryOpEnum.Lt(left, right, pos = pos)
            BinaryOpEnum.LtEq -> BinaryOpEnum.Gt(left, right, pos = pos)
            BinaryOpEnum.NotEq -> BinaryOpEnum.Eq(left, right, pos = pos)
            BinaryOpEnum.In -> BinaryOpEnum.NotIn(left, right, pos = pos)
            BinaryOpEnum.NotIn -> BinaryOpEnum.In(left, right, pos = pos)
            BinaryOpEnum.Is -> BinaryOpEnum.IsNot(left, right, pos = pos)
            BinaryOpEnum.IsNot -> BinaryOpEnum.Is(left, right, pos = pos)
            else -> null
        }
        is Py.UnaryExpr -> when (this.op.opEnum) {
            UnaryOpEnum.BoolNot -> this.operand
            else -> null
        }
        is Py.Num -> PyConstant.bool(this.n == 0).at(pos)
        is Py.Str -> PyConstant.bool(this.s.isEmpty()).at(pos)
        is Py.Constant -> when (this.value) {
            PyConstant.Ellipsis -> PyConstant.False.at(pos)
            PyConstant.None -> PyConstant.True.at(pos)
            PyConstant.False -> PyConstant.True.at(pos)
            PyConstant.True -> PyConstant.False.at(pos)
            PyConstant.NotImplemented -> null
        }
        else -> null
    } ?: UnaryOpEnum.BoolNot(this, pos = pos)

/** Test whether a body of python statements need a pass appended. */
fun Iterable<Py.Stmt>.needsPass() = all { it is Py.CommentLine }

fun Py.Import.Companion.simple(
    pos: Position,
    module: PyDottedIdentifier,
    nameToImport: OutName,
    asName: OutName,
): Py.ImportStmt = Py.ImportFrom(
    pos,
    module.asAst(pos),
    listOf(Py.ImportAlias(pos, name = dotted(nameToImport).asAst(pos), asname = asName.asPyId(pos))),
)

fun pyIdent(pos: Position, name: String): Py.Identifier = Py.Identifier(pos, PyIdentifierName(name))

/**
 * This is faking a comma operator by constructing a tuple that can be released as soon as the subscript is
 * evaluated.
 *
 * We could be more aggressive in releasing prior expressions, for example:
 *
 *     ( (ex1,)[0:0], (ex2,)[0:0], (ex3,)[0:0], ... exN )[-1]
 *
 * In CPython, that's five interpreter instructions per expression and still generates garbage, so we're sticking
 * with the original.
 */
fun pyCommaOp(exprs: List<Py.Expr>, pos: Position = exprs[exprs.size - 1].pos): Py.Expr = when {
    exprs.size == 1 -> exprs[0]
    exprs.size > 1 ->
        Py.Tuple(exprs[0].pos, exprs.toList()).subscript(Py.Num(pos, exprs.size - 1), pos = pos)
    else -> error("Empty arguments")
}

fun pyCommaOp(vararg exprs: Py.Expr, pos: Position) = pyCommaOp(exprs.toList(), pos = pos)

private fun garbageString(pos: Position, src: String, diagnostic: String?) =
    Py.Str(pos, listOfNotNull(src, diagnostic).joinToString(": ", prefix = "<<", postfix = ">>"))

fun Py.Expr.annotate(msg: String, pos: Position = this.pos): Py.Expr =
    pyCommaOp(garbageString(pos, "annotate", msg), this, pos = pos)

fun garbageExpr(pos: Position, src: String, diagnostic: String?): Py.Expr =
    pyCommaOp(
        garbageString(pos, src, diagnostic),
        PyConstant.NotImplemented.at(pos),
        pos = pos,
    )
fun garbageExpr(pos: Position, d: TmpL.Diagnostic?): Py.Expr = garbageExpr(pos, "TmpL", d?.text)

fun garbageStmt(pos: Position, src: String, diagnostic: String?): Py.Stmt =
    Py.ExprStmt(pos, garbageString(pos, src, diagnostic))
fun garbageStmt(pos: Position, d: TmpL.Diagnostic?): Py.Stmt = garbageStmt(pos, "TmpL", d?.text)

/** Null programs are useful in [PyModule] to indicate when no code has been generated. */
fun Py.Program?.orEmpty(outputPath: FilePath): Py.Program =
    this ?: Py.Program(
        unknownPos,
        listOf(),
        DependencyCategory.Production,
        Genre.Library,
        outputPath = outputPath,
    )

fun Py.Program.addAfter(stmts: List<Py.Stmt>): Py.Program {
    body = body + stmts
    return this
}

fun Py.Program.addBefore(stmts: List<Py.Stmt>): Py.Program {
    body = stmts + body
    return this
}

fun Py.Tuple.Companion.simple(pos: Position, vararg exprs: Py.Expr): Py.Expr = Py.Tuple(pos, exprs.asList())

fun Py.With.Companion.simple(
    pos: Position,
    con: Py.Expr,
    asName: OutName?,
    body: List<Py.Stmt>,
): Py.With = Py.With(
    pos,
    items = listOf(
        Py.WithItem(
            pos,
            contextExpr = con,
            optionalVars = asName?.asPyName(pos),
        ),
    ),
    body = body,
)

fun Py.With.Companion.simple(
    pos: Position,
    con: OutName,
    asName: OutName?,
    body: List<Py.Stmt>,
): Py.With = Py.With.simple(pos, con.asPyName(pos).call(pos = pos), asName, body)

enum class ArgKind { This, Required, Rest, Optional }

fun TmpL.Parameters.forEachFormal(func: (pos: Position, name: TmpL.Id, type: TmpL.Type?, kind: ArgKind) -> Unit) {
    val thisName = this.thisName

    this.parameters.forEach {
        val kind = when {
            thisName == it.name -> ArgKind.This
            it.optional -> ArgKind.Optional
            else -> ArgKind.Required
        }
        func(it.pos, it.name, it.type.ot, kind)
    }

    this.restParameter?.let {
        func(it.pos, it.name, it.type.ot, ArgKind.Rest)
    }
}

/**
 * @param assign a function to run to assign the replacement for [statements]
 */
private fun processStatements(
    statements: Iterable<Py.Stmt>,
    act: (Py.Stmt) -> Iterable<Py.Stmt>?,
    assign: (List<Py.Stmt>) -> Unit,
) {
    var updated = false
    val result = statements.flatMap {
        val replacements = act(it)
        if (replacements != null) {
            updated = true
            replacements
        } else {
            listOf(it)
        }
    }
    if (updated) {
        assign(result)
    }
}

/**
 * for each nested statement runs [act], if all are null does not change it otherwise replaces that statements that were
 * non-null with the result
 */
fun Py.Stmt.replaceMany(act: (Py.Stmt) -> Iterable<Py.Stmt>?) {
    when (this) {
        is Py.FunctionDef -> {
            processStatements(body, act) { body = it }
        }
        is Py.ClassDef -> {
            processStatements(body, act) { body = it }
        }
        is Py.For -> {
            processStatements(body, act) { body = it }
            processStatements(orElse, act) { orElse = it }
        }
        is Py.While -> {
            processStatements(body, act) { body = it }
            processStatements(orElse, act) { orElse = it }
        }
        is Py.If -> {
            processStatements(body, act) { body = it }
            elifs.forEach { elif -> processStatements(elif.body, act) { elif.body = it } }
            processStatements(orElse, act) { orElse = it }
        }
        is Py.With -> {
            processStatements(body, act) { body = it }
        }
        is Py.Try -> {
            processStatements(body, act) { body = it }
            handlers.forEach { handler -> processStatements(handler.body, act) { handler.body = it } }
            processStatements(orElse, act) { orElse = it }
            processStatements(finalbody, act) { finalbody = it }
        }
        else -> {}
    }
}
