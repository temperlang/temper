package lang.temper.supportedBackends

import lang.temper.be.Backend
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.name.BackendId

internal actual fun getTemperPluginListJsonFromEnvironment(): String? =
    System.getenv("TEMPER_PLUGINS")

internal actual fun backendInfoFromJvmClassName(
    jvmClassName: String,
): RResult<Pair<BackendId, BackendInfo>, IllegalArgumentException> {
    val clazz: Class<*> = try {
        Class.forName(jvmClassName)
    } catch (e: ClassNotFoundException) {
        return RFailure(IllegalArgumentException("Failed to load class `$jvmClassName`", e))
    }

    val pluginBackendIdAnnotClass = Backend.PluginBackendId::class.java
    val pluginIdAnnot = clazz.getAnnotation(pluginBackendIdAnnotClass)
    if (pluginIdAnnot == null) {
        // In the future, we might have more varieties of plugins, but for now, require it.
        return RFailure(
            IllegalArgumentException(
                "Plugin $clazz is missing expected annotation $pluginBackendIdAnnotClass",
            ),
        )
    }
    val backendId = RResult.of(IllegalArgumentException::class) {
        BackendId(pluginIdAnnot.backendId)
    }
    when (backendId) {
        is RSuccess -> {}
        is RFailure -> return backendId
    }
    if (!Backend.Factory::class.java.isAssignableFrom(clazz)) {
        return RFailure(
            IllegalArgumentException(
                "Plugin $clazz is not a subtype of Backend.Factory",
            ),
        )
    }
    var isSupported = false
    var isDefaultSupported = false
    var isTested = false
    val supportLevel: Backend.BackendSupportLevel? =
        clazz.getAnnotation(Backend.BackendSupportLevel::class.java)
    if (supportLevel != null) {
        isSupported = supportLevel.isSupported
        isDefaultSupported = supportLevel.isDefaultSupported
        isTested = supportLevel.isTested
    }
    return RSuccess(
        backendId.result to BackendInfo(
            lazy {
                @Suppress("DEPRECATION") // newInstance for plugin loading
                val instance = clazz.kotlin.objectInstance // Object instance
                    ?: clazz.newInstance()
                instance as Backend.Factory<*>
            },
            isDefaultSupported = isDefaultSupported,
            isSupported = isSupported,
            isTested = isTested,
        ),
    )
}
