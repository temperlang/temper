package lang.temper.be.elixir

import lang.temper.be.tmpl.TmpL
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid

/**
 * Turns one [TmpL.Module] into Elixir.
 *
 * A Temper module's top-level statements run when the module loads. Elixir
 * has no load-time code that a Mix project runs on start, so they become the
 * body of the entry function `mix run` calls, in order. That is
 * [mainBody].
 *
 * Unhandled nodes are `TODO()` carrying the node, on purpose: pick a
 * functional test, see what breaks, fill in the path it needs. A crash at
 * build time is a work item; plausible wrong output would be a bug that
 * hides.
 */
internal class ElixirTranslator(private val module: TmpL.Module) {

    /** Expressions that run, in order, when the program starts. */
    private val mainBody = mutableListOf<Elixir.BlockItem>()

    fun translateModule(): Translated {
        for (topLevel in module.topLevels) {
            processTopLevel(topLevel)
        }
        return Translated(mainBody = mainBody.toList())
    }

    data class Translated(val mainBody: List<Elixir.BlockItem>)

    // ── Top levels ───────────────────────────────────────────────────────

    private fun processTopLevel(topLevel: TmpL.TopLevel) {
        when (topLevel) {
            is TmpL.ModuleInitBlock -> for (statement in topLevel.body.statements) {
                translateStatementInto(statement, mainBody)
            }
            is TmpL.ModuleLevelDeclaration -> processModuleLevelDeclaration(topLevel)
            is TmpL.ModuleFunctionDeclaration -> TODO("module function: $topLevel")
            is TmpL.TypeDeclaration -> TODO("type declaration: $topLevel")
            is TmpL.Test -> TODO("test: $topLevel")
            // TypeConnection, PooledValueDeclaration, SupportCodeDeclaration,
            // comments and garbage carry no Elixir output, as in be-blimp.
            else -> {}
        }
    }

    private fun processModuleLevelDeclaration(decl: TmpL.ModuleLevelDeclaration) {
        if (decl.isConsole()) return
        TODO("module level declaration: $decl")
    }

    // ── Statements ───────────────────────────────────────────────────────

    private fun translateStatementInto(statement: TmpL.Statement, out: MutableList<Elixir.BlockItem>) {
        when (statement) {
            is TmpL.ExpressionStatement -> out.add(translateExpression(statement.expression))
            is TmpL.BlockStatement -> for (inner in statement.statements) translateStatementInto(inner, out)
            else -> TODO("statement: $statement")
        }
    }

    // ── Expressions ──────────────────────────────────────────────────────

    private fun translateExpression(expression: TmpL.Expression): Elixir.Expr = when (expression) {
        is TmpL.ValueReference -> translateValueReference(expression)
        is TmpL.CallExpression -> translateCallExpression(expression)
        else -> TODO("expression: $expression")
    }

    private fun translateCallExpression(call: TmpL.CallExpression): Elixir.Expr =
        when (val fn = call.fn) {
            // support code such as console.log becomes Elixir right here
            is TmpL.InlineSupportCodeWrapper ->
                (fn.supportCode as ElixirInlineSupportCode)
                    .callFactory(call.pos, call.parameters.map { translateActual(it) })

            else -> TODO("callable: $fn")
        }

    private fun translateActual(actual: TmpL.Actual): Elixir.Expr = when (actual) {
        is TmpL.Expression -> translateExpression(actual)
        else -> TODO("actual: $actual")
    }

    private fun translateValueReference(expression: TmpL.ValueReference): Elixir.Expr {
        val pos = expression.pos
        return when (val tag = expression.value.typeTag) {
            TBoolean -> Elixir.BoolLit(pos, TBoolean.unpack(expression.value))
            TFloat64 -> Elixir.NumberLit(pos, TFloat64.unpack(expression.value))
            TInt -> Elixir.NumberLit(pos, TInt.unpack(expression.value))
            TInt64 -> Elixir.NumberLit(pos, TInt64.unpack(expression.value))
            is TString -> Elixir.StringLit(pos, TString.unpack(expression.value))
            // RepresentationOfVoid.ReifyVoid: a void value really flows, and nil is it
            TNull, TVoid -> Elixir.NilLit(pos)
            is TClass, TClosureRecord, TFunction, TList, TListBuilder, TMap, TMapBuilder,
            TProblem, TStageRange, TSymbol, TType,
            -> TODO("value of type $tag: $expression")
        }
    }
}
