package lang.temper.type

import lang.temper.type2.Descriptor

/**
 * Information about how a member defined in a sub-type overrides a member in one of its super types.
 *
 * @see [VisibleMemberShape.overriddenMembers]
 */
data class MemberOverride(
    /**
     * The member being overridden.
     * The overriding member is the one that points to this via its [VisibleMemberShape.overriddenMembers].
     */
    val superTypeMember: VisibleMemberShape,
    /**
     * The [VisibleMemberShape.staticType] of [superTypeMember] translated into
     * the context of the sub-type whose member is overridden.
     *
     * This involves mapping the super-type's type parameters to the bindings used
     * to extend it in the sub-type.
     *
     * If the member is a method, it also involves mapping the super-type method's
     * [type formals][lang.temper.value.FnParts.typeFormals] to the corresponding
     * formals for the sub-type method.
     */
    val superTypeMemberTypeInSubTypeContext: StaticType,
)

/**
 * Information about how a member defined in a sub-type overrides a member in one of its super types.
 *
 * @see [VisibleMemberShape.overriddenMembers]
 */
data class MemberOverride2(
    /**
     * The member being overridden.
     * The overriding member is the one that points to this via its [VisibleMemberShape.overriddenMembers].
     */
    val superTypeMember: VisibleMemberShape,
    /**
     * The [VisibleMemberShape.staticType] of [superTypeMember] translated into
     * the context of the sub-type whose member is overridden.
     *
     * This involves mapping the super-type's type parameters to the bindings used
     * to extend it in the sub-type.
     *
     * If the member is a method, it also involves mapping the super-type method's
     * [type formals][lang.temper.value.FnParts.typeFormals] to the corresponding
     * formals for the sub-type method.
     */
    val superTypeMemberTypeInSubTypeContext: Descriptor,
)
