package lang.temper.be

import lang.temper.ast.OutData
import lang.temper.ast.OutFormattable
import lang.temper.log.FilePositions

abstract class BaseOutData<DATA : OutData<DATA>> : OutData<DATA> {
    override fun toString() = toString(filePositions = FilePositions.nil)

    override val childCount: Int
        get() {
            var n = 0
            for (p in childMemberRelationships.accessors) {
                n += when (val value = p(this)) {
                    null -> 0
                    is List<*> -> value.size
                    is OutFormattable<*> -> 1
                    else -> throw ClassCastException("$value")
                }
            }
            return n
        }
}
