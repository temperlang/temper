package lang.temper.be

import lang.temper.format.TokenSerializable

/**
 * May be used to define a relationship between a Temper name and a target
 * language identifier.
 *
 * This extends [TokenSerializable] to allow rendering to a type name
 * recognizable in the debug view of a [TmpL module][lang.temper.be.tmpl.TmpL.Module].
 */
interface TargetLanguageName : TokenSerializable
