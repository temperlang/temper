package lang.temper.type2

import lang.temper.common.firstOrNullAs
import lang.temper.common.subListToEnd
import lang.temper.name.Symbol
import lang.temper.type.MemberShape
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeDefinition
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.invalidTypeDefinition
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type.WellKnownTypes.resultTypeDefinition
import lang.temper.type.WellKnownTypes.voidTypeDefinition
import lang.temper.type2.Nullity.NonNull
import lang.temper.value.functionalInterfaceSymbol

/**
 * Allows using `when` with distinct categories of [Type2]s.
 */
enum class TypeCategory {
    /**
     * A special return type which bundles a normal return type with
     * some abnormal result types.
     *
     * A function call's result may be a result type in which case
     * the type of the call expression is the normal return type or
     * [Void] and any containing `orelse` clause or function might
     * receive the abnormal result types.
     */
    Result,

    /**
     * The special return type which indicates no normal result.
     */
    Void,

    /**
     * A type that represents a functional interface.
     */
    Functional,

    /**
     * The special *Never* type which has a type parameter.
     * This is bottom-like in that there is no value of it, but languages
     * with no mentionable bottom type can translate to its type parameter.
     *
     * `Never<Never<T>>` is silly and should be a static error.
     * `<T extends Never<T>>` is syntactically correct, but probably
     * untranslatable and should be a static error.
     */
    Never,

    /**
     * A type that represents a failure to derive a usable type and which
     * should not be introduced into the Frontend IR without writing out
     * an error message.
     */
    Invalid,

    /** All user defined types. */
    Other,
}

private val defToCategory = mapOf(
    resultTypeDefinition to TypeCategory.Result,
    voidTypeDefinition to TypeCategory.Void,
    neverTypeDefinition to TypeCategory.Never,
    invalidTypeDefinition to TypeCategory.Invalid,
)

val TypeDefinition.typeCategory: TypeCategory
    get() =
        defToCategory[this]
            ?: if (functionalInterfaceSymbol in metadata) {
                TypeCategory.Functional
            } else {
                TypeCategory.Other
            }

val Type2.typeCategoryOrNullIfMalformed: TypeCategory?
    get() {
        val defn = this.definition
        val n = this.bindings.size
        val c = defn.typeCategory
        val wellFormed = when (c) {
            TypeCategory.Result -> n >= 1 && nullity == NonNull
            TypeCategory.Void -> n == 0 && nullity == NonNull
            else -> n == defn.formals.size
        }
        return if (wellFormed) { c } else { null }
    }

/**
 * A pre-allocated throwable used to transfer control in [withType].
 */
@Suppress("ObjectInheritsException")
object JumpToFallbackForTypeSwitch : Throwable() {
    @Suppress("unused") // Throwable objects need to be deserializable
    private fun readResolve(): Any = JumpToFallbackForTypeSwitch
}

/**
 * Switches based on [t]'s [category][TypeCategory] and calls one of the provided handlers.
 *
 * It the type is in a category but does not pass well-formedness checks, then calls [malformed].
 * If [invalid] is not specified and [t] is [WellKnownTypes.invalidType2], also calls [malformed].
 *
 * If a category handler is not specified, it calls [fallback].
 */
inline fun <T> withType(
    t: Type2,
    fallback: (t: Type2) -> T,
    // The fallback must always be specified.  We have a zero-allocation throwable that we use to jump
    // to fallback in the default case where one of the optional ones below is unspecified.
    result: (pass: Type2, results: List<Type2>, t: DefinedType) -> T = { _, _, _ -> throw JumpToFallbackForTypeSwitch },
    voidType: (t: DefinedType) -> T = { _ -> throw JumpToFallbackForTypeSwitch },
    fn: (nullity: Nullity, sig: Signature2, t: DefinedType) -> T = { _, _, _ -> throw JumpToFallbackForTypeSwitch },
    never: (butIf: Type2, nullity: Nullity, t: DefinedType) -> T = { _, _, _ -> throw JumpToFallbackForTypeSwitch },
    other: (nullity: Nullity, t: Type2) -> T = { _, _ -> throw JumpToFallbackForTypeSwitch },
    malformed: (c: TypeCategory, t: Type2) -> T = { _, _ -> throw JumpToFallbackForTypeSwitch },
    invalid: (t: DefinedType) -> T = { _ -> throw JumpToFallbackForTypeSwitch },
): T =
    try {
        // The bulk of the classification logic is not inlined
        when (t.typeCategoryOrNullIfMalformed) {
            null -> malformed(t.definition.typeCategory, t)
            TypeCategory.Result ->
                result((t as DefinedType).bindings.first(), t.bindings.subListToEnd(1), t)

            TypeCategory.Void -> voidType(t as DefinedType)

            TypeCategory.Functional -> when (val sig = sigForFunInterfaceType(t as DefinedType)) {
                null -> malformed(TypeCategory.Functional, t)
                else -> fn(t.nullity, sig, t)
            }

            TypeCategory.Never ->
                never((t as DefinedType).bindings.first(), t.nullity, t)

            TypeCategory.Other -> other(t.nullity, t)

            TypeCategory.Invalid -> try {
                invalid(t as DefinedType)
            } catch (_: JumpToFallbackForTypeSwitch) {
                malformed(t.definition.typeCategory, t)
            }
        }
    } catch (_: JumpToFallbackForTypeSwitch) {
        fallback(t)
    }

/**
 * Helper for [withType].
 * Assumes [t]'s definition is a functional interface type and returns the corresponding signature.
 * If the underlying definition is not complete, for example, it lacks a method with [applyDotName],
 * or there is a mismatch in number of type parameters, returns null.
 */
fun sigForFunInterfaceType(t: DefinedType): Signature2? {
    val (fnTypeShape, bindings) = t
    val formals = fnTypeShape.formals
    val applyMethodShape = fnTypeShape.membersMatching(applyDotName)
        .firstOrNullAs<MemberShape, MethodShape> {
            it.methodKind == MethodKind.Normal && it.visibility == Visibility.Public
        }
    val iSig = applyMethodShape?.descriptor
    return if (iSig != null && t.bindings.size == formals.size) {
        iSig.mapType(
            buildMap {
                for (i in formals.indices) {
                    this[formals[i]] = bindings[i]
                }
            },
        )
    } else {
        null
    }
}

/** True for `Void`, `Never<Void>`, `Result<Void, ...>` */
val Type2?.isVoidLike get(): Boolean = when (this) {
    is TypeParamRef? -> false
    is DefinedType -> when (definition) {
        voidTypeDefinition -> true
        neverTypeDefinition -> this.bindings.size == 1 && this.bindings.first().isVoidLike
        resultTypeDefinition -> this.bindings.firstOrNull().isVoidLike
        else -> false
    }
}

/** The dot name of the functional interface "apply" method. */
val applyDotName = Symbol("apply")

fun passTypeOf(t: Type2) = withType(
    t,
    result = { pass, _, _ -> pass },
    fallback = { it },
)
