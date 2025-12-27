package lang.temper.frontend.define

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.frontend.prefixWith
import lang.temper.name.InternalModularName
import lang.temper.name.unusedAnalogueFor
import lang.temper.type2.Nullity
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.ReifiedType
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.defaultSymbol
import lang.temper.value.elseSymbol
import lang.temper.value.fnSymbol
import lang.temper.value.freeTarget
import lang.temper.value.ifBuiltinName
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.staticTypeContained
import lang.temper.value.typeSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vOptionalSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.void

/**
 * Moves initializers out of typed declarations so that their failure can be handled by later
 * passes.
 *
 * There are two cases.
 *
 * ### Non-function argument declarations
 *
 *     let n: T = i
 *
 * becomes
 *
 *     let n: T;
 *     n = i
 *
 * We split the declaration into a declaration followed by an assignment to the same.
 *
 * ### Function argument declarations
 *
 * When the declaration is part of a function argument we need to be a bit more careful.
 * Formal parameter initializers are not executed when the function is passed an explicit
 * value.
 *
 *     fn f(n: T = i) { ... }
 *
 * becomes
 *
 *     fn (n: T?) {
 *       if (isNull(n)) {
 *         n = i
 *       }
 *       ...
 *     }
 */
internal class SimplifyDeclarations {

    fun simplify(root: BlockTree) {
        val fnPartMap = mutableMapOf<FunTree, FnParts?>()
        TreeVisit
            .startingAt(root)
            .forEachContinuing visitOne@{ tree ->
                when (tree) {
                    is DeclTree -> {
                        val incoming = tree.incoming
                        val parent = incoming?.source
                        val isFormal = parent is FunTree && run {
                            val fnParts = fnPartMap.getOrPut(parent) {
                                parent.parts
                            }
                            fnParts == null || tree in fnParts.formals
                        }
                        if (!isFormal) {
                            simplifyNonFormalDecl(tree)
                        }
                    }
                    is FunTree -> {
                        val fnParts = fnPartMap.getOrPut(tree) { tree.parts }
                        if (fnParts != null) {
                            simplifyFormals(tree, fnParts)
                        }
                    }
                    else -> {}
                }
            }
            .visitPreOrder()
    }

    /**
     * Simplify all formal parameter initializers for the given function.
     * Doing them all at once makes it easier to preserve order of initialization.
     *
     * It doesn't matter whether a parameter is an output parameters.
     * Output parameters' initializers run on function entry; we
     * do not have to wait until we know whether the body assigned
     * a value and returned normally to initialize.
     */
    private fun simplifyFormals(funTree: FunTree, fnParts: FnParts) {
        val doc = funTree.document

        val prefix = mutableListOf<Tree>()
        for (isOutput in listOf(false, true)) {
            val declList = if (isOutput) listOfNotNull(fnParts.returnDecl) else fnParts.formals
            for (decl in declList) {
                val dp = decl.parts ?: continue
                val assignment = assignmentForInitializer(decl, dp, true) ?: continue
                var extracted: Tree? = null
                if (!isOutput) {
                    // Only bother to coalesce if the value isn't statically known to be null.
                    // TODO In TypeChecker, validate that optionals are nullable?
                    if (assignment.child(2).valueContained != TNull.value) {
                        extracted = buildOptionalDefaulting(decl, dp, assignment)
                    }
                }
                if (extracted != null) {
                    prefix.add(extracted)
                }
            }
        }
        if (prefix.isNotEmpty()) {
            val body = fnParts.body
            // The result of evaluating a declaration is void, but the result of
            // an assignment is the value stored.
            // Add an explicit void edge so that we don't create value producing
            // terminal expressions which might be treated as an implicit result
            // in, e.g., an empty function body.
            prefix.add(ValueLeaf(doc, body.pos.rightEdge, void))
            prefixWith(prefix, body)
        }
    }

    private fun buildOptionalDefaulting(decl: DeclTree, dp: DeclParts, assignment: CallTree): Tree {
        // We're still pre-typing, so we might not have explicit types.
        val (type, nullableType) = dp.type?.let type@{ typeNode ->
            val type = hackMapOldStyleToNew(
                typeNode.staticTypeContained ?: return@type null,
            )
            type to type.withNullity(Nullity.OrNull)
        } ?: (null to null)
        // Copy the decl for internal use, except the optional indicator.
        val internal = decl.copy() as DeclTree
        // Gets the index of the value, so the key is one before that.
        val optionalIndex = decl.edges.indexOf(dp.optional)
        internal.removeChildren(optionalIndex - 1..optionalIndex)
        // And figure out a new name for the parameter.
        val formalName = decl.document.nameMaker.unusedAnalogueFor(dp.name.content as InternalModularName)
        dp.name.incoming!!.replace { Ln(formalName) }
        if (nullableType != null && nullableType != type) {
            dp.metadataSymbolMap[typeSymbol]?.let { typeEdge ->
                typeEdge.replace {
                    val hasExplicitActuals = nullableType.bindings.isNotEmpty()
                    V(Value(ReifiedType(nullableType, hasExplicitActuals = hasExplicitActuals)))
                }
            }
        }
        // Modify internal to use the formal or else the default value.
        internal.insert(internal.size) {
            V(vInitSymbol)
            // TODO Could implement a coalesce macro with this content.
            Call {
                Rn(ifBuiltinName)
                Call {
                    V(Value(BuiltinFuns.equalsFn))
                    Rn(formalName)
                    V(TNull.value)
                }
                Fn { Replant(freeTarget(assignment.edge(2))) }
                V(elseSymbol)
                Fn {
                    val f0 = decl.document.nameMaker.unusedTemporaryName("f")
                    Decl(f0) {}
                    Call {
                        Rn(f0)
                        Fn {
                            // This will get autocast to not-null later.
                            Rn(formalName)
                        }
                    }
                }
            }
        }
        return internal
    }

    private fun simplifyNonFormalDecl(declTree: DeclTree) {
        val dp = declTree.parts ?: return
        val assignment = assignmentForInitializer(declTree, dp, false) ?: return
        // See comment above about terminal expressions.
        val terminal = ValueLeaf(declTree.document, declTree.pos.rightEdge, void)

        val declEdge = declTree.incoming!! // The root is not a DeclTree
        val parent = declEdge.source
        if (parent is BlockTree && parent.content is LinearFlow) {
            // Insert the assignment and the terminal at the same scope level.
            // This is necessary for the interpreter to see the initial value until the Weaver
            // flattens blocks.
            val declEdgeIndex = declEdge.edgeIndex
            val insertionPoint = declEdgeIndex + 1
            val atEnd = insertionPoint == parent.size
            parent.replace(insertionPoint until insertionPoint) {
                Replant(assignment)
                if (atEnd) {
                    Replant(terminal)
                }
            }
        } else {
            declEdge.replace {
                Block(declTree.pos) {
                    Replant(freeTarget(declEdge))
                    Replant(assignment)
                    Replant(terminal)
                }
            }
        }
    }

    private fun assignmentForInitializer(
        declTree: DeclTree,
        dp: DeclParts,
        isFormal: Boolean,
    ): CallTree? {
        val rightHandSideKey = if (isFormal) { defaultSymbol } else { initSymbol }
        val rhsEdge = dp.metadataSymbolMap[rightHandSideKey] ?: return null
        val rhs = rhsEdge.target
        val nameLeaf = dp.name
        val name = nameLeaf.content

        val doc = nameLeaf.document

        val isFunctionLike = !isFormal && dp.initAsFunctionLike != null
        val needsFunctionLike = isFunctionLike && fnSymbol !in dp.metadataSymbolMap

        // We need to remove the initializer along with the \init symbol that marks
        // it as such.
        val rhsEdgeIndex = rhsEdge.edgeIndex
        val keySymbolAndRhsEdgeIndices = (rhsEdgeIndex - 1)..rhsEdgeIndex
        val metadataPos = rhs.pos.leftEdge
        declTree.replace(keySymbolAndRhsEdgeIndices) {
            if (isFormal) {
                // We also need to record the fact that the declaration had an initializer so that
                // DynamicMessage can properly determine which arguments are optional.
                V(metadataPos, vOptionalSymbol)
                // Value is effectively a tristate because we don't insert defaulting for explicit nulls, but some
                // backends need to.
                val value = when (rhs.valueContained) {
                    TNull.value -> TNull.value
                    else -> TBoolean.valueTrue
                }
                V(metadataPos, value)
            } else if (needsFunctionLike) {
                V(metadataPos, fnSymbol)
                V(metadataPos, void)
            }
        }

        return doc.treeFarm.grow {
            Call(rhs.pos) {
                V(rhs.pos.leftEdge, BuiltinFuns.vSetLocalFn)
                Ln(dp.name.pos, name)
                Replant(rhs)
            }
        }
    }
}

/**
 * True if the initializer is found to be a function tree.
 *
 * This is not purely lexically determined above because elsewhere
 * we treat as equivalent
 *
 *     let f() { ... }
 *
 *     let f = fn { ... };
 *
 * A `var` declaration is never function like.
 *
 * @see fnSymbol
 */
val DeclTree.initAsFunctionLike: FunTree? get() =
    parts?.initAsFunctionLike

fun asFunctionLike(edge: TEdge): FunTree? =
    lookThroughDecorations(edge).target as? FunTree

val DeclParts.initAsFunctionLike get() =
    if (varSymbol in metadataSymbolMultimap) {
        null
    } else {
        this.metadataSymbolMap[initSymbol]?.let { asFunctionLike(it) }
    }
