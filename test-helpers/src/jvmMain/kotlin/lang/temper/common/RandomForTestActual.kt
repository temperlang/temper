package lang.temper.common

actual fun getPrngSeed(): Long = System.currentTimeMillis()

actual fun getConfigFromEnvironment(varName: String): String? = System.getenv(varName)
