package lang.temper.be.tmpl

import lang.temper.common.Either
import lang.temper.log.Position
import lang.temper.type2.Signature2

/**
 * Provides *rewrite* methods that apply themselves recursively to a [TmpL] tree.
 * * The default implementations simply call the appropriate *rewrite* method for each child and
 * then construct a copy of the tree with those children.
 * * Until you `override` a method, this does a deep copy of any tree that you pass it.
 * Override methods to intercept and do deep-copying with adjustments.
 * * For example, overriding [TmpLTreeRewriter.rewriteId] will allow substituting one name for
 * another.
 * * Overrides should not return the input since it cannot be incorporated into a new tree as its
 * parent link may already be sent.  Return a [deep copy][TmpL.Tree.deepCopy] instead.
 */
internal interface TmpLTreeRewriter {
    fun rewriteId(x: TmpL.Id): TmpL.Id = TmpL.Id(
        x.pos,
        name = x.name,
        outName = x.outName,
    )

    /**
     * Called for [TmpL.Id]s that are used as labels, as in labeled statements, `break`
     * and `continue` statements.
     * * A `null` input means the default label as in [TmpL.BreakStatement].
     * * The default implementation returns `null` when given `null`, but delegates to [rewriteId]
     * otherwise.
     */
    fun rewriteLabel(x: TmpL.JumpLabel?) = x?.let {
        TmpL.JumpLabel(rewriteId(it.id))
    }

    fun rewriteExpression(x: TmpL.Expression): TmpL.Expression = when (x) {
        is TmpL.GarbageExpression -> rewriteGarbageExpression(x)
        is TmpL.ValueReference -> rewriteValueReference(x)
        is TmpL.AwaitExpression -> rewriteAwaitExpression(x)
        is TmpL.BubbleSentinel -> rewriteBubbleSentinel(x)
        is TmpL.Reference -> rewriteReference(x)
        is TmpL.This -> rewriteThis(x)
        is TmpL.CallExpression -> rewriteCallExpression(x)
        is TmpL.InfixOperation -> rewriteInfixOperation(x)
        is TmpL.PrefixOperation -> rewritePrefixOperation(x)
        is TmpL.InstanceOfExpression -> rewriteInstanceOfExpression(x)
        is TmpL.CastExpression -> rewriteCastExpression(x)
        is TmpL.UncheckedNotNullExpression -> rewriteUncheckedNotNullExpression(x)
        is TmpL.RestParameterExpression -> rewriteRestParameterExpression(x)
        is TmpL.RestParameterCountExpression -> rewriteRestParameterCountExpression(x)
        is TmpL.GetProperty -> rewriteGetProperty(x)
        is TmpL.FunInterfaceExpression -> rewriteFunInterfaceExpression(x)
    }

    fun rewriteGarbageExpression(x: TmpL.GarbageExpression): TmpL.Expression {
        return TmpL.GarbageExpression(pos = x.pos, diagnostic = x.diagnostic?.deepCopy())
    }

    fun rewriteGarbageCallable(x: TmpL.GarbageCallable): TmpL.Callable {
        return TmpL.GarbageCallable(pos = x.pos, diagnostic = x.diagnostic?.deepCopy())
    }

    fun rewriteSupportCodeWrapper(x: TmpL.SupportCodeWrapper): TmpL.SupportCodeWrapper =
        when (x) {
            is TmpL.InlineSupportCodeWrapper -> rewriteInlineSupportCodeWrapper(x)
            is TmpL.SimpleSupportCodeWrapper -> rewriteSimpleSupportCodeWrapper(x)
        }

    fun rewriteInlineSupportCodeWrapper(x: TmpL.InlineSupportCodeWrapper) =
        rewriteSupportCode(x.supportCode).wrapSupportCode(pos = x.pos, type = x.type)

    fun rewriteSimpleSupportCodeWrapper(x: TmpL.SimpleSupportCodeWrapper) =
        rewriteSupportCode(x.supportCode).wrapSupportCode(pos = x.pos, type = x.type)

    fun rewriteSupportCode(x: SupportCode) = x

    fun rewriteHandlerScope(x: TmpL.HandlerScope): TmpL.HandlerScope = TmpL.HandlerScope(
        pos = x.pos,
        failed = rewriteId(x.failed),
        handled = rewriteHandled(x.handled),
    )

    fun rewriteBoilerplateCodeFoldBoundary(
        x: TmpL.BoilerplateCodeFoldBoundary,
    ): TmpL.BoilerplateCodeFoldBoundary =
        when (x) {
            is TmpL.BoilerplateCodeFoldStart -> rewriteBoilerplateCodeFoldStart(x)
            is TmpL.BoilerplateCodeFoldEnd -> rewriteBoilerplateCodeFoldEnd(x)
        }

    fun rewriteBoilerplateCodeFoldStart(x: TmpL.BoilerplateCodeFoldStart) =
        TmpL.BoilerplateCodeFoldStart(x.pos)

    fun rewriteBoilerplateCodeFoldEnd(x: TmpL.BoilerplateCodeFoldEnd) =
        TmpL.BoilerplateCodeFoldEnd(x.pos)

    fun rewriteEmbeddedComment(x: TmpL.EmbeddedComment): TmpL.EmbeddedComment =
        TmpL.EmbeddedComment(pos = x.pos, commentText = x.commentText)

    fun rewriteHandled(x: TmpL.Handled): TmpL.Handled = when (x) {
        is TmpL.Expression -> rewriteExpression(x)
        is TmpL.SetAbstractProperty ->
            when (val rewrite = rewriteSetAbstractProperty(x)) {
                is TmpL.SetAbstractProperty -> rewrite
                is TmpL.ExpressionStatement -> rewrite.expression
                else -> error("$x")
            }
    }

    fun rewriteValueReference(x: TmpL.ValueReference): TmpL.Expression {
        return TmpL.ValueReference(pos = x.pos, type = x.type, value = x.value)
    }

    fun rewriteAwaitExpression(x: TmpL.AwaitExpression): TmpL.Expression {
        return TmpL.AwaitExpression(
            pos = x.pos,
            promise = rewriteExpression(x.promise),
            type = x.type,
        )
    }

    fun rewriteBubbleSentinel(x: TmpL.BubbleSentinel): TmpL.Expression {
        return TmpL.BubbleSentinel(pos = x.pos)
    }

    fun rewriteReference(x: TmpL.Reference): TmpL.Expression {
        return TmpL.Reference(pos = x.pos, type = x.type, id = rewriteId(x.id))
    }

    fun rewriteFnReference(x: TmpL.FnReference): TmpL.Callable {
        return TmpL.FnReference(pos = x.pos, type = x.type, id = rewriteId(x.id))
    }

    fun rewriteThis(x: TmpL.This): TmpL.Expression {
        return TmpL.This(pos = x.pos, id = rewriteId(x.id), type = x.type)
    }

    fun rewriteCallExpression(x: TmpL.CallExpression): TmpL.Expression {
        return TmpL.CallExpression(
            pos = x.pos,
            fn = rewriteCallable(x.fn),
            typeActuals = rewriteCallTypeActuals(x.typeActuals),
            parameters = x.parameters.map { rewriteActual(it) },
            type = x.type,
        )
    }

    fun rewriteCallTypeActuals(x: TmpL.CallTypeActuals): TmpL.CallTypeActuals =
        TmpL.CallTypeActuals(
            x.pos,
            x.types.map { rewriteAType(it) },
            x.bindings,
        )

    fun rewriteInfixOperation(x: TmpL.InfixOperation): TmpL.Expression {
        return TmpL.InfixOperation(
            pos = x.pos,
            left = rewriteExpression(x.left),
            op = x.op.deepCopy(),
            right = rewriteExpression(x.right),
        )
    }

    fun rewritePrefixOperation(x: TmpL.PrefixOperation): TmpL.Expression {
        return TmpL.PrefixOperation(
            pos = x.pos,
            op = x.op.deepCopy(),
            operand = rewriteExpression(x.operand),
        )
    }

    fun rewriteInstanceOfExpression(x: TmpL.InstanceOfExpression): TmpL.Expression {
        return TmpL.InstanceOfExpression(
            pos = x.pos,
            expr = rewriteExpression(x.expr),
            checkedType = rewriteAType(x.checkedType),
            checkedFrontendType = x.checkedFrontendType,
        )
    }

    fun rewriteCastExpression(x: TmpL.CastExpression): TmpL.Expression {
        return TmpL.CastExpression(
            pos = x.pos,
            expr = rewriteExpression(x.expr),
            checkedType = rewriteAType(x.checkedType),
            type = x.type,
            checkedFrontendType = x.checkedFrontendType,
        )
    }

    fun rewriteUncheckedNotNullExpression(x: TmpL.UncheckedNotNullExpression): TmpL.Expression {
        return TmpL.UncheckedNotNullExpression(
            pos = x.pos,
            expression = rewriteExpression(x.expression),
            type = x.type,
        )
    }

    fun rewriteRestParameterExpression(x: TmpL.RestParameterExpression): TmpL.Expression {
        return TmpL.RestParameterExpression(
            pos = x.pos,
            parameterName = rewriteId(x.parameterName),
            index = x.index.deepCopy(),
            type = x.type,
        )
    }

    fun rewriteRestParameterCountExpression(x: TmpL.RestParameterCountExpression): TmpL.Expression {
        return TmpL.RestParameterCountExpression(
            pos = x.pos,
            parameterName = rewriteId(x.parameterName),
        )
    }

    fun rewriteConstructorReference(x: TmpL.ConstructorReference): TmpL.Callable =
        TmpL.ConstructorReference(
            pos = x.pos,
            typeName = rewriteTypeName(x.typeName),
            type = x.type,
        )

    fun rewriteGetProperty(x: TmpL.GetProperty): TmpL.Expression = when (x) {
        is TmpL.GetAbstractProperty -> rewriteGetAbstractProperty(x)
        is TmpL.GetBackedProperty -> rewriteGetBackedProperty(x)
    }

    fun rewriteSubject(x: TmpL.Subject): TmpL.Subject = when (x) {
        is TmpL.Expression -> rewriteExpression(x)
        is TmpL.TypeName -> rewriteTypeName(x)
    }

    fun rewriteGetAbstractProperty(x: TmpL.GetAbstractProperty): TmpL.Expression {
        return TmpL.GetAbstractProperty(
            pos = x.pos,
            subject = rewriteExpression(x.subject),
            property = x.property.deepCopy(),
            type = x.type,
        )
    }

    fun rewriteGetBackedProperty(x: TmpL.GetBackedProperty): TmpL.Expression {
        return TmpL.GetBackedProperty(
            pos = x.pos,
            subject = rewriteSubject(x.subject),
            property = x.property.deepCopy(),
            type = x.type,
        )
    }

    fun rewriteFunInterfaceExpression(x: TmpL.FunInterfaceExpression): TmpL.Expression {
        return TmpL.FunInterfaceExpression(x.pos, rewriteCallable(x.callable), x.type)
    }

    fun rewriteFunInterfaceCallable(x: TmpL.FunInterfaceCallable): TmpL.Callable {
        return TmpL.FunInterfaceCallable(x.pos, rewriteExpression(x.expr), x.type)
    }

    fun rewriteStatement(x: TmpL.Statement): TmpL.Statement = when (x) {
        is TmpL.GarbageStatement -> rewriteGarbageStatement(x)
        is TmpL.LocalDeclaration -> rewriteLocalDeclaration(x)
        is TmpL.BlockStatement -> rewriteBlockStatement(x)
        is TmpL.LocalFunctionDeclaration -> rewriteLocalFunctionDeclaration(x)
        is TmpL.ExpressionStatement -> rewriteExpressionStatement(x)
        is TmpL.Assignment -> rewriteAssignment(x)
        is TmpL.BreakStatement -> rewriteBreakStatement(x)
        is TmpL.ComputedJumpStatement -> rewriteComputedJumpStatement(x)
        is TmpL.ContinueStatement -> rewriteContinueStatement(x)
        is TmpL.IfStatement -> rewriteIfStatement(x)
        is TmpL.LabeledStatement -> rewriteLabeledStatement(x)
        is TmpL.ModuleInitFailed -> rewriteModuleInitFailedStatement(x)
        is TmpL.YieldStatement -> rewriteYieldStatement(x)
        is TmpL.ReturnStatement -> rewriteReturnStatement(x)
        is TmpL.SetProperty -> rewriteSetProperty(x)
        is TmpL.ThrowStatement -> rewriteThrowStatement(x)
        is TmpL.TryStatement -> rewriteTryStatement(x)
        is TmpL.WhileStatement -> rewriteWhileStatement(x)
        is TmpL.HandlerScope -> rewriteHandlerScope(x)
        is TmpL.BoilerplateCodeFoldBoundary -> rewriteBoilerplateCodeFoldBoundary(x)
        is TmpL.EmbeddedComment -> rewriteEmbeddedComment(x)
    }

    fun rewriteStatementOrTopLevel(x: TmpL.StatementOrTopLevel): TmpL.StatementOrTopLevel = when (x) {
        is TmpL.Statement -> rewriteStatement(x)
        is TmpL.TopLevel -> rewriteTopLevel(x)
    }

    fun rewriteGarbageStatement(x: TmpL.GarbageStatement): TmpL.Statement {
        return TmpL.GarbageStatement(pos = x.pos, diagnostic = x.diagnostic?.deepCopy())
    }

    fun rewriteLocalDeclaration(x: TmpL.LocalDeclaration): TmpL.Statement {
        return TmpL.LocalDeclaration(
            pos = x.pos,
            metadata = x.metadata.mapNotNull { rewriteDeclarationMetadata(it) },
            name = rewriteId(x.name),
            init = x.init?.let { rewriteExpression(it) },
            assignOnce = x.assignOnce,
            type = x.type,
            descriptor = x.descriptor,
        )
    }

    fun rewriteDeclarationMetadata(x: TmpL.DeclarationMetadata): TmpL.DeclarationMetadata? =
        TmpL.DeclarationMetadata(
            sourceLibrary = x.sourceLibrary,
            key = rewriteMetadataKey(x.key),
            value = rewriteMetadataValue(x.value),
        )

    fun rewriteMetadataValue(x: TmpL.MetadataValue): TmpL.MetadataValue = when (x) {
        is TmpL.NameData -> rewriteNameData(x)
        is TmpL.ValueData -> rewriteValueData(x)
    }

    fun rewriteNameData(x: TmpL.NameData): TmpL.MetadataValue = x
    fun rewriteValueData(x: TmpL.ValueData): TmpL.MetadataValue = x

    fun rewriteDeclarationMetadataList(
        xs: List<TmpL.DeclarationMetadata>,
    ): List<TmpL.DeclarationMetadata> = xs.mapNotNull { rewriteDeclarationMetadata(it) }

    fun rewriteMetadataKey(x: TmpL.MetadataKey): TmpL.MetadataKey =
        TmpL.MetadataKey(x.sourceLibrary, x.symbol)

    fun rewriteBlockStatement(x: TmpL.BlockStatement): TmpL.Statement {
        return TmpL.BlockStatement(
            pos = x.pos,
            statements = x.statements.map {
                rewriteStatement(it)
            },
        )
    }

    fun rewriteLocalFunctionDeclaration(x: TmpL.LocalFunctionDeclaration): TmpL.Statement {
        return TmpL.LocalFunctionDeclaration(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            returnType = rewriteAType(x.returnType),
            body = rewriteBody(x.body),
            mayYield = x.mayYield,
            sig = x.sig,
        )
    }

    fun rewriteExpressionStatement(x: TmpL.ExpressionStatement): TmpL.Statement {
        return TmpL.ExpressionStatement(pos = x.pos, expression = rewriteExpression(x.expression))
    }

    fun rewriteAssignment(x: TmpL.Assignment): TmpL.Statement {
        return TmpL.Assignment(
            pos = x.pos,
            left = rewriteId(x.left),
            right = rewriteRightHandSide(x.right),
            type = x.type,
        )
    }

    fun rewriteBreakStatement(x: TmpL.BreakStatement): TmpL.Statement {
        return TmpL.BreakStatement(pos = x.pos, label = rewriteLabel(x.label))
    }

    fun rewriteComputedJumpStatement(x: TmpL.ComputedJumpStatement): TmpL.Statement {
        return TmpL.ComputedJumpStatement(
            pos = x.pos,
            caseExpr = rewriteExpression(x.caseExpr),
            cases = x.cases.map { rewriteComputedJumpCase(it) },
            elseCase = rewriteComputedJumpElse(x.elseCase),
        )
    }

    fun rewriteComputedJumpCase(x: TmpL.ComputedJumpCase): TmpL.ComputedJumpCase =
        TmpL.ComputedJumpCase(pos = x.pos, values = rewriteCaseValues(x.values), body = rewriteBody(x.body))

    fun rewriteCaseValues(x: List<TmpL.ConstIndex>): List<TmpL.ConstIndex> =
        x.map { it.deepCopy() }

    fun rewriteComputedJumpElse(x: TmpL.ComputedJumpElse): TmpL.ComputedJumpElse =
        TmpL.ComputedJumpElse(pos = x.pos, body = rewriteBody(x.body))

    fun rewriteContinueStatement(x: TmpL.ContinueStatement): TmpL.Statement {
        return TmpL.ContinueStatement(pos = x.pos, label = rewriteLabel(x.label))
    }

    fun rewriteIfStatement(x: TmpL.IfStatement): TmpL.Statement = TmpL.IfStatement(
        pos = x.pos,
        test = rewriteExpression(x.test),
        consequent = rewriteStatement(x.consequent),
        alternate = run {
            val alternate = x.alternate
            val rewritten =
                rewriteStatement(alternate ?: TmpL.BlockStatement(x.pos.rightEdge, emptyList()))
            if (
                alternate == null && rewritten is TmpL.BlockStatement &&
                rewritten.statements.isEmpty()
            ) {
                null
            } else {
                rewritten
            }
        },
    )

    fun rewriteLabeledStatement(x: TmpL.LabeledStatement): TmpL.Statement {
        val label = rewriteLabel(x.label)
        val statement = rewriteStatement(x.statement)
        return if (label != null) {
            TmpL.LabeledStatement(pos = x.pos, label = label, statement = statement)
        } else {
            statement
        }
    }

    fun rewriteModuleInitFailedStatement(x: TmpL.ModuleInitFailed): TmpL.Statement {
        return TmpL.ModuleInitFailed(pos = x.pos)
    }

    fun rewriteYieldStatement(x: TmpL.YieldStatement): TmpL.Statement {
        return TmpL.YieldStatement(pos = x.pos)
    }

    fun rewriteReturnStatement(x: TmpL.ReturnStatement): TmpL.Statement {
        return TmpL.ReturnStatement(pos = x.pos, x.expression?.let { rewriteExpression(it) })
    }

    fun rewriteSetProperty(x: TmpL.SetProperty): TmpL.Statement = when (x) {
        is TmpL.SetAbstractProperty -> rewriteSetAbstractProperty(x)
        is TmpL.SetBackedProperty -> rewriteSetBackedProperty(x)
    }

    fun rewriteSetAbstractProperty(x: TmpL.SetAbstractProperty): TmpL.Statement {
        return TmpL.SetAbstractProperty(
            pos = x.pos,
            left = x.left.deepCopy(),
            right = rewriteExpression(x.right),
        )
    }

    fun rewriteSetBackedProperty(x: TmpL.SetBackedProperty): TmpL.Statement {
        return TmpL.SetBackedProperty(
            pos = x.pos,
            left = x.left.deepCopy(),
            right = rewriteExpression(x.right),
        )
    }

    fun rewriteThrowStatement(x: TmpL.ThrowStatement): TmpL.Statement {
        return TmpL.ThrowStatement(pos = x.pos)
    }

    fun rewriteTryStatement(x: TmpL.TryStatement): TmpL.Statement {
        return TmpL.TryStatement(
            pos = x.pos,
            tried = rewriteStatement(x.tried),
            recover = rewriteStatement(x.recover),
        )
    }

    fun rewriteWhileStatement(x: TmpL.WhileStatement): TmpL.Statement {
        return TmpL.WhileStatement(
            pos = x.pos,
            test = rewriteExpression(x.test),
            body = rewriteStatement(x.body),
        )
    }

    fun rewriteCallable(x: TmpL.Callable): TmpL.Callable = when (x) {
        is TmpL.InlineSupportCodeWrapper -> x.deepCopy()
        is TmpL.ConstructorReference -> rewriteConstructorReference(x)
        is TmpL.MethodReference -> x.deepCopy()
        is TmpL.FnReference -> rewriteFnReference(x)
        is TmpL.FunInterfaceCallable -> rewriteFunInterfaceCallable(x)
        is TmpL.GarbageCallable -> rewriteGarbageCallable(x)
    }

    fun rewriteActual(x: TmpL.Actual): TmpL.Actual = when (x) {
        is TmpL.Expression -> rewriteExpression(x)
        is TmpL.RestSpread -> rewriteRestSpread(x)
    }

    fun rewriteRestSpread(x: TmpL.RestSpread): TmpL.Actual =
        TmpL.RestSpread(pos = x.pos, parameterName = rewriteId(x.parameterName))

    fun rewriteRightHandSide(x: TmpL.RightHandSide): TmpL.RightHandSide = when (x) {
        is TmpL.Expression -> rewriteExpression(x)
        is TmpL.HandlerScope -> rewriteHandlerScope(x)
    }

    fun rewriteTopLevel(x: TmpL.TopLevel): TmpL.TopLevel = when (x) {
        is TmpL.BoilerplateCodeFoldBoundary -> rewriteBoilerplateCodeFoldBoundary(x)
        is TmpL.EmbeddedComment -> rewriteEmbeddedComment(x)
        is TmpL.GarbageTopLevel -> rewriteGarbageTopLevel(x)
        is TmpL.ModuleFunctionDeclaration -> rewriteModuleFunctionDeclaration(x)
        is TmpL.ModuleInitBlock -> rewriteModuleInitBlock(x)
        is TmpL.ModuleLevelDeclaration -> rewriteModuleLevelDeclaration(x)
        is TmpL.PooledValueDeclaration -> rewritePooledValueDeclaration(x)
        is TmpL.SupportCodeDeclaration -> rewriteSupportCodeDeclaration(x)
        is TmpL.Test -> rewriteTest(x)
        is TmpL.TypeDeclaration -> rewriteTypeDeclaration(x)
        is TmpL.TypeConnection -> rewriteTypeConnection(x)
    }

    fun rewriteGarbageTopLevel(x: TmpL.GarbageTopLevel): TmpL.TopLevel =
        TmpL.GarbageTopLevel(x.pos, x.diagnostic?.deepCopy())

    fun rewriteModuleFunctionDeclaration(x: TmpL.ModuleFunctionDeclaration): TmpL.TopLevel =
        TmpL.ModuleFunctionDeclaration(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            returnType = rewriteAType(x.returnType),
            body = rewriteBody(x.body),
            mayYield = x.mayYield,
            sig = x.sig,
        )

    fun rewriteATypeParameters(x: TmpL.ATypeParameters): TmpL.ATypeParameters = when (val t = x.t) {
        is Either.Left -> TmpL.ATypeParameters(x.pos, rewriteTypeParameters(t.item), null)
        is Either.Right -> TmpL.ATypeParameters(x.pos, null, rewriteFormalTypeDefs(t.item))
    }

    fun rewriteFormalTypeDefs(x: TmpL.FormalTypeDefs): TmpL.FormalTypeDefs = TODO("$x")

    fun rewriteTypeParameters(x: TmpL.TypeParameters): TmpL.TypeParameters =
        TmpL.TypeParameters(x.pos, x.typeParameters.map { rewriteTypeFormal(it) })

    fun rewriteTypeFormal(x: TmpL.TypeFormal): TmpL.TypeFormal = TmpL.TypeFormal(
        pos = x.pos,
        name = rewriteId(x.name),
        upperBounds = x.upperBounds.map { rewriteNominalType(it) as TmpL.NominalType },
        definition = x.definition,
    )

    fun rewriteValueFormals(x: TmpL.ValueFormalList): TmpL.ValueFormalList =
        TmpL.ValueFormalList(
            pos = x.pos,
            formals = x.formals.map { rewriteValueFormal(it) },
            rest = x.rest?.let { rewriteRestFormal(it) },
        )

    fun rewriteValueFormal(x: TmpL.ValueFormal): TmpL.ValueFormal =
        TmpL.ValueFormal(
            pos = x.pos,
            name = x.name?.deepCopy(),
            type = rewriteAType(x.type),
            isOptional = x.isOptional,
        )

    fun rewriteRestFormal(x: TmpL.AType): TmpL.AType = rewriteAType(x)

    fun rewriteParameters(x: TmpL.Parameters): TmpL.Parameters = TmpL.Parameters(
        pos = x.pos,
        thisName = x.thisName?.let { rewriteId(it) },
        parameters = x.parameters.map { rewriteFormal(it) },
        restParameter = x.restParameter?.let { rewriteRestFormal(it) },
    )

    fun rewriteFormal(x: TmpL.Formal): TmpL.Formal = TmpL.Formal(
        pos = x.pos,
        metadata = rewriteDeclarationMetadataList(x.metadata),
        name = rewriteId(x.name),
        type = rewriteAType(x.type),
        descriptor = x.descriptor,
    )

    fun rewriteRestFormal(x: TmpL.RestFormal): TmpL.RestFormal = TmpL.RestFormal(
        pos = x.pos,
        metadata = rewriteDeclarationMetadataList(x.metadata),
        name = rewriteId(x.name),
        type = rewriteAType(x.type),
        descriptor = x.descriptor,
    )

    fun rewriteAType(x: TmpL.AType): TmpL.AType = when (val t = x.t) {
        is Either.Left -> TmpL.AType(x.pos, rewriteType(t.item), null)
        is Either.Right -> TmpL.AType(x.pos, null, rewriteNewType(t.item))
    }

    fun rewriteNewType(x: TmpL.NewType): TmpL.NewType = TODO("$x")

    fun rewriteType(x: TmpL.Type): TmpL.Type = when (x) {
        is TmpL.GarbageType -> rewriteGarbageType(x)
        is TmpL.NeverType -> rewriteNeverType(x)
        is TmpL.BubbleType -> rewriteBubbleType(x)
        is TmpL.NominalType -> rewriteNominalType(x)
        is TmpL.FunctionType -> rewriteFunctionType(x)
        is TmpL.TopType -> rewriteTopType(x)
        is TmpL.TypeIntersection -> rewriteTypeIntersection(x)
        is TmpL.TypeUnion -> rewriteTypeUnion(x)
    }

    fun rewriteGarbageType(x: TmpL.GarbageType): TmpL.Type =
        TmpL.GarbageType(x.pos, x.diagnostic?.deepCopy())

    fun rewriteNeverType(x: TmpL.NeverType): TmpL.Type = TmpL.NeverType(x.pos)
    fun rewriteBubbleType(x: TmpL.BubbleType): TmpL.Type = TmpL.BubbleType(x.pos)
    fun rewriteTopType(x: TmpL.TopType): TmpL.Type = TmpL.TopType(x.pos)

    fun rewriteTypeIntersection(x: TmpL.TypeIntersection): TmpL.Type = TmpL.TypeIntersection(
        pos = x.pos,
        types = x.types.mapTo(mutableSetOf()) { rewriteType(it) }.toList(),
    )

    fun rewriteTypeUnion(x: TmpL.TypeUnion): TmpL.Type = TmpL.TypeUnion(
        pos = x.pos,
        types = x.types.mapTo(mutableSetOf()) { rewriteType(it) }.toList(),
    )

    fun rewriteNominalType(x: TmpL.NominalType): TmpL.Type = TmpL.NominalType(
        pos = x.pos,
        typeName = rewriteTypeName(x.typeName),
        params = x.params.map { rewriteAType(it) },
        connectsFrom = x.connectsFrom,
    )

    fun rewriteConnectedKey(x: TmpL.ConnectedKey): TmpL.ConnectedKey = TmpL.ConnectedKey(
        pos = x.pos,
        key = x.key,
    )

    fun rewriteFunctionType(x: TmpL.FunctionType): TmpL.Type = TmpL.FunctionType(
        pos = x.pos,
        typeParameters = rewriteATypeParameters(x.typeParameters),
        valueFormals = rewriteValueFormals(x.valueFormals),
        returnType = rewriteAType(x.returnType),
    )

    fun rewriteTypeName(x: TmpL.TypeName): TmpL.TypeName = when (x) {
        is TmpL.TemperTypeName -> rewriteTemperTypeName(x)
        is TmpL.ConnectedToTypeName -> rewriteConnectedToTypeName(x)
    }

    fun rewriteTemperTypeName(x: TmpL.TemperTypeName): TmpL.TypeName =
        TmpL.TemperTypeName(
            pos = x.pos,
            typeDefinition = x.typeDefinition,
        )

    fun rewriteConnectedToTypeName(x: TmpL.ConnectedToTypeName): TmpL.TypeName =
        TmpL.ConnectedToTypeName(
            pos = x.pos,
            name = x.name,
            sourceDefinition = x.sourceDefinition,
        )

    fun rewriteModuleInitBlock(x: TmpL.ModuleInitBlock): TmpL.TopLevel =
        TmpL.ModuleInitBlock(
            pos = x.pos,
            body = rewriteBody(x.body),
        )

    fun rewriteModuleLevelDeclaration(x: TmpL.ModuleLevelDeclaration): TmpL.TopLevel =
        TmpL.ModuleLevelDeclaration(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            init = x.init?.let { rewriteExpression(it) },
            assignOnce = x.assignOnce,
            type = x.type,
            descriptor = x.descriptor,
        )

    fun rewritePooledValueDeclaration(x: TmpL.PooledValueDeclaration): TmpL.TopLevel =
        TmpL.PooledValueDeclaration(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            init = rewriteExpression(x.init),
            descriptor = x.descriptor,
        )

    fun rewriteSupportCodeDeclaration(x: TmpL.SupportCodeDeclaration): TmpL.TopLevel =
        TmpL.SupportCodeDeclaration(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            init = rewriteSupportCodeWrapper(x.init),
            descriptor = x.descriptor,
        )

    fun rewriteTypeDeclaration(x: TmpL.TypeDeclaration): TmpL.TopLevel =
        TmpL.TypeDeclaration(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            superTypes = x.superTypes.map {
                rewriteNominalType(it) as TmpL.NominalType
            },
            members = x.members.map { rewriteMemberOrGarbage(it) },
            inherited = x.inherited.map { rewriteSuperTypeMethod(it) },
            kind = x.kind,
            typeShape = x.typeShape,
        )

    fun rewriteTypeConnection(x: TmpL.TypeConnection): TmpL.TopLevel =
        TmpL.TypeConnection(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            superTypes = x.superTypes.map {
                rewriteNominalType(it) as TmpL.NominalType
            },
            to = rewriteNominalType(x.to) as TmpL.NominalType,
            connectedKey = rewriteConnectedKey(x.connectedKey),
            kind = x.kind,
            typeShape = x.typeShape,
        )

    fun rewriteMemberOrGarbage(x: TmpL.MemberOrGarbage): TmpL.MemberOrGarbage = when (x) {
        is TmpL.GarbageStatement -> rewriteGarbageStatement(x) as TmpL.MemberOrGarbage
        is TmpL.Constructor -> rewriteConstructor(x)
        is TmpL.Getter -> rewriteGetter(x)
        is TmpL.Setter -> rewriteSetter(x)
        is TmpL.NormalMethod -> rewriteNormalMethod(x)
        is TmpL.StaticProperty -> rewriteStaticProperty(x)
        is TmpL.StaticMethod -> rewriteStaticMethod(x)
        is TmpL.Property -> rewriteProperty(x)
    }

    fun rewriteConstructor(x: TmpL.Constructor): TmpL.MemberOrGarbage =
        TmpL.Constructor(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            body = rewriteBody(x.body),
            memberShape = x.memberShape,
            visibility = rewriteVisibilityModifier(x.visibility),
            returnType = rewriteAType(x.returnType),
        )

    fun rewriteGetter(x: TmpL.Getter): TmpL.MemberOrGarbage =
        TmpL.Getter(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            dotName = rewriteDotName(x.dotName),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            returnType = rewriteAType(x.returnType),
            body = x.body?.let { rewriteBody(it) },
            overridden = x.overridden.map { rewriteSuperTypeMethod(it) },
            visibility = rewriteVisibilityModifier(x.visibility),
            memberShape = x.memberShape,
            propertyShape = x.propertyShape,
        )

    fun rewriteSetter(x: TmpL.Setter): TmpL.MemberOrGarbage =
        TmpL.Setter(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            dotName = rewriteDotName(x.dotName),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            returnType = rewriteAType(x.returnType),
            body = x.body?.let { rewriteBody(it) },
            overridden = x.overridden.map { rewriteSuperTypeMethod(it) },
            visibility = rewriteVisibilityModifier(x.visibility),
            memberShape = x.memberShape,
            propertyShape = x.propertyShape,
        )

    fun rewriteNormalMethod(x: TmpL.NormalMethod): TmpL.MemberOrGarbage =
        TmpL.NormalMethod(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            dotName = rewriteDotName(x.dotName),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            returnType = rewriteAType(x.returnType),
            body = x.body?.let { rewriteBody(it) },
            overridden = x.overridden.map { rewriteSuperTypeMethod(it) },
            visibility = rewriteVisibilityModifier(x.visibility),
            memberShape = x.memberShape,
            mayYield = x.mayYield,
        )

    fun rewriteStaticMethod(x: TmpL.StaticMethod): TmpL.MemberOrGarbage =
        TmpL.StaticMethod(
            pos = x.pos,
            metadata = rewriteDeclarationMetadataList(x.metadata),
            dotName = rewriteDotName(x.dotName),
            name = rewriteId(x.name),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteParameters(x.parameters),
            returnType = rewriteAType(x.returnType),
            body = x.body?.let { rewriteBody(it) },
            visibility = rewriteVisibilityModifier(x.visibility),
            memberShape = x.memberShape,
            mayYield = x.mayYield,
        )

    fun rewriteSuperTypeMethod(x: TmpL.SuperTypeMethod): TmpL.SuperTypeMethod =
        TmpL.SuperTypeMethod(
            pos = x.pos,
            superType = rewriteNominalType(x.superType) as TmpL.NominalType,
            name = rewriteDotName(x.name),
            visibility = rewriteVisibilityModifier(x.visibility),
            typeParameters = rewriteATypeParameters(x.typeParameters),
            parameters = rewriteValueFormals(x.parameters),
            returnType = rewriteAType(x.returnType),
            memberOverride = x.memberOverride,
        )

    fun rewriteTest(x: TmpL.Test): TmpL.TopLevel = TmpL.Test(
        pos = x.pos,
        name = rewriteId(x.name),
        rawName = x.rawName,
        parameters = rewriteParameters(x.parameters),
        returnType = rewriteAType(x.returnType),
        body = rewriteBody(x.body),
        metadata = rewriteDeclarationMetadataList(x.metadata),
    )

    fun rewriteProperty(x: TmpL.Property): TmpL.MemberOrGarbage = when (x) {
        is TmpL.InstanceProperty -> rewriteInstanceProperty(x)
        is TmpL.StaticProperty -> rewriteStaticProperty(x)
    }

    fun rewriteInstanceProperty(x: TmpL.InstanceProperty): TmpL.MemberOrGarbage = TmpL.InstanceProperty(
        pos = x.pos,
        metadata = rewriteDeclarationMetadataList(x.metadata),
        dotName = rewriteDotName(x.dotName),
        name = rewriteId(x.name),
        type = rewriteAType(x.type),
        visibility = rewriteVisibilityModifier(x.visibility),
        memberShape = x.memberShape,
        assignOnce = x.assignOnce,
        descriptor = x.descriptor,
    )

    fun rewriteStaticProperty(x: TmpL.StaticProperty): TmpL.MemberOrGarbage = TmpL.StaticProperty(
        pos = x.pos,
        metadata = rewriteDeclarationMetadataList(x.metadata),
        dotName = rewriteDotName(x.dotName),
        name = rewriteId(x.name),
        type = rewriteAType(x.type),
        expression = rewriteExpression(x.expression),
        visibility = rewriteVisibilityModifier(x.visibility),
        memberShape = x.memberShape,
        descriptor = x.descriptor,
    )

    fun rewriteVisibilityModifier(x: TmpL.VisibilityModifier): TmpL.VisibilityModifier =
        TmpL.VisibilityModifier(pos = x.pos, visibility = x.visibility)

    fun rewriteDotName(x: TmpL.DotName): TmpL.DotName = TmpL.DotName(pos = x.pos, dotNameText = x.dotNameText)

    private fun rewriteBody(x: TmpL.BlockStatement): TmpL.BlockStatement {
        val body = rewriteBlockStatement(x)
        return body as? TmpL.BlockStatement
            ?: TmpL.BlockStatement(body.pos, listOf(body))
    }
}

private fun SupportCode.wrapSupportCode(pos: Position, type: Signature2): TmpL.SupportCodeWrapper =
    if (this is InlineSupportCode<*, *>) {
        TmpL.InlineSupportCodeWrapper(pos, type, this)
    } else {
        TmpL.SimpleSupportCodeWrapper(pos, type, this)
    }
