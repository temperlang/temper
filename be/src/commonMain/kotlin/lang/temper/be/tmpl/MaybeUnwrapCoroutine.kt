package lang.temper.be.tmpl

import lang.temper.frontend.AdaptGeneratorFn
import lang.temper.frontend.getBlockChildrenInOrderIfLinear
import lang.temper.frontend.isAdaptGeneratorFnCall
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.functionContained
import lang.temper.value.wrappedGeneratorFnSymbol

internal fun maybeUnwrapCoroutine(body: Tree, returnDecl: DeclTree): Pair<FunTree, AdaptGeneratorFn>? {
    // Look for a pattern in body.
    //
    //     let fn__123;
    //     fn__123 = @wrappedGeneratorFn fn ...;
    //     return__123 = adaptGeneratorFnSafe(fn__123)
    if (body !is BlockTree) { return null }

    val children = getBlockChildrenInOrderIfLinear(body) ?: return null
    val returnName = returnDecl.parts?.name?.content ?: return null
    @Suppress("MagicNumber") // A declaration, two assignments
    if (children.size != 3) { return null }
    val (firstEdge, secondEdge, thirdEdge) = children
    val first = firstEdge.target
    val second = secondEdge.target
    val third = thirdEdge.target

    val assignedFunctionName = (first as? DeclTree)?.parts?.name?.content
        ?: return null
    if ( // Verify structure above except for the right-side call and FunTree metadata
        !isAssignmentCall(second) ||
        !isAssignmentCall(third) ||
        (second.child(1) as? LeftNameLeaf)?.content != assignedFunctionName ||
        (third.child(1) as? LeftNameLeaf)?.content != returnName
    ) {
        return null
    }
    val returnedCall = third.child(2)
    if (!isAdaptGeneratorFnCall(returnedCall)) { return null }
    val adapter = returnedCall.child(0).functionContained as AdaptGeneratorFn

    val assignedFunction = second.child(2) as? FunTree ?: return null
    val assignedCall = third.child(2) as CallTree
    if ((assignedCall.child(1) as? RightNameLeaf)?.content != assignedFunctionName) {
        return null
    }
    if (assignedFunction.parts?.metadataSymbolMultimap?.contains(wrappedGeneratorFnSymbol) == true) {
        return assignedFunction to adapter
    }
    return null
}
