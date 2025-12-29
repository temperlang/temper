package lang.temper.cli

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal fun Path.highestAncestorWithAnyOf(names: Iterable<String>): Path? =
    normalize().parent?.highestAncestorWithAnyOf(names) ?: if (names.any { resolve(it).exists() }) {
        this
    } else {
        null
    }

internal fun Path.lowestAncestorWithAnyOf(
    names: Iterable<String>,
): Path? =
    lowestAncestorAndMemberWithAnyOf(names, allowDir = true, allowFile = true)?.first

internal fun Path.lowestAncestorAndMemberWithAnyOf(
    names: Iterable<String>,
    allowDir: Boolean = false,
    allowFile: Boolean = false,
): Pair<Path, String>? {
    var p = normalize()
    while (true) {
        val match = when {
            allowDir && allowFile -> names.firstOrNull { p.resolve(it).exists() }
            allowDir -> names.firstOrNull { p.resolve(it).isDirectory() }
            allowFile -> names.firstOrNull { p.resolve(it).isRegularFile() }
            else -> return null
        }
        if (match != null) {
            return p to match
        }
        val parent = p.parent
        if (parent == null || p == parent) { return null }
        p = parent
    }
}
