package lang.temper.type

import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.common.isEmpty
import lang.temper.env.BindingNamingContext
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallableValue
import lang.temper.value.CoverFunction
import lang.temper.value.Document
import lang.temper.value.Fail
import lang.temper.value.LeafTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.staticBuiltinName
import lang.temper.value.typeShapeAtLeafOrNull

private const val DEBUG = true // do not commit
private inline fun debug(action: () -> Unit) {
    if (DEBUG) {
        action()
    }
}

/**
 * Info about how to resolve an extension method
 * (see @extension and @staticExtension in builtins).
 */
sealed class ExtensionResolution : TokenSerializable {
    abstract fun toLeaf(document: Document, pos: Position): LeafTree
}

sealed class NameExtensionResolution : ExtensionResolution() {
    abstract val resolution: ResolvedName

    override fun toLeaf(document: Document, pos: Position) =
        RightNameLeaf(document, pos, resolution)

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(resolution.toToken(false))
    }
}
data class InstanceExtensionResolution(
    override val resolution: ResolvedName,
) : NameExtensionResolution()

data class StaticExtensionResolution(
    override val resolution: ResolvedName,
) : NameExtensionResolution()

data class FunctionResolution(
    val fn: CallableValue,
) : ExtensionResolution() {
    override fun toLeaf(document: Document, pos: Position) =
        ValueLeaf(document, pos, Value(fn))

    override fun renderTo(tokenSink: TokenSink) {
        val builtinOperatorId = (fn as? NamedBuiltinFun)?.builtinOperatorId
        if (builtinOperatorId != null) {
            tokenSink.emit(OutputToken(builtinOperatorId.name, OutputTokenType.Word))
        } else {
            Value(fn).renderTo(tokenSink)
        }
    }
}

/**
 * Implements support for desugared dot operations including:
 *
 * - `subject.adjective = expr` : property set via [ExternalSet] and [InternalSet]
 * - `subject.adjective` : property read via [ExternalGet] and [InternalGet]
 * - `subject.verb(args)` : method call via [ExternalCall] and [InternalCall]
 * - `subject.verb` : read of a bound method via [ExternalGet] and [InternalGet]
 * - `subject.adjective(args)` : call to a function stored in a property via [ExternalCall] and
 *   [InternalCall] but rewritten statically by [lang.temper.frontend.maybeAdjustDotHelper]
 *
 * It also allows
 */
class DotHelper(
    val memberAccessor: MemberAccessor,
    val member: Member,
    /** Resolutions of the relevant extension function in scope with the same symbol. */
    val extensions: List<ExtensionResolution> = emptyList(),
) : SpecialFunction, NamedBuiltinFun, BuiltinStatelessMacroValue, TokenSerializable {
    override val name: String get() = buildString {
        append("do_")
        append(memberAccessor.prefix(member))
    }

    // May be filled in by the typer.
    override var sigs: List<Signature2>? = null

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(BuiltinName(name).toToken(inOperatorPosition = false))
        if (extensions.isNotEmpty()) {
            tokenSink.emit(OutToks.leftSquare)
            extensions.forEachIndexed { i, extensionResolution ->
                if (i != 0) {
                    tokenSink.emit(OutToks.comma)
                }
                when (extensionResolution) {
                    is FunctionResolution,
                    is InstanceExtensionResolution,
                    -> {}
                    is StaticExtensionResolution -> {
                        tokenSink.emit(staticBuiltinName.toToken(inOperatorPosition = false))
                    }
                }
                extensionResolution.renderTo(tokenSink)
            }
            tokenSink.emit(OutToks.rightSquare)
        }
    }

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val c = lang.temper.common.console // do not commit
        c.log("DotHelper.invoke $this")
        if ((interpMode == InterpMode.Partial || extensions.isNotEmpty()) &&
            memberAccessor !is CallMemberAccessor
        ) {
            // If we want to implement pre DefineStage execution,
            // we'd need to recognize and predict changes by
            // maybeAdjustDotHelper like treating Internal{Get,Set}s
            // of backed properties as getp/setp calls, and treating
            // gets and calls when the subject is a type expression
            // as applying to a static member.

            // One simple thing we could do is, if it's not a static
            // member, and we have no candidate members, then just
            // delegate to a union of the extensions.
            return NotYet
        }
        val args = macroEnv.args
        val sizeWanted = when (memberAccessor) {
            ExternalGet -> arityOne // (this)
            InternalGet -> arityTwo // (containingTypeShape, this)
            ExternalSet -> arityTwo // (this, newValue)
            InternalSet ->
                @Suppress("MagicNumber") // arity
                arityThree // (containingTypeShape, this, newValue)
            ExternalCall -> arityOneOrMore // (this, arg0, arg1, ...)
            InternalCall -> arityTwoOrMore // (containingTypeShape, this, arg0, arg1)
        }
        c.log(". args=$args, sizeWanted=$sizeWanted, n=${args.size}")
        if (args.size !in sizeWanted) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(sizeWanted))
        }
        val subjectIndex = memberAccessor.firstArgumentIndex
        val originalSubject = args.evaluate(subjectIndex, interpMode)
        var subject = originalSubject as? Value<*> ?: return originalSubject
        c.log(". subject=$subject")
        // Promote the subject from a builtin type (like TInt) to the backing class
        if (subject.typeTag !is TClass) {
            subject = promoteSimpleValue(subject) ?: subject
        }
        val subjectTypeTag = subject.typeTag
        val classType = subjectTypeTag as? TClass
        val typeShape = classType?.typeShape

        val accessingTypeShape = when (memberAccessor) {
            is InternalMemberAccessor -> {
                val enclosingTypeIndex = memberAccessor.enclosingTypeIndexOrNegativeOne
                when (val result = args.evaluate(enclosingTypeIndex, interpMode)) {
                    is Fail, NotYet -> return result
                    is Value<*> ->
                        result.typeShapeAtLeafOrNull
                            ?: run {
                                return@invoke macroEnv.fail(
                                    MessageTemplate.ExpectedValueOfType,
                                    pos = args.pos(1),
                                    values = listOf("nominal type", result.typeTag),
                                )
                            }
                }
            }
            is ExternalMemberAccessor -> null
        }
        val argIndex = memberAccessor.firstArgumentIndex + 1 // skip over subject

        val accessibleMembers = typeShape?.let { accessibleMembers(it) } ?: listOf()
        debug {
            console.log("memberAccessor=$memberAccessor member=$member")
            console.log(". subject=$subject")
            console.log(". typeShape=$typeShape")
            console.log(". accessingTypeShape=$accessingTypeShape")
            console.log(". argIndex=$argIndex")
            console.log(". accessibleMember")
            for (member in accessibleMembers) {
                console.log(". . $member")
            }
        }
        fun inaccessible(): Fail {
            val problem = if (classType == null) {
                LogEntry(
                    Log.Error,
                    MessageTemplate.ExpectedValueOfType,
                    pos = args.pos(0),
                    values = listOf("$subjectTypeTag's wrapper class", subject.typeTag),
                )
            } else {
                LogEntry(
                    Log.Error,
                    MessageTemplate.NoAccessibleMember,
                    pos = macroEnv.pos,
                    values = listOf(member, typeShape?.name ?: subjectTypeTag),
                )
            }
            macroEnv.explain(problem)
            return Fail(problem)
        }

        // If we know any resolution has to be via an extension, use a cover function as an
        // abstraction to solve any overload resolution.
        // This is important for early collapsing of arithmetic operations.
        if (accessibleMembers.isEmpty() && extensions.isNotEmpty() && extensions.all { it is FunctionResolution }) {
            val cover = CoverFunction(extensions.map { (it as FunctionResolution).fn })
            cover.debugDoNotCommit = true
            cover.uncover(macroEnv.args, macroEnv, interpMode)?.let { (resolution, args) ->
                if (args != null) {
                    val fn = TFunction.unpackOrNull(resolution as? Value<*>)
                    c.log("uncovered to $fn")
                    if (fn is CallableValue) {
                        val actuals = args.toPositionalActuals(macroEnv)
                        if (actuals != null) {
                            val result = fn.invoke(actuals, macroEnv, interpMode).also {
                                c.log("covered extensions -> $it")
                            }
                            if (macroEnv.call != null && result is Value<*> && interpMode == InterpMode.Partial && fn.isPure) {
                                macroEnv.replaceMacroCallWith {
                                    V(macroEnv.pos, result)
                                }
                            }
                            return result
                        }
                    }
                }
            }
        }

        val doc = macroEnv.document

        fun dispatchCallTo(methodShape: MemberShape): PartialResult =
            when (val methodDefinition = lookupMemberDefinition(methodShape)) {
                is Value<*> -> {
                    val calleeTree = ValueLeaf(doc, macroEnv.pos, methodDefinition)
                    val argTrees = listOf(
                        ValueLeaf(doc, macroEnv.pos.rightEdge, subject),
                    ) + args.rawTreeList.drop(argIndex)
                    macroEnv.dispatchCallTo(
                        calleeTree,
                        methodDefinition,
                        argTrees,
                        interpMode,
                    )
                }
                NotYet, is Fail -> methodDefinition
            }

        return when (memberAccessor) {
            InternalGet, ExternalGet,
            ExternalSet, InternalSet,
            -> {
                val wantGetter = memberAccessor is GetMemberAccessor
                var helperName: TemperName? = null
                // Find the name of the corresponding helper function.
                for (p in accessibleMembers) {
                    if (p is PropertyShape) {
                        helperName = if (wantGetter) { p.getter } else { p.setter }
                        if (helperName != null) {
                            break
                        }
                    }
                }
                // Make sure there is a helper and it is accessible
                val helperShape = if (helperName == null) {
                    null
                } else {
                    accessibleMembers.first { it.name == helperName }
                }
                if (helperName == null || helperShape == null) {
                    macroEnv.explain(
                        if (wantGetter) {
                            MessageTemplate.NoAccessibleGetter
                        } else {
                            MessageTemplate.NoAccessibleSetter
                        },
                        values = listOf(member, typeShape?.name ?: subjectTypeTag),
                    )
                    return Fail
                }

                dispatchCallTo(helperShape)
            }
            ExternalCall, InternalCall ->
                when (val method = accessibleMembers.firstOrNull()) {
                    null -> inaccessible()
                    is MethodShape if (method.methodKind == MethodKind.Normal) ->
                        dispatchCallTo(method)
                    else ->
                        // TODO: handle call of functions from getter as in
                        //     obj.f()
                        // where (obj.f) is a use of a getter than gets a function.
                        // Currently, maybeAdjustDotHelper handles this but perhaps late.
                        Fail
                }
        }
    }

    fun accessibleMembers(accessingTypeShape: TypeShape): Iterable<MemberShape> =
        AccessibleFilter(accessingTypeShape.membersMatching(member, member is OperatorMember), accessingTypeShape)

    fun publicMembers(accessingTypeShape: TypeShape): Iterable<MemberShape> =
        AccessibleFilter(accessingTypeShape.membersMatching(member, member is OperatorMember), null)

    override val callMayFailPerSe: Boolean
        get() = true

    override fun toString() = "DotHelper(${this.memberAccessor.prefix}, ${this.member})"
}

class AccessibleFilter<MEMBER_T : MemberShape>(
    val members: Iterable<MEMBER_T>,
    val accessingTypeShape: TypeShape?,
) : Iterable<MEMBER_T> {
    @Suppress("IteratorHasNextCallsNextMethod") // Because we're filtering.
    override fun iterator(): Iterator<MEMBER_T> = object : Iterator<MEMBER_T> {
        private var nextAccessible: MEMBER_T? = null
        private val it = members.iterator()
        private val accessorName = accessingTypeShape?.name

        override fun hasNext(): Boolean {
            while (nextAccessible == null && it.hasNext()) {
                val member = it.next()
                val accessible =
                    when ((member as? VisibleMemberShape)?.visibility) {
                        null -> false
                        Visibility.Private -> member.enclosingType.name == accessorName
                        Visibility.Protected -> accessorName != null
                        Visibility.Public -> true
                    }
                if (accessible) {
                    nextAccessible = member
                }
            }
            return nextAccessible != null
        }

        override fun next(): MEMBER_T {
            if (!hasNext()) { throw NoSuchElementException() }
            val result = nextAccessible!!
            nextAccessible = null
            return result
        }
    }
}

private fun lookupMemberDefinition(
    memberShape: MemberShape,
): PartialResult {
    val methodName = memberShape.name
    // If we've reached Stage.Define, then member definitions should be top-level
    // definitions, so look-up via the defining Module's top-levels.
    val methodContext = memberShape.enclosingType.name.origin as? BindingNamingContext
    val methodDefinition = methodContext?.getTopLevelBinding(methodName)
    return methodDefinition?.value ?: NotYet
}

private val arityOne = 1..1
private val arityTwo = 2..2

@Suppress("MagicNumber") // Three is what's written on the tin.  (The magic tin)
private val arityThree = 3..3
private val arityOneOrMore = 1..Int.MAX_VALUE
private val arityTwoOrMore = 2..Int.MAX_VALUE
