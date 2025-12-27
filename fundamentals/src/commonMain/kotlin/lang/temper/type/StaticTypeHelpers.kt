package lang.temper.type

import lang.temper.common.mapFirst

/**
 * Given an inferred type that is probably a super-type of [T],
 * but which could be an intersection type, look through any
 * intersection type to get at the part we're interested in.
 *
 * @param [asFn] should return its input if it matches
 *     [T] and some other conditions.
 * @return *asFn(type)* if it is non-null or *asFn(x)* if
 *     type is an [AndType] of which *x* is the first member
 *     for which *asFn* returns non-null.
 */
fun <T : StaticType> typeOrAndTypeMemberMatching(
    type: StaticType,
    asFn: (StaticType) -> T?,
): T? =
    asFn(type)
        ?: (type as? AndType)?.members?.mapFirst { typeOrAndTypeMemberMatching(it, asFn) }
