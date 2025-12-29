package lang.temper.fs

import lang.temper.common.Console
import java.nio.file.Files
import kotlin.io.path.name

actual fun NativePath.fileTree(out: Console) = out.log(
    buildString {
        FileTreeRenderer(this@buildString).tree(this@fileTree)
    },
)

private class FileTreeRenderer(private val sb: StringBuilder) {
    fun tree(path: NativePath) {
        if (Files.isDirectory(path)) {
            directoryTree(path, mutableListOf())
        } else {
            regularFile(path)
        }
    }
    private fun regularFile(path: NativePath) {
        sb.append(path.name).append(' ').append(Files.size(path)).append('B')
    }
    private fun directoryTree(path: NativePath, lastElements: MutableList<Boolean>) {
        if (lastElements.isNotEmpty()) {
            sb.append(if (!lastElements.last()) "├─ " else "└─ ")
        }
        sb.append(path.name)

        val listing = path.list()
        val listingSorted = sortListing(listing)
        for (i in listingSorted.indices) {
            sb.append('\n')
            for (lastElement in lastElements) {
                sb.append(
                    if (lastElement) {
                        "   "
                    } else {
                        "│  "
                    },
                )
            }
            val child = listingSorted[i]
            if (Files.isDirectory(child)) {
                lastElements.add(i == listingSorted.lastIndex) // removed below
                directoryTree(child, lastElements)
                lastElements.removeLast()
            } else {
                sb.append(if (i == listingSorted.lastIndex) "└─ " else "├─ ")
                regularFile(child)
            }
        }
    }

    fun sortListing(files: List<NativePath>): List<NativePath> =
        files.map {
            // Attach isDirectory bit to each
            it to Files.isDirectory(it)
        }.sortedWith { a, b ->
            // Order files before directories then by name
            val delta = a.second.compareTo(a.second)
            if (delta != 0) {
                delta
            } else {
                a.first.name.compareTo(b.first.name)
            }
        }.map { it.first }
}
