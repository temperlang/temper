package lang.temper.frontend.syntax

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Log
import lang.temper.common.compatRemoveLast
import lang.temper.frontend.disambiguate.reifiedTypeFor
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.dotBuiltinName
import lang.temper.value.functionContained
import lang.temper.value.initSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.resolutionSymbol
import lang.temper.value.staticSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.unpackUnappliedDecoration
import lang.temper.value.varSymbol

/**
 * Given a module root, replace [ParsedName]s with [ResolvedName]s.
 *
 * <!-- snippet: scoping/examples -->
 * `let`, [`var`][snippet/builtin/@var], and [`const`][snippet/builtin/@const] are used to
 * define variables.
 *
 * - `let x` defines a variable within the innermost containing block with the name `x`.
 *   It may be assigned once.
 *
 *   ```temper
 *   let x = "initial-value";
 *   x == "initial-value"
 *   ```
 *
 *   but it may not be re-assigned.
 *
 *   ```temper FAIL
 *   let x = "initial-value";
 *   x = "new-value";
 *   ```
 *
 * - `const x` is equivalent to `@const let x`.
 *
 *   ```temper
 *   const x = "initial-value";
 *   x == "initial-value"
 *   ```
 *
 * - `var x` is equivalent to `@var let x` and defines a variable that may be re-assigned after
 *   being initialized.
 *
 *   ```temper
 *   var x = "initial-value";
 *   x = "new-value"; // Re-assigned
 *   x == "new-value"
 *   ```
 *
 * Unlike in JavaScript, `var` is scoped the same way as `let`.
 *
 * ```temper
 * var i = "outer-var-i";
 * do {
 *   var i = "inner-var-i";
 *   console.log("inside  block ${i}"); //!outputs "inside  block inner-var-i"
 * }
 * console.log("outside block ${i}"); //!outputs "outside block outer-var-i"
 * ```
 *
 * Names can be re-used.  A declared name does not mask a name declared in a containing scope that
 * appear **before** it, but do mask uses that **appear** after it.
 *
 * ```temper
 * let i = "outer-i";
 * do {
 *   let j = i; // The `i` in the initializer refers to the innermost earlier declaration.
 *   let i = "inner-i";
 *   console.log("Inside : j=${j}, i=${i}");
 *   //!outputs "Inside : j=outer-i, i=inner-i"
 * }
 * console.log("Outside: i=${i}");
 * //!outputs "Outside: i=outer-i"
 * ```
 */
internal fun resolveNames(root: BlockTree, logSink: LogSink) {
    // We walk the tree looking for declarations and block labels, and
    // associate their names with the innermost containing block.
    // As we go we replace ParsedNames with ResolvedNames.
    val rdoc = root.document
    val nameMaker = rdoc.nameMaker

    fun treeContextFor(tree: Tree, blockContext: BlockContext): BlockContext = when (tree) {
        is BlockTree -> BlockContext(blockContext) // For locals
        is FunTree -> BlockContext(blockContext) // For parameters
        else -> blockContext
    }

    // Maybe allocate a name for a name leaf that declares a name, and replace it with the
    // newly allocated name.
    // Return any relation between ParsedNames that could refer to the newly allocated name.
    fun rewriteDeclaredName(
        left: LeftNameLeaf,
        edgeToRewrite: TEdge,
        preResolution: ResolvedName? = null,
        decl: DeclTree? = null,
    ) = when (val leftName = left.content) {
        is ParsedName -> {
            val newName = preResolution ?: nameMaker.unusedSourceName(leftName)
            edgeToRewrite.replace {
                Ln(left.pos, newName)
            }
            val isStatic = decl?.parts?.metadataSymbolMap?.let { staticSymbol in it } == true
            leftName to NameResolution(newName, isStatic = isStatic)
        }
        is ExportedName -> leftName.baseName to NameResolution(leftName)
        is Temporary, is SourceName, is BuiltinName -> null
    }

    // Rewrite declared names in a declaration, and return a list of new names so that we can update
    // the block context
    fun rewriteDeclaredNames(tree: DeclTree): List<Pair<ParsedName, Resolution>> {
        val leftEdge = tree.edgeOrNull(0) ?: return emptyList()
        val left = leftEdge.target
        return if (left is LeftNameLeaf) {
            val parts = tree.parts
            // Maybe the name was pre-resolved
            val resolutionEdge = parts?.metadataSymbolMap?.get(resolutionSymbol)
            val preResolution = if (resolutionEdge != null) {
                // Remove metadata that is not useful after name resolution
                val edgeIndex = resolutionEdge.edgeIndex
                tree.replace((edgeIndex - 1)..edgeIndex) {}
                (resolutionEdge.target as? LeftNameLeaf)?.content as? ResolvedName
            } else {
                null
            }
            listOfNotNull(rewriteDeclaredName(left, leftEdge, preResolution, decl = tree))
        } else if (isCommaCall(left)) {
            var i = 1 // After callee
            val n = left.size
            val declarations = mutableListOf<Pair<ParsedName, Resolution>>()
            while (i < n) {
                val edge = left.edge(i)
                val child = edge.target
                if (child is LeftNameLeaf) {
                    rewriteDeclaredName(child, edge)?.let {
                        declarations.add(it)
                    }
                }
                i += if (child.symbolContained != null) {
                    2 // Skip over metadata value associated with this metadata key
                } else {
                    1
                }
            }
            return declarations.toList()
        } else {
            return emptyList()
        }
    }

    // Keep a stack of type declarations we're inside of so that we can resolve members of `this`.
    val typeShapeStack = mutableListOf<TypeShape>()

    // Add bindings from a declarations to the block context.
    fun addBindings(
        decl: DeclTree,
        newBindings: List<Pair<ParsedName, Resolution>>,
        blockContext: BlockContext,
    ) {
        val metadata = decl.parts?.metadataSymbolMap
        val isMemberDeclaration = metadata != null && typeShapeStack.isNotEmpty() &&
            (propertySymbol in metadata || methodSymbol in metadata)
        if (!isMemberDeclaration) {
            // Already handled by analysis of type shape so that we can rewrite
            // uses to `this.memberName`.
            for ((beforeName, after) in newBindings) {
                blockContext.resolutions[beforeName] = after
            }
        }
    }

    fun walk(tree: Tree, parentBlockContext: BlockContext, top: Boolean = false) {
        val blockContext = treeContextFor(tree, parentBlockContext)
        var afterChildren = {}
        when (tree) {
            is BlockTree -> if (!top) {
                // Skip this if first pass for tops because label renames don't affect others.
                // Just kids, and we want to see the label fresh when recursing.
                val labelEdge = tree.parts.label
                val label = labelEdge?.target
                if (labelEdge != null && label is LeftNameLeaf) {
                    val labelName = label.content
                    if (labelName is ParsedName) {
                        val resolvedLabelName = nameMaker.unusedSourceName(labelName)
                        blockContext.resolutions[labelName] = NameResolution(resolvedLabelName)
                        labelEdge.replace {
                            Ln(label.pos, resolvedLabelName)
                        }
                    }
                }
            }
            is CallTree -> {
                val callee = tree.childOrNull(0)
                // Rewrite unqualified `this` references using the type containment stack.
                if (
                    callee?.functionContained == BuiltinFuns.thisPlaceholder && tree.size == 1 &&
                    typeShapeStack.isNotEmpty()
                ) {
                    val typeShape = typeShapeStack.last()
                    tree.insert(tree.size) {
                        V(tree.pos, Value(reifiedTypeFor(typeShape)))
                    }
                }
            }
            is DeclTree -> {
                val newBindings = rewriteDeclaredNames(tree)
                if (isFunctionDeclaration(tree)) {
                    // Functions can recursively refer to themselves via their own name.
                    addBindings(tree, newBindings, blockContext)
                } else {
                    // Walk after rewriting the names, but before inserting the names into the
                    // context so that shadowed variables can be used in the initializer for a
                    // shadowing declaration.
                    //     let declaration = getSourceCode();
                    //     let declaration = parse(declaration);
                    // to allow reusing the same notation for different
                    // representations of the same notion.
                    afterChildren = {
                        addBindings(tree, newBindings, blockContext)
                    }
                }
            }
            is FunTree -> if (!top) {
                // Skip this if first pass for top-levels because we expect the params not to have
                // been renamed yet when recursing.
                val fnParts = tree.parts
                // Formal parameters to a function can reference one another in a fluid way.
                // The default expression for a formal parameter can reference formal parameters
                // defined earlier.  TODO: is this supported in the TmpL translator?
                // Doing this early, instead of in the order children are visited below, ensures
                // that we allocate a new name for each formal before visiting any of their
                // default expressions.
                if (fnParts != null) {
                    for (formal in fnParts.formals) {
                        addBindings(
                            formal,
                            rewriteDeclaredNames(formal),
                            blockContext,
                        )
                    }
                }

                // If this function is a type declaration body, bring member names into scope so
                // that we can resolve implicit references to members via MemberResolutions
                val typeDefinedValue = fnParts?.metadataSymbolMap?.get(typeDefinedSymbol)?.target
                val typeShape = typeDefinedValue?.typeShapeAtLeafOrNull
                if (typeShape != null) {
                    typeShapeStack.add(typeShape)
                    addInheritedNamesToScope(typeShape, blockContext)
                    afterChildren = { typeShapeStack.compatRemoveLast() }
                }
            }
            is NameLeaf -> if (!top) {
                // Skip on first pass for tops because these are only for resolving not declaring.
                val name = tree.content
                var resolution = blockContext[name]
                    ?: when {
                        name !is ParsedName -> null
                        else -> NameResolution(BuiltinName(name.nameText))
                    }
                val edge = tree.incoming!! // safe because root is not a name leaf
                if (name !is ParsedName && isInPreResolvedContext(edge)) {
                    // Don't override a declared name
                    resolution = null
                }
                if (resolution != null) {
                    val opos = tree.pos
                    edge.replace {
                        when (resolution) {
                            is NameResolution -> {
                                if (resolution.isStatic) {
                                    // Report error, but let it resolve below anyway for convenience.
                                    logSink.log(Log.Error, MessageTemplate.StaticMemberNeedsQualified, opos, listOf())
                                }
                                when (tree) {
                                    is LeftNameLeaf -> Ln(opos, resolution.name)
                                    is RightNameLeaf -> Rn(opos, resolution.name)
                                }
                            }
                            is MemberResolution -> {
                                Call(opos) {
                                    Rn(opos, dotBuiltinName)
                                    Call(opos, BuiltinFuns.vThis) {
                                        V(
                                            opos,
                                            Value(reifiedTypeFor(resolution.typeShape)),
                                        )
                                    }
                                    V(opos, resolution.member.symbol)
                                }
                            }
                        }
                    }
                }
            }
            is EscTree,
            is StayLeaf,
            is ValueLeaf,
            -> Unit
        }
        // If `top`, don't recurse until after first pass.
        val underRoot = tree === root
        if (!top) {
            for (child in tree.children) {
                // Remember the top context in the case of CallTree, so we don't dig into others.
                walk(child, blockContext, top = underRoot || top)
            }
        } else if (tree is CallTree) {
            // Except call trees can be decorators hiding declarations, so dig into them.
            val unpacked = unpackUnappliedDecoration(tree.incoming!!)
            if (unpacked != null) {
                walk(tree.children[unpacked.decoratedIndex], blockContext, top = underRoot || top)
            }
        }
        afterChildren()
        if (underRoot) {
            // Process deep only after finding all the tops.
            for (child in tree.children) {
                walk(child, blockContext)
            }
        }
    }
    walk(root, BlockContext(null))

    // Flip a switch in root's document preventing it from being used to create new
    // NameLeaves with ParsedNames.
    rdoc.markNamesResolved()
}

private class BlockContext(
    val parent: BlockContext?,
) {
    val resolutions = mutableMapOf<TemperName, Resolution>()

    operator fun get(name: TemperName): Resolution? =
        resolutions[name] ?: parent?.get(name)
}

private sealed class Resolution

private data class NameResolution(
    val name: ResolvedName,
    val isStatic: Boolean = false,
) : Resolution()
private data class MemberResolution(
    /** It may be a sub-type of member's enclosing type */
    val typeShape: TypeShape,
    val member: VisibleMemberShape,
) : Resolution()

private fun addInheritedNamesToScope(typeShape: TypeShape, blockContext: BlockContext) {
    // Handle inheritance cycles gracefully
    val inheritedFrom = mutableSetOf<ModularName>()

    val typeShapes = ArrayDeque<Pair<TypeShape, Boolean>>()
    typeShapes.add(typeShape to true)
    while (typeShapes.isNotEmpty()) {
        val (superTypeShape, includePrivate) = typeShapes.removeFirst()
        val superTypeName = superTypeShape.name
        if (superTypeName in inheritedFrom) {
            continue
        }
        inheritedFrom.add(superTypeName)
        for (member in superTypeShape.members) {
            if (member is VisibleMemberShape) {
                val inScope = when (member.visibility) {
                    Visibility.Private -> includePrivate
                    Visibility.Protected, Visibility.Public -> true
                }
                if (inScope) {
                    val unqualifiedMemberName = ParsedName(member.symbol.text)
                    if (unqualifiedMemberName !in blockContext.resolutions) {
                        blockContext.resolutions[unqualifiedMemberName] =
                            MemberResolution(typeShape, member)
                        // We use typeShape, not the super type, because any access
                        // is from the sub-type being declared.
                    }
                    val qualifiedMemberName = member.name as? ResolvedName
                    if (qualifiedMemberName != null && qualifiedMemberName !in blockContext.resolutions) {
                        blockContext.resolutions[qualifiedMemberName] =
                            MemberResolution(typeShape, member)
                    }
                }
            }
        }
        superTypeShape.superTypes.forEach {
            val ts = it.definition as? TypeShape
            if (ts != null) {
                typeShapes.add(ts to false)
            }
        }
    }
}

private fun isFunctionDeclaration(declTree: DeclTree): Boolean {
    val parts = declTree.parts ?: return false
    if (varSymbol in parts.metadataSymbolMap) {
        // If the declaration can be re-assigned, it is not a stable function declaration.
        return false
    }
    val init = parts.metadataSymbolMap[initSymbol]
    return init?.target is FunTree
}

private fun isInPreResolvedContext(edge: TEdge): Boolean {
    val parent = edge.source
    if (parent is DeclTree && edge.edgeIndex == 0) {
        // name is a pre-resolved declaration.  do not override
        return true
    }
    if (parent is CallTree) {
        when (parent.child(0).functionContained) {
            // Already used as a pre-resolved this reference
            BuiltinFuns.getpFn,
            BuiltinFuns.setpFn,
            -> return true
        }
    }

    return false
}
