package lang.temper.type2

import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.ignore
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.type.BubbleType
import lang.temper.type.MkType
import lang.temper.type.TypeContext
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.value.ReifiedType
import lang.temper.value.ResolutionProblem
import lang.temper.value.TType
import lang.temper.value.TypeReasonElement
import lang.temper.value.Value
import lang.temper.value.actualsListFromTree
import lang.temper.value.applicationOrderForActuals
import kotlin.math.min
import lang.temper.type.WellKnownTypes as WKT

fun inferBounds(
    typeContext: TypeContext,
    typeContext2: TypeContext2,
    solverVarNamer: SolverVarNamer,
    calls: MutableList<UntypedCall>,
    debug: Boolean,
) {
    ignore(debug)
    val solver = TypeSolver(typeContext = typeContext2, solverVarNamer = solverVarNamer)

    // For each call, we'll allocate some solver variables.
    // Collect them so we can refer back after we've got solutions.
    data class CallBundle(
        val call: UntypedCall,
        val calleeChoice: SimpleVar,
        val typeActuals: SimpleVar,
        val callPass: TypeVar,
        val callFail: SimpleVar?,
    )

    val callBundles = calls.map { call ->
        val calleeChoice = solver.unusedSimpleVar("callee")
        val explicitTypeArgs = call.explicitActuals?.map {
            hackMapOldStyleToNew(it.first, it.second)
        }

        // Keep a list of type variables for each type parameter
        // We do not precompute this, because callees can have different counts
        // of these.
        // Only in the case where we have a reification of a type parameter
        // do we fill this in, and in that case, we know a priori that the callees
        // all have that relationship.
        val typeArgVars = mutableListOf<TypeVar>()
        fun relateTypeVarToTypeActual(typeArgIndex: Int, typeArgVar: TypeVar) {
            while (typeArgVars.size < typeArgIndex) {
                typeArgVars.add(solverVarNamer.unusedTypeVar("T"))
            }
            if (typeArgIndex !in typeArgVars.indices) {
                typeArgVars.add(typeArgVar)
            } else {
                solver.sameAs(typeArgVar, typeArgVars[typeArgIndex])
            }
        }

        // For each actual, in position order, we'll need a type variable.
        val args = run {
            val argVars = mutableListOf<TypeVar>()
            val boundaries = mutableListOf<TypeBoundary?>()

            for (inputBound in call.inputBounds) {
                val (bound, solverVar) = when (inputBound) {
                    is InputBound.Pretyped -> inputBound.type to null
                    is InputBound.Typeless -> null to null
                    is InputBound.UntypedCallInput -> inputBound.passVar to null
                    is InputBound.ValueInput -> ValueBound(inputBound.value) to inputBound.typeVar
                    is InputBound.IncompleteReification -> {
                        // When solving `x as Foo`, we need to recognize that `Foo` might be
                        // a partial type.  We need to establish it as a bound for the type
                        // parameter that can be solved.
                        val (_, reifiedType, typeArgIndex, typeArgVar) = inputBound
                        relateTypeVarToTypeActual(typeArgIndex, typeArgVar)
                        val wellFormedBound = run {
                            val incomplete = reifiedType.type2
                            when (val defn = incomplete.definition) {
                                is TypeShape -> {
                                    val typeParameters = defn.typeParameters
                                    val n = incomplete.bindings.size
                                    val m = typeParameters.size
                                    if (n < m) {
                                        PartialType.from(
                                            defn,
                                            buildList bindings@{
                                                addAll(incomplete.bindings)
                                                val typeWord = defn.word?.text ?: ""
                                                for (i in size until m) {
                                                    val typeArgWord = typeParameters[i].definition.word?.text ?: "T$i"
                                                    val typeVar = solverVarNamer.unusedTypeVar(
                                                        "$typeWord$typeArgWord",
                                                    )
                                                    add(TypeVarRef(typeVar, Nullity.NonNull))
                                                }
                                            },
                                            incomplete.nullity,
                                            (incomplete as? PositionedType)?.pos,
                                        )
                                    } else {
                                        incomplete
                                    }
                                }
                                is TypeFormal -> incomplete // complete actually
                            }
                        }
                        solver.sameAs(wellFormedBound, typeArgVar)
                        inputBound.describedValueArgumentIndex?.let { i ->
                            boundaries[i]?.let { boundary ->
                                solver.relatesTo(wellFormedBound, boundary)
                            }
                        }
                        WKT.typeType2 to null
                    }
                }
                val arg = solver.unusedTypeVar("a")
                argVars.add(arg)
                boundaries.add(bound)
                if (bound != null) {
                    solver.assignable(arg, bound)
                }
                if (solverVar != null) {
                    solver.sameAs(bound!!, solverVar)
                }
            }
            argVars.toList()
        }
        val hasTrailingBlock = call.hasTrailingBlock
        val typeActuals = solver.unusedSimpleVar("Ts")
        val callPass = call.passVar ?: solver.unusedTypeVar("pass")
        val callees = call.calleeVariants
        val callFail = if (callees.any { it.sig.returnType2.definition == WKT.resultTypeDefinition }) {
            solver.unusedSimpleVar("fail")
        } else {
            null
        }
        call.contextType?.let { contextType ->
            solver.assignable(contextType, callPass)
        }
        solver.called(
            callees = callees,
            calleeChoice = calleeChoice,
            explicitTypeArgs = explicitTypeArgs,
            typeArgVars = typeArgVars,
            args = args,
            hasTrailingBlock = hasTrailingBlock,
            typeActuals = typeActuals,
            callPass = callPass,
            callFail = callFail,
        )
        CallBundle(call, calleeChoice, typeActuals, callPass = callPass, callFail = callFail)
    }

    solver.solve()

    for (callBundle in callBundles) {
        val call = callBundle.call
        val explanations = mutableListOf<TypeReasonElement>()
        val calleeIndex = (solver[callBundle.calleeChoice] as? IntSolution)?.n
        call.chosenCallee = calleeIndex

        val callees = call.calleeVariants
        val callee: Callee? = calleeIndex?.let { i ->
            call.calleeVariants[i]
        }
        if (callee == null && callees.isNotEmpty()) {
            val inputTypes = call.inputBounds.map { it.solvedType(solver) }
            explanations.add(
                TypeReason(
                    LogEntry(
                        Log.Error,
                        MessageTemplate.NoCalleeMatching,
                        call.callPosition, listOf(inputTypes, callees.map { it.sig }),
                    ),
                ),
            )
        }

        call.resultType = when (val solution = solver[callBundle.callPass]) {
            is Type2 -> {
                val passType = hackMapNewStyleToOld(solution)
                if (callee?.sig?.returnType2?.definition == WKT.resultTypeDefinition) {
                    MkType.or(passType, BubbleType)
                } else {
                    passType
                }
            }
            is Unsolvable? -> null
        }

        if (callee != null) {
            var calleeHasProblems = false
            when (val solution = solver[callBundle.typeActuals]) {
                is TypeListSolution -> {
                    val (sig) = callee
                    val n = min(solution.types.size, sig.typeFormals.size)
                    call.bindings = (0 until n).associate { i ->
                        val typeFormal = sig.typeFormals[i]
                        val actual2 = when (val solutionI = solution.types[i]) {
                            is Type2 -> solutionI
                            is Unsolvable -> WKT.invalidType2
                        }
                        val actualOldStyle = hackMapNewStyleToOld(actual2)
                        val outOfBounds = typeFormal.upperBounds.filter { upperBound ->
                            !typeContext.isSubType(actualOldStyle, upperBound)
                        }
                        if (outOfBounds.isNotEmpty()) {
                            explanations.add(
                                TypeReason(
                                    LogEntry(
                                        Log.Error,
                                        MessageTemplate.ActualNotInBounds,
                                        call.callPosition,
                                        listOf(typeFormal, actual2, outOfBounds),
                                    ),
                                ),
                            )
                            calleeHasProblems = true
                        }
                        typeFormal to actualOldStyle
                    }

                    val bindingMap = buildMap {
                        for ((t, f) in solution.types zip callee.sig.typeFormals) {
                            if (t is Type2) {
                                this[f] = t
                            }
                        }
                    }
                    val sigInContext = callee.sig.mapType(bindingMap)
                    val badArgIndices = mutableListOf<Int>()
                    val applicationOrderResult =
                        applicationOrderForActuals(actualsListFromTree(call.destination), callee.sig)
                    val applicationOrder = when (applicationOrderResult) {
                        is Either.Left -> applicationOrderResult.item
                        is Either.Right -> {
                            explanations.add(
                                ResolutionProblemReason(call.callPosition, applicationOrderResult.item),
                            )
                            calleeHasProblems = true
                            listOf()
                        }
                    }
                    for ((formalIndex, argIndex) in applicationOrder.withIndex()) {
                        if (argIndex == null) {
                            // Assume the default expression falls with the formal's type bounds.
                            continue
                        }
                        val inputBound = call.inputBounds[argIndex]
                        val formal = sigInContext.valueFormalForActual(formalIndex)
                        if (formal == null) {
                            calleeHasProblems = true
                            explanations.add(
                                TypeReason(
                                    LogEntry(
                                        Log.Error,
                                        MessageTemplate.ArityMismatch,
                                        inputBound.pos,
                                        listOf(call.inputBounds.size, sig.arityRange),
                                    ),
                                ),
                            )
                            badArgIndices.clear()
                            break
                        }

                        val argType = inputBound.solvedType(solver)
                        val expectedType = formal.type
                        if (argType.definition == WKT.nullTypeDefinition) {
                            // HACK: need to treat `null` inputs as value bounds so we know which null.
                            continue
                        }
                        if (argType.definition == WKT.invalidTypeDefinition) {
                            // Do not mask problem reported elsewhere
                            continue
                        }
                        if (!typeContext2.isSubType(argType, expectedType)) {
                            badArgIndices.add(argIndex)
                        }
                    }
                    if (badArgIndices.isNotEmpty()) {
                        calleeHasProblems = true
                        val argPositions = badArgIndices.map { call.inputBounds[it].pos }
                        val argTypes = call.inputBounds.map { it.solvedType(solver) }
                        val expectedTypes = call.inputBounds.indices.map {
                            sigInContext.valueFormalForActual(it)!!.type
                        }

                        explanations.add(
                            TypeReason(
                                LogEntry(
                                    Log.Error,
                                    MessageTemplate.SignatureInputMismatch,
                                    argPositions.spanningPosition(argPositions.first()),
                                    listOf(
                                        sig,
                                        expectedTypes,
                                        argTypes,
                                    ),
                                ),
                            ),
                        )
                    }
                }
                is Unsolvable? -> {
                    calleeHasProblems = true
                    val inputTypes = call.inputBounds.map { inputBound ->
                        inputBound.solvedType(solver)
                    }

                    explanations.add(
                        TypeReason(
                            LogEntry(
                                Log.Error,
                                MessageTemplate.TypeActualsUnavailable,
                                call.callPosition,
                                listOf(callee.sig, inputTypes, call.contextType ?: "unknown"),
                            ),
                        ),
                    )
                }
                is IntSolution, is TypeSolution -> error("$solution")
            }

            if (calleeHasProblems) {
                call.chosenCallee = null
            }
        }
        call.explanations = explanations.toList()

        // Store any solution for inputs that needed
        for (inputBound in call.inputBounds) {
            when (inputBound) {
                is InputBound.Pretyped,
                is InputBound.Typeless,
                -> {}
                // Handled by other call loop iterations
                is InputBound.UntypedCallInput -> {}
                // Store the type.
                is InputBound.ValueInput -> {
                    inputBound.valueSolvedType = inputBound.solvedType(solver)
                }
                is InputBound.IncompleteReification -> {
                    // Store any reified type for an `as` or `is` operator back in the tree.
                    val (_, reifiedType, _, typeVar, edge) = inputBound
                    val solution = solver[typeVar]
                    if (edge != null && solution is Type2) {
                        val incomplete = reifiedType.type2
                        if (incomplete.bindings.size < solution.bindings.size) {
                            edge.replace {
                                V(Value(ReifiedType(solution), TType), type = WKT.typeType)
                            }
                        }
                        // If there are too many bindings, leave it in place so we don't mask errors.
                    }
                }
            }
        }
    }
}

data class TypeReason(
    val logEntry: LogEntry,
) : TypeReasonElement {
    override fun logTo(logSink: LogSink) {
        logEntry.logTo(logSink)
    }

    override val pos: Position get() = logEntry.pos
}

data class ResolutionProblemReason(
    override val pos: Position,
    val resolutionProblem: ResolutionProblem,
) : TypeReasonElement {
    override fun logTo(logSink: LogSink) {
        resolutionProblem.logTo(logSink, pos)
    }
}
