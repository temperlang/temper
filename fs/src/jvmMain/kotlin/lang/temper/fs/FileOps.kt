package lang.temper.fs

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.toList

/** Recursively copy *contents* of [from] into [to]. */
fun copyRecursive(from: Path, to: Path) {
    check(from.isDirectory()) { from.toString() }
    if (!to.exists()) {
        Files.createDirectories(to)
    }
    check(to.isDirectory()) { to.toString() }

    // Do the recursive tree copy
    Files.walk(from).use { files ->
        files.forEach { source ->
            // Use `toString` here in case they come from different file systems.
            val destination = to.resolve(source.relativeTo(from).toString())
            if (source.isDirectory() && destination.isDirectory()) {
                // done
            } else {
                Files.copy(source, destination)
            }
        }
    }
}

fun listKidNames(path: Path): List<String> = Files.list(path).use { paths -> paths.map { it.name }.toList() }

fun removeDirRecursive(path: Path) {
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        // Check against type other in case of windows before treating as a dir.
        if (!Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isOther) {
            for (f in Files.list(path)) {
                removeDirRecursive(f)
            }
        }
    }
    Files.deleteIfExists(path)
}
