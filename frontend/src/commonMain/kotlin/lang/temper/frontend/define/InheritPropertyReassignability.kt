package lang.temper.frontend.define

import lang.temper.name.Symbol
import lang.temper.type.MethodKind
import lang.temper.type.TypeShape
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.maybeVarSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.vVarSymbol
import lang.temper.value.varSymbol

/**
 * Convert type property declarations like
 *
 *     @property(\x) @maybeVar let x;
 *
 * into either a single assignment declaration
 *
 *     @property(\x) let x;
 *
 * or a re-assignable declaration
 *
 *     @property(\x) var x;
 *
 * based on whether the declaring type or one of its super-types has
 * a setter.
 */
internal class InheritPropertyReassignability {
    private val reassignableByType = mutableMapOf<TypeShape, MutableSet<Symbol>>()

    fun process(root: Tree) = process(root, null)

    private fun process(tree: Tree, containingType: TypeShape?) {
        var containingTypeForChildren = containingType
        if (tree is FunTree) {
            val parts = tree.parts
            parts?.metadataSymbolMap?.get(typeDefinedSymbol)?.target?.typeShapeAtLeafOrNull?.let {
                containingTypeForChildren = it
            }
        } else if (tree is DeclTree && containingType != null) {
            val parts = tree.parts
            val metadata = parts?.metadataSymbolMap
            val maybeVarEdge = metadata?.get(maybeVarSymbol)
            if (maybeVarEdge != null) {
                val propertySymbol =
                    parts.metadataSymbolMap[propertySymbol]?.target?.symbolContained
                if (propertySymbol != null) {
                    val symbolsWithSetters = symbolsWithSettersForType(containingType)
                    val maybeVarEdgeIndex = maybeVarEdge.edgeIndex
                    if (propertySymbol in symbolsWithSetters && varSymbol !in metadata) {
                        // @maybeVar -> @var
                        val maybeVarKeyEdge = tree.edge(maybeVarEdgeIndex - 1)
                        maybeVarKeyEdge.replace(
                            ValueLeaf(
                                tree.document,
                                maybeVarKeyEdge.target.pos,
                                vVarSymbol,
                            ),
                        )
                    } else {
                        // Splice out @maybeVar
                        tree.removeChildren((maybeVarEdgeIndex - 1)..maybeVarEdgeIndex)
                    }
                }
            }
        }

        for (child in tree.children) {
            process(child, containingTypeForChildren)
        }
    }

    private fun symbolsWithSettersForType(typeShape: TypeShape): Set<Symbol> {
        val extant = reassignableByType[typeShape]
        if (extant != null) {
            return extant
        }
        val setterSymbolSet = mutableSetOf<Symbol>()
        reassignableByType[typeShape] = setterSymbolSet
        for (method in typeShape.methods) {
            if (method.methodKind == MethodKind.Setter) {
                setterSymbolSet.add(method.symbol)
            }
        }
        for (property in typeShape.properties) {
            val decl = property.stay?.incoming?.source as? DeclTree?
            if (decl != null) {
                val parts = decl.parts
                if (parts?.metadataSymbolMap?.containsKey(varSymbol) == true) {
                    setterSymbolSet.add(property.symbol)
                }
            }
        }
        for (superType in typeShape.superTypes) {
            val superTypeSymbols = symbolsWithSettersForType(superType.definition as TypeShape)
            setterSymbolSet.addAll(superTypeSymbols)
        }
        return setterSymbolSet
    }
}
