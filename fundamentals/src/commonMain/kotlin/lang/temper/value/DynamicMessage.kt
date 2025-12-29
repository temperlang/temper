package lang.temper.value

import lang.temper.common.Either
import lang.temper.common.KBitSet
import lang.temper.common.Log
import lang.temper.common.bitIndices
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.env.Constness
import lang.temper.env.InterpMode
import lang.temper.log.FailLog
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.type2.AbstractValueFormal
import lang.temper.type2.AnySignature
import lang.temper.type2.AnyValueFormal
import lang.temper.type2.IValueFormal
import lang.temper.type2.ValueFormalKind

/** Bundles the parts of a function or method call used at runtime. */
data class DynamicMessage(val valueActuals: Actuals, val interpMode: InterpMode)

interface Arguments {
    fun toPositionalActuals(cb: InterpreterCallback): ActualValues?
    fun namedArguments(cb: InterpreterCallback): List<NamedArgument>?
    fun toFullyNamedActualsInOrder(cb: InterpreterCallback): Actuals
}

data class NamedArgument(
    val formalIndex: Int,
    /** TODO: maybe group the below into [lang.temper.env.DeclarationBits]? */
    val type: BaseReifiedType?,
    val initialValue: Value<*>?,
    val constness: Constness,
)

/**
 * This is used to decide whether, and if so, which part, of a multi-function applies to a specific
 * set of inputs represented by the [DynamicMessage].
 */
fun unify(
    dynamicMessage: DynamicMessage,
    signature: AnySignature,
    resolutions: Resolutions,
): Arguments? {
    if (resolutions.contradiction) {
        return null
    }

    val (valueActuals, interpMode) = dynamicMessage
    val valueFormals = signature.requiredAndOptionalValueFormals
    val restValuesFormal = signature.restValuesFormal

    // See if we can assign the actual input values we've got to the input parameters.
    // Step 1. Get the parameter ordering
    // Step 2. Pair actuals and formal parameters.  If there's a rest parameter, figure out which
    //         actuals contribute to the resty list.
    // Step 3. If everything is paired off, for each pair with an input, unify it with its formal.
    //         Do this in formal declaration order, not actual order.

    // Step 1: get order
    val actualOrderResult = applicationOrderForActuals(
        valueActuals.indices.map { i ->
            val symbol = valueActuals.key(i)
            if (symbol != null) {
                Either.Left(symbol)
            } else if (valueActuals.peekType(i) == TFunction) {
                Either.Right(TFunction)
            } else {
                null
            }
        },
        signature,
    )

    val actualOrder = when (actualOrderResult) {
        is Either.Right -> {
            resolutions.problem = actualOrderResult.item
            resolutions.contradict()
            return null
        }
        is Either.Left -> actualOrderResult.item
    }

    // Step 2: pair formals and actuals
    val restActualIndices = KBitSet()
    // formalActualPairs should match up with valueActuals order.
    val formalActualPairs = buildList {
        for (positionIndex in actualOrder.indices) {
            val actualIndex = actualOrder[positionIndex]
                ?: continue // Unsupplied optional parameter
            if (positionIndex in valueFormals.indices) {
                add(positionIndex to actualIndex)
            } else {
                restActualIndices.set(actualIndex)
            }
        }
    }

    // Step 3
    pairLoop@
    for ((formalIndex, actualIndex) in formalActualPairs) {
        val formal = valueFormals[formalIndex]
        resolutions.checkCompatible(formal, valueActuals, actualIndex, interpMode)
        if (resolutions.contradiction) {
            break
        }
    }
    if (restValuesFormal != null) {
        for (restActualIndex in restActualIndices.bitIndices) {
            resolutions.checkCompatible(restValuesFormal, valueActuals, restActualIndex, interpMode)
            if (resolutions.contradiction) {
                break
            }
        }
    }
    return if (resolutions.contradiction) {
        null
    } else {
        val formalIndexToActualIndex = formalActualPairs.toMap()
        return UnifiedArguments(
            valueFormals = valueFormals,
            restValuesFormal = restValuesFormal,
            valueActuals = valueActuals,
            interpMode = interpMode,
            formalIndexToActualIndex = formalIndexToActualIndex,
            restActuals = restActualIndices,
        )
    }
}

private class UnifiedArguments(
    val valueFormals: List<AnyValueFormal>,
    val restValuesFormal: AnyValueFormal?,
    val valueActuals: Actuals,
    val interpMode: InterpMode,
    val formalIndexToActualIndex: Map<Int, Int>,
    val restActuals: KBitSet,
) : Arguments {
    private val allFormalsCount
        get() = valueFormals.size + if (restValuesFormal != null) { 1 } else { 0 }

    private val allValueFormals get() = if (restValuesFormal != null) {
        valueFormals + restValuesFormal
    } else {
        valueFormals
    }

    private fun valueFor(
        formalIndex: Int,
        cb: InterpreterCallback,
    ): Pair<Result, Int?>? {
        if (restValuesFormal != null && formalIndex == valueFormals.size) {
            val elements = buildList {
                for (givenIndex in restActuals.bitIndices) {
                    add(
                        valueActuals.result(givenIndex, interpMode) as? Value<*>
                            ?: return@valueFor null,
                    )
                }
            }
            return Value(elements.toMutableList(), TList) to null
        }
        val formal = valueFormals[formalIndex]
        val givenIndex = formalIndexToActualIndex[formalIndex]
        var result: Result? = if (givenIndex != null) {
            valueActuals.result(givenIndex, interpMode) as? Value<*>
        } else {
            null
        }
        if (result == null || (formal.isOptional && result == TNull.value)) {
            // Pre-compute the default if a default expression is available.
            result = when (val init = formal.defaultExpr) {
                null ->
                    if (formal.isOptional) {
                        TNull.value
                    } else {
                        cb.explain(
                            MessageTemplate.NoValuePassed,
                            values = listOf(formal.symbol?.text ?: "<anonymous>"),
                        )
                        Fail
                    }
                else -> {
                    cb.apply(init, ActualValues.Empty, interpMode).or {
                        cb.explain(
                            MessageTemplate.FailedToComputeDefault,
                            values = listOf(formal.symbol?.text ?: "<anonymous>"),
                        )
                        Fail
                    }
                }
            }
        }
        return result to givenIndex
    }

    override fun toPositionalActuals(cb: InterpreterCallback): ActualValues? {
        val valueIndexPairs = (0 until allFormalsCount).map { formalIndex ->
            val (result, actualIndex) = valueFor(formalIndex, cb)
                ?: return@toPositionalActuals null
            if (result !is Value<*>) { return@toPositionalActuals null }
            result to actualIndex
        }
        return PositionalActuals(valueActuals, valueIndexPairs)
    }

    override fun namedArguments(cb: InterpreterCallback): List<NamedArgument>? {
        return allValueFormals.mapIndexed { formalIndex, formal ->
            val initial = if (
                formal.isOptional &&
                formal.defaultExpr == null &&
                formalIndex !in formalIndexToActualIndex &&
                formal.kind != ValueFormalKind.Rest
            ) {
                // It's ok if an optional value is not mapped.  See builtin isSet
                null
            } else {
                val valueAndActualIndex = valueFor(formalIndex, cb)
                val initial = valueAndActualIndex?.first
                if (initial !is Value<*>?) {
                    // If we have no value, and no way to derive one,
                    // report an error message and abort.
                    cb.explain(
                        MessageTemplate.MissingArgument,
                        values = listOf(formal.symbol?.text ?: "$formalIndex"),
                    )
                    return@namedArguments null
                }
                initial
            }
            val type = formal.reifiedType
            NamedArgument(
                formalIndex,
                type = type,
                initialValue = initial,
                constness = formal.constness,
            )
        }
    }

    override fun toFullyNamedActualsInOrder(cb: InterpreterCallback): Actuals {
        val cherryPicker = valueActuals.cherryPicker
        valueFormals.forEachIndexed { formalIndex, formal ->
            val actualIndex = formalIndexToActualIndex[formalIndex]
            if (actualIndex != null) {
                cherryPicker.add(actualIndex)
            } else if (formal.defaultExpr != null) {
                val resultAndActualIndex = valueFor(formalIndex, cb)
                if (resultAndActualIndex != null) {
                    cherryPicker.new(formal.symbol to resultAndActualIndex.first)
                }
            } else if (formal.isOptional) {
                cherryPicker.new(formal.symbol to TNull.value)
            }
        }
        return cherryPicker.build()
    }

    override fun toString() =
        "(UnifiedArguments $valueFormals $restValuesFormal $valueActuals $interpMode ${
            ""
        } $formalIndexToActualIndex $restActuals)"
}

class Resolutions(
    internal val callback: InterpreterCallback,
) : Structured {
    private var contradicted = false

    var problem: ResolutionProblem? = null

    fun checkCompatible(
        f: AbstractValueFormal,
        valueActuals: Actuals,
        actualIndex: Int,
        interpMode: InterpMode,
    ) {
        when (val t = f.reifiedType) {
            null -> {}
            is StructureExpectation -> {
                val tree = (valueActuals as? MacroActuals)?.valueTree(actualIndex)
                    ?: run {
                        this.problem = ResolutionProblem.TreeNotAvailable(actualIndex, f)
                        this.contradict()
                        return@checkCompatible
                    }
                this.unifyStructure(t, tree)
                if (this.contradiction) {
                    this.problem = ResolutionProblem.StructureExpectationMismatch(actualIndex, f)
                }
            }
            is ReifiedType -> {
                when (val actualResult = valueActuals.result(actualIndex, interpMode)) {
                    is Fail, NotYet -> {
                        // Value not available
                        this.problem = ResolutionProblem.ValueNotAvailable(actualIndex, f)
                        this.contradict()
                        return
                    }
                    is Value<*> -> {
                        // TODO: take an interpreter callback from outside so that explanations for
                        // type check failure end up on the fail log
                        if (f.isOptional && TNull.value == actualResult) {
                            // OK.
                        } else if (!t.valuePredicate(actualResult)) {
                            this.contradict()
                            this.problem = ResolutionProblem.TypeValueMismatch(
                                actualIndex,
                                f,
                                actualResult,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun unifyStructure(receiver: StructureExpectation, tree: Tree) {
        if (receiver.applicableTo(tree)) {
            // ok
        } else {
            contradict()
        }
    }

    fun contradict() {
        contradicted = true
    }

    val contradiction get() = contradicted

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("contradiction", if (contradicted) emptySet() else Hints.u) { value(contradicted) }
    }
}

sealed class ResolutionProblem : Structured {
    fun logTo(logSink: LogSink, pos: Position) = toLogEntry(pos).logTo(logSink)
    fun logTo(failLog: FailLog, pos: Position) = failLog.explain(toLogEntry(pos))
    abstract fun toLogEntry(pos: Position): LogEntry

    data class NamedArgumentMismatch(
        val actualIndex: Int,
        val formalIndex: Int?,
        val key: Symbol,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("NamedArgumentMismatch") }
            key("actualIndex") { value(actualIndex) }
            key("formalIndex") { value(formalIndex) }
        }

        override fun toLogEntry(pos: Position) =
            LogEntry(Log.Error, MessageTemplate.NoArgumentNamed, pos, listOf(key))
    }

    data class DuplicateName(
        val actualIndex: Int,
        val formalIndex: Int,
        val key: Symbol,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("DuplicateName") }
            key("actualIndex") { value(actualIndex) }
            key("formalIndex") { value(formalIndex) }
        }

        override fun toLogEntry(pos: Position) =
            LogEntry(Log.Error, MessageTemplate.TooManyArgumentsNamed, pos, listOf(key))
    }

    data class ArgumentListSizeMismatch(
        val nPositionalActuals: Int,
        val nFormals: Int,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("ArgumentListSizeMismatch") }
            key("nPositionalActuals") { value(nPositionalActuals) }
            key("nFormals") { value(nFormals) }
        }

        override fun toLogEntry(pos: Position) =
            LogEntry(Log.Error, MessageTemplate.ArityMismatch, pos, listOf(nFormals))
    }

    data class NoFormalForActual(
        val actualIndex: Int,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("NoFormalForActual") }
            key("actualIndex") { value(actualIndex) }
        }

        override fun toLogEntry(pos: Position) =
            LogEntry(Log.Error, MessageTemplate.ArityMismatch, pos, listOf("$actualIndex"))
    }

    data class StructureExpectationMismatch(
        val actualIndex: Int,
        val formal: IValueFormal,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("StructureExpectationMismatch") }
            key("actualIndex") { value(actualIndex) }
            key("type") { value(formal.type) }
        }

        override fun toLogEntry(pos: Position) = LogEntry(
            Log.Error, MessageTemplate.ExpectedStructure, pos,
            listOf("${formal.reifiedType}"),
        )
    }

    data class TypeValueMismatch(
        val actualIndex: Int,
        val formal: IValueFormal,
        val actualValue: Value<*>,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("TypeValueMismatch") }
            key("actualIndex") { value(actualIndex) }
            key("type") { value(formal.type.toString()) }
        }

        override fun toLogEntry(pos: Position) = LogEntry(
            Log.Error, MessageTemplate.ExpectedValueOfType, pos,
            listOf(formal.type!!, actualValue),
        )
    }

    data class TreeNotAvailable(
        val actualIndex: Int,
        val formal: AbstractValueFormal,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("TreeNotAvailable") }
            key("actualIndex") { value(actualIndex) }
            key("formal", Hints.u) { value(formal) }
        }

        override fun toLogEntry(pos: Position) =
            LogEntry(Log.Error, MessageTemplate.TooLateForMacro, pos, listOf())
    }

    data class ValueNotAvailable(
        val actualIndex: Int,
        val formal: AbstractValueFormal,
    ) : ResolutionProblem() {
        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value("ValueNotAvailable") }
            key("actualIndex") { value(actualIndex) }
            key("formal", Hints.u) { value(formal) }
        }

        override fun toLogEntry(pos: Position) =
            LogEntry(Log.Error, MessageTemplate.UnableToEvaluate, pos, listOf())
    }
}

private class PositionalActuals(
    val underlying: Actuals,
    val valueIndexPairs: List<Pair<Value<*>, Int?>>,
) : ActualValues {
    override fun result(index: Int, computeInOrder: Boolean) = valueIndexPairs[index].first
    override fun clearResult(index: Int) {
        // Nothing to do
    }
    override val size: Int get() = valueIndexPairs.size
    override fun key(index: Int): Symbol? = null // They're positional
    override fun pos(index: Int): Position? = when (val uIndex = valueIndexPairs[index].second) {
        null -> null
        else -> underlying.pos(uIndex)
    }

    override fun peekType(index: Int): TypeTag<*>? {
        val uIndex = valueIndexPairs[index].second
        return if (uIndex != null) {
            underlying.peekType(uIndex)
        } else {
            null
        }
    }
}
