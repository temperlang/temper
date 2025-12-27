package lang.temper.frontend.typestage

import lang.temper.common.subListToEnd
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type.mapFunctionTypesThroughIntersection

object BindMethodTypeHelper {
    /**
     * Bind member accessors curry this, instead of applying immediately, so curry each variant:
     *
     *     fn (This, ...): ... -> fn (This): fn (...): ...
     */
    fun curry(
        thisType: StaticType,
        variantType: StaticType,
    ): FunctionType = MkType.fn(
        emptyList(),
        listOf(thisType),
        restValuesFormal = null,
        returnType = mapFunctionTypesThroughIntersection(variantType) { ft ->
            if (ft.valueFormals.isNotEmpty()) {
                MkType.fnDetails(
                    typeFormals = ft.typeFormals,
                    valueFormals = ft.valueFormals.subListToEnd(1),
                    restValuesFormal = ft.restValuesFormal,
                    returnType = ft.returnType,
                )
            } else {
                InvalidType
            }
        },
    )

    /** Reverses [curry] */
    fun uncurry(curried: StaticType): StaticType =
        mapFunctionTypesThroughIntersection(
            curried,
        ) uncurryOne@{ curriedFnType ->
            if (
                curriedFnType.typeFormals.isEmpty() &&
                curriedFnType.valueFormals.size == 1 &&
                curriedFnType.restValuesFormal == null
            ) {
                val rt = curriedFnType.returnType
                if (rt is FunctionType) {
                    return@uncurryOne MkType.fnDetails(
                        typeFormals = rt.typeFormals,
                        valueFormals = curriedFnType.valueFormals + rt.valueFormals,
                        restValuesFormal = rt.restValuesFormal,
                        returnType = rt.returnType,
                    )
                }
            }
            InvalidType
        }
}
