package lang.temper.fs

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Kotlin/Common version of resource auto-closing `.use`. */
@OptIn(ExperimentalContracts::class)
inline fun <SELF : AutoCloseable, T> SELF.use(block: (SELF) -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block(this)
    } finally {
        close()
    }
}
