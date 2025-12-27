package lang.temper.frontend.generate

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.RttiCheckFunction
import lang.temper.builtin.SETP_ARITY
import lang.temper.builtin.problems
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.ignore
import lang.temper.common.padTo
import lang.temper.frontend.Module
import lang.temper.frontend.typestage.isConstructor
import lang.temper.frontend.typestage.logInvalid2BecauseMissingType
import lang.temper.frontend.typestage.logInvalidBecauseMissingType
import lang.temper.interp.New
import lang.temper.interp.forEachActual
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.AndType
import lang.temper.type.BubbleType
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.OrType
import lang.temper.type.StaticType
import lang.temper.type.TopType
import lang.temper.type.TypeActual
import lang.temper.type.TypeContext
import lang.temper.type.TypeDefinition
import lang.temper.type.TypePartMapper
import lang.temper.type.TypeShape
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.invalidType2
import lang.temper.type.isBooleanLike
import lang.temper.type.isBubbly
import lang.temper.type.isVoid
import lang.temper.type.isVoidAllowing
import lang.temper.type.mentionsInvalid
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.arityRange
import lang.temper.value.functionContained
import lang.temper.value.nameContained
import lang.temper.value.staticTypeContained
import lang.temper.value.symbolContained
import lang.temper.value.typeDeclSymbol
import lang.temper.value.visibilitySymbol

/**
 * Reports errors on typing violations.
 *
 * This happens during [code generation stage][lang.temper.stage.Stage.GenerateCode]
 * instead of during [the type stage][lang.temper.stage.Stage.Type] because we may want type-aware
 * macros to be able to analyze and repair type violations.
 * For example, mid-stage macros that let type inference fill in where possible, but fall-back to
 * adding missing type information to function / type definitions based on conventions of a system
 * Temper is interoperating with.
 *
 * It assumes that the [Typer][lang.temper.frontend.typestage.Typer] has filled in type metadata.
 */
internal class TypeChecker(
    val module: Module,
) {
    @Suppress("unused") // When printf debugging, routes messages properly.
    private val console = module.console
    private var inConstructor = false
    private val logSink = module.logSink
    private val typeContext = TypeContext()
    private val typeContext2 = TypeContext2()
    private val voidReturnDeclNames = mutableSetOf<TemperName>()

    fun check(root: BlockTree) {
        // First make sure we track which things actually can be void. Could store all return values, but only bother
        // with those that can be void, since that's all that needs the extra checks.
        if (module.outputType?.isVoidAllowing == true) {
            voidReturnDeclNames.add(module.outputName!!)
        }
        // Now visit everywhere to check compliance.
        fun dig(t: Tree) {
            t.typeInferences?.explanations?.forEach { explanation ->
                explanation.logTo(logSink)
            }
            when (t) {
                is BlockTree -> checkBlock(t)
                is CallTree -> checkCall(t)
                is DeclTree -> checkDecl(t)
                is EscTree -> checkEsc(t)
                is FunTree -> checkFun(t)
                is StayLeaf -> checkStay(t)
                is LeftNameLeaf -> checkLeftName(t)
                is RightNameLeaf -> checkRightName(t)
                is ValueLeaf -> checkValue(t)
            }
            // Custom recursion to support constructor tracking.
            val isConstructor = t is FunTree && t.isConstructor()
            if (isConstructor) {
                inConstructor = true
            }
            try {
                for (kid in t.children) {
                    dig(kid)
                }
            } finally {
                if (isConstructor) {
                    // We de-nest type definitions, so we don't expect nested constructors.
                    inConstructor = false
                }
            }
        }
        dig(root)
    }

    private fun checkBlock(t: BlockTree) {
        // Conditions should not be a sub-type of Boolean || Bubble because we should have already
        // converted failing subtrees into passing branches with explicit failure variable checks.
        (t.flow as? StructuredFlow)?.let { flow ->
            fun visit(cf: ControlFlow) {
                if (cf is ControlFlow.Conditional) {
                    t.dereference(cf.condition)?.target?.let { condition ->
                        val type = condition.typeInferences?.type
                        if (type?.isBooleanLike != true) {
                            logSink.log(
                                level = Log.Error,
                                template = MessageTemplate.ExpectedValueOfType,
                                pos = condition.pos,
                                values = listOf(
                                    WellKnownTypes.booleanType,
                                    type ?: logSink.logInvalidBecauseMissingType(condition, "condition"),
                                ),
                            )
                        }
                    }
                }
                cf.clauses.forEach { visit(it) }
            }
            visit(flow.controlFlow)
        }
    }

    private fun checkCall(t: CallTree) {
        val fn = t.childOrNull(0)?.functionContained
        when (fn) {
            BuiltinFuns.setLocalFn -> checkAssignment(t)
            BuiltinFuns.handlerScope -> Unit // TODO
            BuiltinFuns.getpFn, BuiltinFuns.getsFn, BuiltinFuns.igetsFn -> Unit // TODO
            BuiltinFuns.setpFn -> checkSetp(t)
            BuiltinFuns.angleFn -> Unit // Already taken into account
            BuiltinFuns.asFn, BuiltinFuns.isFn -> {
                checkRegularCall(t)
                checkRttiCheckAllowed(t, fn as RttiCheckFunction)
            }
            else -> checkRegularCall(t)
        }
        // And make sure we don't use Void in calls. Assignment is handled specially, so exclude that in checks here.
        // Also avoid preserve calls because it can store whatever args it wants, including void.
        // TODO How to avoid so much special handling for preserve?
        if (!(fn == BuiltinFuns.preserveFn || fn == BuiltinFuns.setLocalFn)) {
            for (kid in t.children) {
                if (kid.typeInferences?.type?.isVoid == true) {
                    reportBadVoid(kid.pos)
                }
            }
        }
    }

    private fun checkDecl(t: DeclTree) {
        val meta = t.parts?.metadataSymbolMap ?: return
        meta[typeDeclSymbol]?.let { typeDeclEdge ->
            // Working on the type as a whole might reduce lookup effort vs each member decl separately.
            val type = typeDeclEdge.target.staticTypeContained ?: return@checkDecl
            val shape = ((type as? NominalType)?.definition as? TypeShape) ?: return@checkDecl
            members@for (member in shape.members) {
                val memberStay = member.stay ?: continue@members
                val memberParts = (memberStay.incoming?.source as? DeclTree)?.parts ?: continue@members
                val memberMeta = memberParts.metadataSymbolMap
                if (member is VisibleMemberShape) {
                    val visibility = member.visibility
                    for (overridden in member.overriddenMembers ?: emptyList()) {
                        // Check reduced visibility.
                        // Inequality check should support protected as well as public and private.
                        val baseVisibility = overridden.superTypeMember.visibility
                        if (visibility < baseVisibility) {
                            logSink.log(
                                level = Log.Error,
                                template = MessageTemplate.OverrideLowersVisibility,
                                pos = memberMeta[visibilitySymbol]?.target?.pos ?: memberParts.name.pos,
                                values = listOf(overridden.superTypeMember.enclosingType.name),
                            )
                        }
                        // TODO Check mismatched return or property types.
                    }
                }
            }
        }
    }

    private fun checkEsc(t: EscTree) {
        ignore(t)
    }

    private fun checkFun(t: FunTree) {
        // TODO: Check that the body obeys some properties:
        // - if the return type is Never, then every body branch from the start should either
        //   - pass over a call to a Never returning function, or
        //   - not reach the exit
        // - if the return type is disjoint from Bubble, then there must be no body path backwards
        //   from the failExit to the start that does not pass over a Never-typed call.
        //
        // TODO: for conditional failure we're going to need to additionally recognize that
        //    fn <T extends Top>(f: fn (): T): T { f() }
        // is allowed but
        //    fn <T extends Top>(f: fn (): T, g: fn (T): T throws Bubble): T { g(f()) }
        // is not because a branch from g can lead to failure even when f does not.

        // Check returnType vs returnDecl type.
        // Failure can happen in cases of inferred return type for lambda blocks.
        // With some effort, we likely can fix that, but this checks against that
        // for now as well as anything else that might slip through in the future.
        val returnType = (t.typeInferences?.type as? FunctionType)?.returnType
        val returnDecl = t.parts?.returnDecl
        val returnDeclName = returnDecl?.parts?.name
        val returnDeclType = returnDeclName?.typeInferences?.type
        checkSubType(returnDecl?.pos, returnType, returnDeclType)
        if (returnType?.isVoidAllowing == true && returnDeclName != null) {
            voidReturnDeclNames.add(returnDeclName.content)
        }
        if (returnType?.isBubbly == false) {
            checkAgainstBubbles(t)
        }
    }

    private fun checkAgainstBubbles(t: FunTree) {
        val body = t.parts?.body ?: return
        // This runs for any non-bubbly function.
        // TODO Could use the outer tree walk if we track scope going into and out of the function.
        TreeVisit.startingAt(body).forEach subs@{ sub ->
            // Don't go into nested functions, as that's a new scope for bubble allowance.
            sub is FunTree && return@subs VisitCue.SkipOne
            if (sub is CallTree) {
                // In the end, we only reference bubble when it actually escapes from functions.
                // And if some logic doesn't allow a branch to execute, we should clean it out before here.
                // Given the above, we can complain here about any call to bubble.
                if (sub.child(0).functionContained === BubbleFn) {
                    logSink.log(Log.Error, MessageTemplate.ExpectedNoBubble, sub.pos, listOf())
                }
            }
            VisitCue.Continue
        }.visitPreOrder()
    }

    private fun checkStay(t: StayLeaf) {
        ignore(t)
    }

    private fun checkLeftName(t: LeftNameLeaf) {
        ignore(t)
    }

    private fun checkRightName(t: RightNameLeaf) {
        ignore(t)
    }

    private fun checkValue(t: ValueLeaf) {
        ignore(t)
    }

    private fun checkAssignment(t: CallTree) {
        if (t.size != BINARY_OP_CALL_ARG_COUNT) {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.ArityMismatch,
                pos = t.pos,
                values = listOf(2),
            )
            return
        }
        // TODO: check that right type is a sub-type of left-type.
        val (_, leftTree, rightTree) = t.children
        val leftType = leftTree.typeInferences?.type
        val rightType = rightTree.typeInferences?.type
        // TODO: We probably want to enforce that we have either a left or a right type from the
        // checker, but baby steps.
        checkSubType(t.pos, leftType, rightType)
        // And make sure we don't use void as a value. Focus on actual void, not just void-like for now.
        if (rightType?.isVoid == true) {
            // We can assign voids only to simple names that are temporaries or appropriate return decls.
            val badVoid = when (val name = leftTree.nameContained) {
                null -> true
                is Temporary -> false
                else -> name !in voidReturnDeclNames
            }
            if (badVoid) {
                reportBadVoid(t.pos)
            }
        }
    }

    /** @param pos must be non-null if the other params are. */
    private fun checkSubType(pos: Position?, leftType: StaticType?, rightType: StaticType?) {
        if (leftType != null && rightType != null && !typeContext.isSubType(rightType, leftType)) {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.ExpectedSubType,
                pos = pos!!,
                values = listOf(leftType, rightType),
            )
        }
    }

    private fun checkSetp(t: CallTree) {
        val (_, nameTree, thisTree, valueTree) = t.children.padTo(SETP_ARITY + 1, null)
        val name = nameTree?.nameContained ?: return
        val typeShape = ((thisTree?.typeInferences?.type as? NominalType)?.definition as? TypeShape) ?: return
        // Tests say we're evaluating supertypes, maybe via some union type for `this`?
        val property = typeShape.properties.find { it.name == name } ?: return
        // Check has setter.
        // For now, don't worry about constructors.
        // In constructors, other checks seem to handle explicit `get` method cases.
        // TODO For constructors, separate init from post-init code, and error on post-init.
        if (!inConstructor && !property.hasSetter) {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.IncompatibleUsage,
                pos = t.pos,
                values = listOf(name.displayName, typeShape.name),
            )
        }
        // Check type.
        val propertyType = property.descriptor
            ?: logSink.logInvalid2BecauseMissingType(nameTree, "name")
        val valueType = valueTree?.typeInferences?.type
            ?: logSink.logInvalidBecauseMissingType(valueTree ?: t.pos.rightEdge, "value")
        if (failsValidSubtypeCheck(hackMapOldStyleToNew(valueType), propertyType)) {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.ExpectedSubType,
                pos = valueTree?.pos ?: t.pos,
                values = listOf(propertyType, valueType),
            )
        }
    }

    private fun checkRegularCall(t: CallTree) {
        val tTypeInferences = t.typeInferences
        val callee = t.childOrNull(0) ?: return
        val calleeTypes = tTypeInferences?.variant ?: return
        val actuals = extractTypedActuals(t) ?: return
        val bindings = tTypeInferences.bindings2
        fun bind(t: StaticType): StaticType =
            MkType.map(
                t,
                object : TypePartMapper {
                    override fun mapType(t: StaticType): StaticType {
                        if (t is NominalType && t.bindings.isEmpty()) {
                            val binding = bindings[t.definition]
                            if (binding is StaticType) {
                                return binding
                            }
                        }
                        return t
                    }

                    override fun mapBinding(b: TypeActual): TypeActual = b
                    override fun mapDefinition(d: TypeDefinition) = d
                },
                mutableMapOf(),
            )

        fun checkAgainstCalleeType(calleeType: StaticType) {
            when (calleeType) {
                BubbleType,
                -> Unit // Call never happens.  Odd but ok.  TODO: should we warn
                InvalidType,
                TopType,
                is NominalType,
                -> {
                    var reported = false
                    if (callee.functionContained is New) {
                        run newCall@{
                            val type = t.children.getOrNull(1)?.staticTypeContained ?: return@newCall null
                            val shape = ((type as? NominalType)?.definition as? TypeShape) ?: return@newCall null
                            when (shape.abstractness) {
                                // Customize the error messaging for this case.
                                Abstractness.Abstract -> {
                                    reported = true
                                    logSink.log(
                                        level = Log.Error,
                                        template = MessageTemplate.CannotInstantiateAbstractType,
                                        pos = t.child(1).pos,
                                        values = listOf(shape.name.displayName),
                                    )
                                }
                                Abstractness.Concrete -> null
                            }
                        }
                    }
                    if (!reported && calleeType != InvalidType) { // Invalid callee types already reported
                        logSink.log(
                            level = Log.Error,
                            template = MessageTemplate.ExpectedFunctionType,
                            pos = callee.pos,
                            values = listOf(calleeType),
                        )
                    }
                }
                is FunctionType -> {
                    val boundCalleeType = if (calleeType.typeFormals.isEmpty()) {
                        calleeType
                    } else {
                        MkType.fnDetails(
                            typeFormals = emptyList(),
                            valueFormals = calleeType.valueFormals.map { unbound ->
                                FunctionType.ValueFormal(
                                    symbol = unbound.symbol,
                                    staticType = bind(unbound.staticType),
                                    isOptional = unbound.isOptional,
                                )
                            },
                            restValuesFormal = calleeType.restValuesFormal?.let { bind(it) },
                            returnType = bind(calleeType.returnType),
                        )
                    }
                    val valueFormals = boundCalleeType.valueFormals
                    val restValuesFormal = boundCalleeType.restValuesFormal

                    if (actuals.size !in boundCalleeType.arityRange) {
                        logSink.log(
                            level = Log.Error,
                            template = MessageTemplate.ArityMismatch,
                            pos = t.pos,
                            values = listOf(valueFormals.size),
                        )
                    }
                    for (i in actuals.indices) {
                        val actual = actuals[i]
                        val formal = valueFormals.getOrNull(i)
                        if (actual.symbol != null && actual.symbol != formal?.symbol) {
                            // Check matching arg names.
                            // And until we improve features, expect all named actuals to be in order with the formals.
                            // This error message is less than ideal for correct names out of order, but it gets the job
                            // done for now.
                            // TODO(tjp): Improve error messages once overall named arg support improves.
                            logSink.log(
                                level = Log.Error,
                                template = MessageTemplate.UndeclaredName,
                                pos = actual.symbolPos!!,
                                values = listOf(actual.symbol.text),
                            )
                        }
                        val formalType = formal?.staticType
                            ?: restValuesFormal
                            ?: break
                        if (failsValidSubtypeCheck(actual.type, formalType)) {
                            // Preserve can do whatever it wants, especially for storing void args.
                            // TODO Figure out how not to special-case it?
                            if (callee.functionContained != BuiltinFuns.preserveFn) {
                                logSink.log(
                                    level = Log.Error,
                                    template = MessageTemplate.ExpectedSubType,
                                    pos = actual.pos,
                                    values = listOf(formalType, actual.type),
                                )
                            }
                        }
                    }
                    val computedType = tTypeInferences.type
                    val boundCalleeReturnType = boundCalleeType.returnType.let {
                        // HACK: allow Never-ish compatibility on return type for nullary specials
                        // until we get rid of NeverType entirely.
                        MkType.map(
                            it,
                            object : TypePartMapper {
                                override fun mapType(t: StaticType): StaticType =
                                    if (t is NominalType && t.definition == WellKnownTypes.neverTypeDefinition) {
                                        OrType.emptyOrType
                                    } else {
                                        t
                                    }
                                override fun mapBinding(b: TypeActual): TypeActual = b
                                override fun mapDefinition(d: TypeDefinition): TypeDefinition = d
                            },
                        )
                    }
                    if (failsValidSubtypeCheck(boundCalleeReturnType, computedType)) {
                        logSink.log(
                            level = Log.Error,
                            template = MessageTemplate.ExpectedSubType,
                            pos = t.pos,
                            values = listOf(computedType, boundCalleeType.returnType),
                        )
                    }
                }
                is OrType ->
                    // We should have pruned out incompatible variants already, so just check each.
                    calleeType.members.forEach { checkAgainstCalleeType(it) }
                is AndType ->
                    calleeType.members.forEach { checkAgainstCalleeType(it) }
            }
        }
        checkAgainstCalleeType(calleeTypes)
    }

    private fun checkRttiCheckAllowed(tree: CallTree, checkFn: RttiCheckFunction) {
        val typeTree = tree.childOrNull(2) ?: return
        val checkedExpr = tree.child(1)
        val targetType = typeTree.staticTypeContained ?: return
        val exprType = checkedExpr.typeInferences?.type ?: return
        if (exprType.mentionsInvalid || targetType.mentionsInvalid) {
            return // Error reported elsewhere
        }
        checkFn.problems(Either.Left(exprType), targetType, tree.pos)
            .forEach { it.logTo(logSink) }
    }

    // InvalidType showing up in an error message is low quality.  Focus error tracking elsewhere.
    private fun failsValidSubtypeCheck(s: StaticType, t: StaticType): Boolean =
        s != InvalidType && t != InvalidType && !typeContext.isSubType(s, t)

    // InvalidType showing up in an error message is low quality.  Focus error tracking elsewhere.
    private fun failsValidSubtypeCheck(s: Type2, t: Type2): Boolean =
        s != invalidType2 && t != invalidType2 && !typeContext2.isSubType(s, t)

    private fun reportBadVoid(pos: Position) {
        logSink.log(level = Log.Error, template = MessageTemplate.ExpectedNonVoid, pos = pos, values = listOf())
    }
}

private class TypedActual(
    val symbol: Symbol?,
    val symbolPos: Position?,
    val type: StaticType,
    val pos: Position,
)

private fun extractTypedActuals(t: CallTree): List<TypedActual>? {
    val actuals = mutableListOf<TypedActual>()
    t.forEachActual { _, symbolChild, child ->
        val type = child.typeInferences?.type ?: return@extractTypedActuals null
        val symbol = symbolChild?.symbolContained
        actuals.add(TypedActual(symbol = symbol, symbolPos = symbolChild?.pos, type = type, pos = child.pos))
    }
    return actuals
}
