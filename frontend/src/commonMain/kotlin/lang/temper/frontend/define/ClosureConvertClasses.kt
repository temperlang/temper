package lang.temper.frontend.define

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.LeftOrRight
import lang.temper.common.Log
import lang.temper.common.MultilineOutput
import lang.temper.common.OpenOrClosed
import lang.temper.common.TextTable
import lang.temper.common.console
import lang.temper.common.putAllMulti
import lang.temper.common.putMulti
import lang.temper.frontend.disambiguate.isTypeMember
import lang.temper.frontend.disambiguate.reifiedTypeFor
import lang.temper.frontend.maybeAdjustDotHelper
import lang.temper.frontend.prefixBlockWith
import lang.temper.interp.New
import lang.temper.interp.emptyValue
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.Temporary
import lang.temper.type.Abstractness
import lang.temper.type.DotHelper
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.MutableTypeShape
import lang.temper.type.PropertyShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ClosureRecord
import lang.temper.value.DeclTree
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.constructorSymbol
import lang.temper.value.crParsedName
import lang.temper.value.defaultSymbol
import lang.temper.value.freeTree
import lang.temper.value.fromTypeSymbol
import lang.temper.value.functionContained
import lang.temper.value.getterSymbol
import lang.temper.value.impliedThisSymbol
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.methodSymbol
import lang.temper.value.optionalSymbol
import lang.temper.value.parameterNameSymbolsListSymbol
import lang.temper.value.setterSymbol
import lang.temper.value.staySymbol
import lang.temper.value.symbolContained
import lang.temper.value.thisParsedName
import lang.temper.value.toPseudoCode
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typePlaceholderSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.typeSymbol
import lang.temper.value.vFromTypeSymbol
import lang.temper.value.vGetterSymbol
import lang.temper.value.vImpliedThisSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vMethodSymbol
import lang.temper.value.vParameterNameSymbolsListSymbol
import lang.temper.value.vPropertySymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.vSyntheticSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVisibilitySymbol
import lang.temper.value.valueContained
import lang.temper.value.visibilitySymbol
import lang.temper.value.void
import lang.temper.value.wordSymbol

private const val DEBUG = false

@Suppress("unused")
private inline fun debugStr(f: () -> String) {
    if (DEBUG) {
        console.log(f())
    }
}
private inline fun debug(f: () -> Unit) {
    if (DEBUG) {
        f()
    }
}

/**
 * When there is a nested type definition that uses a local variable
 *
 * ```
 * let f(x) {
 *     class C {
 *         let m(this__0: C) {
 *             return x
 *         }
 *         let constructor(this__1: C) {}
 *     }
 *     return new C()
 * }
 * ```
 *
 * we need to pull the type definition to the module level, for compatibility with backends that
 * treat type definitions as top-levels; and we need to make sure that instances constructed via
 * `new C()` capture `x`.
 *
 * This processing happens in a few stages.  First, we need to define [ClosureRecord]s that cover
 * the non-module-level free variables used by type definitions.
 *
 * ```patch
 *  let f(x) {
 * +    let cr#0 = closRec(x, \getter, () => x);
 *      class C {
 *          let m(this__0: C) {
 *              return x
 *          }
 *          let constructor(this__1: C) {}
 *      }
 *      return new C()
 *  }
 * ```
 *
 * Then we inject properties to hold them.
 *
 * ```patch
 *  let f(x) {
 *      let cr#2 = closRec(x, \getter, () => x);
 *      class C {
 * +        let cr__2;
 *          let m(this__0: C) {
 *              return x
 *          }
 * -        let constructor(this__1: C) {}
 * +        let constructor(this__1: C, cr__2) { this__1.cr__2 = cr__2; }
 *      }
 *      return new C()
 *  }
 * ```
 *
 * We rewrite uses of free variables to read from the closure record.
 *
 * ```patch
 *  let f(x) {
 *      let cr#0 = closRec(x, \getter, () => x);
 *      class C {
 *          let cr__2;
 *          let m(this__0: C) {
 * -            return x
 * +            return crGet(this__0.cr__2, 0 /* x */)
 *          }
 *          let constructor(this__1: C, cr__2) { this__1.cr__2 = cr__2; }
 *      }
 *      return new C()
 *  }
 * ```
 *
 * With all the local changes done, we're ready to create a top-level name for the class and pull
 * the members out.
 *
 * ```patch
 * +@fromType(C__3) let C__3::cr__2;
 * +@fromType(C__3) let C__3::m(this__0: C__3) {
 * +    return crGet(this__0.cr__2, 0 /* x */)
 * +}
 * +@fromType(C__3) let C__3::constructor(this__1: C__3, cr__2) { this__1.cr__2 = cr__2; }
 *  let f(x) {
 *      let cr#0 = closRec(x, \getter, () => x);
 * -    class C {
 * -        let cr__2;
 * -        let m(this__0: C) {
 * -            return crGet(this__0.cr__2, 0 /* x */)
 * -        }
 * -        let constructor(this__1: C, cr__2) { this__1.cr__2 = cr__2; }
 * -    }
 * +    let C = C__3;
 *      return new C()
 *  }
 * ```
 *
 * Finally, we wire the closure record through to the constructor.
 *
 * ```patch
 *  @fromType(C__3) let C__3::m(this__0: C__3) {
 *      return crGet(this__0.cr__2, 0 /* x */)
 *  }
 *  @fromType(C__3) let C__3::constructor(this__1: C__3, cr__2) { this__1.cr__2 = cr__2; }
 *  let C__3 = /* reified type */;
 *  let C__3::cr__2;
 *  let f(x) {
 *      let cr#0 = closRec(x, \getter, () => x);
 * -    let C = C__3;
 * +    let C = __glueType__(C__3, cr#0);
 *      return new C()
 *  }
 * ```
 *
 * It's important that we pull all members out to preserve the invariant that modules declares a
 * finite number of classes.  Consider
 *
 *     let f(T) {
 *         class C<U extends T> extends T {
 *             t: T;
 *         }
 *     }
 *
 * We can't have the inheritance graph or properties' types depend on a type provided at runtime as
 * that complicates type checking and cycle checking, and maps poorly to widely used OO languages
 * like C++, C#, Java, Swift.
 */
internal fun closureConvertClasses(root: BlockTree, logSink: LogSink): ConvertedTypeInfo =
    ClosureConvertClasses(root, logSink).convert()

private class ClosureConvertClasses(
    val root: BlockTree,
    val logSink: LogSink,
) {
    /** Information about a method that needs to be hoisted. */
    private class MethodRecord(
        val methodDecl: DeclTree,
    ) {
        val freeVars = mutableSetOf<ResolvedName>()
    }

    /**
     * Relates a closure record for a set of free variables to the corresponding properties in
     * type definitions being hoisted and the constructor parameters that
     */
    private class ClosureRecordInfo(
        /** The variables aliased by the closure record, in order. */
        val vars: List<ResolvedName>,
        /** The declaration for the temporary holding the closure record. */
        val decl: DeclTree,
        /**
         * The temporary holding the closure record in the environment from which the referring
         * types are converted.
         */
        val closRecHoldingTemporary: Temporary,
        /**
         * A common ancestor of all the free variables in the closure record which also determines
         * the scope to contain [decl].
         */
        val containingScope: Tree,
    ) : Comparable<ClosureRecordInfo> {
        val typeWiring = mutableMapOf<TypeShape, ClosureRecordAlias>()

        override fun compareTo(other: ClosureRecordInfo): Int =
            this.closRecHoldingTemporary.uid.compareTo(other.closRecHoldingTemporary.uid)
    }

    /** For a given type definition, the ways a closure record is aliased. */
    private class ClosureRecordAlias(
        val closureRecordInfo: ClosureRecordInfo,
        val typeShape: TypeShape,
        val property: PropertyShape,
    ) {
        var constructorParameterIndex: Int = -1
    }

    private val nameMaker = root.document.nameMaker
    private val typeDefinitionParts = mutableMapOf<TypeShape, FnParts>()
    private val varsRead = mutableSetOf<ResolvedName>()
    private val varsWritten = mutableSetOf<ResolvedName>()
    private val methodsByType = mutableMapOf<TypeShape, MutableList<MethodRecord>>()
    private val closureRecordInfos = mutableListOf<ClosureRecordInfo>()

    fun convert(): ConvertedTypeInfo {
        // First, find the methods we're going to pull.
        findMethodDeclsAndFinalizeSpecialDotOperators(root)
        val allMethodDecls = methodsByType.values.flatten()
        // Fill out the free variable lists.
        val convertedNames = findConverted()
        allMethodDecls.forEach { findFreeVars(it, convertedNames) }
        debug {
            console.group("Methods to convert") {
                console.logMulti(
                    TextTable(
                        listOf(
                            MultilineOutput.of("type"),
                            MultilineOutput.of("method"),
                            MultilineOutput.of("free"),
                        ),
                        methodsByType.flatMap { (type, methods) ->
                            methods.map { method ->
                                listOf(
                                    MultilineOutput.of(type.name.toString()),
                                    MultilineOutput.of(
                                        "${method.methodDecl.parts?.name?.content}",
                                    ),
                                    MultilineOutput.of(method.freeVars.joinToString(",")),
                                )
                            }
                        },
                    ),
                )
            }
        }
        val freeVariables = allMethodDecls.flatMapTo(mutableSetOf()) { it.freeVars }
        // Group free variables based on the common ancestor of their declaration.
        val freeVarToClosureRecordTemporary = mutableMapOf<ResolvedName, Temporary>()
        // Create a definition to hold a closure record for each group.
        for ((commonAncestor, freeVariableGroup) in groupFreeVariablesByRoot(freeVariables)) {
            val closRecTemporary =
                createClosureRecordConstructor(commonAncestor, freeVariableGroup.toList())
            freeVariableGroup.forEach { freeVarToClosureRecordTemporary[it] = closRecTemporary }
        }
        debug {
            console.group("Closure records") {
                console.logMulti(
                    TextTable(
                        listOf(
                            MultilineOutput.of("temporary"),
                            MultilineOutput.of("vars"),
                        ),
                        closureRecordInfos.map { cri ->
                            listOf(
                                MultilineOutput.of(cri.closRecHoldingTemporary.toString()),
                                MultilineOutput.of(cri.vars.joinToString(", ")),
                            )
                        },
                    ),
                )
            }
        }
        // Add properties to each class/interface to hold the closure records needed.
        // These are abstract properties for interfaces and backed properties for classes.
        for (typeShape in methodsByType.keys) {
            definePropertiesForClosureRecords(typeShape)
        }
        debug {
            console.group("Closure record properties") {
                console.logMulti(
                    TextTable(
                        listOf(
                            MultilineOutput.of("closRec"),
                            MultilineOutput.of("type"),
                            MultilineOutput.of("property"),
                        ),
                        closureRecordInfos.flatMap { cri ->
                            cri.typeWiring.map { (ts, cra) ->
                                listOf(
                                    MultilineOutput.of(cri.closRecHoldingTemporary.toString()),
                                    MultilineOutput.of(ts.name.toString()),
                                    MultilineOutput.of(
                                        cra.property.stay?.incoming?.source?.toPseudoCode() ?: "???",
                                    ),
                                )
                            }
                        },
                    ),
                )
            }
        }

        for (typeShape in methodsByType.keys) {
            val mutTypeShape = asSameModuleOrNull(typeShape)
            if (mutTypeShape?.abstractness == Abstractness.Concrete) {
                // Only concrete types have constructors
                addConstructorParameters(mutTypeShape)
            }
        }
        for (typeShape in typeDefinitionParts.keys) {
            checkForMissingVisibility(typeShape)
        }

        for ((typeShape, fnParts) in typeDefinitionParts) {
            rewriteFreeVariableUses(asSameModuleOrNull(typeShape) ?: continue, fnParts)
        }
        storeParameterMetadataWithFactories()
        val convertedTypeInfo = extractMembersToRoot()
        curryClassConstructors()
        declareClosureRecordTemporaries()
        return convertedTypeInfo
    }

    /**
     * Used to walk the tree from the root finding declarations with [methodSymbol] metadata.
     *
     * Opportunistically, now that we know the locally defined types,e and they have member info,
     * we can finalize any references to backed/static properties to use the special
     * getp/setp/getStatic operators.
     */
    private fun findMethodDeclsAndFinalizeSpecialDotOperators(t: Tree, type: TypeShape? = null) {
        var typeForChildren = type
        if (t is FunTree) {
            when (val parts = t.parts) {
                null -> typeForChildren = null
                else -> {
                    val typeEdge = parts.metadataSymbolMap[typeDefinedSymbol]
                    if (typeEdge != null) {
                        typeForChildren = typeEdge.target.typeShapeAtLeafOrNull
                        if (typeForChildren != null) {
                            typeDefinitionParts[typeForChildren] = parts
                            // Make sure we have a type entry even if the type defines no methods,
                            // so that when it comes time to extract types to the root, we have a
                            // complete list.
                            methodsByType.getOrPut(typeForChildren) { mutableListOf() }
                        }
                    }
                }
            }
        } else if (t is DeclTree && t.parts?.metadataSymbolMap?.contains(methodSymbol) == true) {
            if (type != null) {
                val record = MethodRecord(t)
                methodsByType.getOrPut(type) { mutableListOf() }.add(record)
            }
        }
        for (child in t.children) {
            findMethodDeclsAndFinalizeSpecialDotOperators(child, typeForChildren)
        }

        if (t is CallTree) {
            val calleeTree = t.childOrNull(0)
            val callee = calleeTree?.functionContained
            if (callee is DotHelper) {
                maybeAdjustDotHelper(t, callee, preserveExtensions = true)
            }
        }
    }

    /**
     * Enumerate the names that are internally defined in the content being closure converted.
     * These things should not end up in closure records.
     */
    private fun findConverted(): Set<ResolvedName> {
        val converted = mutableSetOf<ResolvedName>()
        typeDefinitionParts.forEach { (ts, parts) ->
            converted.add(ts.name)
            TreeVisit.startingAt(parts.body)
                .forEachContinuing { t ->
                    when (t) {
                        is DeclTree -> t.parts?.let {
                            converted.add(it.name.content as ResolvedName)
                        }
                        is BlockTree ->
                            (t.parts.label?.target as? LeftNameLeaf)?.content?.let {
                                converted.add(it as ResolvedName)
                            }
                        else -> Unit
                    }
                }
                .visitPreOrder()
        }
        return converted.toSet()
    }

    /** Populate the free variable lists of each method declaration record. */
    private fun findFreeVars(methodRecord: MethodRecord, converted: Set<ResolvedName>) {
        // Enumerates declared names and used names and subtracts the former from the latter.
        val used = mutableSetOf<Pair<ResolvedName, LeftOrRight>>()
        TreeVisit.startingAt(methodRecord.methodDecl)
            .forEach { t ->
                when (t) {
                    is LeftNameLeaf -> used.add((t.content as ResolvedName) to LeftOrRight.Left)
                    is RightNameLeaf -> used.add((t.content as ResolvedName) to LeftOrRight.Right)
                    is FunTree -> {
                        if (typeDefinedSymbol in (t.parts?.metadataSymbolMap ?: emptyMap())) {
                            // Don't descend into nested classes
                            return@forEach VisitCue.SkipOne
                        }
                    }
                    else -> Unit
                }
                VisitCue.Continue
            }
            .visitPreOrder()
        for ((name, dir) in used) {
            if (name !in converted) {
                methodRecord.freeVars.add(name)
                when (dir) {
                    LeftOrRight.Left -> varsRead.add(name)
                    LeftOrRight.Right -> varsWritten.add(name)
                }
            }
        }
    }

    private fun groupFreeVariablesByRoot(
        allFreeVars: Set<ResolvedName>,
    ): List<Pair<Tree, Set<ResolvedName>>> {
        // Find relevant declarations.
        val decls = mutableMapOf<ResolvedName, MutableList<DeclTree>>()
        var fnDepth = 0 // Used by findVariables to identify possible non-module-level declarations.
        fun findVariables(t: Tree) {
            if (t is FunTree) { fnDepth += 1 }

            if (fnDepth > 0 && t is DeclTree) { // A non-module-level declaration
                val parts = t.parts
                if (parts != null) {
                    val name = parts.name.content
                    if (name in allFreeVars) {
                        decls.getOrPut(name as ResolvedName) { mutableListOf() }.add(t)
                    }
                }
            }

            t.children.forEach { findVariables(it) }

            if (t is FunTree) { fnDepth -= 1 }
        }
        findVariables(root)
        debug {
            console.group("Free variable declarations") {
                console.logMulti(
                    TextTable(
                        listOf(MultilineOutput.of("var"), MultilineOutput.of("decl")),
                        decls.flatMap { (v, ds) ->
                            ds.map { d ->
                                listOf(
                                    MultilineOutput.of("$v"),
                                    MultilineOutput.of(d.toPseudoCode()),
                                )
                            }
                        },
                    ),
                )
            }
        }

        // Declarations that share the same parent can be grouped together.
        // This works because we have not yet flattened loops into complex flows.
        val freeVariableByDeclarationParent =
            mutableMapOf<Tree, MutableSet<ResolvedName>>()
        for ((name, declList) in decls.entries) {
            if (declList.size != 1) {
                check(declList.isNotEmpty())
                logSink.log(
                    level = Log.Error,
                    template = MessageTemplate.CannotCaptureMultiplyDeclared,
                    pos = declList[0].pos,
                    values = listOf(name, declList[1].pos),
                )
            }
            val (decl) = declList
            val parent = decl.incoming?.source!! // safe bc root is a block
            freeVariableByDeclarationParent.getOrPut(parent) { mutableSetOf() }.add(name)
        }

        return freeVariableByDeclarationParent.entries.map { (key, values) ->
            val scope = when (key) {
                // If a free variable is a parameter declaration, then we need to group it with
                // declarations in the body.
                is FunTree -> key.lastChild
                else -> key
            }
            scope to values.toSet()
        }
    }

    private fun createClosureRecordConstructor(
        scope: Tree,
        vars: List<ResolvedName>,
    ): Temporary {
        val pos = scope.pos.leftEdge
        val temporary = nameMaker.unusedTemporaryName("cr")
        val decl = scope.treeFarm.grow(pos) {
            Decl(temporary) {
                V(vInitSymbol)
                Call {
                    V(BuiltinFuns.vMakeClosRec)
                    vars.forEach { varName ->
                        val hasGetter = varName in varsRead
                        val hasSetter = varName in varsWritten
                        V(wordSymbol)
                        Ln(varName)
                        if (hasGetter) {
                            V(getterSymbol)
                            Fn {
                                Rn(varName)
                            }
                        }
                        if (hasSetter) {
                            V(setterSymbol)
                            Fn {
                                val newValueTemporary = nameMaker.unusedTemporaryName("v")
                                Decl(newValueTemporary)
                                Call(BuiltinFuns.vSetLocalFn) {
                                    Ln(varName)
                                    Rn(newValueTemporary)
                                }
                            }
                        }
                    }
                }
            }
        }
        closureRecordInfos.add(
            ClosureRecordInfo(
                vars = vars,
                decl = decl,
                closRecHoldingTemporary = temporary,
                containingScope = scope,
            ),
        )
        return temporary
    }

    private val propertiesDefined =
        mutableMapOf<TypeShape, Map<ClosureRecordInfo, List<ClosureRecordAlias>>>()

    /**
     * If a type definition uses a free variable, make sure that instance's have access to a closure
     * record that aliases that variable.
     *
     * @return the set of closure records needed with the property symbols by which each is
     *    known in [typeShape] or a super-type.
     */
    private fun definePropertiesForClosureRecords(
        typeShape: TypeShape,
    ): Map<ClosureRecordInfo, List<ClosureRecordAlias>> {
        // A type needs access to closure records that are needed by its super-types.
        // So we recurse to super-types, but first squirrel away a bit to make sure that super-type
        // cycles (which are errors but still possible to express) don't kill compilation.
        // We may also trip this condition when we process a super-type and then the outer loop
        // that calls into this function visits it again, so it's not itself indicative of an error.
        propertiesDefined[typeShape]?.let {
            return@definePropertiesForClosureRecords it
        }
        propertiesDefined[typeShape] = emptyMap()

        val closureRecordInfosNeeded = mutableSetOf<ClosureRecordInfo>()
        methodsByType[typeShape]?.let { methodRecs ->
            val freeVars = methodRecs.flatMap { it.freeVars }.toSet()
            for (cri in this.closureRecordInfos) {
                if (cri.vars.any { it in freeVars }) {
                    closureRecordInfosNeeded.add(cri)
                }
            }
        }

        val closureRecordInfoMap =
            mutableMapOf<ClosureRecordInfo, MutableList<ClosureRecordAlias>>()
        for (superType in typeShape.superTypes) {
            val superTypeShape = superType.definition as? TypeShape ?: continue
            closureRecordInfoMap.putAllMulti(
                definePropertiesForClosureRecords(superTypeShape),
            ) {
                mutableListOf()
            }
        }

        // Maybe the typeShape comes from another module.
        // In that case, it shouldn't have any needs; exported type shapes should be freestanding.
        // But it may be possible for a type shape in the current module to extend an imported
        // type shape which extends a type shape from this module.
        // That's why we recurse above regardless of whether the super-type shape is part of the
        // group being closure converted.
        val typeDefinitionBodyParts = typeDefinitionParts[typeShape]
        val sameModuleTypeShape = asSameModuleOrNull(typeShape)
        if (typeDefinitionBodyParts != null && sameModuleTypeShape != null) {
            // Sorting enforce a predictable order for closure records in constructor arg lists.
            val allClosureRecordInfosNeeded =
                (closureRecordInfosNeeded + closureRecordInfoMap.keys).sorted()

            // Define holding properties for each closure record.
            for (cri in allClosureRecordInfosNeeded) {
                val inheritedAliases = closureRecordInfoMap[cri] ?: emptyList()
                val alias = when (typeShape.abstractness) {
                    Abstractness.Abstract -> {
                        if (inheritedAliases.isNotEmpty()) {
                            // If typeShape inherits an alias, we can just use one.
                            inheritedAliases.first()
                        } else {
                            // typeShape is an abstract type, so define an abstract property.
                            val newAlias = defineProperty(
                                cri,
                                sameModuleTypeShape,
                                typeDefinitionBodyParts,
                                Visibility.Protected,
                                Abstractness.Abstract,
                            )
                            closureRecordInfoMap.putMulti(cri, newAlias) { mutableListOf() }
                            newAlias
                        }
                    }
                    Abstractness.Concrete -> {
                        // If typeShape is a concrete type, we need to define a backing property
                        // and override each super-type's abstract property to return it.
                        val backedAlias = defineProperty(
                            cri,
                            sameModuleTypeShape,
                            typeDefinitionBodyParts,
                            Visibility.Private,
                            Abstractness.Concrete,
                        )
                        for (inheritedAlias in inheritedAliases) {
                            defineProperty(
                                cri,
                                sameModuleTypeShape,
                                typeDefinitionBodyParts,
                                Visibility.Private,
                                Abstractness.Abstract,
                                gets = backedAlias,
                                symbolOverride = inheritedAlias.property.symbol,
                            )
                        }
                        closureRecordInfoMap.putMulti(cri, backedAlias) { mutableListOf() }
                        backedAlias
                    }
                }
                cri.typeWiring[typeShape] = alias
            }
        }

        propertiesDefined[typeShape] = closureRecordInfoMap
        return closureRecordInfoMap
    }

    private fun defineProperty(
        closureRecordInfo: ClosureRecordInfo,
        enclosingType: MutableTypeShape,
        typeDefinitionParts: FnParts,
        visibility: Visibility,
        abstractness: Abstractness,
        gets: ClosureRecordAlias? = null,
        symbolOverride: Symbol? = null,
    ): ClosureRecordAlias {
        // The `class` and `interface` macros ensure it's a block.
        val definedTypeValue =
            typeDefinitionParts.metadataSymbolMap[typeDefinedSymbol]?.target?.valueContained!!
        val body = typeDefinitionParts.body as BlockTree
        check(body.flow is LinearFlow) // Otherwise, we need a helper to add at end.

        val pos = body.pos.rightEdge

        val name = nameMaker.unusedSourceName(crParsedName)
        val pSymbol = symbolOverride ?: unusedSymbol(enclosingType, name)

        @Suppress("DuplicatedCode") // This is not very similar to the getter creation code.
        val decl = body.treeFarm.grow(pos) {
            Decl(name) {
                V(vPropertySymbol)
                V(pSymbol)
                V(vVisibilitySymbol)
                V(visibility.toSymbol())
                V(vStaySymbol)
                Stay()
                V(vSyntheticSymbol)
                V(void)
                V(vTypeSymbol)
                V(Types.vClosureRecord)
            }
        }

        body.add(decl)
        val stay = decl.parts!!.metadataSymbolMap.getValue(staySymbol).target as StayLeaf

        val getter = gets?.property?.let { property ->
            val getterName = nameMaker.unusedSourceName(ParsedName("get.cr"))
            val getterSymbol = unusedSymbol(enclosingType, getterName)
            val getterVisibility = Visibility.Protected
            val methodDecl = body.treeFarm.grow(pos) {
                Decl(getterName) {
                    V(vMethodSymbol)
                    V(pSymbol)
                    V(vGetterSymbol)
                    V(void)
                    V(vVisibilitySymbol)
                    V(getterVisibility.toSymbol())
                    V(vStaySymbol)
                    Stay()
                    V(vSyntheticSymbol)
                    V(void)
                    V(initSymbol)
                    Fn {
                        val thisName = nameMaker.unusedSourceName(thisParsedName)
                        Decl(thisName) {
                            V(typeSymbol)
                            V(definedTypeValue)
                            V(vImpliedThisSymbol)
                            V(definedTypeValue)
                        }
                        Call(BuiltinFuns.getpFn) {
                            Rn(property.name)
                            Rn(thisName)
                        }
                    }
                }
            }
            // The getter also needs to hoist out.
            methodsByType.putMulti(enclosingType, MethodRecord(methodDecl)) { mutableListOf() }
            val getterStay = methodDecl.parts!!.metadataSymbolMap.getValue(staySymbol).target as StayLeaf
            body.add(methodDecl)

            enclosingType.methods.add(
                MethodShape(
                    enclosingType,
                    getterName,
                    getterSymbol,
                    getterStay,
                    getterVisibility,
                    MethodKind.Normal,
                    OpenOrClosed.Closed,
                ),
            )

            getterName
        }

        val propertyShape = PropertyShape(
            enclosingType,
            name,
            pSymbol,
            stay,
            visibility,
            abstractness,
            getter,
            setter = null,
        )
        enclosingType.properties.add(propertyShape)

        return ClosureRecordAlias(closureRecordInfo, enclosingType, propertyShape)
    }

    private fun addConstructorParameters(typeShape: MutableTypeShape) {
        val closureRecordAliases = closureRecordInfos
            .mapNotNull { it.typeWiring[typeShape] }
            .filter { it.typeShape == typeShape }
            .sortedBy { it.closureRecordInfo }
        if (closureRecordAliases.isEmpty()) {
            return
        }
        val doc = root.document
        for (constructor in typeShape.membersMatching(constructorSymbol)) {
            val constructorDecl =
                constructor.stay?.incoming?.source as? DeclTree ?: continue
            val constructorFun =
                constructorDecl.parts?.metadataSymbolMap?.get(initSymbol)?.target as? FunTree
            val constructorParts = constructorFun?.parts ?: continue
            val thisDecl = constructorParts.formals.firstOrNull {
                val parts = it.parts
                parts != null && impliedThisSymbol in parts.metadataSymbolMap
            }
            val thisName = thisDecl?.parts?.name?.content ?: continue
            console.groupIf(DEBUG, "Constructors for ${typeShape.name}") {
                val formalsAndPropertySets = closureRecordAliases.mapIndexed { index, alias ->
                    val formalParameterName = nameMaker.unusedSourceName(crParsedName)
                    val pos = thisDecl.pos.leftEdge

                    val (formal, propertySet) = doc.treeFarm.growAll(pos) {
                        Decl(formalParameterName) {
                            V(vTypeSymbol)
                            V(Types.vClosureRecord)
                        }
                        Call(BuiltinFuns.vSetp) {
                            Ln(alias.property.name)
                            Rn(thisName)
                            Rn(formalParameterName)
                        }
                    }

                    alias.constructorParameterIndex = index

                    formal to propertySet
                }

                val constructorBodyEdge = constructorParts.body.incoming!!
                val constructorBody: BlockTree =
                    when (val body = constructorBodyEdge.target) {
                        is BlockTree -> body
                        else -> {
                            constructorBodyEdge.replace {
                                BlockS {
                                    Replant(freeTree(body))
                                }
                            }
                            constructorBodyEdge.target as BlockTree
                        }
                    }

                debug {
                    console.group("Before") {
                        constructorFun.toPseudoCode(out = console.textOutput)
                    }
                }
                val thisParamIndex = thisDecl.incoming!!.edgeIndex
                val afterThisParam = thisParamIndex + 1 until thisParamIndex + 1
                constructorFun.replace(afterThisParam) {
                    formalsAndPropertySets.forEach {
                        Replant(it.first)
                    }
                }
                prefixBlockWith(
                    formalsAndPropertySets.map { it.second },
                    constructorBody,
                )
                debug {
                    console.group("After") {
                        constructorFun.toPseudoCode(out = console.textOutput)
                    }
                }
            }
        }
    }

    /** Explicit visibility notations are required for all class members. */
    private fun checkForMissingVisibility(typeShape: TypeShape) {
        if (typeShape.abstractness == Abstractness.Abstract) {
            // Interfaces don't have any visibility requirements
            return
        }
        val missingVisibility = buildSet {
            typeShape.members.mapNotNullTo(this) { memberShape ->
                if (memberShape !is VisibleMemberShape) { return@mapNotNullTo null }
                val declParts = (memberShape.stay?.incoming?.source as? DeclTree)?.parts
                if (declParts != null && visibilitySymbol !in declParts.metadataSymbolMap) {
                    memberShape
                } else {
                    null
                }
            }
        }
        if (missingVisibility.isNotEmpty()) {
            logSink.log(
                Log.Error,
                MessageTemplate.MissingMemberVisibility,
                missingVisibility.first().stay?.pos ?: typeShape.pos.leftEdge,
                listOf(typeShape.name, missingVisibility.map { it.loggable }),
            )
        }
    }

    private fun rewriteFreeVariableUses(
        typeShape: TypeShape,
        typeDefinitionFnParts: FnParts,
    ) {
        // First we need to map free variables to the closure records that hold them and thence to
        // properties of typeShape.
        // That will allow us to find a property given a possibly free name.
        val varMap = mutableMapOf<ResolvedName, ClosureRecordAlias>()
        for ((cri, aliases) in propertiesDefined[typeShape] ?: emptyMap()) {
            aliases.firstOrNull()?.let { alias ->
                cri.vars.forEach { varName ->
                    varMap[varName] = alias
                }
            }
        }

        if (varMap.isEmpty()) {
            return
        }

        // Next, walk the body of the type definition.
        // For each function with an @impliedThis, rewrite uses in it.
        TreeVisit.startingAt(typeDefinitionFnParts.body)
            .forEach { t ->
                if (t is FunTree) {
                    val parts = t.parts
                    if (parts != null) {
                        // Do not recurse into nested types
                        if (typeDefinedSymbol in parts.metadataSymbolMap) {
                            return@forEach VisitCue.SkipOne
                        }
                        for (d in parts.formals) {
                            val dParts = d.parts
                            if (dParts != null && impliedThisSymbol in dParts.metadataSymbolMap) {
                                rewriteFreeVariableUsesInMethod(t, dParts.name, varMap)
                                return@forEach VisitCue.SkipOne
                            }
                        }
                    }
                }
                VisitCue.Continue
            }
            .visitPreOrder()
    }

    private fun rewriteFreeVariableUsesInMethod(
        methodTree: FunTree,
        thisName: NameLeaf,
        varMap: MutableMap<ResolvedName, ClosureRecordAlias>,
    ) {
        val reads = mutableListOf<RightNameLeaf>()
        val writes = mutableListOf<CallTree>()
        TreeVisit.startingAt(methodTree)
            .forEach { t ->
                if (
                    t is FunTree &&
                    t.parts?.metadataSymbolMap?.containsKey(typeDefinedSymbol) == true
                ) {
                    return@forEach VisitCue.SkipOne
                }

                if (t is RightNameLeaf && t.content in varMap) {
                    reads.add(t)
                }

                if (t is CallTree && t.size == BINARY_OP_CALL_ARG_COUNT) {
                    val (callee, leftHand) = t.children
                    if (callee.functionContained == BuiltinFuns.setLocalFn &&
                        leftHand is LeftNameLeaf && leftHand.content in varMap
                    ) {
                        writes.add(t)
                    }
                }

                VisitCue.Continue
            }
            .visitPreOrder()

        @Suppress("FunctionName") // Symmetry with TreeFarm DSL where up-case -> planting
        fun (Planting).PlantClosureRecordReference(
            pos: Position,
            alias: ClosureRecordAlias,
        ) {
            Call(pos, BuiltinFuns.vGetp) {
                Rn(pos, alias.property.name)
                Rn(pos.rightEdge, thisName.content)
            }
        }

        reads.forEach { read ->
            val alias = varMap[read.content]
            if (alias != null) {
                val cri = alias.closureRecordInfo
                read.incoming!!.replace {
                    Call(read.pos, BuiltinFuns.vGetCR) {
                        PlantClosureRecordReference(read.pos.leftEdge, alias)
                        V(Value(cri.vars.indexOf(read.content), TInt))
                        // TODO: Store name as hint for pseudo-code form somehow
                    }
                }
            }
        }

        // Handle writes in reverse order.  This avoids a problem in
        //     a = (b = c)
        // where, if we processed (a = ...) before (b = ...)
        // the edge pointing to (b = ...) would have been removed from (a = ...) during its
        // translation.
        writes.asReversed().forEach { write ->
            val (_, leftSide, newValueExpr) = write.children
            check(leftSide is LeftNameLeaf)
            val alias = varMap[leftSide.content]
            if (alias != null) {
                val cri = alias.closureRecordInfo
                write.incoming!!.replace {
                    Call(write.pos, BuiltinFuns.vSetCR) {
                        PlantClosureRecordReference(write.pos.leftEdge, alias)
                        V(leftSide.pos, Value(cri.vars.indexOf(leftSide.content), TInt))
                        Replant(freeTree(newValueExpr))
                    }
                }
            }
        }
    }

    /**
     * Stores metadata with factories so we can easily desugar property bags like `({ x: ..., y: ... })`
     * to positional argument lists.
     */
    private fun storeParameterMetadataWithFactories() {
        for ((type, methodRecords) in this.methodsByType) {
            for (methodRecord in methodRecords) {
                val decl = methodRecord.methodDecl
                val parts = decl.parts ?: continue
                if (parameterNameSymbolsListSymbol !in parts.metadataSymbolMap) {
                    val name = parts.name.content
                    val methodShape = type.methods.firstOrNull { it.name == name }

                    val init = parts.metadataSymbolMap[initSymbol]
                    val funTree = init?.let { lookThroughDecorations(it) }?.target as? FunTree
                    val funParts = funTree?.parts
                    if (methodShape != null && funParts != null) {
                        var ok = true
                        var sawOptional = false
                        val valueList = buildList {
                            for (formal in funParts.formals) {
                                val formalParts = formal.parts
                                if (formalParts == null) {
                                    ok = false
                                } else if (impliedThisSymbol in formalParts.metadataSymbolMultimap) {
                                    // Do nothing.  Implied parameter
                                } else {
                                    val word = formalParts.metadataSymbolMap[wordSymbol]?.symbolContained
                                    if (word == null) {
                                        ok = false
                                    } else {
                                        val isOptional =
                                            optionalSymbol in formalParts.metadataSymbolMultimap ||
                                                defaultSymbol in formalParts.metadataSymbolMultimap
                                        if (isOptional) {
                                            if (!sawOptional) {
                                                sawOptional = true
                                                add(TNull.value)
                                            }
                                        } else {
                                            if (sawOptional) {
                                                ok = false
                                            }
                                        }
                                        add(Value(word))
                                    }
                                }
                            }
                            if (!sawOptional) {
                                add(TNull.value)
                            }
                        }

                        if (ok) {
                            val symbolsList = Value(valueList, TList)

                            decl.insert {
                                V(decl.pos.rightEdge, vParameterNameSymbolsListSymbol)
                                V(symbolsList, type = symbolOrNullList.value)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Move method declarations, which should no longer refer directly to free variables, into the
     * module root.
     *
     *     ...
     *     let f() {
     *         class C {
     *             @method(\m) let m = fn { ... };
     *         }
     *         // etc.
     *     }
     *
     * becomes
     *
     *     ...
     *     @method(\m) @fromType(C__0) let m = fn { ... };
     *     let f() {
     *         class C {
     *         }
     *     }
     */
    private fun extractMembersToRoot(): ConvertedTypeInfo {
        val converted = mutableListOf<Pair<TypeShape, TEdge?>>()
        for ((typeShape) in methodsByType) {
            val parts = typeDefinitionParts[typeShape] ?: continue
            var insertionPoint = parts.body.incoming!! // Safe since root is not a function
            while (insertionPoint.source != root) {
                insertionPoint = insertionPoint.source!!.incoming!!
            }

            // Shift the insertion point behind any constructor reference declaration.
            // It's convenient for the typer to be able to type members before typing constructor
            // references.
            while (insertionPoint != insertionPoint.source!!.edgeOrNull(0)) {
                val before = insertionPoint.source!!.edge(insertionPoint.edgeIndex - 1)
                val beforeTree = before.target
                if (
                    beforeTree is DeclTree &&
                    beforeTree.parts?.metadataSymbolMap?.contains(typeDeclSymbol) == true
                ) {
                    insertionPoint = before
                } else {
                    break
                }
            }

            // Insert the extracted methods before the insertion point to preserve ordering
            // requirements.
            check(root.flow is LinearFlow) // Otherwise, we'll need some extra insert-before logic.
            val insertionPointIndex = insertionPoint.edgeIndex
            val beforeInsertionPoint = insertionPointIndex until insertionPointIndex
            val edgeAfter = root.edgeOrNull(insertionPointIndex + 1)

            val typeDefinedTree = parts.metadataSymbolMap[typeDefinedSymbol]?.target

            root.replace(beforeInsertionPoint) {
                var extractedTypeMemberCount = 0
                fun extract(t: Tree) {
                    if (t is DeclTree) {
                        val tParts = t.parts
                        if (
                            tParts != null &&
                            tParts.isTypeMember &&
                            typeDefinedTree is ValueLeaf &&
                            fromTypeSymbol !in tParts.metadataSymbolMap
                        ) {
                            extractedTypeMemberCount += 1
                            // Add information about declaring class since we no-longer can just
                            // look up for @typeDefined on an ancestor
                            t.replace(t.size until t.size) {
                                V(t.pos.rightEdge, vFromTypeSymbol)
                                Replant(typeDefinedTree.copy())
                            }
                        }
                    }
                    Replant(freeTree(t))
                }
                val body = parts.body
                when (body) {
                    is BlockTree -> {
                        check(body.flow is LinearFlow)
                        for (child in body.children.toList()) {
                            if (!(child is BlockTree && child.size == 0)) {
                                extract(child)
                            }
                        }
                    }
                    else -> extract(body)
                }
                if (extractedTypeMemberCount == 0) {
                    val placeholderName = nameMaker.unusedTemporaryName(
                        typePlaceholderSymbol.text,
                    )
                    val reifiedType = Value(reifiedTypeFor(typeShape))
                    val placeholderPos = body.pos.leftEdge
                    // We need a breadcrumb for empty interfaces so that TmpLControlFlow can find
                    // enough information, even for nested empty interfaces.
                    Decl(placeholderPos) {
                        Ln(placeholderName)
                        V(typePlaceholderSymbol)
                        V(reifiedType)
                        V(typeSymbol)
                        V(Types.vEmpty)
                    }
                    Call(placeholderPos, BuiltinFuns.setLocalFn) {
                        Ln(placeholderName)
                        V(emptyValue)
                    }
                }
            }

            converted.add(typeShape to edgeAfter)
        }
        return ConvertedTypeInfo(converted.toList())
    }

    private fun curryClassConstructors() {
        TreeVisit.startingAt(root)
            .forEachContinuing { t ->
                if (t is CallTree && t.size >= 2 && t.child(0).functionContained == New) {
                    var constructedTypeNode = t.child(1)
                    if (constructedTypeNode is CallTree && constructedTypeNode.size >= 2 &&
                        constructedTypeNode.childOrNull(0)?.functionContained == BuiltinFuns.angleFn
                    ) {
                        constructedTypeNode = constructedTypeNode.child(1)
                    }
                    val typeShape = constructedTypeNode.typeShapeAtLeafOrNull
                    if (typeShape != null) {
                        val aliases = closureRecordInfos.mapNotNull {
                            it.typeWiring[typeShape]
                        }
                        if (aliases.isNotEmpty()) {
                            @Suppress("EmptyRange", "InvalidRange")
                            val afterConstructedTypeNode = 2 until 2
                            val pos = constructedTypeNode.pos.rightEdge
                            t.replace(afterConstructedTypeNode) {
                                aliases.sortedBy { it.constructorParameterIndex }.forEach {
                                    Rn(pos, it.closureRecordInfo.closRecHoldingTemporary)
                                }
                            }
                        }
                    }
                }
            }
            .visitPreOrder()
    }

    private fun declareClosureRecordTemporaries() {
        val declarations = closureRecordInfos.groupBy { it.containingScope.incoming!! }

        for ((scopeEdge, closureRecordInfos) in declarations) {
            val scopeBlock = when (val scopeTarget = scopeEdge.target) {
                is BlockTree -> scopeTarget
                else -> {
                    val block = BlockTree.wrap(freeTree(scopeTarget))
                    scopeEdge.replace(block)
                    block
                }
            }

            prefixBlockWith(
                closureRecordInfos.map { it.decl },
                scopeBlock,
            )
        }
    }

    /**
     * Keep track of which symbols are used so that we can generate dot-accessible
     * synthetic members.
     */
    private val usedSymbolsByType = mutableMapOf<TypeShape, MutableSet<Symbol>>()

    private fun unusedSymbol(typeShape: TypeShape, name: SourceName): Symbol {
        val usedSymbols = usedSymbols(typeShape)
        var candidate = Symbol(name.rawDiagnostic)
        while (candidate in usedSymbols) {
            candidate = nameMaker.unusedSourceName(name.baseName).toSymbol()
        }
        usedSymbolsByType[typeShape]?.add(candidate)
        return candidate
    }

    private fun usedSymbols(typeShape: TypeShape): Set<Symbol> = usedSymbolsByType[typeShape]
        ?: run {
            val set = mutableSetOf<Symbol>()
            usedSymbolsByType[typeShape] = set // Curtail inf. recursion
            typeShape.superTypes.forEach { st ->
                val definition = st.definition
                if (definition is TypeShape) {
                    set.addAll(usedSymbols(definition))
                }
            }
            for (member in typeShape.members) {
                set.add(member.symbol)
            }
            set
        }

    private fun asSameModuleOrNull(typeShape: TypeShape): MutableTypeShape? {
        return if (typeShape.name.origin == root.document.context.namingContext) {
            // Unsound but safe as long as naming contexts are modules and the usual module flow
            // is followed.
            typeShape as? MutableTypeShape
        } else {
            null
        }
    }
}

private val symbolOrNullList = lazy {
    MkType.nominal(
        WellKnownTypes.listTypeDefinition,
        listOf(MkType.nullable(WellKnownTypes.symbolType)),
    )
}
