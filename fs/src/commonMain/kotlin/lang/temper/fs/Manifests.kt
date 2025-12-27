package lang.temper.fs

import kotlin.reflect.KClass

object Manifests {
    private val data: MutableMap<KClass<*>, ManifestData> = mutableMapOf()

    @Synchronized fun manifestFor(klass: KClass<*>) =
        data.getOrPut(klass) {
            klass.loadManifest()
        }
}

/**
 * Contains manifest data from the relevant `META_INF/MANIFEST.MF`.
 *
 * Various properties look up standard entries in the manifest.
 */
data class ManifestData(
    val main: Map<String, String>,
    val other: Map<String, Map<String, String>>,
) {
    /** `cli/build.gradle` defines this to be "Temper" */
    val implementationTitle: String? get() = main["Implementation-Title"]

    /** `cli/build.gradle` defines this to be "Temper Systems" */
    val implementationVendor: String? get() = main["Implementation-Vendor"]

    /** `cli/build.gradle` defines this to be the version set by git */
    val implementationVersion: String? get() = main["Implementation-Version"]

    /** Class run when launched as `java -jar foo.jar` */
    val mainClass: String? get() = main["Main-Class"]
}

expect fun KClass<*>.loadManifest(): ManifestData
