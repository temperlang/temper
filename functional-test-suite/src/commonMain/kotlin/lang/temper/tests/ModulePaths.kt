package lang.temper.tests

import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.plus

/**
 * Some common paths used to identify resources in this module.
 */
object ModulePaths {
    /** These are paths within the source tree. */
    private val commonMainPath = dirPath("functional-test-suite", "src", "commonMain")

    /** The base path the `.temper` and `.temper.md` test files lives under. */
    val testResourcesPath: FilePath = commonMainPath + dirPath("resources")

    /** The base path the Kotlin test objects live under. */
    val testKotlinPath: FilePath = commonMainPath + dirPath("kotlin", "lang", "temper", "tests")

    /** The default root path for a constructed temper library; simple to avoid customizing for backends. */
    val defaultProjectRoot = dirPath("work")
}
