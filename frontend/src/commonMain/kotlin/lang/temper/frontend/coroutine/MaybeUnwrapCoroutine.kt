package lang.temper.frontend.coroutine

import lang.temper.frontend.AdaptGeneratorFn
import lang.temper.frontend.getBlockChildrenInOrderIfLinear
import lang.temper.frontend.isAdaptGeneratorFnCall
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withType
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.functionContained
import lang.temper.value.isAssignment
import lang.temper.value.wrappedGeneratorFnSymbol

fun maybeUnwrapCoroutine(body: Tree, returnDecl: DeclTree): Triple<FunTree, AdaptGeneratorFn, Type2>? {
    // Look for a pattern like this in the body.
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
        !isAssignment(second) ||
        !isAssignment(third) ||
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
    val innerFnType = assignedCall.child(1).typeInferences?.type
        ?: return null
    val generatorType = withType(
        hackMapOldStyleToNew(innerFnType),
        fn = { _, sig, _ -> sig },
        fallback = { null },
    )?.returnType2 ?: return null
    if (assignedFunction.parts?.metadataSymbolMultimap?.contains(wrappedGeneratorFnSymbol) == true) {
        return Triple(assignedFunction, adapter, generatorType)
    }
    return null
}
