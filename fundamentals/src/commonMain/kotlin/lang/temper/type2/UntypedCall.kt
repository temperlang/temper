package lang.temper.type2

import lang.temper.log.Position
import lang.temper.type.StaticType
import lang.temper.type.TypeFormal
import lang.temper.value.CallTree
import lang.temper.value.TypeReasonElement

/** Information about a call that we can use to resolve type parameter bounds. */
data class UntypedCall(
    val callPosition: Position,
    val calleeVariants: List<Callee>,
    val explicitActuals: List<Pair<StaticType, Position>>?,
    /** For each input, its inferred type, or if it is delayed, an inference variable. */
    val inputBounds: List<InputBound>,
    val hasTrailingBlock: Boolean,
    /**
     * null or a type for the context in which the call is used.
     * For example, it could be the type from a declaration initialized with the result of the call.
     */
    val contextType: Type2?,
    /**
     * If the result is used in another typed call in lieu of an inferred type for that parameter,
     * then the inference variable that will be used there.
     */
    val passVar: TypeVar?,
    /**
     * The tree that should receive inferences.
     */
    val destination: CallTree,
) {
    var resultType: StaticType? = null
    var bindings: Map<TypeFormal, StaticType>? = null
    var explanations: List<TypeReasonElement>? = null
    var chosenCallee: Int? = null
}
