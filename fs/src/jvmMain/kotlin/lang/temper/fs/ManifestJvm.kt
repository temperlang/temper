package lang.temper.fs

import java.util.jar.Attributes
import java.util.jar.Manifest
import kotlin.reflect.KClass

actual fun KClass<*>.loadManifest(): ManifestData {
    val cl = this.java.classLoader
    val rsrcs = cl.getResources("META-INF/MANIFEST.MF")
    val main = mutableMapOf<String, String>()
    val other = mutableMapOf<String, MutableMap<String, String>>()
    while (rsrcs.hasMoreElements()) {
        val url = rsrcs.nextElement()
        url.openStream().use {
            val man = Manifest(it)
            saveManifestAttrs(man.mainAttributes, tgt = main)
            man.entries.forEach { (section, attrs) ->
                if (section != null && attrs != null) {
                    saveManifestAttrs(attrs, other.getOrPut(section, ::mutableMapOf))
                }
            }
        }
    }
    return ManifestData(main, other)
}

private fun saveManifestAttrs(attrs: Attributes?, tgt: MutableMap<String, String>) =
    attrs?.entries.orEmpty().forEach { (k, v) ->
        tgt[k.toString()] = v.toString()
    }
