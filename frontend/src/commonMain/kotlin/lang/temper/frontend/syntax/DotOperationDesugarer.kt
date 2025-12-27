package lang.temper.frontend.syntax

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.asReifiedType
import lang.temper.builtin.leftHandOfMacroContext
import lang.temper.common.Cons
import lang.temper.common.Log
import lang.temper.common.abbreviate
import lang.temper.common.console
import lang.temper.common.putMultiList
import lang.temper.common.subListToEnd
import lang.temper.interp.convertToErrorNode
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.Temporary
import lang.temper.type.DotHelper
import lang.temper.type.ExtensionResolution
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.ExternalSet
import lang.temper.type.InstanceExtensionResolution
import lang.temper.type.InternalBind
import lang.temper.type.InternalGet
import lang.temper.type.InternalSet
import lang.temper.type.MemberAccessor
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.StaticExtensionResolution
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.FlowMaker
import lang.temper.value.FunTree
import lang.temper.value.IsNullFn
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.chainNullBuiltinName
import lang.temper.value.dotBuiltinName
import lang.temper.value.extensionSymbol
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.lookThroughDecorations
import lang.temper.value.nameContained
import lang.temper.value.reifiedTypeContained
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.symbolContained
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeFromSignature
import lang.temper.value.unpackPairValue
import lang.temper.value.vIsNullFn
import lang.temper.value.vMissingSymbol
import lang.temper.value.valueContained

private const val DEBUG = false
private inline fun debug(message: () -> Any?) {
    if (DEBUG) {
        val o = message()
        if (o != Unit) {
            console.log("$o")
        }
    }
}

private typealias DotOperationReplacer = Planting.() -> UnpositionedTreeTemplate<*>

private enum class DotContext {
    Read,
    Write,
    Call,
}

/**
 * A block that defines functions with the `@extension` metadata that
 * allows them to act as dot operations.
 */
private data class Extensions(
    private val extensionNames: Map<Symbol, List<ExtensionResolution>>,
    private val parent: Extensions?,
) {
    private val cache = mutableMapOf<Symbol, List<ExtensionResolution>>()

    operator fun get(symbol: Symbol): List<ExtensionResolution> = cache.getOrPut(symbol) {
        buildList {
            extensionNames[symbol]?.let { addAll(it) }
            parent?.get(symbol)?.let { addAll(it) }
        }
    }

    companion object {
        val none = Extensions(emptyMap(), null)
    }
}

private typealias ExtensionStack = Cons<Extensions>

/**
 * Convert `x.y` and `x?.y` into a call to a *DotHelper* use that bundles information about
 * member access until the Typer can pick a specific member.
 *
 * The umbrella function lets us balance:
 * - internal (private/protected) class member access
 * - public class member access
 * - extension members
 *
 * <!-- snippet: builtin/%3F. : operator `?.` -->
 * # Null-chaining `?.`
 * Infix `?.` considers the right-hand side only when the left side is non-null.
 * It otherwise evaluates to null.
 *
 * ```temper
 * let maybeLength(a: String?): Int? {
 *   // Because of non-null inference, `a.end` is ok here.
 *   // Inferred non-null on the right side works only for simple name references,
 *   // not for nested expressions on the left side of `?.`.
 *   a?.countBetween(String.begin, a.end)
 * }
 * maybeLength("ab") == 2 &&
 * maybeLength(null) == null
 * ```
 */
internal class DotOperationDesugarer(
    private val root: BlockTree,
    private val logSink: LogSink,
    private val considerExtensions: Boolean,
) {
    private var extensionStack: ExtensionStack = Cons(Extensions.none, Cons.Empty)

    fun desugar() {
        val changes = mutableListOf<Pair<TEdge, DotOperationReplacer>>()

        fun walk(t: Tree, enclosingTypeTree: Tree?) {
            if (t is CallTree && t.size != 0) {
                val callee = t.child(0)
                if (callee is RightNameLeaf) {
                    val dotKind = when (callee.content) {
                        dotBuiltinName -> DotKind.SimpleDot
                        chainNullBuiltinName -> DotKind.NullSafeDot
                        else -> null
                    }
                    if (dotKind != null) {
                        val change = desugarDotOperation(
                            t.incoming!!, // Safe since root is not a call
                            t,
                            logSink,
                            extensionStack.head,
                            actuallyEnclosingTypeTree = enclosingTypeTree,
                            dotKind = dotKind,
                        )
                        if (change != null) {
                            changes.add(change)
                        }
                    }
                }
            }
            val extensionStackBefore = extensionStack
            if (t is BlockTree && considerExtensions) {
                // Find extension declarations so that we can store them with dot helpers
                val extensions = buildMap<Symbol, MutableList<ExtensionResolution>> {
                    for (e in t.edges) {
                        val decorated = lookThroughDecorations(e).target
                        if (decorated is DeclTree) {
                            val parts = decorated.parts
                            if (parts != null) {
                                val resolvedName = parts.name.content as ResolvedName
                                parts.metadataSymbolMultimap[extensionSymbol]?.forEach { metadataEdge ->
                                    val ext = metadataEdge.target.valueContained
                                    var metadataProblem: Any? = null
                                    when (ext?.typeTag) {
                                        TString -> putMultiList( // For instance extensions we get "dotName"
                                            Symbol(TString.unpack(ext)),
                                            InstanceExtensionResolution(resolvedName),
                                        )
                                        null -> metadataProblem = "missing"
                                        else -> metadataProblem = ext
                                    }
                                    if (metadataProblem != null) {
                                        logSink.log(
                                            Log.Error,
                                            MessageTemplate.UnexpectedMetadata,
                                            metadataEdge.target.pos,
                                            listOf(extensionSymbol, metadataProblem),
                                        )
                                    }
                                }
                                parts.metadataSymbolMultimap[staticExtensionSymbol]?.forEach { metadataEdge ->
                                    val ext = metadataEdge.target.valueContained
                                    var metadataProblem: Any? = null
                                    val unpacked = ext?.let { unpackPairValue(it) }
                                    if (unpacked != null) {
                                        val (_, dotName) = unpacked
                                        val dotNameText = TString.unpackOrNull(dotName)
                                        if (dotNameText != null) {
                                            putMultiList(
                                                Symbol(dotNameText),
                                                StaticExtensionResolution(resolvedName),
                                            )
                                        } else {
                                            metadataProblem = dotName
                                        }
                                    } else if (ext == null) {
                                        metadataProblem = "missing"
                                    } else {
                                        metadataProblem = ext
                                    }
                                    if (metadataProblem != null) {
                                        logSink.log(
                                            Log.Error,
                                            MessageTemplate.UnexpectedMetadata,
                                            metadataEdge.target.pos,
                                            listOf(extensionSymbol, metadataProblem),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (extensions.isNotEmpty()) {
                    extensionStack = Cons(Extensions(extensions, extensionStack.headOrNull), extensionStack)
                }
            }
            val newlyEnclosingTypeTree = (t as? FunTree)?.parts?.metadataSymbolMap?.get(typeDefinedSymbol)?.target
                ?: enclosingTypeTree
            for (c in t.children) {
                walk(c, newlyEnclosingTypeTree)
            }
            extensionStack = extensionStackBefore
        }
        walk(root, null)

        // Do replacements in reverse so that deeper edges are replaced before their targets are
        // potentially freed to be used in other replacements.
        for ((edge, makeReplacement) in changes.asReversed()) {
            edge.replace {
                makeReplacement()
            }
        }
    }
}

private fun Planting.plantHandler(
    pos: Position,
    memberAccessor: MemberAccessor,
    symbol: Symbol,
    extensions: Extensions,
) {
    val helper = DotHelper(memberAccessor, symbol, extensions[symbol])
    V(pos, Value(helper))
}

private fun desugarDotOperation(
    dotCallEdge: TEdge,
    dotCall: CallTree,
    logSink: LogSink,
    extensions: Extensions,
    actuallyEnclosingTypeTree: Tree?,
    dotKind: DotKind,
): Pair<TEdge, DotOperationReplacer>? {
    if (dotCall.size != BINARY_OP_CALL_ARG_COUNT) {
        logSink.log(Log.Error, MessageTemplate.ArityMismatch, dotCall.pos, values = listOf(2))
        // Though we fail, plow ahead to get type info for tooling.
        dotCall.add(
            newChild = ValueLeaf(dotCall.document, dotCall.edge(0).target.pos, vMissingSymbol),
        )
    }

    val subjectEdge = dotCall.edge(1)
    val subject = subjectEdge.target
    val subjectPos = subject.pos
    val nameEdge = dotCall.edge(2)
    val nameTree = nameEdge.target
    val symbol = nameTree.symbolContained
    if (symbol == null) {
        val problem = LogEntry(
            Log.Error,
            MessageTemplate.ExpectedValueOfType,
            dotCall.pos,
            listOf(TSymbol, nameTree),
        )
        problem.logTo(logSink)
        convertToErrorNode(dotCallEdge, problem)
        return null
    }

    val subjectValue = subject.valueContained
    // See if it's a static member access first.
    subjectValue?.let { subjectValue ->
        val subjectType = asReifiedType(subjectValue)
        // If we know that the static member is resolved against the type,
        // then turn it into a `getStatic` call now.
        // If it might resolve to an extension, we need to delay resolution
        // until the *Typer* can check whether there is a member on the
        // type and/or use type info to filter the extension list.
        if (subjectType != null && extensions[symbol].isEmpty()) {
            val gets = when (subjectType) {
                // Nothing really causes internal static get here these days, because this is already a special case.
                // TODO Some way to fake conjure for testing?
                actuallyEnclosingTypeTree?.reifiedTypeContained -> BuiltinFuns.vIGets
                else -> GetStaticOp.externalStaticGot(subjectType.type2, symbol)?.let { got ->
                    return@desugarDotOperation dotCallEdge to { V(got) }
                } ?: BuiltinFuns.vGets
            }
            when (dotKind) {
                DotKind.SimpleDot -> Unit // Cool.
                DotKind.NullSafeDot -> logSink.log(
                    Log.Warn,
                    MessageTemplate.StaticMemberUsesChaining,
                    dotCall.pos,
                    emptyList(),
                )
            }
            return@desugarDotOperation dotCallEdge to {
                Call {
                    V(dotCall.child(0).pos, gets)
                    Replant(freeTree(subject))
                    Replant(freeTree(nameTree))
                }
            }
        }
    }
    if (actuallyEnclosingTypeTree != null && dotKind == DotKind.SimpleDot) {
        val actuallyEnclosingType = actuallyEnclosingTypeTree.reifiedTypeContained?.type2
        val actuallyEnclosingTypeShape = (actuallyEnclosingType as? DefinedNonNullType)?.definition
        if (actuallyEnclosingTypeShape?.name == subject.nameContained && extensions[symbol].isEmpty()) {
            // The name is a type name, post name resolution, and no matching extension, so must be a static get.
            return dotCallEdge to {
                Call {
                    V(dotCall.child(0).pos, BuiltinFuns.vIGets) // also matches the enclosing type name specifically
                    Replant(freeTree(subject))
                    Replant(freeTree(nameTree))
                }
            }
        }
    }

    // If not, proceed to treat as an instance member.
    val leftHandOfMacroContext = leftHandOfMacroContext(dotCall)
    val context: DotContext = run dotContext@{
        if (leftHandOfMacroContext != null) {
            return@dotContext DotContext.Write
        }
        val parent = dotCallEdge.source!! // Root is not a call
        if (parent is CallTree) {
            if (dotCallEdge.edgeIndex == 0) {
                return@dotContext DotContext.Call
            } else if (dotCallEdge.edgeIndex == 1 && parent.child(0).isSetLocalRef) {
                return@dotContext DotContext.Write
            }
        }
        DotContext.Read
    }

    // If it's an internal use from within the body of a class, which one?
    val enclosingTypeShape: TypeShape?
    val enclosingTypeTree: Tree?
    val enclosingTypeValue: Value<*>?
    run thisType@{
        if (subject is CallTree && subject.size == 2) {
            val subjectCallee = subject.child(0)
            if (subjectCallee.functionContained == BuiltinFuns.thisPlaceholder) {
                val subjectTypeTree = subject.child(1)
                val subjectType = subjectTypeTree.reifiedTypeContained
                val subjectNominalType = subjectType?.type as? NominalType
                val definition = subjectNominalType?.definition as? TypeShape
                if (definition is TypeShape) {
                    enclosingTypeShape = definition
                    enclosingTypeTree = subjectTypeTree
                    enclosingTypeValue = Value(subjectType)
                    return@thisType
                }

                // This is probably an internal error since we auto-attach type
                // information to uses of `this`.
                val problem = LogEntry(
                    Log.Error,
                    MessageTemplate.ExpectedTypeShape,
                    subjectPos,
                    listOf(subjectType ?: abbreviate(subjectTypeTree.toPseudoCode())),
                )
                problem.logTo(logSink)
                convertToErrorNode(subjectEdge, problem)
            }
        } else if (actuallyEnclosingTypeTree != null) {
            // TODO Instead go straight to internal static get? See above logic on direct jump.
            val actuallyEnclosingType = actuallyEnclosingTypeTree.reifiedTypeContained?.type
            val actuallyEnclosingTypeShape = (actuallyEnclosingType as? NominalType)?.definition as? TypeShape
            if (actuallyEnclosingTypeShape?.name == subject.nameContained) {
                enclosingTypeShape = actuallyEnclosingTypeShape
                enclosingTypeTree = actuallyEnclosingTypeTree
                enclosingTypeValue = actuallyEnclosingTypeTree.valueContained
                return@thisType
            }
        }
        enclosingTypeShape = null
        enclosingTypeTree = null
        enclosingTypeValue = null
    }

    // We need to distinguish backed properties from other kinds of accesses.
    // If this is running during the define stage, we have that info.
    // Otherwise, turn it into a dot-helper that packs info up and adjust during
    // closureConvertClasses.  If there's an extension method or property in scope
    // for symbol, then we delay adjustment all the way through the first run of
    // the Typer.
    val member = enclosingTypeShape?.properties?.find { it.symbol == symbol }
    val doc = dotCall.document

    debug {
        console.group("Desugar dot @ ${dotCall.pos}") {
            dotCall.incoming?.source?.toPseudoCode(console.textOutput, singleLine = false)
            console.log("Subject  is ${subject.toLispy()}")
            console.log("Symbol   is $symbol")
            console.log("Context  is $context")
            console.log("thisType is ${enclosingTypeShape?.name}")
            console.log("member   is ${member?.name}")
        }
    }

    // Backed properties are special.  There is no need to go through a getter or a setter
    // to access them, and their name corresponds directly to a binding in the environment
    // record that represents the instance's state vector.
    //     this(TypeName__0).backedProperty
    // ->
    //     getp(this(TypeName__0), backedProperty__123)
    //
    // We don't have enough information during the syntax stage to resolve these, but during
    // the define stage, after we've incorporate resolved names into PropertyShape definitions
    // we rewrite uses of iget/iset to use setp/getp instead.
    // We also resolve some reads and calls to getStatic during later stages.
    // See MaybeAdjustDotHelper for details.

    val edgeToReplace: TEdge
    val replacer: DotOperationReplacer
    when (context) {
        DotContext.Write -> {
            // Replace the whole assignment
            // Either we have a leftHandOfMacro context or edgeToReplace's target is a call to `=`
            edgeToReplace = leftHandOfMacroContext?.edgeToReplace
                ?: dotCallEdge.source!!.incoming!! // Root is not an assignment
            val assignmentPos = edgeToReplace.target.pos
            replacer = {
                val right = leftHandOfMacroContext?.assigned?.target
                    ?: (edgeToReplace.target as CallTree).child(2)
                Block(assignmentPos) {
                    val rightTemporary = if (right is ValueLeaf) {
                        null
                    } else {
                        val t = doc.nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
                        Decl(assignmentPos.leftEdge, t) {}
                        t
                    }
                    // We need a block so that we can route the new value around as the result.
                    Call(assignmentPos) {
                        plantHandler(
                            nameTree.pos,
                            if (enclosingTypeShape != null) {
                                InternalSet
                            } else {
                                ExternalSet
                            },
                            symbol,
                            Extensions.none,
                        )
                        if (enclosingTypeTree != null) {
                            // internal calls need the enclosing type at
                            // InternalCall.enclosingTypeIndexOrNegativeOne
                            V(enclosingTypeTree.pos, enclosingTypeValue!!)
                        }
                        Replant(freeTarget(subjectEdge))
                        // `this.x = y` -> (Call nym`=` this.x y) so right is at position 2
                        if (rightTemporary != null) {
                            // Pass (t#0 = right) as argument to setter so that we capture
                            // the assigned value before any setter side effect
                            Call(right.pos, BuiltinFuns.setLocalFn) {
                                Ln(rightTemporary)
                                Replant(freeTree(right))
                            }
                        } else {
                            Replant(freeTree(right))
                        }
                    }
                    // The result of an assignment is the value passed to the setter,
                    // regardless of what the setter actually does internally.
                    if (rightTemporary != null) {
                        Rn(assignmentPos.rightEdge, rightTemporary)
                    } else {
                        Replant(right.copy())
                    }
                }
            }
        }
        DotContext.Call -> {
            // Replace the call with a method invocation handler that internally will fall back
            // on invoking a property that is callable
            edgeToReplace = dotCallEdge.source!!.incoming!! // Root is not a call
            replacer = {
                val call = edgeToReplace.target as CallTree
                Call(call.pos) {
                    Call(listOf(subjectPos, nameTree.pos).spanningPosition(subjectPos)) {
                        plantHandler(
                            nameTree.pos,
                            if (enclosingTypeShape != null) {
                                InternalBind
                            } else {
                                ExternalBind
                            },
                            symbol,
                            extensions,
                        )
                        if (enclosingTypeTree != null) {
                            // internal calls need the enclosing type at
                            // InternalCall.enclosingTypeIndexOrNegativeOne
                            V(enclosingTypeTree.pos, enclosingTypeValue!!)
                        }
                        Replant(freeTarget(subjectEdge))
                    }
                    // `this.f(x) -> (Call this.f x) so arg 0 is at position 1
                    call.edges.subListToEnd(1).forEach {
                        Replant(freeTarget(it))
                    }
                }
            }
        }
        DotContext.Read -> {
            // Replace with a property get handler that internally will fall back to
            // returning a bound method.
            edgeToReplace = dotCallEdge
            replacer = {
                Call(dotCall.pos) {
                    plantHandler(
                        nameTree.pos,
                        if (enclosingTypeShape != null) {
                            InternalGet
                        } else {
                            ExternalGet
                        },
                        symbol,
                        Extensions.none,
                    )
                    if (enclosingTypeTree != null) {
                        // internal calls need the enclosing type at
                        // InternalCall.enclosingTypeIndexOrNegativeOne
                        V(enclosingTypeTree.pos, enclosingTypeValue!!)
                    }
                    Replant(freeTarget(subjectEdge))
                }
            }
        }
    }

    val needsChainingAdjustment =
        dotKind == DotKind.NullSafeDot && (subjectValue == null || subjectValue == TNull.value)

    val chainingReplacer = if (!needsChainingAdjustment) {
        replacer
    } else {
        // x?.member ->
        // do {
        //   // if x was captured in a temporary, declare it here
        //   if (x == null) {
        //     null
        //   } else {
        //     dotCall
        //   }
        // }
        {
            val needToExtractSubjectIntoTemporary = when (subject) {
                is ValueLeaf -> false
                is NameLeaf -> false
                else -> true
            }
            // Introduce a temporary and store its declaration and assignment for later incorporation
            // into a replacement so that we have proper order and frequency of operations for
            //      callThatMightSideEffect(orArgThatMightSideEffect())?.method(args)
            val temporary: Temporary? =
                if (needToExtractSubjectIntoTemporary) {
                    doc.nameMaker.unusedTemporaryName("subject")
                } else {
                    null
                }

            // Set when we create children
            var conditionIndex: Int = -1
            var thenClauseIndex: Int = -1
            var elseClauseIndex: Int = -1

            // Create a flow control with an if/then/else based on whether
            // the subject is null.
            val flowMaker: FlowMaker = { block ->
                fun stmt(i: Int) =
                    ControlFlow.Stmt(BlockChildReference(i, block.children[i].pos))
                StructuredFlow(
                    ControlFlow.StmtBlock(
                        block.pos,
                        buildList {
                            (0 until conditionIndex).mapTo(this, ::stmt)
                            add(
                                ControlFlow.If(
                                    block.pos,
                                    // The condition: isNull(subject)
                                    stmt(conditionIndex).ref,
                                    // then null
                                    ControlFlow.StmtBlock.wrap(stmt(thenClauseIndex)),
                                    // else, the rest of the output from the non-null-chaining replacer
                                    (elseClauseIndex until block.size)
                                        .map(::stmt)
                                        .let { stmts ->
                                            ControlFlow.StmtBlock(
                                                stmts.spanningPosition(block.pos.rightEdge),
                                                stmts,
                                            )
                                        },
                                ),
                            )
                        },
                    ),
                )
            }

            Block(dotCall.pos, flowMaker) {
                var childIndex = 0 // Keep track of child indices for BlockChildReferences
                // Declare any temporary
                val simpleSubject: Tree
                if (temporary != null) {
                    val subjectExpr = freeTarget(subjectEdge)
                    simpleSubject = RightNameLeaf(doc, subjectPos, temporary)
                    subjectEdge.replace {
                        Call(subjectPos, BuiltinFuns.vNotNullFn) {
                            Rn(subjectPos, temporary)
                        }
                    }
                    Decl(temporary)
                    childIndex++
                    Call(BuiltinFuns.vSetLocalFn) {
                        Ln(temporary)
                        Replant(subjectExpr)
                    }
                    childIndex++
                } else {
                    val subject = subjectEdge.target
                    simpleSubject = subject.copy()
                    if (simpleSubject !is ValueLeaf) {
                        freeTree(subject)
                        subjectEdge.replace {
                            Call(subjectPos, BuiltinFuns.vNotNullFn) {
                                Replant(subject)
                            }
                        }
                    }
                }
                conditionIndex = childIndex++
                val subjectTypeNullable = simpleSubject.typeInferences?.type?.let {
                    MkType.nullable(it)
                }
                val isNullFnType = typeFromSignature(IsNullFn.sig)
                val isNullCallType = subjectTypeNullable?.let {
                    CallTypeInferences(
                        WellKnownTypes.booleanType,
                        isNullFnType,
                        mapOf(isNullFnType.typeFormals.first() to it),
                        listOf(),
                    )
                }
                Call(subjectPos.leftEdge, type = isNullCallType) {
                    V(vIsNullFn, isNullFnType)
                    Replant(simpleSubject)
                }
                thenClauseIndex = childIndex++
                V(subjectPos.leftEdge, TNull.value, subjectTypeNullable)
                elseClauseIndex = childIndex
                replacer()
            }
        }
    }

    return edgeToReplace to chainingReplacer
}

private val Tree.isSetLocalRef: Boolean get() {
    if (this is RightNameLeaf) {
        val builtinName = this.content as? BuiltinName
        if (builtinName?.builtinKey == "=") {
            return true
        }
    }
    return this.functionContained == BuiltinFuns.setLocalFn
}

enum class DotKind {
    SimpleDot,
    NullSafeDot,
}
