package lang.temper.be.java

import lang.temper.be.tmpl.TmpL
import kotlin.math.min

/**
 * When a Java class overrides a generic super-types method it might substitute
 * a primitive type for a type parameter.
 *
 *     // Temper
 *     interface I<T> {
 *       public f(x:T): Void;
 *     }
 *     class C extends I<Boolean> {
 *       public f(x: Boolean): Void {
 *         ...
 *       }
 *     }
 *
 * Naïvely we might translate `C.f`'s parameter `x: Boolean` to `boolean x` as in
 *
 *     class C implements I<Boolean> {
 *       public void f(boolean x) {
 *         ...
 *       }
 *     }
 *
 * Note the extends clause mentions upper-case *Boolean* but the method parameter
 * mentions lower-case *boolean*.
 *
 * The bits in this class are true when we need to adjust primitive types in an
 * overridden method to the boxed equivalent type.
 *
 *     class C implements I<Boolean> {
 *       public void f(Boolean x) { // ADJUSTED
 *         f(x.booleanValue());
 *       }
 *       private void f(boolean x) {
 *         ...
 *       }
 *     }
 *
 * Note that the translated body still operates in a context where `x` is
 * a primitive boolean, to avoid any semantic box/unboxed type confusion.
 */
internal data class BoxedTypeAdjustments(
    val parametersToAdjust: BooleanArray,
    val adjustReturn: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is BoxedTypeAdjustments &&
            adjustReturn == other.adjustReturn &&
            parametersToAdjust.contentEquals(other.parametersToAdjust)

    override fun hashCode(): Int =
        0x3de94c7a xor (parametersToAdjust.contentHashCode() + 31 * adjustReturn.hashCode())
}

internal fun findJavaParametersThatNeedAdjustmentToBoxedType(
    names: JavaNames,
    parameters: List<Java.MethodParameter>,
    returnType: Java.ResultType,
    memberOverrides: List<TmpL.SuperTypeMethod>,
): BoxedTypeAdjustments? {
    val parametersToAdjust = BooleanArray(parameters.size)
    var adjustReturn = false
    var adjustmentNeeded = false
    for (overriddenMethod in memberOverrides) {
        val overriddenParameters = overriddenMethod.parameters.formals
        for (i in 0 until min(parameters.size, overriddenParameters.size - 1)) {
            val op = overriddenParameters[i + 1]
            val p = parameters[i]
            if (op.isOptional || p !is Java.FormalParameter) { continue } // Implicitly nullable
            if (p.type is Java.PrimitiveType) {
                val opType = JavaType.fromTmpL(op.type, names)
                if (opType is ReferenceType) {
                    parametersToAdjust[i] = true
                    adjustmentNeeded = true
                }
            }
        }
        if (returnType is Java.PrimitiveType) {
            val oReturn = JavaType.fromTmpL(overriddenMethod.returnType, names)
            if (oReturn is ReferenceType) {
                adjustReturn = true
                adjustmentNeeded = true
            }
        }
    }
    return if (adjustmentNeeded) {
        BoxedTypeAdjustments(
            parametersToAdjust = parametersToAdjust, adjustReturn = adjustReturn,
        )
    } else {
        null
    }
}

fun unboxToPrimitive(e: Java.Expression, t: Primitive): Java.Expression {
    val methodName = "${t.primitiveName}Value" // JavaBoolean -> booleanValue
    return e.method(methodName)
}
