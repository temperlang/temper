package lang.temper.common

import kotlin.js.Date

actual fun getPrngSeed(): Long = Date.now().toLong()

actual fun getConfigFromEnvironment(varName: String): String? {
    val process = js("(typeof process === 'object' && process && process.env) || null")
    if (process != null) {
        val environmentValue = process[varName]
        if (environmentValue is String) {
            return environmentValue
        }
    }
    return null
}
