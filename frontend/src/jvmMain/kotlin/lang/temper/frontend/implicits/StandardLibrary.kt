package lang.temper.frontend.implicits

import lang.temper.fs.FileSystem
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.RealFileSystem
import lang.temper.fs.copyRecursive
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.toPath

actual fun accessStd(): FileSystem? {
    val classLoader = ImplicitsModule::class.java.classLoader
    val stdConfig = classLoader.getResource(STANDARD_LIBRARY_NAME)!!
    fun copyFrom(path: Path) = MemoryFileSystem().also {
        copyRecursive(from = RealFileSystem(path), to = it)
    }
    return when (stdConfig.protocol) {
        "file" -> copyFrom(stdConfig.toURI().toPath())
        "jar" -> {
            val (fileUriString, entry) = stdConfig.file.split("!/")
            val fileUri = URI(fileUriString)
            when (fileUri.scheme) {
                "file" -> FileSystems.newFileSystem(fileUri.toPath(), classLoader).use {
                    copyFrom(it.getPath(entry))
                }
                else -> null
            }
        }
        else -> null
    }
}
