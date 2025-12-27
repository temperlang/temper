package lang.temper.name

import lang.temper.common.AtomicCounter

/**
 * A naming context may declare names which are distinct from those declared in others, and may
 * transition from using [ParsedName]s to using [ResolvedName]s at the end of the syntax stage.
 */
abstract class NamingContext(
    private val counter: AtomicCounter = AtomicCounter(),
) {
    abstract val loc: ModuleLocation
    protected fun unusedNameUid(): Int {
        val uid = counter.getAndIncrement()
        check(uid in validUidRange)
        return uid.toInt()
    }
    fun peekUnusedNameUid(): Int {
        val uid = counter.get()
        check(uid in validUidRange)
        return uid.toInt()
    }

    /** Returns the underlying counter so that two contexts can return guaranteed distinct numbers */
    fun adoptCounter(): AtomicCounter = counter

    open val locationDiagnostic: String
        get() = loc.diagnostic

    companion object {
        internal fun newNameUid(namingContext: NamingContext) = namingContext.unusedNameUid()
    }
}

private val validUidRange = 0..Int.MAX_VALUE.toLong()
