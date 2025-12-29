package lang.temper.frontend.syntax

import lang.temper.common.putMultiList
import lang.temper.frontend.typestage.joinToTokenSerializable
import lang.temper.interp.forEachActual
import lang.temper.log.FailLog
import lang.temper.log.MessageTemplate
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.MethodKind
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.constructorSymbol
import lang.temper.value.freeTarget
import lang.temper.value.impliedThisSymbol
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.methodSymbol
import lang.temper.value.newBuiltinName
import lang.temper.value.staticTypeContained
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import lang.temper.value.wordSymbol

/**
 * Prior to logic here, object literals like `{ a: 1 }` are converted to match
 * `new void(a = 1)` form. Logic here finds unique constructors matching the
 * named args in `new void` and converts them to reference those classes.
 */
internal class ConvertObjectLiteralNew(private val failLog: FailLog) {
    private val edits = mutableListOf<Pair<TEdge, Tree>>()

    fun process(root: BlockTree) {
        edits.clear()
        val env = PropertyEnv(parent = null)
        gatherTopDecls(root, env)
        useTopDecls(root, env)
        for (edit in edits) {
            val (edgeToReplace, replacement) = edit
            freeTarget(edgeToReplace)
            edgeToReplace.replace(replacement)
        }
    }

    private fun gatherTopDecls(tree: Tree, env: PropertyEnv) {
        for (kid in tree.children) {
            when (kid) {
                // But don't go into lower scopes.
                is BlockTree -> if (!kid.isTypeDefinitionBody) {
                    continue
                }
                is DeclTree -> kid.processDecl(env)
                else -> {}
            }
            gatherTopDecls(kid, env)
        }
    }

    private fun useTopDecls(tree: Tree, env: PropertyEnv) {
        for (kid in tree.children) {
            when (kid) {
                is BlockTree -> if (!kid.isTypeDefinitionBody) {
                    // We can descend scopes now, and use non-top processing on them.
                    walk(kid, env)
                    continue
                }
                is CallTree -> kid.matchIfNewVoid(env)
                else -> {}
            }
            useTopDecls(kid, env)
        }
    }

    private fun walk(tree: Tree, env: PropertyEnv) {
        var childEnv = env
        when (tree) {
            is BlockTree -> {
                // Classes are defined in a `class { ... }` call so we need to ignore that
                // block so that constructors found are in the scope containing the type definition.
                if (!tree.isTypeDefinitionBody) {
                    childEnv = env.push()
                }
            }
            is CallTree -> tree.matchIfNewVoid(env)
            is DeclTree -> tree.processDecl(env)
            else -> {}
        }
        for (child in tree.children) {
            walk(child, childEnv)
        }
    }

    private fun CallTree.matchIfNewVoid(env: PropertyEnv) {
        if (size >= 2) {
            val callee = child(0)
            if (callee is RightNameLeaf && callee.content == newBuiltinName) {
                val constructor = child(1)
                if (constructor is ValueLeaf && constructor.content == void) {
                    val propertyNames = buildList {
                        forEachActual actuals@{ _, symbolLeaf, _ ->
                            this.add(TSymbol.unpack((symbolLeaf ?: return@actuals).content).text)
                        }
                    }
                    val types = env.findMatching(propertyNames).toSet()
                    val type = types.firstOrNull()
                        ?: run {
                            failLog.fail(MessageTemplate.NoSignatureMatches, pos)
                            return@matchIfNewVoid
                        }
                    if (types.size > 1) {
                        // Sort the match list for easier scanning and predictable testing.
                        val matchList = types.map { it.definition.name }.sortedBy { it.rawDiagnostic }.map {
                            it.toToken(inOperatorPosition = false)
                        }
                        val failArgs = listOf(matchList.joinToTokenSerializable { it })
                        failLog.fail(MessageTemplate.MultipleConstructorSignaturesMatch, pos, failArgs)
                        return
                    }
                    val typeValue = Value(ReifiedType(hackMapOldStyleToNew(type)))
                    edits.add(edge(1) to ValueLeaf(constructor.document, constructor.pos, typeValue))
                }
            }
        }
    }
}

/** Update the environment so that we have know which types are available for import */
private fun DeclTree.processDecl(env: PropertyEnv) {
    val parts = this.parts ?: return
    val meta = parts.metadataSymbolMap
    val init = meta[initSymbol]?.let { lookThroughDecorations(it).target }

    // Once we've found a type that we want to import, register it with the environment so
    // that it's available to desugar {...} expressions that follow it.

    // Look for two kinds of declarations:
    // - Direct value assignments, which have static types if imported
    //   let x = ReifiedType(T);
    // - Local type declarations
    //   @method(\constructor) let f = ...
    val typeContained = init?.staticTypeContained
    if (typeContained != null) {
        if (typeContained is NominalType) {
            val typeShape = (typeContained.definition as? TypeShape) ?: return
            if (
                // Check if the type is from a different location, or we won't have static type info yet.
                typeShape.pos.loc != pos.loc &&
                typeShape.abstractness == Abstractness.Concrete &&
                typeShape.name !in env.trackedTypeNames
            ) {
                env.trackedTypeNames.add(typeShape.name)
                for (method in typeShape.methods) {
                    if (method.methodKind != MethodKind.Constructor) { continue }
                    method.parameterInfo?.let { extraNonNormativeParameterInfo ->
                        for ((i, parameterName) in extraNonNormativeParameterInfo.names.withIndex()) {
                            if (i != 0) { // Not thisValue
                                if (parameterName != null) {
                                    env.addMatch(parameterName.text, typeContained)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (TSymbol.unpackOrNull(meta[methodSymbol]?.target?.valueContained) == constructorSymbol) {
        if (init is FunTree) {
            val methodParts = init.parts
            val methodFormals = methodParts?.formals
            if (methodFormals != null) {
                val thisParts = methodFormals.getOrNull(0)?.parts
                if (thisParts != null) {
                    val type = thisParts.metadataSymbolMap[impliedThisSymbol]?.target?.staticTypeContained
                    val nominalType = type as? NominalType ?: return
                    for (i in 1 until methodFormals.size) {
                        val formalParts = methodFormals[i].parts
                        val parameterName =
                            formalParts?.metadataSymbolMap?.get(wordSymbol)?.target?.valueContained(TSymbol)
                        if (parameterName != null) {
                            env.addMatch(parameterName.text, nominalType)
                        }
                    }
                }
            }
        }
    }
}

private data class PropertyEnv(
    val parent: PropertyEnv?,
    /** Searched recursively through parents as nested scopes to restrict false matches. */
    var properties: MutableMap<String, MutableList<NominalType>>? = null,
    /** Kept same throughout whole module presuming unique names after SyntaxStage. */
    val trackedTypeNames: MutableSet<TemperName> = mutableSetOf(),
) {
    fun addMatch(name: String, type: NominalType) {
        if (properties == null) {
            // For efficiency across potentially many blocks, only instantiate the scope as needed.
            properties = mutableMapOf()
        }
        properties!!.putMultiList(name, type)
    }

    fun findMatching(names: List<String>): Set<NominalType> {
        val counts = mutableMapOf<NominalType, Int>()
        for (name in names) {
            forEachMatch(name) { match ->
                // Each constructor should be declared only once across all scopes, so sum should be fine.
                counts.compute(match) { _, old -> (old ?: 0) + 1 }
            }
        }
        return buildSet {
            counts.mapNotNullTo(this) { (type, count) ->
                if (count == names.size) {
                    type
                } else {
                    null
                }
            }
        }
    }

    fun forEachMatch(name: String, action: (NominalType) -> Unit) {
        for (match in (properties?.get(name) ?: emptyList())) {
            action(match)
        }
        parent?.forEachMatch(name, action)
    }

    fun push() = PropertyEnv(parent = this, trackedTypeNames = trackedTypeNames)
}

private val BlockTree.isTypeDefinitionBody: Boolean get() {
    val parent = incoming?.source
    if (parent is FunTree) {
        val fnParts = parent.parts
        if (fnParts?.body == this && typeDefinedSymbol in fnParts.metadataSymbolMap) {
            return true
        }
    }
    return false
}
