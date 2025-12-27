package lang.temper.supportedBackends

import lang.temper.be.Backend
import lang.temper.common.Log
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.LanguageLabel
import lang.temper.name.interpBackendId

/**
 * This is the master list of known backends. Each backend is registered by some object (convention is the companion)
 * that implements the `Backend.Factory` interface. That provides a factory method and metadata.
 *
 * TODO(bps): allow a supplementary list of backends for tests or plugins
 *
 * <!-- snippet: env/backend-factories -->
 * # Backend factory configuration
 *
 * Some backends may be under local development or proprietary.
 * Environment variables do not affect how files are translated but they can affect
 * which translations are produced.
 *
 * ```sh
 * TEMPER_PLUGINS='["lang.temper.be.my_backend.MyBackend$Factory"]'
 * ```
 *
 * The *TEMPER_PLUGINS* environment variable, if specified, should be a JSON string list,
 * it may specify additional Temper compiler plugins.
 *
 * There is a default list of [snippet/backends/supported] which is used when no environment
 * variable is set.
 * If the first string is `"+"` or `"-"`, the two "particles", then that default list is
 * prepended and the other strings are either added (`+`) or removed (`-`) depending on
 * whether the most recently seen particle.
 *
 * ```sh
 * TEMPER_PLUGINS='["+", "lang.temper.be.my_backend.MyBackend$Factory", "-", "lang.temper.be.js.JsBackend$Factory"]'
 * ```
 *
 * That plugin list includes particles, so the first class name is added, and the
 * JS backend class name is removed from the default list.
 *
 * Besides particles, each string is a JVM class name.
 * If that class has a [Backend.PluginBackendId] annotation, it specifies the backend id
 * that may be used to invoke the backend via the `-b` flag or from within the Temper REPL.
 * The [Backend.BackendSupportLevel] annotation notes the level of support.
 *
 * TODO: If there's no environment variable, look for user preference directories, esp. for
 * `temper repl` usage by backend developers.
 *
 * <!-- snippet: backends/experimental -->
 * # Experimental Backends
 *
 * ⎀ backend/cpp
 */
private val defaultPluginListJson = lazy {
    // A gradle task derives this resource from the basic-plugin-list.json and any
    // extras from proprietary backends that are git sub-moduled in.
    SupportedBackends::class.java.getResource("plugin-list.json")!!.readText(Charsets.UTF_8)
}

private fun backendsFromJson(
    pluginListJson: String,
    /** Used to load the backends that are added to or subtracted when the plugin list has particles */
    baseline: Lazy<String> = defaultPluginListJson,
): SupportedBackends {
    console.groupSoft("Parsing Temper plugin list", Log.Fine) {
        return when (val r = JsonValue.parse(pluginListJson)) {
            is RSuccess -> backendsFromJson(r.result, baseline)
            is RFailure -> {
                console.group("Error in Temper plugin list JSON", Log.Error) {
                    console.error(pluginListJson)
                }
                console.error(r.failure)
                return SupportedBackends(emptyMap())
            }
        }
    }
}

private fun backendsFromJson(
    pluginList: JsonValue,
    baseline: Lazy<String>,
): SupportedBackends {
    fun jsonArrayToStrings(jsonValue: JsonValue): List<String> {
        require(jsonValue is JsonArray) {
            "Expected JSON array of JVM class names for plugins, not ${jsonValue.toJsonString()}"
        }
        return jsonValue.elements.mapNotNull { element ->
            (element as? JsonString)?.content.also {
                if (it == null) {
                    console.error(
                        "Expected string JVM class name in Temper plugin list not ${
                            element::class.simpleName
                        }: ${element.toJsonString()}",
                    )
                }
            }
        }
    }

    var strings = jsonArrayToStrings(pluginList)

    // If the particles "+" or "-" occur, then prepend the baseline.
    // If they appear, one should appear in first position.  Otherwise,
    val allowParticles = when (strings.firstOrNull()) {
        "+", "-" -> true
        else -> false
    }

    if (allowParticles) {
        when (val r = JsonValue.parse(baseline.value)) {
            is RSuccess -> {
                val accumulatedStrings = jsonArrayToStrings(r.result).toMutableSet()
                var deletion = false
                for (string in strings) {
                    when (string) {
                        "+" -> deletion = false
                        "-" -> deletion = true
                        else -> if (deletion) {
                            accumulatedStrings.remove(string)
                        } else {
                            accumulatedStrings.add(string)
                        }
                    }
                }
                strings = accumulatedStrings.toList()
            }
            is RFailure -> {
                console.group("Bad baselines plugin list", Log.Error) {
                    console.error(baseline.value)
                }
                console.error(r.failure)
            }
        }
    }

    strings = strings.toSet().toList()

    val backends = buildMap {
        for (jvmClassName in strings) {
            when (val r = backendInfoFromJvmClassName(jvmClassName)) {
                is RSuccess -> {
                    val (backendId, backendInfo) = r.result
                    if (backendId in this) {
                        console.warn("Backend ID $backendId from $jvmClassName collides with earlier plugin")
                    } else {
                        this[backendId] = backendInfo
                    }
                }
                is RFailure -> {
                    console.group("Error loading Temper plugin $jvmClassName", Log.Error) {
                        console.error(r.failure)
                    }
                }
            }
        }
    }
    return SupportedBackends(backends)
}

internal data class BackendInfo(
    val backend: Lazy<Backend.Factory<*>>,
    val isDefaultSupported: Boolean,
    val isSupported: Boolean,
    val isTested: Boolean,
)

private data class SupportedBackends(
    val factories: Map<BackendId, BackendInfo>,
)

private val supportedBackendsGlobal = backendsFromJson(
    getTemperPluginListJsonFromEnvironment() ?: defaultPluginListJson.value,
)

fun lookupFactory(backendId: BackendId): Backend.Factory<*>? =
    supportedBackendsGlobal.factories[backendId]?.backend?.value

/** @return true iff the backend is known. */
fun isSupported(backendId: BackendId): Boolean =
    supportedBackendsGlobal.factories[backendId]?.isSupported == true

val availableBackends: Iterable<BackendId> get() = supportedBackendsGlobal.factories.keys

val supportedBackends: List<BackendId> = buildList {
    supportedBackendsGlobal.factories.mapNotNullTo(this) { (backendId, info) ->
        if (info.isSupported) {
            backendId
        } else {
            null
        }
    }
    sort()
}

val defaultSupportedBackendList: List<BackendId> = buildList {
    supportedBackendsGlobal.factories.mapNotNullTo(this) { (backendId, info) ->
        if (info.isDefaultSupported) {
            backendId
        } else {
            null
        }
    }
    sort()
}

/** Used to create the functional test matrix. */
val testedBackends: List<BackendId> = buildList {
    add(interpBackendId)
    for ((backendId, info) in supportedBackendsGlobal.factories) {
        if (info.isTested) {
            add(backendId)
        }
    }
    sort()
}

val defaultBackend: Lazy<Backend.Factory<*>> =
    supportedBackendsGlobal.factories.getValue(BackendId("js")).backend

/** Find the language generated by a supported backend. */
fun BackendId.lookupLanguage(): LanguageLabel? = lookupMeta()?.languageLabel

/** Find the metadata for a supported backend. */
fun BackendId.lookupMeta(): BackendMeta? = lookupFactory(this)?.backendMeta

internal expect fun getTemperPluginListJsonFromEnvironment(): String?

internal expect fun backendInfoFromJvmClassName(
    jvmClassName: String,
): RResult<Pair<BackendId, BackendInfo>, IllegalArgumentException>
