package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.NotYet
import lang.temper.value.TProblem
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.operatorSymbol
import lang.temper.value.valueContained

/**
 * <!-- snippet: builtin/@operator -->
 * # `@operator` decorator
 * The *\@operator* decorator applies to a function or method declaration
 * that implements an operator.
 *
 * For example, maybe you're defining an arbitrary precision integer type,
 * *BigInteger*, and its class includes methods that implement arithmetic.
 * You could add `@operator` decorators to the methods and then adding two
 * *BigIntegers* (and a *BigInteger* and a [snippet/type/Int32]) would
 * delegate semantics to that method.
 *
 * The string argument must be a valid operator text or an [snippet/operator-specifier].
 * If an operator text, then the operator kind is inferred from the decorated function
 * or methods arity (2 means infix, 1 means prefix).
 *
 * ```temper inert
 * /**
 *  * I just need an example of an operator definition.
 *  * This mirrors electrical-engineering notation.
 *  */
 * @operator("+") // Infix plus operator because arity is 2
 * let addingTwoBooleans(a: Boolean, b: Boolean): Boolean {
 *   a || b
 * }
 *
 * true + false
 * ```
 *
 * This decorator may be applied multiple times.
 *
 * ⎀ operator-specifier
 */
internal val operatorImplementationDecorator =
    MetadataDecorator(operatorSymbol, argumentTypes = listOf(Types.string)) { args ->
        val v = args.valueTree(1).valueContained
            ?: return@MetadataDecorator NotYet
        var specifier = TString.unpackOrNull(v)
        var op = specifier ?: ""
        var hasOperandBefore = op.startsWith("_").also {
            if (it) { op = op.drop(1) }
        }
        var hasOperandAfter = op.endsWith("_").also {
            if (it) { op = op.dropLast(1) }
        }

        // If we got an operator text instead of a specifier, infer the kind of operator
        // from the annotated function's maximum declared arity.
        if (op.isNotEmpty() && !hasOperandAfter && !hasOperandBefore) {
            val decorated = this.call?.childOrNull(1)
            if (decorated is DeclTree) {
                val initEdge = decorated.parts?.metadataSymbolMap[initSymbol]
                val init = initEdge?.let { lookThroughDecorations(it).target }
                // Guess based on arity
                val arity = (init as? FunTree)?.parts?.formals?.size
                when (arity) {
                    1 -> {
                        specifier = "${op}_"
                        hasOperandAfter = true
                    }
                    2 -> {
                        specifier = "_${op}_"
                        hasOperandBefore = true
                        hasOperandAfter = true
                    }
                    else -> {}
                }
            }
            if (!hasOperandBefore && !hasOperandAfter && stage < Stage.Define) {
                return@MetadataDecorator NotYet
            }
        }
        if ((hasOperandAfter || hasOperandBefore) && op.isNotEmpty()) {
            Value(specifier!!, TString)
        } else {
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.MalformedOperatorSpec,
                args.pos(1),
                listOf(v),
            )
            Value(problem, TProblem)
        }
    }
