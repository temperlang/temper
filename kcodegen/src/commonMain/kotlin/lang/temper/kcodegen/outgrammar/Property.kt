package lang.temper.kcodegen.outgrammar

import lang.temper.log.Position
import lang.temper.log.Positioned

internal class Property(
    override val pos: Position,
    val propertyName: Id,
    var propertyType: PropertyType?,
) : Commentable, Positioned {
    var isOverride: Boolean = false
    override var docComment: DocComment? = null

    /** Inferred later from syntax declarations */
    var propertyCount: PropertyCount? = null

    fun copy(
        pos: Position = this.pos,
        propertyName: Id = this.propertyName,
        propertyType: PropertyType? = this.propertyType,
        isOverride: Boolean = this.isOverride,
        docComment: DocComment? = this.docComment,
        propertyCount: PropertyCount? = this.propertyCount,
    ): Property {
        val copy = Property(pos, propertyName, propertyType)
        copy.isOverride = isOverride
        copy.docComment = docComment
        copy.propertyCount = propertyCount
        return copy
    }

    /**
     * Non-null if we know the type and count.
     *
     * We may not know either the type and count until we've done inference for the containing
     * type and its super-types.
     */
    val propertyTypeAndCount: PropertyTypeAndCount?
        get() {
            val t = propertyType
            val c = propertyCount
            return if (t != null && c != null) {
                PropertyTypeAndCount(t, c)
            } else {
                null
            }
        }
}
