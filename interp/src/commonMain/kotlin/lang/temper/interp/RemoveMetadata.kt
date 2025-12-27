package lang.temper.interp

import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.NameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf

fun removeDeclMetadata(
    declTree: DeclTree,
    shouldRemove: MetadataPairPredicate,
) = removeMetadata(declTree, 1, declTree.size, shouldRemove)

fun removeFunMetadata(
    funTree: FunTree,
    shouldRemove: MetadataPairPredicate,
) {
    val bodyIndex = funTree.size - 1
    var metadataStart = 0
    while (metadataStart < bodyIndex && funTree.child(metadataStart) is DeclTree) {
        metadataStart += 1
    }
    removeMetadata(funTree, metadataStart, bodyIndex, shouldRemove)
}

/**
 * Removes each metadata (key, value) pair in [t] for which
 * [shouldRemove]\(keySymbol, keyTree, valueTree) is true.
 *
 * This enumerates metadata pairs in reverse child order.
 *
 * @return true iff [t] is the kind of thing that can have metadata pairs
 */
fun removeMetadata(
    t: Tree,
    shouldRemove: MetadataPairPredicate,
): Boolean = when (t) {
    is DeclTree -> {
        removeDeclMetadata(t, shouldRemove)
        true
    }
    is FunTree -> {
        removeFunMetadata(t, shouldRemove)
        true
    }
    is BlockTree,
    is CallTree,
    is EscTree,
    is StayLeaf,
    is NameLeaf,
    is ValueLeaf,
    -> false
}
