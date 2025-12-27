package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.NotNullFn
import lang.temper.builtin.RttiCheckFunction
import lang.temper.builtin.SETP_ARITY
import lang.temper.builtin.Types
import lang.temper.builtin.asReifiedType
import lang.temper.builtin.isHandlerScopeCall
import lang.temper.builtin.isNotNullCall
import lang.temper.builtin.isTypeAngleCall
import lang.temper.common.AtomicCounter
import lang.temper.common.Either
import lang.temper.common.KBitSet
import lang.temper.common.Log
import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.abbreviate
import lang.temper.common.asciiUnTitleCase
import lang.temper.common.buildListMultimap
import lang.temper.common.ignore
import lang.temper.common.logIf
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.common.removeMatching
import lang.temper.common.subListToEnd
import lang.temper.env.Environment
import lang.temper.format.logTokens
import lang.temper.format.toStringViaTokenSink
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleNamingContext
import lang.temper.frontend.adjustDeclarationMetadataWithSinglyAssignedHints
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.frontend.syntax.isAssignment
import lang.temper.interp.docgenalts.isPreserveCall
import lang.temper.interp.forEachActual
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.InternalModularName
import lang.temper.name.ModularName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.AndType
import lang.temper.type.BindMemberAccessor
import lang.temper.type.BubbleType
import lang.temper.type.DotHelper
import lang.temper.type.ExtensionResolution
import lang.temper.type.ExtraNonNormativeParameterInfo
import lang.temper.type.FunctionType
import lang.temper.type.GetMemberAccessor
import lang.temper.type.InstanceExtensionResolution
import lang.temper.type.InternalMemberAccessor
import lang.temper.type.InvalidType
import lang.temper.type.MemberOverride2
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.OrType
import lang.temper.type.PropertyShape
import lang.temper.type.SetMemberAccessor
import lang.temper.type.StaticExtensionResolution
import lang.temper.type.StaticPropertyShape
import lang.temper.type.StaticType
import lang.temper.type.SuperTypeTree
import lang.temper.type.TopType
import lang.temper.type.TypeActual
import lang.temper.type.TypeBindingMapper
import lang.temper.type.TypeContext
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.anyValueType
import lang.temper.type.WellKnownTypes.booleanType
import lang.temper.type.WellKnownTypes.booleanType2
import lang.temper.type.WellKnownTypes.functionType
import lang.temper.type.WellKnownTypes.invalidType2
import lang.temper.type.WellKnownTypes.voidType2
import lang.temper.type.addTypeNamesMentionedTo
import lang.temper.type.excludeBubble
import lang.temper.type.excludeNull
import lang.temper.type.excludeNullAndBubble
import lang.temper.type.isBubbly
import lang.temper.type.isNeverType
import lang.temper.type.isNullType
import lang.temper.type.mentionsInvalid
import lang.temper.type2.Callee
import lang.temper.type2.CalleePriority
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.InputBound
import lang.temper.type2.MkType2
import lang.temper.type2.PositionedType
import lang.temper.type2.Signature2
import lang.temper.type2.SolverVarNamer
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.TypeReason
import lang.temper.type2.TypeVar
import lang.temper.type2.UntypedCall
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackTryStaticTypeToSig
import lang.temper.type2.inferBounds
import lang.temper.type2.invalidSig
import lang.temper.type2.mapType
import lang.temper.type2.passTypeOf
import lang.temper.type2.withType
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BasicTypeInferencesTree
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.CallTypeInferencesTree
import lang.temper.value.CallableValue
import lang.temper.value.CoverFunction
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.LeftNameLeaf
import lang.temper.value.MacroValue
import lang.temper.value.NameLeaf
import lang.temper.value.NoTypeInferencesTree
import lang.temper.value.PreserveFn
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TNull
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.TypeInferencesHaver
import lang.temper.value.TypeReasonElement
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.factorySignatureFromConstructorSignature
import lang.temper.value.firstArgumentIndex
import lang.temper.value.fnSymbol
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.isNewCall
import lang.temper.value.isNullaryNeverCall
import lang.temper.value.isPureVirtualBody
import lang.temper.value.lookThroughDecorations
import lang.temper.value.nameContained
import lang.temper.value.optionalSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.superSymbol
import lang.temper.value.symbolContained
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.typeForFunctionValue
import lang.temper.value.typeForValue
import lang.temper.value.typeFromSignature
import lang.temper.value.typeSymbol
import lang.temper.value.unpackPairValue
import lang.temper.value.valueContained
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val DEBUG = false
private const val DEBUG_LATE_TYPE_CHECK = false

/**
 * Fills in [typeInferences][Tree.typeInferences] for each AST node under the root passed to
 * [Typer.type].
 *
 * For variables with declared type, the inferences are straightforward.
 *
 * For temporaries and other symbols the inferences are based on solving constraints between
 * reads and writes.
 *
 * For calls, result types are inferred based on inputs where possible, but otherwise take into
 * account context, so the call to `emptyList()`
 *
 *     let s: List<String> = emptyList();
 *
 * might relate the contextual type `List<String>` to its return type of `List<T>` to infer that
 * the instantiation of `<T>` for this call is `<String>`.
 *
 * For function definitions ([FunTree]s), the result type is a [FunctionType] based on the declared
 * types of inputs, outputs, and formal type parameters.  We assume that input parameters without a
 * type have type `AnyValue`.
 *
 * TODO: This implementation does not currently correctly type values that require contextual
 * information.  TODO: For values like tuples that contain a nested empty list, reverse engineer
 * a constructor call and use that to type the value.
 *
 * Any time it infers an [InvalidType][lang.temper.type.InvalidType] it also logs an error message
 * to [module]'s log sink. TODO: make this true
 */
internal class Typer(
    private val module: Module,
    /** An environment used to type builtins. */
    private val env: Environment,
) {
    // Mask global console so that debug trace doesn't spam for ImplicitsModule
    private val console = module.console

    private val typeContext = TypeContext()
    private val typeContext2 = TypeContext2()
    private lateinit var ti: TypingInfo
    private val solverVarNamer = SolverVarNamer.new()

    /** hsT */
    private val handlerScopeTypeFormal = BuiltinFuns.handlerScope.sigs!![0].typeFormals[0]

    // For convenience rather than recreating on demand when needed.
    private val reorderArgsHelper = ReorderArgs(root = module.generatedCode!!) {
        ti.decision(it)?.variant
    }

    private inner class InitializerInfo(
        /** Count down for initializers that still need to be counted. */
        val initializers: List<Tree>,
    ) {
        /** For each initializer, set if the initializer has been typed. */
        val checkedMask = KBitSet(initializers.size)

        /** Set true if we see an initializer that assigns `null` */
        var assignedNullable: Boolean = false

        init {
            for ((i, initializer) in initializers.withIndex()) {
                val assignment = initializer.incoming!!.source as CallTree
                if (ti.isDecided(assignment)) {
                    checkedMask.set(i)
                }
            }
        }

        /** True if we've marked all initializers as initialized. */
        fun allChecked() = checkedMask.nextClearBit(0) >= initializers.size

        override fun toString() =
            "InitializerInfo(typedMask=$checkedMask, assignedNullable=$assignedNullable)"
    }

    private data class Decision(
        val type: StaticType,
        val variant: FunctionType? = null,
        val bindings: Map<TypeFormal, TypeActual>? = null,
        val explanations: List<TypeReasonElement> = listOf(),
    )

    private inner class TypingInfo(val typerPlan: TyperPlan) {
        private val bindings = mutableMapOf<ResolvedName, StaticType>()
        private val decisions = mutableMapOf<Tree, Decision>()
        val initializerInfo = mutableMapOf<ResolvedName, InitializerInfo>()
        val lateTypedCalls = mutableListOf<LateTypedCall>()
        val callsRequirePostProcessing = mutableListOf<LateTypedCall>() // TODO: delete me?
        private val extensionReceiverTypes: Map<ResolvedName, List<StaticType>>

        private val callTreeToAliasedCall = typerPlan.aliasedCalls.values.associateBy {
            it.aliased
        }

        private val forwardingNames: Map<ResolvedName, List<ResolvedName>>

        init {
            val forwardingNames = mutableMapOf<ResolvedName, MutableList<ResolvedName>>()
            typerPlan.mayInferTypeForVariableFrom.forEach { (toName, fromNames) ->
                check(toName is ResolvedName)
                fromNames.forEach { fromName ->
                    forwardingNames.putMultiList(fromName as ResolvedName, toName)
                }
            }
            this.forwardingNames = forwardingNames.mapValues { it.value.toList() }
            this.extensionReceiverTypes = buildListMultimap {
                for (declTree in typerPlan.declarations.values) {
                    val parts = declTree.parts ?: continue
                    for (e in parts.metadataSymbolMultimap[staticExtensionSymbol] ?: emptyList()) {
                        val (t, _) = unpackPairValue(e.valueContained ?: continue) ?: continue
                        val rt = TType.unpackOrNull(t)
                        if (rt is ReifiedType) {
                            putMultiList(parts.name.content as ResolvedName, rt.type)
                        }
                    }
                }
            }
        }

        fun bind(name: ResolvedName, type: StaticType) {
            console.groupIf(DEBUG, "Binding $name: $type") {
                bindings.compute(name) { _, old ->
                    when (old) {
                        null -> type
                        else -> mergeTypeArgs(from = type, into = old)
                    }
                }
                // If this name directly implies the type of another, record that.
                for (forwardingName in forwardingNames[name] ?: emptyList()) {
                    if (forwardingName !in bindings) {
                        console.logIf(DEBUG) { "$name implies type of $forwardingName" }
                        bind(forwardingName, type)
                    }
                }
                // If name is of a typed member, then store the type with the member.
                val member = typerPlan.namesToLocalMemberShapes[name]
                if (member is VisibleMemberShape) {
                    console.logIf(DEBUG) {
                        "Storing type of $name with ${member.enclosingType.name}.${
                            member.symbol.text
                        }"
                    }
                    when (member) {
                        is PropertyShape -> member.descriptor = hackMapOldStyleToNew(type)
                        is MethodShape -> member.descriptor = hackTryStaticTypeToSig(type)
                        is StaticPropertyShape ->
                            member.descriptor =
                                if (fnSymbol in member.metadata) {
                                    hackTryStaticTypeToSig(type)
                                } else {
                                    hackMapOldStyleToNew(type)
                                }
                    }

                    val initializers = typerPlan.initializers[name]
                    if (member.parameterInfo == null && initializers?.size == 1 && initializers[0] is FunTree) {
                        // Store parameter names with method and function-like static property shapes
                        // so that, when generating adapters and overrides, we have ready access to
                        // name hints.
                        member.parameterInfo = (initializers[0] as? FunTree)?.parts?.let { funParts ->
                            ExtraNonNormativeParameterInfo(funParts)
                        }
                    }
                }
            }
        }

        fun decide(tree: Tree, type: StaticType) = decide(tree, Decision(type))
        fun decide(tree: Tree, decision: Decision) {
            decisions[tree] = decision
        }
        fun clearDecision(tree: Tree) {
            decisions.remove(tree)
        }

        fun isInsufficientlyTyped(name: ResolvedName) = bindings[name]?.hasUnbound() ?: true

        fun isUnbound(name: ResolvedName) = name !in bindings
        fun isDecided(tree: Tree) = tree in decisions
        fun isUndecided(tree: Tree) = !isDecided(tree)

        fun binding(name: ResolvedName) = bindings[name]
        fun decisionType(tree: Tree) = decisions[tree]?.type
        fun decision(tree: Tree) = decisions[tree]

        fun extensionReceiverTypes(name: ResolvedName) = extensionReceiverTypes[name] ?: emptyList()

        /**
         * The late-typed call corresponding to the given argument edge.
         * Late-typed calls can be aliased via a name or directly nested.
         */
        fun lateTypedCallArgument(argumentEdge: TEdge): LateTypedCall? {
            val argument = argumentEdge.target
            return if (argument is RightNameLeaf) {
                val name = argument.content as ResolvedName
                lateTypedCalls.firstOrNull {
                    it.aliasedCall?.alias == name
                }
            } else {
                lateTypedCalls.firstOrNull {
                    it.callTree.incoming == argumentEdge
                }
            }
        }

        fun sinkTypeOf(name: ResolvedName): StaticType? {
            val nameToReceiver = typerPlan.nameToReceiver
            val visited = mutableSetOf<ResolvedName>()
            val q = ArrayDeque<ResolvedName>()
            q.add(name)
            while (true) {
                val n = q.removeFirstOrNull() ?: break
                if (n in visited) { continue }
                visited.add(n)
                val t = binding(n)
                if (t != null) { return t }
                nameToReceiver[n]?.let {
                    for (next in it) {
                        q.add(next as ResolvedName)
                    }
                }
            }
            return null
        }

        fun aliasedCallFor(call: CallTree): TyperPlan.AliasedCall? = callTreeToAliasedCall[call]

        val bindingsMap: Map<ResolvedName, StaticType> get() = bindings
    }

    fun type(root: BlockTree): Map<ResolvedName, StaticType> {
        if (DEBUG) {
            console.group("Before Typing") {
                root.toPseudoCode(console.textOutput)
            }
        }

        // Make sure any temporaries introduced by the weaver or other micro-passes are marked SSA
        // so the typer can adopt initializer types for unguarded declarations.
        adjustDeclarationMetadataWithSinglyAssignedHints(root)

        val typerPlan = TyperPlan(root, module.outputName)
        // Clean up any type inferences from previous typer runs.
        // This allows us to skip re-typing in typeSubTrees while keeping initializer counts in
        // sync with the count of initializers that have no inferences yet.
        for (tree in typerPlan.typeOrder) {
            if (!tree.isMetadataValue) {
                tree.clearTypeInferences()
            }
        }

        ti = TypingInfo(typerPlan)
        if (DEBUG) {
            console.group("Initializers") {
                typerPlan.initializers.forEach { (k, v) ->
                    console.log("- $k -> ${v.joinToString { abbreviate(it.toPseudoCode()) }}")
                }
            }
        }
        // Pull out declared types
        for ((name) in typerPlan.declarations) {
            val declaredType = typerPlan.declaredType(name)
            if (declaredType != null) {
                val type = if (name in typerPlan.returnNames) {
                    // A return declaration, unlike an input or local declaration,
                    // can be declared as (X throws Bubble).
                    // The local variable to hold the result cannot hold a Bubble because that is
                    // not a real value.
                    MkType.and(declaredType, anyValueType) // Mask out Bubble
                } else {
                    declaredType
                }
                ti.bind(name as ResolvedName, type)
            }
        }

        if (DEBUG) {
            console.group("Expressions used in conditions") {
                typerPlan.usedAsCondition.forEach {
                    console.log("- ${abbreviate(it.target.toPseudoCode())}")
                }
            }
        }

        // If a name is used directly in a condition, then its type must be Boolean.
        val namesUsedAsConditions = typerPlan.usedAsCondition.mapNotNull {
            (it.target as? RightNameLeaf)?.content as ResolvedName?
        }.toSet()
        fun maybePreTypeAsBoolean(name: ResolvedName) {
            if (name in namesUsedAsConditions && ti.isUnbound(name) && name is ModularName) {
                console.groupIf(DEBUG, "Pre-typed name used as a condition") {
                    ti.bind(name, booleanType)
                }
            }
        }

        for ((name, initializers) in typerPlan.initializers) {
            check(name is ResolvedName)

            val canPreType = ti.isUnbound(name) && initializers.all {
                // For values and functions, we can often compute a type without context.
                // Functions may be used before their definition, as in co-recursive functions,
                // so typing them and pure function values, early, disentangles problems.
                // We could pre-type functions only if `!shouldDeferFunTreeTyping` but in GenerateCodeStage, we
                // lose previous type information here by skipping, and in TypeStage, lambdas as arguments don't show
                // among initializers, so we don't get them here wrongly.
                it is ValueLeaf || it is FunTree || (it is CallTree && isPreserveCall(it))
            }
            if (canPreType) {
                console.groupIf(DEBUG, "Attempting to pre-type $name") {
                    var allTyped = true
                    var hasInvalid = false
                    val types = mutableListOf<StaticType>()
                    initializers.mapNotNullTo(types) { initializer ->
                        when (initializer) {
                            is ValueLeaf -> preTypeValueLeaf(initializer)
                            is FunTree -> preTypeFunTree(initializer)
                            is CallTree -> typePreserveCall(initializer)
                            else -> error(initializer)
                        }
                        when (val preType = ti.decisionType(initializer)) {
                            null -> {
                                allTyped = false
                                null
                            }
                            InvalidType -> {
                                hasInvalid = true
                                null
                            }
                            else -> preType
                        }
                    }
                    if (allTyped) {
                        if (types.isEmpty() && hasInvalid) {
                            types.add(InvalidType)
                        }
                        console.logIf(DEBUG) { "Pre-typed $name to $types" }
                        ti.bind(name, typeContext.simpleOr(types.toList()))
                    }
                    val member = typerPlan.namesToLocalMemberShapes[name]
                    if (member is MethodShape && initializers.size == 1) {
                        // If it's pure virtual, mark it as such.
                        // We need to not extract pure virtual methods to
                        // functions.
                        val initializer = lookThroughDecorations(
                            initializers.first().incoming!!,
                        ).target
                        if (initializer is FunTree) {
                            val body = initializer.parts?.body
                            if (body.isPureVirtualBody()) {
                                member.isPureVirtual = true
                            }
                        }
                    }
                }
            }

            maybePreTypeAsBoolean(name)

            if (ti.isInsufficientlyTyped(name)) {
                ti.initializerInfo[name] = InitializerInfo(initializers)
            }
        }
        namesUsedAsConditions.forEach { maybePreTypeAsBoolean(it) }

        if (DEBUG) {
            console.group("Initializer Count downs") {
                ti.initializerInfo.forEach { (k, v) ->
                    console.log("- $k -> ${v.initializers.size - v.checkedMask.cardinality()}")
                }
            }
        }

        // Make sure we have types for properties so that we can type property reads.
        console.groupIf(DEBUG, "Make sure properties have types") {
            for ((name, member) in typerPlan.namesToLocalMemberShapes) {
                check(name is ResolvedName)
                val enclosingType = member.enclosingType
                val decl = typerPlan.declarations[name]
                val parts = decl?.parts ?: continue
                if (member is PropertyShape) {
                    if (parts.type == null && ti.isUnbound(name)) {
                        // Try to infer from a (possibly inherited) getter
                        val getter = enclosingType.membersMatching(member.symbol).firstOrNull {
                            it is MethodShape && it.methodKind == MethodKind.Getter && run {
                                val sig = it.descriptor
                                sig != null && sig.typeFormals.isEmpty()
                            }
                        } as MethodShape?
                        val propertyType =
                            getter?.descriptor?.returnType2?.let {
                                hackMapNewStyleToOld(passTypeOf(it))
                            }
                                // TODO(mikesamuel, static-checks): Truly typeless properties are
                                // an anti-pattern.  Maybe forbid them.
                                ?: anyValueType
                        if (getter != null) {
                            console.logIf(DEBUG) {
                                "Assuming type for ${enclosingType.name}.$name from getter ${
                                    getter.enclosingType.name
                                }.${getter.name}: ${getter.descriptor}"
                            }
                        }
                        ti.bind(name, propertyType)
                    }
                }
            }
        }

        // Make sure that we know which members of locally declared types, override
        // which members of their super types.
        console.groupIf(DEBUG, "Make sure methods have overrides") {
            for (member in typerPlan.namesToLocalMemberShapes.values) {
                if (member is VisibleMemberShape && member.overriddenMembers == null) {
                    linkOverrides(member, typeContext2, module.logSink)
                    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
                    if (DEBUG && !member.overriddenMembers.isNullOrEmpty()) {
                        console.group(
                            "Overridden by ${member.enclosingType.name}.${member.name}: ${member.descriptor}",
                        ) {
                            member.overriddenMembers?.forEach { mo: MemberOverride2 ->
                                console.log(
                                    "- ${mo.superTypeMember.enclosingType.name}.${
                                        mo.superTypeMember.name
                                    }: ${mo.superTypeMemberTypeInSubTypeContext}",
                                )
                            }
                        }
                    }
                }
            }
        }

        for (tree in typerPlan.typeOrder) {
            if (ti.isDecided(tree)) { continue } // Don't redundantly type pre-typed trees
            @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
            val subTreeDesc = if (DEBUG && !tree.typingIsSpammyInLogs) {
                "${tree.treeType.name} `${abbreviate(tree.toPseudoCode())}`"
            } else {
                null
            }
            @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
            console.groupIf(
                DEBUG && subTreeDesc != null,
                "Typing $subTreeDesc",
            ) {
                typeSubTree(tree)
                if (DEBUG) {
                    val decision = ti.decision(tree)
                    if (decision != null && subTreeDesc != null) {
                        console.log("-> $decision")
                    }
                }
            }
        }

        // Assume a binding for any names that lack them.  This affects declarations of temporaries
        // that have been found to be not useful, but also affects declarations in user code that
        // are neither read nor initialized.
        for (name in ti.typerPlan.declarations.keys) {
            if (name is InternalModularName && ti.isUnbound(name)) {
                ti.bind(name, TopType)
            }
        }

        if (DEBUG) {
            console.group("Before fill in blanks") {
                console.group("Bindings") {
                    console.logMulti(
                        TextTable(
                            listOf("name", "binding").map { MultilineOutput.of(it) },
                            ti.bindingsMap.map {
                                listOf(
                                    MultilineOutput.of(it.key.toString()),
                                    MultilineOutput.of(it.value.toString()),
                                )
                            },
                        ),
                    )
                }
                console.group("Before fill in blanks") {
                    root.toPseudoCode(console.textOutput, detail = PseudoCodeDetail(true))
                }
            }
        }

        fillInBlanksAndRecheckAssignments(root)

        if (DEBUG) {
            console.group("Before rewrite fail vars of ${root.pos.loc.diagnostic}") {
                root.toPseudoCode(console.textOutput, detail = PseudoCodeDetail(true))
            }
        }

        // Store decisions with nodes
        storeDecisionsInTree(root, skipStored = false)

        // Replace failure branch condition variables with `false` where type info shows it is safe.
        rewriteUsingTypeInformation(root, singlyAssigned = typerPlan.singlyAssigned)

        if (DEBUG) {
            console.group("After type check of ${root.pos.loc.diagnostic}") {
                root.toPseudoCode(console.textOutput, detail = PseudoCodeDetail(true))
                console.log(root.toLispy(multiline = true, includeTypeInfo = true))
            }
        }

        return ti.bindingsMap
    }

    private fun storeDecisionsInTree(tree: Tree, skipStored: Boolean) {
        TreeVisit.startingAt(tree)
            .forEachContinuing { t ->
                val decision = ti.decision(t)
                if (decision == null && !(t.needsTypeInfo && t.typeInferences == null && !t.isMetadataValue)) {
                    return@forEachContinuing
                }
                if (skipStored && t.typeInferences != null) {
                    return@forEachContinuing
                }
                when (t) {
                    is NoTypeInferencesTree -> {}
                    is BasicTypeInferencesTree -> if (decision != null) {
                        t.typeInferences = BasicTypeInferences(decision.type, decision.explanations)
                    } else if (t.needsTypeInfo && !t.isMetadataValue) {
                        val (e, type) = invalidBecauseMissingType(t, "expr")
                        t.typeInferences = BasicTypeInferences(type, listOf(e))
                    }
                    is CallTypeInferencesTree -> if (decision != null) {
                        t.typeInferences = CallTypeInferences(
                            decision.type,
                            decision.variant ?: InvalidType,
                            decision.bindings ?: emptyMap(),
                            decision.explanations,
                        )
                    } else if (t.needsTypeInfo) {
                        val (e, type) = invalidBecauseMissingType(t, "expr")
                        t.typeInferences = CallTypeInferences(
                            type,
                            InvalidType,
                            emptyMap(),
                            listOf(e),
                        )
                    }
                }
            }
            .visitPreOrder()
    }

    private fun typeSubTree(tree: Tree) {
        when (tree) {
            is BlockTree -> {
                // TODO: After we've eliminated failing branches, we can see whether any bubbles escape is
                // reachable from the start, so know whether the type is (Void throws Bubble) or (Void)
            }
            is CallTree -> typeCallTree(tree)
            is DeclTree -> {
                // Declarations produce `void` which has nothing to do with the type of the
                // declared name.
                ti.decide(tree, WellKnownTypes.voidType)
                // We already pulled the type info out for the name, so no need to update bindings.
            }
            is EscTree -> Unit
            is FunTree -> if (ti.isUndecided(tree)) { // May have been pre-typed
                preTypeFunTree(tree)
            }
            is StayLeaf -> Unit
            is LeftNameLeaf -> Unit // Fill in declarations and assignment lefts later.
            is RightNameLeaf -> {
                val name = tree.content as ResolvedName
                var typeBinding = ti.binding(name)
                if (typeBinding == null) {
                    // We might have typed some initializers but not others.
                    // We should assume at least a partial type because a name may be read after
                    // we reach initializers that can precede it but after initializers that are
                    // on other branches.
                    // This makes us sensitive to the order parallel branches are visited in which
                    // is sub-ideal.
                    val initializers = ti.typerPlan.initializers[name]
                        ?.mapNotNull { ti.decisionType(it) }
                    if (!initializers.isNullOrEmpty()) {
                        typeBinding = typeContext.simpleOr(initializers)
                    }
                }
                if (typeBinding != null) {
                    ti.decide(tree, typeBinding)
                } else if (name !is InternalModularName) {
                    preTypeNonLocalNames(tree)
                } else {
                    // This is probably a use-before-initialization problem.
                    // Fill in later, possibly with InvalidType.
                }
            }
            is ValueLeaf -> if (ti.isUndecided(tree)) { // May have been pre-typed
                var type = typeForValue(tree.content)
                if (type == null) {
                    // If we have a value, like `null`, which does not have a unique type, and
                    // it's directly in a block, we'll never get context, so just pick a nullable
                    // type for it.
                    if (isNullValueLeaf(tree) && tree.incoming?.source is BlockTree) {
                        type = MkType.nullable(WellKnownTypes.emptyType)
                    }
                }
                if (type != null) {
                    ti.decide(tree, type)
                }
            }
        }
    }

    private fun typeCallTree(tree: CallTree) {
        if (isAssignment(tree)) {
            typeAssignmentCall(tree)
        } else if (isHandlerScopeCall(tree)) {
            typeHandlerScopeCall(tree)
        } else if (isNotNullCall(tree)) {
            typeNotNullCall(tree)
        } else if (isTypeAngleCall(tree)) {
            typeTypeAngleCall(tree)
        } else if (isPreserveCall(tree)) {
            typePreserveCall(tree)
        } else {
            typeRegularCall(tree)
        }
    }

    private fun typeRegularCall(tree: CallTree) {
        fixupCalleeType(tree)

        val priorProblems = mutableListOf<TypeReasonElement>()
        val isNew = isNewCall(tree)
        var effectiveCallee = if (isNew) {
            tree.child(1)
        } else {
            tree.childOrNull(0)
        }

        val calleeFn = effectiveCallee?.functionContained
        val coverFn = calleeFn as? CoverFunction
        val inputTrees = tree.children.subListToEnd(tree.firstArgumentIndex)

        val isDotBind = calleeFn is DotHelper && calleeFn.memberAccessor is BindMemberAccessor

        // Does the context in which the call happens place bounds on the return type?
        // For example, if t is the right hand of the assignment, then we might be able to
        // get information about the left-hand side.
        val contextType: Lazy<Type2?> = lazy findContext@{
            var incoming = tree.incoming
            while (true) {
                val ancestor = incoming?.source

                if (ancestor is CallTree) {
                    if (isHandlerScopeCall(ancestor) && incoming == ancestor.edge(2)) {
                        // Keep looking upwards
                        incoming = ancestor.incoming ?: break
                        continue
                    } else if (isAssignment(ancestor) && incoming == ancestor.edge(2)) {
                        // We're the right-hand side.
                        val left = ancestor.child(1)
                        if (left is LeftNameLeaf) {
                            val leftName = left.content as ResolvedName
                            val deduction = ti.binding(leftName)
                            if (deduction != null) {
                                // If we know about that name, great.
                                return@findContext hackMapOldStyleToNew(deduction)
                            }
                        }
                        // Keep looking upwards.  Assignments can nest.
                        incoming = ancestor.incoming ?: break
                        continue
                    }

                    // A regular call.  If we have already solved its signature, use the
                    // argument type as a context type.
                    val decision = ti.decision(ancestor)
                    val variant = decision?.variant
                    val sig = variant?.let { hackTryStaticTypeToSig(it) }
                    if (sig != null) { // TODO: !mentionsInvalid
                        val edgeIndex = ancestor.edges.indexOf(incoming)
                        val argIndex = edgeIndex - ancestor.firstArgumentIndex
                        if (argIndex >= 0) { // is an argument, not the callee
                            val formal = sig.valueFormalForActual(argIndex)?.type
                            if (formal != null) {
                                return@findContext formal.mapType(
                                    decision.bindings?.mapValues { hackMapOldStyleToNew(it as StaticType) }
                                        ?: mapOf(),
                                )
                            }
                        } // TODO: else use the variant as a context type for the callee
                    }
                } else if (ancestor is BlockTree) {
                    // If the parent is a block with a complex flow, and the expression is
                    // used in a branch condition, the context type is Boolean.
                    if (incoming in ti.typerPlan.usedAsCondition) {
                        return@findContext booleanType2
                    }
                    if (isNullaryNeverCall(tree)) {
                        // Bubble and panic calls can appear top level to cause an
                        // immediate jump out of the containing function or module body.
                        val blockParent = ancestor.incoming?.source
                        if (blockParent == null) { // Module root
                            val moduleResultName = module.outputName
                            if (moduleResultName != null) {
                                val moduleResultType = ti.binding(moduleResultName)
                                if (moduleResultType != null) {
                                    return@findContext hackMapOldStyleToNew(excludeBubble(moduleResultType))
                                }
                            } else {
                                return@findContext voidType2
                            }
                        } else if (blockParent is FunTree) {
                            val returnType =
                                blockParent.parts?.returnDecl?.parts?.metadataSymbolMap[typeSymbol]
                                    ?.reifiedTypeContained
                            if (returnType != null) {
                                var returnTypeUnwrapped = returnType.type2
                                if (returnTypeUnwrapped.definition == WellKnownTypes.resultTypeDefinition) {
                                    returnTypeUnwrapped =
                                        returnTypeUnwrapped.bindings.getOrNull(0) ?: invalidType2
                                }
                                return@findContext returnTypeUnwrapped
                            }
                            // We should really try to tie this to the inferred return type.
                            return@findContext voidType2
                        }
                    }
                }
                break
            }
            null
        }

        val explicitActualTypesAndPositions: List<Pair<StaticType, Position>>? =
            if (effectiveCallee is CallTree && isTypeAngleCall(effectiveCallee)) {
                // If the callee is a function to which `<>` is applied, then pull the effective
                // callee out and store the type actuals.
                val typeActuals = effectiveCallee.children.subListToEnd(2)
                effectiveCallee = effectiveCallee.child(1)
                typeActuals.map { explicitActual ->
                    val type = explicitActual.staticTypeContained ?: InvalidType
                    type to explicitActual.pos
                }
            } else if (isNew && effectiveCallee != null) {
                // Extract explicit type actuals from a reifiedType that has them.
                val reifiedType = effectiveCallee.reifiedTypeContained
                val nominalType = reifiedType?.type as? NominalType
                val probablyHasExplicitTypeActuals = nominalType != null &&
                    nominalType.bindings.size == nominalType.definition.formals.size &&
                    // `new Foo<*>()` is illegal.  You can't put a wildcard in a `new` expression.
                    nominalType.bindings.all { it is StaticType } &&
                    reifiedType.hasExplicitActuals
                if (probablyHasExplicitTypeActuals) {
                    val calleeRight = effectiveCallee.pos.rightEdge
                    nominalType.bindings.map { (it as StaticType) to calleeRight }
                } else {
                    null
                }
            } else {
                null
            }

        val effectiveCalleeType = effectiveCallee?.let { ti.decisionType(it) }?.let { inferredType ->
            if (isNew) {
                inferredType // Probably just Type.  Handled below.
            } else if (inferredType is NominalType && functionalInterfaceSymbol in inferredType.definition.metadata) {
                inferredType
            } else { // Extract function types
                distributeCalleeTypeThroughIntersection(
                    coerceCalleeTypeToIntersectionOfFunctionTypes(inferredType).also { coerced ->
                        if (isDotBind) {
                            calleeFn.sigs = buildList {
                                explodeCalleeType(coerced, CalleePriority.Default)
                            }.map {
                                it.sig
                            }
                        }
                    },
                )
            }
        }
        if (effectiveCalleeType is FunctionType && isDotBind && calleeFn.sigs != null) {
            // We stored the sigs above.  The call wrapping this needs those.
            val decision = Decision(
                type = effectiveCalleeType.returnType,
                variant = effectiveCalleeType,
                explanations = priorProblems.toList(),
            )
            ti.decide(tree, decision)
            return
        }

        val calleeVariants: List<Callee> = buildSet {
            if (isNew) {
                val constructorSigs = when (effectiveCalleeType) {
                    null -> {
                        // Late-typed constructor reference
                        return
                    }
                    WellKnownTypes.typeType -> {
                        @Suppress("SENSELESS_COMPARISON")
                        check(effectiveCallee != null) // because the type is not null above
                        val variants =
                            constructorVariantsForTypeReference(effectiveCallee)
                        val (type, explanations) =
                            typeForConstructorVariants(effectiveCallee.pos, variants)
                        priorProblems.addAll(explanations)

                        type
                    }
                    else -> listOf()
                }
                constructorSigs.mapNotNullTo(this) { constructorSig ->
                    factorySignatureFromConstructorSignature(constructorSig)?.let {
                        Callee(it, CalleePriority.Default)
                    }
                }
            } else if (coverFn != null) {
                val (mainCalleeType, fallbackCalleeType) = splitCoverFnType(coverFn)
                explodeCalleeType(mainCalleeType, CalleePriority.Default)
                fallbackCalleeType?.let { explodeCalleeType(it, CalleePriority.Fallback) }
            } else if (calleeFn?.sigs != null) {
                calleeFn.sigs?.mapNotNullTo(this) { anySig ->
                    (anySig as? Signature2)?.let { Callee(it, CalleePriority.Default) }
                }
            } else if (effectiveCalleeType != null) {
                explodeCalleeType(effectiveCalleeType, CalleePriority.Default)
            }
        }.toList()

        typeCallByParts2(
            priorProblems = priorProblems,
            calleeVariants = calleeVariants,
            callTree = tree,
            isRttiCall = calleeFn is RttiCheckFunction,
            typeActualsAndPositions = explicitActualTypesAndPositions,
            inputTrees = inputTrees,
            contextType = contextType.value,
        )
    }

    private val splitCoverFnCache = mutableMapOf<CoverFunction, Pair<StaticType, StaticType?>>()

    /** Split the fallback function type from the main cover function variants. */
    private fun splitCoverFnType(coverFunction: CoverFunction) =
        splitCoverFnCache.getOrPut(coverFunction) {
            MkType.and(
                coverFunction.covered.map {
                    typeForFunctionValue(it)
                },
            ) to coverFunction.otherwise?.let { typeForFunctionValue(it) }
        }

    private fun typeCallByParts2(
        priorProblems: List<TypeReasonElement>,
        calleeVariants: List<Callee>,
        callTree: CallTree,
        isRttiCall: Boolean,
        /** If we're calling like `f<Foo, Bar>` this is the `Foo` and `Bar` with position metadata */
        typeActualsAndPositions: List<Pair<StaticType, Position>>?,
        inputTrees: List<Tree>,
        contextType: Type2?,
    ) {
        val pos = callTree.pos

        // Get last second types for functions that are actual arguments.
        typeFunTreesInContext(callTree, calleeVariants)

        var effectiveTypeActualsAndPositions = typeActualsAndPositions
        val lateTypedParameterIndices = mutableSetOf<Int>()
        val inputBounds =
            // For each input, we have position metadata which comes in handy when generating
            // diagnostics.
            // We also either know the inferred type, or the result inference variable for the
            // late-typed call that supplies whose result is the input.
            // If the bound is a value that we cannot pre-type, for example, an empty list or `null`,
            // we need to know which so that we can hand it off to the solver and then, after solving,
            // give it a clear, computed type.
            mutableListOf<InputBound>()
        inputTrees.forEachActual { childIndex, _, _, childI ->
            val lateTypedArgument: LateTypedCall? = ti.lateTypedCallArgument(childI.incoming!!)
            if (lateTypedArgument != null) {
                lateTypedParameterIndices.add(childIndex)
            }
            var inferredType = ti.decisionType(childI)
            if (inferredType != null && (inferredType.isNullType || inferredType.isNeverType)) {
                inferredType = null
            }
            inputBounds.add(
                when {
                    lateTypedArgument != null -> InputBound.UntypedCallInput(
                        childI.pos,
                        lateTypedArgument.passVar,
                    )
                    isRttiCall && childIndex == 1 && childI is ValueLeaf && childI.content.typeTag == TType -> {
                        // If we have a complete target type, express it as a type actual.
                        // That way we make better intertwining decisions.
                        // Otherwise, we need an incomplete reification, so that context and input types can
                        // be used to compute the missing bindings.
                        val reifiedType = TType.unpack(childI.content)
                        val targetType = reifiedType.type2
                        if (targetType.bindings.size >= targetType.definition.formals.size) { // Complete
                            val inputPos = childI.pos
                            if (effectiveTypeActualsAndPositions == null) {
                                effectiveTypeActualsAndPositions = listOf(
                                    hackMapNewStyleToOld(targetType) to inputPos,
                                )
                            }
                            InputBound.Pretyped(
                                MkType2(WellKnownTypes.typeTypeDefinition).position(inputPos).get()
                                    as PositionedType,
                            )
                        } else {
                            InputBound.IncompleteReification(
                                pos = childI.pos,
                                reifiedType = reifiedType,
                                typeArgumentIndex = 0,
                                typeVar = solverVarNamer.unusedTypeVar("checkedType"),
                                reificationEdge = childI.incoming,
                                describedValueArgumentIndex = 0,
                            )
                        }
                    }
                    inferredType != null -> InputBound.Pretyped(
                        hackMapOldStyleToNew(inferredType, childI.pos) as PositionedType,
                    )
                    childI is ValueLeaf ->
                        InputBound.ValueInput(
                            childI,
                            solverVarNamer.unusedTypeVar(
                                childI.content.typeTag.name.displayName.asciiUnTitleCase(),
                            ),
                        )
                    else -> InputBound.Typeless(childI.pos)
                },
            )
        }
        val hasTrailingBlock = inputTrees.lastOrNull() is FunTree

        val aliasedCall = ti.aliasedCallFor(callTree)?.let {
            if (it.use.isDirectlyNestedCallParameter) {
                // Aliased call is used as a call parameter
                it
            } else {
                null
            }
        }
        val nestingCall =
            if (aliasedCall == null && callTree.isDirectlyNestedCallParameter) {
                callTree.incoming
            } else {
                null
            }
        val intertwining = checkNeedToLateTypeCall(
            nestingCall = nestingCall,
            aliasedCall = aliasedCall,
            calleeVariants = calleeVariants,
            contextType = contextType,
            explicitActualTypesAndPositions = effectiveTypeActualsAndPositions,
            lateTypedParameterIndices = lateTypedParameterIndices,
            argumentsSupplied = inputTrees.indices,
        )
        if (intertwining == LateType2CheckResult.TypeLater) {
            // Schedule for late typing per intertwined call comment at top of typeBlock()
            queueCallForLateTyping(
                aliasedCall = aliasedCall,
                nestingCall = nestingCall,
                inputTrees = inputTrees,
                inputBounds = inputBounds,
                hasTrailingBlock = hasTrailingBlock,
                contextType = contextType,
                passVar = solverVarNamer.unusedTypeVar("pass"),
                calleeVariants = calleeVariants,
                explicitActualTypesAndPositions = effectiveTypeActualsAndPositions,
            )
            return
        }

        val problems = priorProblems.toMutableList()
        // Call does not need to be typed late.
        // See if there are late-typed calls we can type along with this one.
        // If we need to intertwine this with a call C, because C's result is assigned to a t#123
        // that is an input to this call, then there may be a late-typed call D that is assigned to
        // one of C's inputs.
        val intertwined = mutableListOf<LateTypedCall>()
        run {
            val q = ArrayDeque<List<Tree>>()
            q.add(inputTrees)
            while (true) {
                val callInputTrees: List<Tree> = q.removeFirstOrNull() ?: break
                val inputNames = callInputTrees.mapNotNull { (it as? RightNameLeaf)?.content }
                val inputEdges = callInputTrees.mapNotNullTo(mutableSetOf()) { it.incoming }
                val lateTypedCalls = ti.lateTypedCalls.removeMatching {
                    it.nestingCall in inputEdges || it.aliasedCall?.alias in inputNames
                }
                for (call in lateTypedCalls) {
                    intertwined.add(call)
                    q.add(call.inputTrees)
                }
                ti.callsRequirePostProcessing.addAll(lateTypedCalls)
            }
        }
        if (
            intertwined.isEmpty() && calleeVariants.size == 1 &&
            calleeVariants.first().sig.typeFormals.isEmpty()
        ) {
            // Fast path for simple functions that do not require TypeSolving.
            val variant = calleeVariants.first()
            ti.decide(
                callTree,
                Decision(
                    type = hackMapNewStyleToOld(variant.sig.returnType2),
                    variant = variant.functionType,
                    explanations = problems.toList(),
                ),
            )
            // We would use TypeSolutions for ValueBounds in the slow path to
            // type ambiguous constants, so we still need to specialize some
            // untypable constant inputs.  This handles `null`, but might extend
            // to list of null, for example.
            val sig = variant.sig
            for ((i, inputTree) in inputTrees.withIndex()) {
                if (inputTree is ValueLeaf && ti.isUndecided(inputTree)) {
                    val formal = sig.valueFormalForActual(i)
                    ti.decide(
                        inputTree,
                        if (formal != null) {
                            Decision(hackMapNewStyleToOld(formal.type))
                        } else {
                            Decision(InvalidType, explanations = listOf(becauseRedundantArgument(inputTree)))
                        },
                    )
                }
            }
        } else {
            // We need to create a TypeSolver to infer type variables for one or more calls.
            val calls = mutableListOf(
                UntypedCall(
                    callPosition = pos,
                    calleeVariants = calleeVariants,
                    explicitActuals = effectiveTypeActualsAndPositions,
                    inputBounds = inputBounds,
                    hasTrailingBlock = hasTrailingBlock,
                    contextType = contextType,
                    passVar = null,
                    destination = callTree,
                ),
            )
            intertwined.mapTo(calls) {
                UntypedCall(
                    callPosition = it.callTree.pos,
                    calleeVariants = it.calleeVariants,
                    explicitActuals = it.explicitActualTypesAndPositions,
                    inputBounds = it.inputBounds,
                    hasTrailingBlock = it.hasTrailingBlock,
                    contextType = it.contextType,
                    passVar = it.passVar,
                    destination = it.callTree,
                )
            }

            inferBounds(typeContext, typeContext2, solverVarNamer, calls, debug = DEBUG)

            for (call in calls) {
                val callSite = call.destination
                var explanations = call.explanations ?: emptyList()
                if (callSite === callTree) { // Not an inter-twined call
                    explanations = explanations + problems
                }
                val variant = call.chosenCallee?.let { i -> call.calleeVariants[i].functionType }
                val decision = Decision(
                    type = call.resultType ?: InvalidType,
                    variant = variant,
                    bindings = call.bindings ?: emptyMap(),
                    explanations = explanations,
                )
                ti.decide(callSite, decision)

                // Store solved types with ambiguous value bounds
                for (inputBound in call.inputBounds) {
                    if (inputBound is InputBound.ValueInput) {
                        val valueLeaf = inputBound.valueLeaf
                        if (ti.isUndecided(valueLeaf)) {
                            val solution = inputBound.valueSolvedType
                            if (solution != null) {
                                ti.decide(valueLeaf, hackMapNewStyleToOld(solution))
                            }
                        }
                    }
                }

                // Store the actual variant chosen with the callee now that we know it.
                if (!isNewCall(callSite)) {
                    val variantType = decision.variant ?: InvalidType
                    val callee = callSite.child(0)
                    val storedCalleeType = ti.decisionType(callee)
                    val calleeFn = callee.functionContained
                    if (variantType != storedCalleeType || calleeFn is CoverFunction) {
                        maybeRefineCallee(callee.incoming!!, variantType, callSite)
                        if (!variantType.mentionsInvalid) {
                            when (callee as TypeInferencesHaver) {
                                is BasicTypeInferencesTree -> ti.decide(
                                    callee,
                                    ti.decision(callee)?.copy(type = variantType)
                                        ?: Decision(variantType),
                                )

                                is CallTypeInferencesTree ->
                                    ti.decision(callee)?.let { decision ->
                                        ti.decide(callee, decision.copy(type = variantType))
                                    }

                                is NoTypeInferencesTree -> Unit
                            }
                        }
                    }
                }

                // If we have a delayed nullary-never call (see TyperPlan),
                // we should go ahead and roll it back to complete the typing of any assignment
                // it's part of.
                if (isNullaryNeverCall(callSite)) {
                    val parent = callSite.incoming?.source
                    if (parent is CallTree && isAssignment(parent) && ti.isUndecided(parent)) {
                        typeAssignmentCall(parent)
                    }
                }
            }
        }

        // We do nothing for `=` or `hs` calls that wrap late-typed calls.
        // Revisit their parents to type those.
        console.groupIf(DEBUG, "Revisiting late-typed calls") {
            for (lateTypedCall in intertwined) {
                var ancestor = lateTypedCall.callTree.incoming?.source
                while (ancestor is CallTree && ti.isUndecided(ancestor)) {
                    typeCallTree(ancestor)
                    ancestor = ancestor.incoming?.source
                }
            }
        }
    }

    private fun postTypeDeclTree(tree: DeclTree) {
        val parts = tree.parts ?: return
        val type = ti.binding(parts.name.content as ResolvedName)
        if (type != null) {
            // Optional expressions may be value references that need context to type.
            // For example a `null` default expression like
            //     let f(x: Int? = null) { ... }
            val optionalEdges = parts.metadataSymbolMultimap[optionalSymbol] ?: listOf()
            for (optionalEdge in optionalEdges) {
                val optionalTree = optionalEdge.target
                if (optionalTree is ValueLeaf && ti.isUndecided(optionalTree)) {
                    preTypeValueLeaf(optionalTree)
                    if (ti.isUndecided(optionalTree)) {
                        ti.decide(optionalTree, type)
                    }
                }
            }
        }
    }

    private fun typeFunTreesInContext(callTree: CallTree, variants: List<Callee>) {
        val originalArgs = callTree.children.subListToEnd(callTree.firstArgumentIndex)
        val callee = variants.firstOrNull()

        val args = callee?.let { callee ->
            reorderArgsHelper.buildPositionalArgs(callTree, callee.functionType)
        } ?: originalArgs
        args.forEachActual { _, argIndex, _, argTree ->
            if (argTree is FunTree && ti.isUndecided(argTree)) {
                // See if we can use callee information for better fun type inference.
                val formalType = typeContext2.valueFormalTypeAt(variants, argIndex)
                if (formalType != null) {
                    withType(
                        formalType,
                        fn = { _, sig, _ ->
                            preTypeFunTree(argTree, wantedSig = sig)
                        },
                        fallback = {},
                    )
                }
            }
        }
    }

    private fun queueCallForLateTyping(
        aliasedCall: TyperPlan.AliasedCall?,
        nestingCall: TEdge?,
        inputTrees: List<Tree>,
        inputBounds: List<InputBound>,
        hasTrailingBlock: Boolean,
        contextType: Type2?,
        passVar: TypeVar,
        calleeVariants: List<Callee>,
        explicitActualTypesAndPositions: List<Pair<StaticType, Position>>?,
    ) {
        ti.lateTypedCalls.add(
            LateTypedCall(
                aliasedCall = aliasedCall,
                nestingCall = nestingCall,
                inputTrees = inputTrees,
                inputBounds = inputBounds,
                hasTrailingBlock = hasTrailingBlock,
                contextType = contextType,
                passVar = passVar,
                calleeVariants = calleeVariants,
                explicitActualTypesAndPositions = explicitActualTypesAndPositions,
            ),
        )
    }

    /**
     * Per the intertwined call comment in [TyperPlan], checks whether a call should be typed late.
     *
     * @return null if not eligible.  If eligible, a pair with the variants that require
     *     intertwining, and those that don't.
     */
    private fun checkNeedToLateTypeCall(
        nestingCall: TEdge?,
        aliasedCall: TyperPlan.AliasedCall?,
        calleeVariants: List<Callee>,
        contextType: Type2?,
        explicitActualTypesAndPositions: List<Pair<StaticType, Position>>?,
        lateTypedParameterIndices: Set<Int>,
        argumentsSupplied: IntRange, // TODO: Set<Int> when we get to named arguments
    ): LateType2CheckResult {
        // A call is eligible for combining when
        // 1. We have something to combine it with
        // 2. We do not have explicit type actuals
        // 3. callee has a function type variant F, that has a type formal, <T>, such that
        //    a. <T> is not mentioned in F's return type OR there is no context type, AND
        //    b. there does not exist an input type in F that
        //       i. mentions <T> AND
        //       ii. corresponds to a supplied input AND
        //       iii. that input is not itself a late typed call, i.e. inter-twined with this call
        // We do some of these checks out of order so that we can distinguish between the cases
        // where we do not need to inter-twine because we have enough information from inputs (3.b)
        // and we only have enough information because of the context type (3.a).

        // Per 2, check whether supplied actuals are complete
        if (
            explicitActualTypesAndPositions?.all {
                @Suppress("USELESS_IS_CHECK") // We may allow wildcards shortly
                it.first is StaticType
            } == true
        ) {
            return LateType2CheckResult.Immediate
        }

        console.groupIf(DEBUG_LATE_TYPE_CHECK, "checkingNeedToLateTypeCall") {
            console.logIf(DEBUG_LATE_TYPE_CHECK) {
                "calleeVariants=$calleeVariants, late=$lateTypedParameterIndices,${
                    ""
                } supplied=$argumentsSupplied, contextType=$contextType"
            }

            // Find the function type variants: the Fs above.
            if (calleeVariants.isEmpty()) {
                // Don't know how to inter-twine un-typed callees
                return@checkNeedToLateTypeCall LateType2CheckResult.Immediate
            }

            // Gather info for 3.a checks and to distinguish later between
            // SelfSufficient and NeedsContextType
            val hasContextType = contextType != null
            var contextUseful = false // Until shown otherwise

            // Per 3.b, check whether one or more variant callee types has one or more type
            // parameters that do not appear in the type of any supplied & already typed argument.
            var allCoveredBySpecifiedInputs = true
            var allCoveredBySpecifiedInputsOrContext = true
            for (variant in calleeVariants) {
                if (variant.sig.typeFormals.isEmpty()) {
                    continue // variant is not generic
                }
                val typesMentionedInSuppliedInputs = mutableSetOf<ResolvedName>()
                val sig = variant.sig
                for (argIndex in argumentsSupplied) { // 3.b.ii
                    if (argIndex !in lateTypedParameterIndices) { // 3.b.iii
                        val inputType = sig.valueFormalForActual(argIndex)?.type
                        if (inputType != null) {
                            addTypeNamesMentionedTo(inputType, typesMentionedInSuppliedInputs)
                        }
                    }
                }
                val typesMentionedInReturnType = mutableSetOf<ResolvedName>()
                if (hasContextType) {
                    addTypeNamesMentionedTo(variant.sig.returnType2, typesMentionedInReturnType)
                }
                val typeFormalNames = mutableSetOf<ResolvedName>()
                variant.sig.typeFormals.mapTo(typeFormalNames) { it.name }
                for (typeFormal in variant.sig.typeFormals) {
                    val typeFormalName = typeFormal.name
                    if (typeFormalName !in typesMentionedInSuppliedInputs) { // 3.a
                        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
                        console.logIf(DEBUG_LATE_TYPE_CHECK && allCoveredBySpecifiedInputs) {
                            val mentioned = typesMentionedInSuppliedInputs.toMutableSet()
                            mentioned.retainAll(typeFormalNames)
                            "$typeFormalName not covered by input in variant $variant${
                                ""
                            }; !in $mentioned"
                        }
                        allCoveredBySpecifiedInputs = false
                        if (typeFormalName !in typesMentionedInReturnType) { // 3.b
                            @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
                            console.logIf(
                                DEBUG_LATE_TYPE_CHECK && allCoveredBySpecifiedInputsOrContext,
                            ) {
                                "$typeFormalName not covered by input${
                                    ""
                                } nor context in variant $variant"
                            }
                            allCoveredBySpecifiedInputsOrContext = false
                        } else {
                            contextUseful = true
                        }
                    }
                }
            }

            val lateType2CheckResult = when {
                allCoveredBySpecifiedInputs -> LateType2CheckResult.Immediate
                allCoveredBySpecifiedInputsOrContext -> LateType2CheckResult.UseContextType
                // Check 1: We have something to combine it with
                aliasedCall != null || nestingCall != null -> LateType2CheckResult.TypeLater
                contextUseful -> LateType2CheckResult.UseContextType
                // Constraint solver will fall back to worst-case bounds.
                else -> LateType2CheckResult.Immediate
            }

            console.logIf(DEBUG_LATE_TYPE_CHECK) {
                "lateTypeCheck -> $lateType2CheckResult${
                    ""
                }, allCoveredBySpecifiedInputs=$allCoveredBySpecifiedInputs${
                    ""
                }, allCoveredBySpecifiedInputsOrContext=$allCoveredBySpecifiedInputsOrContext${
                    ""
                }, can combine=${aliasedCall != null || nestingCall != null}${
                    ""
                }, contextUseful=$contextUseful"
            }

            return@checkNeedToLateTypeCall lateType2CheckResult
        }
    }

    private fun typeHandlerScopeCall(tree: CallTree) {
        val failVar = tree.child(1)
        val operation = tree.child(2)
        if (failVar is LeftNameLeaf) {
            val failVarName = failVar.content as ResolvedName
            if (ti.isUnbound(failVarName)) {
                ti.bind(failVarName, booleanType)
            }
        }
        val operationType = ti.decisionType(operation)
        if (operationType != null) {
            val operationTypeMinusBubble = when (operationType) {
                InvalidType -> InvalidType
                else -> excludeBubble(operationType)
            }
            ti.decide(
                tree,
                Decision(
                    operationTypeMinusBubble,
                    variant = MkType.fn(
                        typeFormals = emptyList(),
                        valueFormals = listOf(booleanType, operationType),
                        restValuesFormal = null,
                        returnType = operationTypeMinusBubble,
                    ),
                    bindings = mapOf(
                        handlerScopeTypeFormal to operationTypeMinusBubble,
                    ),
                    explanations = emptyList(),
                ),
            )
        } else {
            // Inter-twined call.  This tree will have its type fixed up as the inter-twined call
            // is typed.
        }
    }

    private fun typeAssignmentCall(tree: CallTree) {
        val (_, left, right) = tree.children
        val name = (left as? LeftNameLeaf)?.content as ResolvedName?
        if (name == null) {
            ti.decide(
                tree,
                Decision(
                    InvalidType,
                    MkType.fn(listOf(), listOf(InvalidType, InvalidType), null, InvalidType),
                    explanations = listOf(
                        TypeReason(
                            LogEntry(Log.Error, MessageTemplate.MalformedAssignment, left.pos, listOf()),
                        ),
                    ),
                ),
            )
            return
        }
        if (ti.isUndecided(right) && right is FunTree) {
            val wantedType = ti.binding(name)
            if (wantedType != null) {
                withType(
                    hackMapOldStyleToNew(wantedType),
                    fn = { _, sig, _ ->
                        preTypeFunTree(right, wantedSig = sig)
                    },
                    fallback = {},
                )
            }
        }

        console.groupIf(DEBUG, "Typing assignment $name") {
            var type = ti.decisionType(right)
            if (DEBUG) {
                console.log("Callee type=${tree.child(0).staticTypeContained}")
            }
            var initializerIndex = -1
            val initInfo: InitializerInfo? =
                ti.initializerInfo[name].let { ii ->
                    // If this assignment is an initializer, fetch the initializer info bundle
                    initializerIndex = ti.typerPlan.initializers[name]?.indexOf(right) ?: -1
                    if (ii != null && initializerIndex >= 0) {
                        ii
                    } else {
                        null
                    }
                }

            if (type == null && isNullValueLeaf(right)) {
                if (initInfo != null) {
                    // For `null` values, we just store the fact that it can be null so that once we've
                    // got a complete type, we can mark it nullable.
                    initInfo.assignedNullable = true
                    // Nothing more we can do for this until the mask is full.
                    initInfo.checkedMask.set(initializerIndex)
                } else {
                    val typeFromName = ti.bindingsMap[name]
                    if (typeFromName != null) {
                        ti.decide(right, typeFromName)
                    }
                    type = ti.decisionType(right)
                }
            }

            if (type != null) {
                ti.decide(
                    tree,
                    Decision(
                        type,
                        variant = MkType.fn(
                            typeFormals = emptyList(),
                            valueFormals = listOf(type, type),
                            restValuesFormal = null,
                            returnType = type,
                        ),
                    ),
                )
                initInfo?.checkedMask?.set(initializerIndex)
            }

            // Check whether tree was the last holdout among a group of initializers
            if (initInfo != null) {
                console.logIf(DEBUG) {
                    "Initializer countdown mask=${initInfo.checkedMask} of ${initInfo.initializers.size}"
                }
            }
            if (initInfo?.allChecked() == true) {
                ti.initializerInfo.remove(name)
                val initializers = initInfo.initializers
                val initializerTypes = buildSet {
                    initializers.mapNotNullTo(this) { ti.decisionType(it) }
                    if (isEmpty()) {
                        // If there are only unsolved constant initializers, use the
                        // declared type.
                        val declaredType = ti.binding(name)
                            ?: ti.sinkTypeOf(name)
                            ?: MkType.nominal(WellKnownTypes.neverTypeDefinition, listOf(WellKnownTypes.emptyType))
                        add(declaredType)
                    }
                }
                var combinedType = typeContext.simpleOr(initializerTypes)
                if (initInfo.assignedNullable && combinedType !is InvalidType) {
                    combinedType = MkType.nullable(combinedType)
                }
                console.logIf(DEBUG) {
                    "Inferred type for $name as $combinedType from initializer types $initializerTypes"
                }
                ti.bind(name, combinedType)
                val basics = Decision(combinedType)
                for (initializer in initializers) {
                    if (ti.isUndecided(initializer) && isNullValueLeaf(initializer)) {
                        ti.decide(initializer, basics)
                    }
                    val assignment = initializer.incoming!!.source as CallTree
                    if (ti.isUndecided(assignment) && ti.isDecided(initializer)) {
                        // initializer could be tree, but this won't recurse infinitely
                        // because we've settled on a type.
                        typeAssignmentCall(assignment)
                    }
                    val left = assignment.child(1) as? LeftNameLeaf
                    if (left != null && ti.isUndecided(left)) {
                        ti.decide(left, basics)
                    }
                }
            }
        }
    }

    private fun typeForConstructorVariants(
        typeReferencePos: Position,
        shapeAndVariants: Pair<TypeShape, List<MethodShape>>?,
    ): Pair<List<Signature2>, List<TypeReasonElement>> {
        if (shapeAndVariants == null) {
            val explanations = listOf(
                BecauseMalformedTypeReference(typeReferencePos),
            )
            return listOf<Signature2>() to explanations
        }
        val (_, variants) = shapeAndVariants
        val explanations = mutableListOf<TypeReasonElement>()
        val sigs = variants.map { constructorShape ->
            constructorShape.descriptor
                ?: run {
                    explanations.add(
                        BecauseTypeInfoMissingForName(
                            pos = typeReferencePos,
                            nameMissingInfo = constructorShape.name as ResolvedName,
                        ),
                    )
                    invalidSig
                }
        }
        return sigs to explanations.toList()
    }

    private fun typeTypeAngleCall(tree: CallTree) {
        // The type angle call specializes a generic function,
        // but the result is the generic function.
        val generalType = ti.decisionType(tree.child(1))
        if (generalType != null) {
            val maybeBoundType = when (generalType) {
                is FunctionType -> when {
                    generalType.typeFormals.size == tree.size - 2 -> run bindings@{
                        val typeActuals = (2..<tree.size).map { kidIndex ->
                            tree.child(kidIndex).staticTypeContained ?: return@bindings null
                        }
                        MkType.bindFormals(generalType, generalType.typeFormals.zip(typeActuals).toMap())
                    }
                    else -> null
                }
                else -> null
            } ?: generalType
            val decision = Decision(
                maybeBoundType,
                variant = MkType.fn(
                    typeFormals = emptyList(),
                    valueFormals = listOf(generalType),
                    restValuesFormal = WellKnownTypes.typeType,
                    returnType = generalType,
                ),
            )
            ti.decide(tree, decision)
        } else {
            // Late-typed calls will revisit.
        }
    }

    private fun typeNotNullCall(tree: CallTree) {
        val generalType = ti.decisionType(tree.child(1))
        if (generalType != null) {
            val notNullType = excludeNull(generalType)
            val sig = NotNullFn.sig
            ti.decide(
                tree,
                Decision(
                    notNullType,
                    variant = typeFromSignature(sig),
                    bindings = mapOf(sig.typeFormals[0] to notNullType),
                ),
            )
        } else {
            // Late-typed calls will revisit.
        }
    }

    private fun preTypeFunTree(t: FunTree, wantedSig: Signature2? = null) {
        val parts = t.parts
        val contradictions = mutableListOf<TypeReasonElement>()
        val resultType: StaticType = run computeResultType@{
            if (parts == null) {
                contradictions.add(BecauseMalformedFunctionDefinition(t.pos))
                functionType
            } else {
                val superTypeEdges = parts.metadataSymbolMultimap[superSymbol] ?: emptyList()
                val superTypes = SuperTypeTree(
                    buildSet {
                        superTypeEdges.forEach {
                            val superType = it.target.staticTypeContained
                            val problem = when {
                                superType == null ->
                                    BecauseUnresolvedTypeReference(it.target.pos)
                                superType !is NominalType ->
                                    BecauseExpectedNamedType(it.target.pos, superType)
                                superType.definition !is TypeShape ->
                                    BecauseExpectedTypeShape(it.target.pos, superType)
                                else -> {
                                    add(superType)
                                    null
                                }
                            }
                            if (problem != null) { contradictions.add(problem) }
                        }
                    },
                )
                ignore(superTypes) // TODO: fold super types into function type.
                val valueDecls = parts.formals
                val restValuesFormal: StaticType? = parts.restFormal?.type
                val returnDecl = parts.returnDecl
                val typeFormals = parts.typeFormals.map {
                    it.second ?: run {
                        // TODO: report malformed type formal
                        contradictions.add(BecauseMalformedTypeFormal(it.first.target.pos))
                        return@computeResultType InvalidType
                    }
                }

                fun typeForDeclOrNull(declTree: DeclTree): StaticType? {
                    val declParts = declTree.parts
                    return if (declParts == null) {
                        contradictions.add(BecauseMalformedParameterDeclaration(declTree.pos))
                        InvalidType
                    } else {
                        val typeTree = declParts.type?.target
                        if (typeTree == null) {
                            null
                        } else {
                            val reifiedType = typeTree.reifiedTypeContained
                            reifiedType?.type ?: run {
                                contradictions.add(
                                    BecauseUnresolvedFunctionSignaturePart(typeTree.pos),
                                )
                                InvalidType
                            }
                        }
                    }
                }

                val previousFunctionType = ti.decisionType(t) as? FunctionType
                    // Reuse a good return type for the function previously
                    // (during Type stage, and now we're in GenerateCodeStage).
                    ?: t.typeInferences?.type as? FunctionType
                val variants = listOfNotNull(wantedSig?.let { Callee(it, CalleePriority.Default) })
                val valueFormals = valueDecls.mapIndexed { declIndex, decl ->
                    val declParts = decl.parts!!
                    val name = declParts.name.content as ResolvedName
                    val explicitType = typeForDeclOrNull(decl)
                    val type = if (explicitType != null) {
                        explicitType
                    } else { // No declared type, so infer.
                        val inferredType = typeContext2.valueFormalTypeAt(variants, declIndex)
                            ?.let { hackMapNewStyleToOld(it) }
                            ?: previousFunctionType?.valueFormals?.getOrNull(declIndex)?.staticType
                            ?: anyValueType
                        if (ti.isUnbound(name)) {
                            ti.bind(name, inferredType)
                        }
                        inferredType
                    }
                    FunctionType.ValueFormal(
                        symbol = name.toSymbol(),
                        staticType = type,
                        isOptional = declParts.isOptional.elseTrue,
                    )
                }
                val lastRequiredIndex = valueFormals.indexOfLast { !it.isOptional }
                val firstOptionalIndex = valueFormals.indexOfFirst { it.isOptional }
                if (lastRequiredIndex >= 0 && firstOptionalIndex >= 0 && firstOptionalIndex < lastRequiredIndex) {
                    contradictions.add(
                        TypeReason(
                            LogEntry(
                                Log.Error,
                                MessageTemplate.OptionalArgumentTooEarly,
                                valueDecls[firstOptionalIndex].pos,
                                emptyList(),
                            ),
                        ),
                    )
                }
                val returnType = returnDecl?.let { typeForDeclOrNull(it) }
                    ?: wantedSig?.returnType2?.let { hackMapNewStyleToOld(it) }
                    ?: previousFunctionType?.returnType?.also {
                        contradictions.addAll(
                            ti.decision(t)?.explanations
                                ?: t.typeInferences!!.explanations,
                        )
                    }
                    ?: run {
                        // Default to TopType here, but provide errors for definitely missing return types.
                        // Constructors and setters get Void default provided in TypeDisambiguateMacro.
                        if (returnDecl != null && returnDecl.parts?.type == null) {
                            contradictions.add(BecauseReturnTypeRequired(returnDecl.pos))
                        }
                        TopType
                    }

                MkType.fnDetails(
                    typeFormals = typeFormals,
                    valueFormals = valueFormals,
                    restValuesFormal = restValuesFormal,
                    returnType = returnType,
                )
            }
        }
        ti.decide(t, Decision(resultType, explanations = contradictions.toList()))
    }

    /**
     * Type [builtin][BuiltinName] and [exported][ExportedName] names from other modules.
     *
     * @return true if [t] was typed.
     */
    private fun preTypeNonLocalNames(t: NameLeaf): Boolean {
        val name = t.content as ResolvedName
        val type = typeForNonLocalName(name)
        val decision = when {
            type != null -> Decision(type)
            name is BuiltinName && env.declarationMetadata(name) == null ->
                // We really should know the type of free variables.
                Decision(
                    InvalidType,
                    explanations = listOf(BecauseNameUndeclared(t.pos, name)),
                )
            else -> null
        }
        if (decision != null) {
            ti.decide(t, decision)
        }
        return decision != null
    }

    private fun typeForNonLocalName(name: ResolvedName): StaticType? = when (name) {
        is BuiltinName -> {
            val metadata = env.declarationMetadata(name)
            val reifiedType = TType.unpackOrNull(metadata?.reifiedType)
            if (reifiedType != null) {
                reifiedType.type
            } else {
                val result = env[name, InterpreterCallback.NullInterpreterCallback]
                if (result is Value<*>) {
                    // TODO(tjp, frontend): Mike would like to revisit this handling of types for values. Discussion:
                    // - https://temperlang.slack.com/archives/D02SXMB2MJT/p1651184227932089
                    // - https://github.com/temperlang/temper/pull/613#discussion_r863200494
                    val valueType = typeForValue(result)
                    when (valueType) {
                        functionType -> {
                            // Vague type, so give implicits a try for more detail.
                            val implicits = ImplicitsModule.module.exports!!
                            val found = implicits.firstOrNull {
                                it.name.baseName.nameText == name.builtinKey
                            }
                            found?.typeInferences?.type
                        }
                        else -> null
                    } ?: valueType
                } else {
                    if (DEBUG) {
                        console.log("Could not find value for builtin $name")
                    }
                    null
                }
            }
        }
        is ExportedName -> {
            val exporter = name.origin as? ModuleNamingContext
            if (exporter != null && exporter.owner != module) {
                exporter.owner.exports?.first { it.name == name }?.typeInferences?.type
            } else {
                null
            }
        }
        else -> null
    }

    private fun preTypeValueLeaf(t: ValueLeaf) {
        typeForValue(t.content)?.let { type ->
            ti.decide(t, type)
        }
    }

    private fun typePreserveCall(tree: CallTree) {
        val reduced = tree.child(2)
        if (reduced is ValueLeaf && ti.isUndecided(reduced)) {
            preTypeValueLeaf(reduced)
        }
        val type = ti.decisionType(reduced)
        if (type != null) {
            ti.decide(
                tree,
                Decision(
                    type = type,
                    variant = MkType.fn(
                        typeFormals = emptyList(),
                        valueFormals = listOf(anyValueType, type),
                        restValuesFormal = null,
                        returnType = type,
                    ),
                    bindings = mapOf(PreserveFn.sig.typeFormals[0] to type),
                ),
            )
        }
    }

    private fun constructorVariantsForTypeReference(typeRef: Tree): Pair<TypeShape, List<MethodShape>>? {
        // Assumes isConstructorReferenceCall(t)
        val reifiedType = typeRef.reifiedTypeContained
            ?: return null
        val nominalType = reifiedType.type as? NominalType ?: return null
        val shape = nominalType.definition as? TypeShape ?: return null
        return shape to shape.methods.mapNotNull {
            if (it.methodKind == MethodKind.Constructor) {
                it
            } else {
                null
            }
        }
    }

    /**
     * After we've solved type constraints, we still have some type structures that need to be
     * filled in.
     *
     * [LeftNameLeaf]s often appear in declarations, lexically before the uses which contribute
     * type information.
     *
     * We go back now, and use bindings to add type inferences there, which allows easy access
     * to the inferred types for declared elements.
     */
    private fun fillInBlanksAndRecheckAssignments(root: BlockTree) {
        TreeVisit.startingAt(root)
            .forEachContinuing { t ->
                if (t is NameLeaf && ti.isUndecided(t)) {
                    val name = t.content as ResolvedName
                    val type = ti.binding(name)
                    ti.decide(
                        t,
                        type?.let { Decision(it) }
                            ?: Decision(
                                InvalidType,
                                explanations = listOf(BecauseTypeInfoMissingForName(t.pos, name)),
                            ),
                    )
                } else if (t is CallTree && isAssignment(t)) {
                    // If the assignment is a value for which we cannot compute a result, use the
                    // context type.
                    val (_, left, right) = t.children
                    if (right is ValueLeaf) {
                        val copyTypeRightward = when (right.content.typeTag) {
                            TNull -> ti.decisionType(right).let {
                                it == null || it.isNullType
                            }
                            else -> false
                        }
                        if (copyTypeRightward) {
                            val leftType = ti.decisionType(left)
                            if (leftType != null && !leftType.mentionsInvalid) {
                                val rightExplanations =
                                    ti.decision(right)?.explanations ?: listOf()
                                ti.decide(right, Decision(leftType, explanations = rightExplanations))
                                if (ti.decisionType(t).let { it == null || it.isNullType }) {
                                    ti.decide(
                                        t,
                                        ti.decision(t)?.copy(type = leftType)
                                            ?: Decision(
                                                leftType,
                                                variant = MkType.fn(
                                                    typeFormals = emptyList(),
                                                    valueFormals = listOf(leftType, leftType),
                                                    restValuesFormal = null,
                                                    returnType = leftType,
                                                ),
                                            ),
                                    )
                                }
                            }
                        }
                    }

                    val type = ti.decisionType(t)
                    // An assignments type is the type of its right side, but there's also a
                    // requirement that the right be a sub-type of the left.
                    // Checking this here means that we don't eliminate failure branches for
                    // guards that we shouldn't.
                    val leftType = ti.decisionType(left)
                    val rightType = ti.decisionType(right)
                    if (
                        type == null || !type.mentionsInvalid && (
                            leftType == null || rightType == null ||
                                !typeContext.isSubType(rightType, leftType)
                            )
                    ) {
                        val rightTypeOrInvalid = rightType ?: InvalidType
                        val contradictions =
                            (ti.decision(t)?.explanations ?: emptyList()) +
                                BecauseIllegalAssignment(
                                    t.pos,
                                    leftType ?: InvalidType,
                                    rightTypeOrInvalid,
                                )
                        if (DEBUG) {
                            console.group("Invalid Assignment") {
                                console.warn(
                                    "$leftType does not extend $rightType, invalidating $type",
                                )
                                t.toPseudoCode(console.textOutput)
                            }
                        }
                        val decisionWithError = ti.decision(t)?.let {
                            it.copy(explanations = it.explanations + contradictions)
                        } ?: Decision(
                            InvalidType,
                            variant = MkType.fn(
                                typeFormals = emptyList(),
                                valueFormals = listOf(rightTypeOrInvalid, rightTypeOrInvalid),
                                restValuesFormal = null,
                                returnType = rightTypeOrInvalid,
                            ),
                            bindings = emptyMap(),
                            explanations = contradictions,
                        )
                        ti.decide(t, decisionWithError)
                    }
                } else if (t is DeclTree) {
                    postTypeDeclTree(t)
                }
            }
            // Make sure we fill in left names before looking at assignments
            .visitPostOrder()
    }

    private fun maybeRefineCallee(
        calleeEdge: TEdge,
        refinedCalleeType: StaticType,
        call: CallTree,
    ) {
        val callee = calleeEdge.target
        console.groupIf(DEBUG, "Refining callee") {
            if (DEBUG) {
                console.log(
                    "Variants are $refinedCalleeType for ${callee.pos}/${
                        abbreviate(callee.toLispy())
                    }",
                )
            }
            if (refinedCalleeType.mentionsInvalid) {
                return@maybeRefineCallee
            }
            val fn = callee.functionContained
            if (fn is CoverFunction) {
                val looseRefinedCalleeType = looseType(refinedCalleeType)
                val eligibleVariants = buildList {
                    for (candidateFunction in fn.covered) {
                        val candidateType = typeForValue(Value(candidateFunction))
                            ?: continue
                        val looseCandidateType = looseType(candidateType)
                        if (typeContext.isSubType(looseRefinedCalleeType, looseCandidateType)) {
                            add(candidateFunction)
                        }
                    }
                    if (isEmpty() && fn.otherwise != null) {
                        add(fn.otherwise!!)
                    }
                    doExtraCoverFunctionVariantRefinement(fn, call, looseRefinedCalleeType, this)
                }
                if (DEBUG) {
                    console.log("Eligible variants are in $looseRefinedCalleeType")
                }
                if (eligibleVariants.isNotEmpty()) {
                    val eligibleVariantFn = when {
                        eligibleVariants.size == 1 -> eligibleVariants[0]
                        fn.otherwise != null -> fn.otherwise
                        eligibleVariants.size != fn.covered.size -> null
                        else -> CoverFunction(eligibleVariants.map { it as CallableValue })
                    }
                    if (eligibleVariantFn != null) {
                        var edgeToReplace = calleeEdge
                        if (callee is CallTree && isPreserveCall(callee)) {
                            edgeToReplace = callee.edge(2)
                        }
                        edgeToReplace.replace {
                            V(callee.pos, Value(eligibleVariantFn))
                        }
                        val newCallee = edgeToReplace.target as ValueLeaf
                        preTypeValueLeaf(newCallee)
                    }
                }
            }

            // TODO: Refine method references
        }
    }

    /**
     * Use type information to do several things:
     *
     * 1. eliminate hs checks of operations that cannot fail
     * 2. eliminate unnecessary RTTI checks
     * 3. remove extensions from DotHelpers that do not use them and
     *    replace calls of DotHelpers that do with references to the extension.
     *
     * Each of these steps removes instructions which were auto-inserted earlier.
     *
     * 1. undoes insertion of hs checks by MagicSecurityDust.sprinkle()
     * 2. removes redundant checks for `.is` and `.as` pseudo methods exploded
     *    by MagicSecurityDust.  For example, null checks when the source expression
     *    is known not to be null.
     * 3. removes extensions that are delaying adjusting dot helpers introduced
     *    by DotOperationDesugarer in case they're needed
     */
    private fun rewriteUsingTypeInformation(
        root: BlockTree,
        singlyAssigned: Set<TemperName>,
    ) {
        // `fail#123` variables inserted by [lang.temper.frontend.MagicSecurityDust]
        // which are found to always be false.
        val cannotFail = mutableSetOf<ResolvedName>()
        val nullOps = mutableListOf<Pair<CallTree, NullOpKind>>()
        val dotHelperCalls = mutableListOf<CallTree>()

        TreeVisit.startingAt(root)
            .forEachContinuing { t ->
                // Store information so that we can do type-dependent optimizations after solving
                // the rest of the tree.
                if (t !is CallTree) { return@forEachContinuing }
                if (isHandlerScopeCall(t)) {
                    val operation = t.child(2)
                    val operationType = ti.decisionType(operation)
                    val failVar = t.child(1)
                    val failVarName = (failVar as? LeftNameLeaf)?.content as? ResolvedName
                    if (operationType != null && failVarName != null && failVarName in singlyAssigned) {
                        val decision = ti.decision(t)
                        // The type might still be invalid, but we don't guarantee semantics on bad types.
                        // It's ok to avoid coordinated bubble handling in such cases.
                        // If we do have valid type, though, we need to handle explicit bubbling.
                        val passes = !operationType.isBubbly
                        if (DEBUG) {
                            console.group("Considering fail variable $failVarName") {
                                console.log("operationType=$operationType passes=$passes")
                                console.log("type=${decision?.type}")
                                console.log("explanations=${decision?.explanations}")
                            }
                        }
                        if (passes && decision != null) {
                            // If the type of f(), above, is disjoint from Bubble,
                            // then operation cannot fail, so we can note that
                            //
                            //     fail#123 = false
                            //
                            // and rewrite the hs(...) call to
                            //
                            //     f()
                            val incoming = t.incoming
                            incoming?.replace {
                                Replant(freeTree(operation))
                            }
                            cannotFail.add(failVarName)
                            if (DEBUG) {
                                console.group(
                                    "Cannot fail $failVarName for $operationType -> ${
                                        decision.type
                                    }",
                                ) {
                                    operation.toPseudoCode(console.textOutput)
                                }
                            }
                        }
                    }
                } else if (isNullCheck(t)) {
                    nullOps.add(t to NullOpKind.IsNull)
                } else if (isNotNullCall(t)) {
                    nullOps.add(t to NullOpKind.NotNull)
                } else if (isDotHelperCall(t)) {
                    dotHelperCalls.add(t)
                }
            }
            // Visit deeper first so that mutations do not prevent covering all.
            .visitPostOrder()

        // Rewrite safe fail# variables with false.
        if (cannotFail.isNotEmpty()) {
            TreeVisit.startingAt(root)
                .forEachContinuing {
                    if (it is RightNameLeaf) {
                        val name = it.content
                        // Replace reference to fail#123 with `false` when fail#123 was deduced to
                        // be so by typeHandlerScopeCall.
                        if (name in cannotFail && name in singlyAssigned) {
                            val replacement = ValueLeaf(it.document, it.pos, TBoolean.valueFalse)
                            replacement.typeInferences = it.typeInferences
                            it.incoming?.replace(replacement)
                        }
                    }
                }
                .visitPreOrder()
        }

        for ((nullOpCall, nullOpKind) in nullOps) {
            when (nullOpKind) {
                NullOpKind.IsNull -> simplifyNullCheck(nullOpCall)
                NullOpKind.NotNull -> simplifyNotNull(nullOpCall)
            }
        }

        for (dotHelperCall in dotHelperCalls) {
            val dotHelper = dotHelperCall.child(0).functionContained as DotHelper
            simplifyDotHelper(
                dotHelperCall,
                dotHelper,
                typeForDotHelper(dotHelperCall, dotHelper).typedCandidates,
            ) { tree ->
                fun retype(t: Tree) {
                    t.children.forEach { retype(it) }
                    if (ti.isUndecided(t)) {
                        typeSubTree(t)
                    }
                }
                retype(tree)
                storeDecisionsInTree(tree, skipStored = true)
            }
        }
    }

    private fun fixupCalleeType(t: CallTree) {
        val callee = t.childOrNull(0)
        val callable = callee?.functionContained ?: return
        val fixedTypeAndProblems = when (callable) {
            BuiltinFuns.getpFn -> typeForGetp(t)
            BuiltinFuns.setpFn -> typeForSetp(t)
            is GetStaticOp -> typeForGets(t, callable)
            BuiltinFuns.asFn, BuiltinFuns.assertAsFn -> typeForAs(t, callable)
            BuiltinFuns.commaFn -> typeForComma(t)
            is DotHelper -> typeForDotHelper(t, callable).let {
                it.fnType to it.reasons
            }
            else -> null
        }
        if (fixedTypeAndProblems != null) {
            val (fixedType, problems) = fixedTypeAndProblems
            if (DEBUG) {
                console.group(
                    "Fixed callee type of ${
                        abbreviate(t.toLispy())
                    } from ${ti.decisionType(callee)} to $fixedType",
                ) {
                    problems.forEach {
                        console.log("- $it")
                    }
                }
            }
            val explanations = (ti.decision(t)?.explanations ?: emptyList()) + problems
            if (isPreserveCall(callee)) {
                check(callee is CallTree)
                val reduced = callee.child(2) as ValueLeaf
                ti.decide(reduced, Decision(fixedType, explanations = explanations))
                ti.clearDecision(callee)
                typePreserveCall(callee)
            } else {
                check(callee is ValueLeaf)
                ti.decide(callee, Decision(fixedType, explanations = explanations))
            }
        }
    }

    private fun typeForGetp(t: CallTree): Pair<StaticType, List<TypeReasonElement>> {
        if (t.size != GETP_ARITY + 1) {
            return InvalidType to listOf(BecauseMalformedSpecialCall(t.pos, t))
        }
        val propertyReference = t.child(1)
        val thisArg = t.child(2)
        val propertyReferenceType = ti.decisionType(propertyReference) ?: InvalidType
        return MkType.fn(
            typeFormals = emptyList(),
            valueFormals = listOf(
                propertyReferenceType,
                ti.decisionType(thisArg) ?: InvalidType,
            ),
            restValuesFormal = null,
            returnType = propertyReferenceType,
        ) to listOf()
    }

    private fun typeForSetp(t: CallTree): Pair<StaticType, List<TypeReasonElement>> {
        if (t.size != SETP_ARITY + 1) {
            return InvalidType to listOf(BecauseMalformedSpecialCall(t.pos, t))
        }
        val (_, propertyArg, thisArg, newValueArg) = t.children

        val problems = mutableListOf<TypeReasonElement>()

        val thisType = ti.decisionType(thisArg)
            ?: invalidBecauseMissingType(thisArg, "this").let { (explanation, t) ->
                problems.add(explanation)
                t
            }

        // TODO: consolidate this left pulling for all assignment like calls, or just
        // treat assignments and setP calls as possibly intertwining.
        if (ti.isUndecided(newValueArg) && isNullValueLeaf(newValueArg)) {
            // Copy the type from the left to the right if we've got an underspecified constant.
            val propertyName = propertyArg.nameContained as? ResolvedName
            val typeShape = ((thisType as? NominalType)?.definition as? TypeShape)
            val property = typeShape?.properties?.find { it.name == propertyName }
            val propertyType = property?.descriptor?.let { hackMapNewStyleToOld(it) }
            if (propertyType?.mentionsInvalid == false) {
                ti.decide(newValueArg, propertyType)
            }
        }

        val rightType = ti.decisionType(newValueArg)
            ?: invalidBecauseMissingType(newValueArg, "value").let { (explanation, t) ->
                problems.add(explanation)
                t
            }
        return MkType.fn(
            typeFormals = emptyList(),
            valueFormals = listOf(
                TopType,
                thisType,
                rightType,
            ),
            restValuesFormal = null,
            returnType = rightType,
        ) to problems.toList()
    }

    private fun typeForGets(
        t: CallTree,
        callable: GetStaticOp,
    ): Pair<StaticType, List<TypeReasonElement>> {
        if (t.size != GETS_ARITY + 1) {
            return InvalidType to listOf(BecauseMalformedSpecialCall(t.pos, t))
        }

        // Could also extract callee here, but we pass it in above because easy enough.
        val typeArg = t.child(1)
        val symbolArg = t.child(2)

        val reifiedType = typeArg.valueContained?.let { asReifiedType(it) }
            ?: return InvalidType to listOf(BecauseUnresolvedTypeReference(typeArg.pos))
        val receiverType = reifiedType.type
        val memberName = symbolArg.symbolContained
            ?: return InvalidType to listOf(BecauseMalformedSpecialCall(t.pos, t))
        val (type, reasons) =
            typeForGets(t.pos, typeArg.pos, receiverType, memberName, emptyList(), callable)
        return type to reasons
    }

    private fun typeForGets(
        pos: Position,
        receiverTypePos: Position,
        receiverType: StaticType,
        memberName: Symbol,
        extensions: List<StaticExtensionResolution>,
        callable: GetStaticOp,
    ): TypedMembersAndExtensions {
        val typeShape = (receiverType as? NominalType)?.definition as? TypeShape

        val member = typeShape?.staticProperties?.firstOrNull {
            it.symbol == memberName && callable.canSee(it)
        }

        val applicableExtensions = extensions.filter { extension ->
            val extensionReceiverTypes = ti.extensionReceiverTypes(extension.resolution)
            extensionReceiverTypes.any { typeContext.isSubType(receiverType, it) }
        }

        if (member == null && applicableExtensions.isEmpty()) {
            // If we have no applicable extensions, then explain any problems with the
            // receiver type or missing members.
            return TypedMembersAndExtensions(
                InvalidType,
                listOf(
                    if (typeShape == null) {
                        BecauseExpectedNamedType(receiverTypePos, receiverType)
                    } else {
                        // Errors should be rare, so just spend a second pass to check
                        // if we had any name matches at all.
                        if (typeShape.staticProperties.any { it.symbol == memberName }) {
                            BecauseCannotAccessMembers(pos, memberName, setOf(typeShape))
                        } else {
                            BecauseNoSuchMember(pos, memberName, setOf(typeShape))
                        }
                    },
                ),
            )
        }

        @Suppress("ControlFlowWithEmptyBody")
        if (applicableExtensions.size > 1) {
            // TODO: Maybe eliminate resolutions whose receiver type is a strict super-type of
            // the others, and whose argument types are equivalent.

            // For example, if we have two extensions like the below, we can
            // eliminate the second because the argument types are equivalent,
            // and the receiver type (String?) is a strict super-type of
            // the other's receiver type (String).
            //
            //    @staticExtension(String, "f")
            //    let stringF(i: Int): Void { ... }
            //
            //    @staticExtension(String?, "f")
            //    let stringOrNullF(i: Int): Void { ... }
        }

        val variants = mutableListOf<StaticType>()
        val resolutions = mutableListOf<Pair<StaticType, Either<VisibleMemberShape, ExtensionResolution>>>()
        fun addVariant(simpleType: StaticType, r: Either<VisibleMemberShape, ExtensionResolution>) {
            val getSType = MkType.fn(
                typeFormals = emptyList(),
                valueFormals = listOf(
                    Types.function.type,
                    Types.symbol.type,
                ),
                restValuesFormal = null,
                returnType = simpleType,
            )
            variants.add(getSType)
            resolutions.add(getSType to r)
        }

        if (member != null) {
            val memberDescriptor = member.descriptor
                ?: return TypedMembersAndExtensions(
                    InvalidType,
                    listOf(BecauseTypeInfoMissingForName(pos, member.name as ResolvedName)),
                )
            val memberType = when (memberDescriptor) {
                is Signature2 -> typeFromSignature(memberDescriptor)
                is Type2 -> hackMapNewStyleToOld(memberDescriptor)
            }
            addVariant(memberType, Either.Left(member))
        }

        for (extension in applicableExtensions) {
            val extensionType = typeForExtensionResolution(pos, extension)
                ?: return TypedMembersAndExtensions(
                    InvalidType,
                    listOf(BecauseTypeInfoMissingForName(pos, extension.resolution)),
                )
            addVariant(extensionType, Either.Right(extension))
        }

        // The callee type is a tad different
        return TypedMembersAndExtensions(
            MkType.and(variants),
            listOf(),
            resolutions.toList(),
        )
    }

    private fun typeForAs(call: CallTree, callable: MacroValue): Pair<StaticType, List<TypeReasonElement>> {
        val problems = mutableListOf<TypeReasonElement>()

        var type: StaticType = functionType

        val reifiedType = call.childOrNull(2)?.reifiedTypeContained
        if (reifiedType != null) {
            val goalType = when {
                reifiedType.type.hasUnbound() -> {
                    val inferredType = call.childOrNull(1)?.let { ti.decisionType(it) }
                    mergeTypeArgs(from = inferredType, into = reifiedType.type)
                }
                else -> reifiedType.type
            }
            type = MkType.fn(
                typeFormals = emptyList(),
                valueFormals = listOf(
                    anyValueType,
                    // Type for types.
                    WellKnownTypes.typeType,
                ),
                restValuesFormal = null,
                returnType = when {
                    callable.callMayFailPerSe -> MkType.or(goalType, BubbleType)
                    else -> goalType
                },
            )
        } else {
            problems.add(
                BecauseUnresolvedTypeReference(
                    call.childOrNull(2)?.pos ?: call.pos.rightEdge,
                ),
            )
        }

        if (call.size != AS_ARITY) {
            problems.add(BecauseArityMismatch(call.pos, 2))
        }

        return type to problems.toList()
    }

    private data class TypedMembersAndExtensions(
        val fnType: StaticType,
        val reasons: List<TypeReasonElement>,
        /** For each variant, the associated member shape or extension function name. */
        val typedCandidates: List<Pair<StaticType, Either<VisibleMemberShape, ExtensionResolution>>> = emptyList(),
    )

    private fun typeForDotHelper(
        t: CallTree,
        dotHelper: DotHelper,
    ): TypedMembersAndExtensions {
        console.groupIf(DEBUG, "typeForDotHelper") {
            val memberAccessor = dotHelper.memberAccessor
            if (DEBUG) {
                console.log(t.toLispy())
                console.log("memberAccessor=$memberAccessor")
            }

            val thisArg = t.child(1 + memberAccessor.firstArgumentIndex)
            val memberSymbol = dotHelper.symbol

            // A DotHelper with a static type receiver should turn into either a
            // call to getStatic, or a call of a @staticExtension function.
            // If thisArg is a type expression, then this dotHelper probably represents
            // a syntactic static member reference which might resolve against an
            // @staticExtension declaration.
            // Delegate to the handler for getStatic special calls.
            val possibleStaticTypeReceiver = thisArg.staticTypeContained
            if (possibleStaticTypeReceiver != null) {
                if (DEBUG) {
                    console.log("Typing ${toStringViaTokenSink { dotHelper.renderTo(it) }} as if gets")
                }
                return@typeForDotHelper typeForGets(
                    t.pos,
                    thisArg.pos,
                    possibleStaticTypeReceiver,
                    dotHelper.symbol,
                    dotHelper.extensions.mapNotNull { it as? StaticExtensionResolution },
                    // No information on whether this is an internal call, so presume it isn't.
                    // This means we need to ensure internal calls get resolved better before this point.
                    BuiltinFuns.getsFn,
                )
            }

            var includeInvalid = false
            fun findNominalTypes(type: StaticType, out: MutableSet<NominalType>) {
                when (type) {
                    is NominalType -> {
                        when (val definition = type.definition) {
                            is TypeShape -> out.add(type)
                            is TypeFormal -> {
                                definition.superTypes.forEach { findNominalTypes(it, out) }
                            }
                        }
                    }
                    TopType -> out.add(anyValueType)
                    BubbleType -> Unit
                    InvalidType -> includeInvalid = true
                    is OrType -> {
                        // Find common super-type
                        val memberNominalTypes = mutableSetOf<NominalType>()
                        type.members.forEach { findNominalTypes(it, memberNominalTypes) }
                        if (memberNominalTypes.isNotEmpty()) {
                            findNominalTypes(typeContext.leastCommonSuperType(memberNominalTypes), out)
                        }
                    }
                    is AndType -> {
                        type.members.forEach {
                            findNominalTypes(it, out)
                        }
                    }
                    is FunctionType -> {
                        out.add(functionType)
                    }
                }
            }

            val thisType = ti.decisionType(thisArg)
            if (DEBUG) {
                console.log("thisType=$thisType")
            }

            val thisVariants = mutableSetOf<NominalType>()
            thisType?.let { findNominalTypes(it, thisVariants) }
            if (DEBUG) {
                console.log("thisVariants=$thisVariants")
            }

            val allThisVariants = mutableSetOf<NominalType>()
            fun explodeThisType(thisVariant: NominalType) {
                if (thisVariant in allThisVariants) {
                    return
                }
                allThisVariants.add(thisVariant)
                thisVariant.definition.superTypes.forEach { superTypeUnbound ->
                    val typeMapper = typeBindingMapper(thisVariant)
                    if (typeMapper != null) {
                        explodeThisType(typeMapper(superTypeUnbound) as NominalType)
                    } else {
                        includeInvalid = true
                    }
                }
            }
            thisVariants.forEach(::explodeThisType)
            if (DEBUG) {
                console.log("allThisVariants=$allThisVariants")
            }

            // TODO: consolidate this with AccessibleFilter
            val useMember: (VisibleMemberShape) -> MemberUsage2 = {
                // Either it's public or we're reading from within the class definition.
                // TODO: check protected vs private.  This is sensitive to whether something is
                // reached directly or via super-type walking above.
                if (DEBUG) {
                    console.log("filtering: $it")
                }
                val visible = it.visibility == Visibility.Public ||
                    memberAccessor is InternalMemberAccessor
                val compatible = when (memberAccessor) {
                    is GetMemberAccessor ->
                        it is PropertyShape ||
                            (it is MethodShape && it.methodKind == MethodKind.Getter)
                    // TODO: method or constructor references
                    is SetMemberAccessor ->
                        (it is PropertyShape && it.hasSetter) ||
                            (it is MethodShape && it.methodKind == MethodKind.Setter)
                    is BindMemberAccessor ->
                        it is MethodShape &&
                            // TODO: call of value of property with function type via getter
                            it.methodKind == MethodKind.Normal
                }
                when {
                    compatible && visible -> MemberUsage2.Good
                    !compatible && visible -> MemberUsage2.Incompatible
                    compatible && !visible -> MemberUsage2.Invisible
                    else -> MemberUsage2.IncompatibleAndInvisible
                }
            }

            val members = mutableListOf<Pair<NominalType, VisibleMemberShape>>()
            val rejectedMemberHolders = mutableSetOf<TypeShape>()
            val acceptedMemberHolders = mutableSetOf<TypeShape>()
            // Track specific match cases with vars for error messaging.
            // Hopefully more efficient than generating additional intermediate filtered lists or other objects.
            var anyMatchedSymbol = false

            @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE") // Used but Kotlin thinks it isn't. (?!?)
            var anyMatchedUsage = false
            for (thisVariant in allThisVariants) {
                val typeShape = thisVariant.definition as TypeShape
                if (DEBUG) {
                    console.log("$thisVariant has members ${typeShape.members}")
                }
                typeShape.members.forEach { member ->
                    if (member is VisibleMemberShape && member.symbol == memberSymbol) {
                        anyMatchedSymbol = true
                        val usage = useMember(member)
                        if (usage == MemberUsage2.Good) {
                            acceptedMemberHolders.add(typeShape)
                            members.add(thisVariant to member)
                        } else {
                            rejectedMemberHolders.add(typeShape)
                            when (usage) {
                                MemberUsage2.Incompatible, MemberUsage2.IncompatibleAndInvisible -> {
                                    // No usage compatibility here.
                                }
                                else -> anyMatchedUsage = true
                            }
                        }
                    } else {
                        rejectedMemberHolders.add(typeShape)
                    }
                }
            }
            // TODO: filter out masked member variants because overrides may narrow a signature.

            val memberTypesGrouped = mutableMapOf<NominalType, MutableSet<VisibleMemberShape>>()
            members.forEach { (nominalType, memberShape) ->
                memberTypesGrouped.putMultiSet(nominalType, memberShape)
            }
            filterOutMaskedMembers(memberTypesGrouped) // Who is that masked method?!?  Don't care.

            // Might need a type argument since the internal member accessors expect the this-type.
            val containingType = if (memberAccessor is InternalMemberAccessor) {
                WellKnownTypes.typeType
            } else {
                null
            }

            // We need to, after typing, decide whether a DotHelper with extensions should use one
            // of the regular members, or one of the extensions.
            // We recall this later to get that relationship between extensions and members.
            val variants = mutableListOf<Pair<StaticType, Either<VisibleMemberShape, ExtensionResolution>>>()
            val explanations = mutableListOf<TypeReasonElement>()
            memberTypesGrouped.forEach { (nominalType, memberShapes) ->
                // If we have a getter or setter, prefer that to any property type
                val hasMethod = memberShapes.any { it is MethodShape }

                val mapper = typeBindingMapper(nominalType)
                if (mapper == null) {
                    includeInvalid = true
                } else {
                    for (memberShape in memberShapes) {
                        if (memberShape is PropertyShape && hasMethod) { continue }
                        val memberShapeType: StaticType? = memberShape.descriptor?.let {
                            when (it) {
                                is Signature2 -> typeFromSignature(it)
                                is Type2 -> hackMapNewStyleToOld(it)
                            }
                        }
                        if (memberShapeType != null) {
                            // If we've got a property shape, as for an abstract property declared in an interface
                            // without explicit getters or setters, adapt that property's shape to a method like
                            // shape.
                            val asMethodType = when (memberShape) {
                                is PropertyShape -> {
                                    if (memberAccessor is SetMemberAccessor) {
                                        MkType.fn(
                                            typeFormals = emptyList(),
                                            valueFormals = listOfNotNull(containingType, nominalType, memberShapeType),
                                            restValuesFormal = null,
                                            returnType = Types.void.type,
                                        )
                                    } else {
                                        check(memberAccessor is GetMemberAccessor)
                                        MkType.fn(
                                            typeFormals = emptyList(),
                                            valueFormals = listOfNotNull(containingType, nominalType),
                                            restValuesFormal = null,
                                            returnType = memberShapeType,
                                        )
                                    }
                                }
                                is MethodShape -> memberShapeType
                                is StaticPropertyShape -> error(memberShape)
                            }
                            variants.add(mapper(asMethodType) to Either.Left(memberShape))
                        } else {
                            explanations.add(
                                BecauseTypeInfoMissingForName(
                                    t.pos,
                                    memberShape.name as ResolvedName,
                                ),
                            )
                            includeInvalid = true
                        }
                    }
                }
            }
            dotHelper.extensions.forEach { extensionResolution ->
                when (extensionResolution) {
                    is InstanceExtensionResolution -> {} // Proceed to below
                    // StaticExtensionResolutions are handled elsewhere by typing
                    // DotHelper operations where the first argument is a type expression
                    // as if they were getStatic resolutions.
                    is StaticExtensionResolution -> return@forEach
                }
                val extensionName = extensionResolution.resolution
                val extensionType = typeForExtensionResolution(t.pos, extensionResolution)
                if (extensionType != null) {
                    variants.add(extensionType to Either.Right(extensionResolution))
                } else {
                    explanations.add(
                        BecauseTypeInfoMissingForName(
                            t.pos,
                            extensionName,
                        ),
                    )
                    includeInvalid = true
                }
            }

            if (memberAccessor is BindMemberAccessor && thisType != null) {
                for (i in variants.indices) {
                    val (variantType, source) = variants[i]
                    val curriedT = BindMethodTypeHelper.curry(thisType, variantType)
                    variants[i] = curriedT to source
                }
            }

            if (variants.isEmpty()) {
                val filteredOut = (rejectedMemberHolders - acceptedMemberHolders).toSet()
                explanations.add(
                    if (anyMatchedSymbol) {
                        if (anyMatchedUsage) {
                            // Use privacy message only for cases where we have a full match otherwise.
                            // No need to inundate.
                            BecauseCannotAccessMembers(t.pos, memberSymbol, filteredOut)
                        } else {
                            BecauseNoMemberCompatible(t.pos, memberSymbol, filteredOut)
                        }
                    } else {
                        // When we have no reference types, show the core types we started with.
                        val typeShapes = when (filteredOut.isNotEmpty()) {
                            true -> filteredOut
                            false -> allThisVariants.map {
                                // Up above, we already cast each definition as TypeShape, so should work here, too.
                                it.definition as TypeShape
                            }.toSet()
                        }
                        BecauseNoSuchMember(t.pos, memberSymbol, typeShapes)
                    },
                )
                includeInvalid = true
            }
            val variantTypes = buildSet {
                for (variant in variants) {
                    add(variant.first)
                }
                if (includeInvalid) {
                    add(InvalidType)
                }
            }
            val type = MkType.and(variantTypes)
            if (DEBUG) {
                console.group("thisVariants") {
                    thisVariants.forEach {
                        console.logTokens(it)
                    }
                }
                console.group("members") {
                    memberTypesGrouped.forEach { (thisType, members) ->
                        console.group("$thisType") {
                            members.forEach {
                                console.log("$it")
                            }
                        }
                    }
                }
                console.group("type") {
                    console.logTokens(type)
                }
                console.group("variants") {
                    for ((variantType, resolution) in variants) {
                        console.log("- $variantType: $resolution")
                    }
                }
            }

            return@typeForDotHelper TypedMembersAndExtensions(
                type,
                explanations.toList(),
                variants.toList(),
            )
        }
    }

    private fun typeForExtensionResolution(
        pos: Position,
        extensionResolution: ExtensionResolution,
    ): StaticType? {
        val document = ti.typerPlan.root.document
        val extensionRef = RightNameLeaf(document, pos, extensionResolution.resolution)
        typeSubTree(extensionRef)
        return ti.decisionType(extensionRef)
    }

    private fun typeForComma(t: CallTree): Pair<StaticType, List<TypeReasonElement>> {
        // Its variadic, but we can't use a variadic signature since its function
        // depends on the last argument, but not any of the preceding.
        // We want context to propagate through it to any generic function calls in
        // the last position, so we generate a function type like
        //    fn <T>(AnyValue, AnyValue, T): T
        // where the number of AnyValue is one less than the number of arguments.
        val arity = t.size - 1
        if (arity == 0) {
            return functionType to listOf(BecauseArityMismatch(t.pos, 1))
        }
        val typeT = MkType.nominal(commaT)
        return MkType.fnDetails(
            typeFormals = listOf(commaT),
            valueFormals = buildList {
                repeat(arity - 1) {
                    add(FunctionType.ValueFormal(null, anyValueType, isOptional = false))
                }
                add(FunctionType.ValueFormal(null, typeT, isOptional = false))
            },
            restValuesFormal = null,
            returnType = typeT,
        ) to emptyList()
    }

    companion object {
        /** Type formal for the comma function. */
        private val commaT = TypeFormal(
            pos = Position(ImplicitsCodeLocation, 0, 0),
            name = BuiltinName("comma.T"),
            symbol = Symbol("T"),
            variance = Variance.Invariant,
            mutationCount = AtomicCounter(),
            upperBounds = listOf(anyValueType),
        )
    }

    private class LateTypedCall(
        /**
         * An edge whose source is the call that nests the late-typed call, and whose target
         * is an ancestor (non-strict) of [callTree].
         */
        val nestingCall: TEdge?,
        /** A call that does not nest directly, but is an argument by reference. */
        val aliasedCall: TyperPlan.AliasedCall?,
        /** The inputs to the call. */
        val inputTrees: List<Tree>,
        /**
         * The inferred types of the inputs or the inference variable that should bind to that type
         * when the call is eventually typed in the context of other calls that can supply that info.
         */
        val inputBounds: List<InputBound>,
        val hasTrailingBlock: Boolean,
        val contextType: Type2?,
        /**
         * An inference variable, if any, that must bind to the same type as the call's passing type.
         * This allows calls which use this calls result as an input to include this inference
         * variable in their [inputBounds] list.
         */
        val passVar: TypeVar,
        /** The calleeType pruned of any incompatible variants. */
        val calleeVariants: List<Callee>,
        /** Types and positions of explicitly provided type parameters for this call. */
        val explicitActualTypesAndPositions: List<Pair<StaticType, Position>>?,
    ) {
        val callTree: CallTree
            get() = aliasedCall?.aliased ?: (nestingCall!!.target as CallTree)
    }
}

private const val AS_ARITY = 3 // Callee, value to test, goal type

private const val GETP_ARITY = 2 // (rightName, this)
private const val GETS_ARITY = 2 // (type, memberName)

private fun typeBindingMapper(basis: NominalType): ((StaticType) -> StaticType)? {
    val formals = basis.definition.formals
    val actuals = basis.bindings
    if (formals.size != actuals.size) { return null }

    if (actuals.isEmpty()) { return { it } }

    val formalToActual =
        (formals zip actuals).associate { it.first.name to it.second }
    val mapper = TypeBindingMapper(formalToActual)
    return { t ->
        MkType.map(t, mapper, mutableMapOf())
    }
}

private val Tree.isDirectlyNestedCallParameter: Boolean
    get() {
        val edge = this.incoming
        val parent = edge?.source
        return parent is CallTree &&
            edge != parent.edgeOrNull(0) && // Is not the callee
            !isAssignment(parent) && !isHandlerScopeCall(parent)
    }

/**
 * Make debug trace less spammy by avoiding trace lines for things that
 * have super-predictable types.
 */
private val Tree.typingIsSpammyInLogs: Boolean get() = when (this) {
    is StayLeaf, is DeclTree, is LeftNameLeaf -> true
    is ValueLeaf -> {
        val parent = incoming?.source
        // Don't log every piece of declaration or function metadata
        parent is DeclTree || parent is FunTree
    }
    else -> false
}

private enum class LateType2CheckResult {
    /** We need to inter-twine the call with the call it nests in */
    TypeLater,

    /**
     * Use the context type, but do not wait until later to type the call.
     *
     * To understand the difference between this and [Immediate], consider:
     *
     * ```temper
     * let f<T>(...els: List<T>): Foo<T> { ... }
     *
     * // This call requires the context type since the type <T> does not appear in the type of any
     * // actual input, but does appear in the return type.
     * let x: Foo<String> = f();
     *
     * // This call is self-sufficient.  Its actual argument's type mentions <T> so there are no type
     * // parameters to f that cannot be bound based on inference to arguments; we need not take into
     * // account return context
     * let y: Foo<String> = f("foo");
     * ```
     *
     * Not using return context when we don't need it makes typing more consistent across code
     * re-organizations.
     */
    UseContextType,

    /** Type the call now */
    Immediate,
}

private fun TypeContext2.valueFormalTypeAt(variants: Iterable<Callee>, index: Int): Type2? {
    val ts = variants.mapNotNull {
        it.sig.valueFormalForActual(index)?.type
    }
    return if (ts.isNotEmpty()) {
        var glb: Type2? = null
        for (t in ts) {
            glb = if (glb == null) {
                t
            } else {
                this.glb(glb, t) ?: return null
            }
        }
        return glb
    } else {
        null
    }
}

private fun StaticType.returnType(): StaticType? = when (this) {
    is AndType -> MkType.and(members.mapNotNull { it.returnType() })
    is OrType -> MkType.or(members.mapNotNull { it.returnType() })
    is FunctionType -> returnType
    else -> null
}

private enum class MemberUsage2 {
    /** Usable member. */
    Good,

    /** Wrong usage, such as getter or setter. */
    Incompatible,

    /** Hidden, such as for being private. */
    Invisible,

    /** Both incompatible and invisible. Could use bits or enum sets, but for now the combos are simple. */
    IncompatibleAndInvisible,
}

/**
 * For example, merging [from] `Fuji<Int>?` [into] `Apple throws Bubble`, return `Apple<Int> throws Bubble`.
 * Results are undefined for more than one [NominalType] on either side.
 */
private fun mergeTypeArgs(from: StaticType?, into: StaticType): StaticType {
    fun findNominal(type: StaticType?): NominalType? {
        return when (type) {
            is NominalType -> when {
                type.definition === WellKnownTypes.nullTypeDefinition -> null
                else -> type
            }
            is OrType -> type.members.firstNotNullOf { findNominal(it) }
            else -> null
        }
    }
    val fromNominal = findNominal(from) ?: return into
    fromNominal.isUnbound() && return into // nothing to work with
    val intoNominal = findNominal(into) ?: return into
    val fromSupers = SuperTypeTree.of(fromNominal)
    val mergedNominals = fromSupers.byDefinition[intoNominal.definition] ?: run reverse@{
        // Down-casting is more complicated without subtype tree. Propagate abstract bindings then map back to actuals.
        val intoFormalBindings = intoNominal.definition.formals.map { MkType.nominal(it) }
        val intoFormalBound = MkType.nominal(intoNominal.definition, intoFormalBindings)
        val fromFormalBounds = SuperTypeTree.of(intoFormalBound).byDefinition[fromNominal.definition]
        fromFormalBounds?.size == 1 || return@reverse null
        val fromFormalBindings = fromFormalBounds.first().bindings
        // Make sure they all map. Don't bother with sets because presumed few.
        fromFormalBindings.all { it in intoFormalBindings } || return@reverse null
        // Then grab them by index from the `from` actuals.
        val mergedBindings = intoFormalBindings.map { fromNominal.bindings[fromFormalBindings.indexOf(it)] }
        listOf(MkType.nominal(intoNominal.definition, mergedBindings))
    } ?: return into
    mergedNominals.size == 1 || return into
    fun insert(type: StaticType): StaticType {
        return when {
            type === intoNominal -> mergedNominals.first()
            type is OrType -> MkType.or(type.members.map { insert(it) }.toSet())
            else -> type
        }
    }
    return insert(into)
}

/** For example, `Apple?` returns true if the class is generic, as in `Apple<T>`. */
private fun StaticType.hasUnbound(): Boolean {
    return when (this) {
        is NominalType -> isUnbound()
        is OrType -> members.any { it.hasUnbound() }
        else -> false
    }
}

private fun isDotHelperCall(t: Tree) =
    t is CallTree && t.childOrNull(0)?.functionContained is DotHelper

/**
 * Pulls out the function type parts of a complex callee type.
 *
 * Specifically, if the type is an intersection of function types and maybe marker interfaces,
 * returns an intersection of just the function types.
 *
 * For example, `MarkerInterface & fn (Arg): Ret` filters to `fn (Arg): Ret`.
 */
private fun coerceCalleeTypeToIntersectionOfFunctionTypes(
    calleeType: StaticType,
): StaticType = when (calleeType) {
    is AndType -> MkType.and(
        calleeType.members.map {
            coerceCalleeTypeToIntersectionOfFunctionTypes(it)
        },
    )
    is FunctionType -> calleeType
    InvalidType -> InvalidType
    // TODO: look through TypeFormal bounds and super-types for functional interface types
    is NominalType -> OrType.emptyOrType
    else -> OrType.emptyOrType
}

/**
 * `fn (Inp): Ret1 & fn (Inp): Ret2` -> `fn (Inp): Ret1 & Ret2`
 */
private fun distributeCalleeTypeThroughIntersection(
    calleeType: StaticType,
): StaticType {
    (calleeType as? AndType)?.let { calleeAndType ->
        val membersIterator = calleeAndType.members.iterator()
        if (!membersIterator.hasNext()) { return@let }
        val member0 = membersIterator.next() as? FunctionType
            ?: return@let
        val returnTypes = mutableSetOf(member0.returnType)
        while (membersIterator.hasNext()) {
            val member = membersIterator.next() as? FunctionType
                ?: return@let
            // Check that the member is consistent with member0 in the following ways:
            // - It has the same number of type parameters and those type parameters
            //   have the same upper bounds when member's <T> is remapped to member0's
            //   corresponding <T0>.
            // - It has the same arity and the value formals are the same when remapped.
            // If those all pass, add the remapped return type, else bail.
            //
            // For example:
            //     fn<T, U extends Listed<T>>(x: T): U &
            //     fn<A, B extends Listed<A>>(x: A): B?
            // That combines to the below:
            //     fn<T, U extends Listed<T>>(x: T): (U & (U?))
            // Since U is a subtype of U?, that return type is effectively U?.
            if (
                member.typeFormals.size != member0.typeFormals.size ||
                member.valueFormals.size != member0.valueFormals.size ||
                (member.restValuesFormal == null) != (member0.restValuesFormal == null)
            ) {
                return@let
            }

            val remappedValueFormals: List<FunctionType.ValueFormal>
            val remappedRestValueFormal: StaticType?
            val remappedReturnType: StaticType
            if (member0.typeFormals.isEmpty()) {
                remappedValueFormals = member.valueFormals
                remappedRestValueFormal = member.restValuesFormal
                remappedReturnType = member.returnType
            } else {
                val remapper = TypeBindingMapper(
                    buildMap {
                        for (i in member.typeFormals.indices) {
                            this[member.typeFormals[i].name] = MkType.nominal(member0.typeFormals[i])
                        }
                    },
                )
                val typeFormalsConsistent = member.typeFormals.indices.all { i ->
                    val tf0 = member0.typeFormals[i]
                    val tf = member.typeFormals[i]
                    tf.variance == tf0.variance &&
                        tf.superTypes.map { MkType.map(it, remapper) }.toSet() ==
                        tf0.superTypes.toSet()
                }
                if (!typeFormalsConsistent) {
                    return@let
                }
                remappedValueFormals = member.valueFormals.map { f ->
                    f.copy(staticType = MkType.map(f.staticType, remapper))
                }
                remappedRestValueFormal = member.restValuesFormal?.let {
                    MkType.map(it, remapper)
                }
                remappedReturnType = MkType.map(member.returnType, remapper)
            }
            if (
                member0.valueFormals != remappedValueFormals ||
                member0.restValuesFormal != remappedRestValueFormal
            ) {
                return@let
            }
            returnTypes.add(remappedReturnType)
        }
        return@distributeCalleeTypeThroughIntersection MkType.fnDetails(
            typeFormals = member0.typeFormals,
            valueFormals = member0.valueFormals,
            restValuesFormal = member0.restValuesFormal,
            returnType = MkType.and(returnTypes),
        )
    }
    return calleeType
}

internal val Tree.isMetadataValue: Boolean get() {
    if (this !is ValueLeaf) { return false }

    val incoming = incoming
    return when (val parent = incoming?.source) {
        is DeclTree, is FunTree -> {
            val priorSibling = parent.childOrNull(incoming.edgeIndex - 1)
            priorSibling is ValueLeaf && priorSibling.content.typeTag == TSymbol
        }
        else -> false
    }
}

internal fun TypeContext.simpleOr(ts: Iterable<StaticType>): StaticType {
    var nullType: StaticType? = null
    var bubbleType: StaticType? = null

    // TODO: We may want to allow a type parameter whose upper bound
    // is null or bubble as free-standing in a union.
    // That change should be accompanied by changes to the *TmpL.UnionType*
    // representation to allow for isolating conditional nulls in a union type.

    val ubs = mutableListOf<StaticType>()
    for (t in ts) {
        if (nullType == null && t is NominalType && t.definition == WellKnownTypes.nullTypeDefinition) {
            nullType = t
        } else if (bubbleType == null && t is BubbleType) {
            bubbleType = t
        } else {
            ubs.add(t)
        }
    }
    if (ubs.size <= 1) {
        return MkType.or(ts)
    }

    var ub = ubs.first()
    for (i in 1 until ubs.size) {
        ub = lub(ub, ubs[i], simplify = true)
    }

    return MkType.or(listOfNotNull(ub, nullType, bubbleType))
}

@OptIn(ExperimentalContracts::class)
internal fun isNullValueLeaf(t: Tree): Boolean {
    contract {
        returns(true) implies (t is ValueLeaf)
    }
    return t is ValueLeaf && t.content == TNull.value
}

internal fun invalidBecauseMissingType(p: Positioned, subject: String): Pair<TypeReason, InvalidType> =
    TypeReason(
        LogEntry(
            Log.Error,
            MessageTemplate.MissingType,
            p.pos,
            listOf(subject),
        ),
    ) to InvalidType

internal fun invalid2BecauseMissingType(p: Positioned, subject: String): Pair<TypeReason, DefinedNonNullType> =
    invalidBecauseMissingType(p, subject).first to invalidType2

internal fun LogSink.logInvalidBecauseMissingType(p: Positioned, subject: String): InvalidType {
    val (r, t) = invalidBecauseMissingType(p, subject)
    r.logTo(this)
    return t
}

internal fun LogSink.logInvalid2BecauseMissingType(p: Positioned, subject: String): DefinedNonNullType {
    val (r, t) = invalid2BecauseMissingType(p, subject)
    r.logTo(this)
    return t
}

private fun MutableCollection<Callee>.explodeCalleeType(t: StaticType, priority: CalleePriority) {
    when (t) {
        is FunctionType -> add(Callee(t, priority))
        is NominalType -> {
            if (functionalInterfaceSymbol in t.definition.metadata) {
                withType(
                    hackMapOldStyleToNew(t),
                    fn = { _, sig, _ ->
                        add(Callee(sig, priority))
                    },
                    fallback = {},
                )
            }
        }
        is AndType ->
            for (mt in t.members) {
                explodeCalleeType(mt, priority)
            }
        OrType.emptyOrType -> {}
        is OrType -> explodeCalleeType(excludeNullAndBubble(t), priority)
        else -> {} // TODO: add a problem?
    }
}

internal val Tree.needsTypeInfo get() = when (this) {
    is NoTypeInferencesTree -> false
    is BlockTree -> false
    is CallTree -> true
    is DeclTree -> false
    is FunTree -> true
    is NameLeaf -> true
    is ValueLeaf -> true
}
