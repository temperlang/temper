package lang.temper.be.names

import lang.temper.be.tmpl.FunctionSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.TmpL
import lang.temper.common.Either
import lang.temper.common.firstOrNullAs
import lang.temper.name.BuiltinName
import lang.temper.name.ResolvedName
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedType

internal fun NameVisitor.doVisit(moduleSet: TmpL.ModuleSet) {
    for (mod in moduleSet.modules) {
        val nm = this.inModule(mod.codeLocation.codeLocation, mod)
        for (imp in mod.imports) {
            nm.import(imp.localName?.name, imp.externalName.name, imp)
        }
        for (tl in mod.topLevels) {
            when (tl) {
                is TmpL.BoilerplateCodeFoldEnd,
                is TmpL.BoilerplateCodeFoldStart,
                is TmpL.EmbeddedComment,
                is TmpL.GarbageTopLevel,
                -> {
                }

                is TmpL.ModuleInitBlock -> nm.visitStmt(tl.body)
                is TmpL.ModuleFunctionDeclaration -> {
                    val n = nm.moduleFunctionDecl(tl.name.name, tl)
                    n.visitTypeParamsDecl(tl.typeParameters)
                    n.visitParams(tl.parameters)
                    n.visitTypeUse(tl.returnType)
                    n.visitStmt(tl.body)
                }

                is TmpL.ModuleLevelDeclaration -> {
                    val n = nm.moduleFieldDecl(tl.name.name, tl)
                    n.visitTypeUse(tl.type)
                    tl.init.letUnit(n::visitExpr)
                }

                is TmpL.PooledValueDeclaration -> {
                    val n = nm.moduleFieldDecl(tl.name.name, tl)
                    n.visitExpr(tl.init)
                }

                is TmpL.SupportCodeDeclaration -> {
                    val n = nm.supportDecl(tl.name.name, tl)
                    when (val init = tl.init) {
                        is TmpL.InlineSupportCodeWrapper -> n.visitCallable(init)
                        is TmpL.SimpleSupportCodeWrapper -> {
                            init.supportCode
                        }
                    }
                }

                is TmpL.Test -> {
                    val n = nm.testDecl(tl.name.name, tl)
                    n.visitParams(tl.parameters)
                    n.visitTypeUse(tl.returnType)
                    n.visitStmt(tl.body)
                }

                is TmpL.TypeConnection -> {
                    val n = nm.typeConnect(tl.name.name, tl)
                    for (t in tl.superTypes) {
                        n.visitTypeUse(t)
                    }
                    for (t in tl.typeParameters.ot.typeParameters) {
                        n.typeFormalDecl(t.name.name, t)
                    }
                }

                is TmpL.TypeDeclaration -> {
                    val n = nm.typeDecl(tl.name.name, tl.typeShape, tl)
                    for (t in tl.superTypes) {
                        n.typeUse(t.typeName.sourceDefinition.name, t)
                    }
                    for (t in tl.typeParameters.ot.typeParameters) {
                        n.typeFormalDecl(t.name.name, t)
                    }
                    for (m in tl.members) {
                        n.visitMember(m)
                    }
                }
            }
        }
        mod.result?.let { nm.visitExpr(it) }
    }
}

private fun NameVisitor.visitParams(px: TmpL.Parameters) {
    for (p in px.parameters) {
        formalVarDecl(p.name.name, p)
        visitTypeUse(p.type)
    }
    px.restParameter?.let { p ->
        formalVarDecl(p.name.name, p)
        visitTypeUse(p.type)
    }
}

private fun subjectTypeName(s: TmpL.Subject): ResolvedName? = when (s) {
    is TmpL.Expression -> (s.type as? DefinedType)?.definition?.name
    is TmpL.TemperTypeName -> s.typeDefinition.name
    is TmpL.ConnectedToTypeName -> s.sourceDefinition.name
}

private fun NameVisitor.visitTypeParamsDecl(px: TmpL.ATypeParameters) = when (val t = px.t) {
    is Either.Left -> visitTypeParamsDecl(t.item)
    is Either.Right -> TODO("$t")
}

private fun NameVisitor.visitTypeParamsDecl(px: TmpL.TypeParameters) {
    for (p in px.typeParameters) {
        typeFormalDecl(p.name.name, p)
    }
}

private fun NameVisitor.visitValueFormalsUse(px: TmpL.ValueFormalList) {
    for (p in px.formals) {
        formalVarDecl(p.name, p)
        visitTypeUse(p.type)
    }
}

private fun NameVisitor.visitTypeUse(s: TmpL.AType) {
    when (val t = s.t) {
        is Either.Left -> visitTypeUse(t.item)
        is Either.Right -> TODO("$t")
    }
}

private fun NameVisitor.visitTypeUse(s: TmpL.Type) {
    typeUse(typeName(s), s)
    when (s) {
        is TmpL.FunctionType -> {
            visitTypeUse(s.returnType)
            visitTypeParamsDecl(s.typeParameters)
            visitValueFormalsUse(s.valueFormals)
        }
        is TmpL.TypeIntersection -> s.types.forEach { visitTypeUse(it) }
        is TmpL.TypeUnion -> s.types.forEach { visitTypeUse(it) }
        is TmpL.NominalType -> typeUse(s.typeName.sourceDefinition.name, s)
        is TmpL.BubbleType, is TmpL.NeverType -> {}
        is TmpL.TopType, is TmpL.GarbageType -> {}
    }
}

private fun typeName(s: TmpL.Type): ResolvedName? =
    when (s) {
        is TmpL.FunctionType -> null
        is TmpL.TypeIntersection -> null
        is TmpL.TypeUnion -> s.types.firstOrNullAs<TmpL.Type, TmpL.NominalType> {
            when (it.typeName.sourceDefinition) {
                WellKnownTypes.nullTypeDefinition -> false
                WellKnownTypes.bubbleTypeDefinition -> false
                else -> true
            }
        }?.typeName?.sourceDefinition?.name
        is TmpL.GarbageType -> null
        is TmpL.NominalType -> s.typeName.sourceDefinition.name
        is TmpL.BubbleType -> null
        is TmpL.NeverType -> null
        is TmpL.TopType -> WellKnownTypes.anyValueTypeDefinition.name
    }

private fun NameVisitor.visitMember(m: TmpL.MemberOrGarbage): Unit = when (m) {
    is TmpL.GarbageStatement -> {}
    is TmpL.Method -> {
        val n = methodDecl(m.name.name, m)
        n.visitTypeParamsDecl(m.typeParameters)
        n.visitParams(m.parameters)
        n.typeUse(typeName(m.returnType.ot), m.returnType.ot)
        m.body.letUnit(n::visitStmt)
    }
    is TmpL.Property -> {
        val n = propertyDecl(m.name.name, m.dotName.dotNameText, m)
        n.visitTypeUse(m.type)
    }
}

private fun NameVisitor.visitStmt(s: TmpL.Statement): Unit = when (s) {
    is TmpL.GarbageStatement,
    is TmpL.YieldStatement,
    is TmpL.ThrowStatement,
    is TmpL.ModuleInitFailed,
    is TmpL.BoilerplateCodeFoldEnd,
    is TmpL.BoilerplateCodeFoldStart,
    is TmpL.EmbeddedComment,
    -> {}
    is TmpL.Assignment -> {
        varUse(s.left.name, s)
        visitRhs(s.right)
    }
    is TmpL.ExpressionStatement -> visitExpr(s.expression)
    is TmpL.HandlerScope -> {
        localVarDeclMisc(s.failed.name, s)
        visitHandled(s.handled)
    }
    is TmpL.LocalDeclaration -> {
        localVarDecl(s.name.name, s)
        typeUse(typeName(s.type.ot), s.type.ot)
        s.init.letUnit(this::visitExpr)
    }
    is TmpL.LocalFunctionDeclaration -> {
        val n = localFunctionDecl(s.name.name, s)
        n.visitTypeParamsDecl(s.typeParameters)
        val px = s.parameters
        for (p in px.parameters) {
            n.formalVarDecl(p.name.name, p)
            n.typeUse(typeName(p.type.ot), p.type.ot)
        }
        px.restParameter?.let { p ->
            n.formalVarDecl(p.name.name, p)
            n.typeUse(typeName(p.type.ot), p.type.ot)
        }
        n.typeUse(typeName(s.returnType.ot), s.returnType.ot)
        n.visitStmt(s.body)
    }
    is TmpL.BlockStatement -> {
        for (ss in s.statements) {
            visitStmt(ss)
        }
    }
    is TmpL.ComputedJumpStatement -> {
        visitExpr(s.caseExpr)
        for (c in s.cases) {
            visitStmt(c.body)
        }
        visitStmt(s.elseCase.body)
    }
    is TmpL.IfStatement -> {
        visitExpr(s.test)
        visitStmt(s.consequent)
        s.alternate.letUnit(this::visitStmt)
    }
    is TmpL.LabeledStatement -> {
        labelDecl(s.label.id.name, s)
        visitStmt(s.statement)
    }
    is TmpL.TryStatement -> {
        visitStmt(s.tried)
        visitStmt(s.recover)
    }
    is TmpL.WhileStatement -> {
        visitExpr(s.test)
        looping(s).visitStmt(s.body)
    }
    is TmpL.ReturnStatement -> s.expression.letUnit(this::visitExpr)
    is TmpL.SetProperty -> {
        val left = s.left
        propertyUse(subjectTypeName(left.subject), left.property, s)
        visitExpr(s.right)
    }
    is TmpL.JumpUpStatement -> s.label?.id?.name.letUnit {
        when (s) {
            is TmpL.BreakStatement -> labelUse(it, s)
            is TmpL.ContinueStatement -> labelUse(it, s)
        }
    }
}

private fun NameVisitor.visitRhs(e: TmpL.RightHandSide): Unit = when (e) {
    is TmpL.Expression -> visitExpr(e)
    is TmpL.HandlerScope -> {
        localVarDeclMisc(e.failed.name, e)
        visitHandled(e.handled)
    }
}

private fun NameVisitor.visitHandled(e: TmpL.Handled): Unit = when (e) {
    is TmpL.Expression -> visitExpr(e)
    is TmpL.SetAbstractProperty -> {
        val left = e.left
        propertyUse(subjectTypeName(left.subject), left.property, e)
        visitExpr(e.right)
    }
}

private fun NameVisitor.visitCallable(e: TmpL.Callable): Unit = when (e) {
    is TmpL.MethodReference -> methodUse(subjectTypeName(e.subject), e.methodName.dotNameText, e)
    is TmpL.FnReference -> varUse(e.id.name, e)
    is TmpL.GarbageCallable -> {}
    is TmpL.InlineSupportCodeWrapper -> supportCodeUse(e)
    is TmpL.ConstructorReference -> constructorReference(e.typeShape.name, e)
    is TmpL.FunInterfaceCallable -> visitExpr(e.expr)
}

private fun NameVisitor.visitActual(e: TmpL.Actual): Unit = when (e) {
    is TmpL.Expression -> visitExpr(e)
    is TmpL.RestSpread -> varUseMisc(e.parameterName.name, e)
}

private fun NameVisitor.visitExpr(e: TmpL.Expression): Unit = when (e) {
    is TmpL.BubbleSentinel,
    is TmpL.ValueReference,
    is TmpL.GarbageExpression,
    -> {}
    is TmpL.AwaitExpression -> {
        visitExpr(e.promise)
    }
    is TmpL.CallExpression -> {
        calling(e).visitCallable(e.fn)
        for (p in e.parameters) {
            visitActual(p)
        }
    }
    is TmpL.CheckedRttiExpression -> {
        typeUse(typeName(e.checkedType.ot), e.checkedType.ot)
        visitExpr(e.expr)
    }
    is TmpL.GetProperty -> propertyUse(subjectTypeName(e.subject), e.property, e)
    is TmpL.InfixOperation -> {
        visitExpr(e.left)
        visitExpr(e.right)
    }
    is TmpL.PrefixOperation -> visitExpr(e.operand)
    is TmpL.AnyReference -> varUse(e.id.name, e)
    is TmpL.RestParameterCountExpression -> varUseMisc(e.parameterName.name, e)
    is TmpL.RestParameterExpression -> varUseMisc(e.parameterName.name, e)
    is TmpL.SupportCodeWrapper -> supportCodeUse(e)
    is TmpL.This -> receiverUse(e.id.name, e)
    is TmpL.UncheckedNotNullExpression -> {
        visitExpr(e.expression)
    }
    is TmpL.FunInterfaceExpression -> visitCallable(e.callable)
}

fun TmpL.SupportCodeWrapper.bestCodeName(): ResolvedName? {
    val codeName = when (val code = this.supportCode) {
        is FunctionSupportCode -> null
        is NamedSupportCode -> code.baseName
        is SeparatelyCompiledSupportCode -> code.stableKey
    } as? ResolvedName
    if (codeName != null) {
        return codeName
    }
    return this.supportCode.builtinOperatorId?.let { BuiltinName(it.name) }
}

/** Enables the `foo?.let { }` pattern, but always returns Unit. */
private inline fun <T, U> T?.letUnit(block: (T) -> U) {
    if (this != null) {
        block(this)
    }
}
