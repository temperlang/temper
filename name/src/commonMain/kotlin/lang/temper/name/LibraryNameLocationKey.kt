package lang.temper.name

import lang.temper.log.CodeLocationKey

/**
 * Allows looking up library names by library root directory.
 */
object LibraryNameLocationKey : CodeLocationKey<DashedIdentifier> {
    override fun cast(x: Any) = x as DashedIdentifier
}
