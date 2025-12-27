package lang.temper.be.java

import lang.temper.fs.NativePath
import xmlparser.XmlParser
import java.nio.file.Files
import java.nio.file.Path

actual val userMavenToolchains: NativePath by lazy {
    Path.of(System.getProperty("user.home"), ".m2/toolchains.xml")
}

/**
 * <?xml version='1.0' encoding='UTF8'?>
 * <toolchains>
 *   <toolchain>
 *     <type>jdk</type>
 *     <provides>
 *       <version>17</version>
 *       <vendor>java</vendor>
 *     </provides>
 *     <configuration>
 *       <jdkHome>/home/ben/.sdkman/candidates/java/current</jdkHome>
 *     </configuration>
 *   </toolchain>
 * </toolchains>
 */

val toolchains: Map<Int, List<Pair<String, NativePath>>> by lazy {
    val found = mutableMapOf<Int, MutableMap<String, Path>>()
    if (Files.exists(userMavenToolchains)) {
        val doc = XmlParser().fromXml(userMavenToolchains)
        for (toolchain in doc.getElementsByTagName("toolchain")) {
            if (toolchain.findChildForName("type", null)?.text != "jdk") continue
            val version = toolchain.findChildForName("provides", null)
                ?.findChildForName("version", null)?.text ?: continue
            val majorVersion = parseJavaMajorVersion(version) ?: continue
            val jdkHome = toolchain.findChildForName("configuration", null)
                ?.findChildForName("jdkHome", null)?.text ?: continue
            found.getOrPut(majorVersion) { mutableMapOf() }[version] = Path.of(jdkHome)
        }
    }
    found.mapValues { (major, map) ->
        val exact = stringifyJavaMajorVersion(major)
        map.entries.sortedBy { (version, _) ->
            version !in exact
        }.map { it.toPair() }
    }
}

actual fun toolchainsForVersion(major: Int): List<Pair<String, NativePath>> = toolchains.getOrDefault(major, listOf())
