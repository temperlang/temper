package lang.temper.be.tmpl

import lang.temper.be.Backend
import lang.temper.be.MetadataKey
import lang.temper.be.MetadataKeyFactory
import lang.temper.name.BackendId
import lang.temper.name.ResolvedName
import lang.temper.type2.Signature2

typealias MemberMap<T> = Map<ResolvedName, T>

/**
 * Changes to an instance method signature from those that would result from
 * naïvely translating its argument and result types.
 *
 * Backends may subclass this type to add additional information as each
 * backend's translator is the only consumer of this information.
 *
 * These are extracted from [TmpL.InstanceMethod] declarations and shared
 * so that sub-type methods can meet expectations imposed by super-type
 * method translation decisions and so that method calls can craft input
 * lists that fit method signatures.
 */
open class SignatureAdjustments(
    /**
     * The function type that derives from naïvely translating argument and result types as described above.
     * Note that input 0 is `this`.
     */
    val unadjustedType: Signature2,
    /**
     * An element is set here if the corresponding parameter needs adjustment.
     * A caller to this method might zip its parameters with these to adapt input expressions
     * to the requirements of the translated function/method.
     *
     * As with the types above, index 0 is the `this` parameter which probably cannot be adjusted.
     */
    val inputAdjustments: List<SignatureAdjustment?>,
    /** Whether adjustments need to be made to the return type or before using the result. */
    val outputAdjustment: SignatureAdjustment?,
) {
    /**
     * A backend-specific representation of the kind of adjustment that needs to be made.
     *
     * All of these should be created by the backend, so a backend should be able to define
     *
     *     sealed interface MyBackendSignatureAdjustment : SignatureAdjustment
     *
     * Then the backend can cast any [SignatureAdjustment] to that type and use a `when`
     * clause to adapt definitions and expressions.
     */
    interface SignatureAdjustment

    object KeyFactory : MetadataKeyFactory<MemberMap<SignatureAdjustments>> {
        /** For all backends, allows retrieving signature adjustments by method name. */
        fun <BACKEND : Backend<BACKEND>> acquireKey(backendId: BackendId):
            MetadataKey<BACKEND, MemberMap<SignatureAdjustments>> =
            SignatureAdjustmentsMetadataKey(backendId)

        /** For all backends, allows retrieving signature adjustments by method name. */
        override fun <BACKEND : Backend<BACKEND>> acquireKey(
            backend: Backend<BACKEND>,
        ): MetadataKey<BACKEND, MemberMap<SignatureAdjustments>> =
            acquireKey(backend.backendId)
    }

    override fun toString() =
        "SignatureAdjustments(in ${inputAdjustments}, out ${outputAdjustment ?: ""})"
}

private class SignatureAdjustmentsMetadataKey<BACKEND : Backend<BACKEND>>(
    override val backendId: BackendId,
) : MetadataKey<BACKEND, MemberMap<SignatureAdjustments>>() {
    override fun equals(other: Any?): Boolean =
        other is SignatureAdjustmentsMetadataKey<*> && this.backendId == other.backendId

    override fun hashCode(): Int = backendId.hashCode()

    override fun toString(): String = "SignatureAdjustmentsMetadataKey($backendId)"
}
