package lang.temper.common

import kotlin.system.getTimeMillis
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual fun getPrngSeed(): Long {
    return getTimeMillis()
}

actual fun getConfigFromEnvironment(varName: String): String? {
    return getenv(varName)?.toKString()
}
