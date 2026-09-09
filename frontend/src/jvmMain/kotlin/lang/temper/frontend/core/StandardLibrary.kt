package lang.temper.frontend.core

import lang.temper.fs.FileSystem
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.RealFileSystem
import lang.temper.fs.copyRecursive
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.toPath

actual fun accessStd(configPluginSource: String): FileSystem? {
    val classLoader = CoreModule::class.java.classLoader
    val stdConfig = classLoader.getResource(STANDARD_LIBRARY_NAME)!!
    fun copyFrom(path: Path) = MemoryFileSystem().also {
        copyRecursive(from = RealFileSystem(path), to = it) { filePath, bytes ->
            when {
                configPluginSource.isNotEmpty() && filePath.lastOrNull()?.fullName == "config.temper.md" -> {
                    // Prefix an extra newline for convenience here. This is a rare cost.
                    // We even could just load as bytes in the first place, but this is easier to look at.
                    // This does presume our main std config.temper.md is utf-8, but we can manage that.
                    val pluginBytes = "\n$configPluginSource".encodeToByteArray()
                    bytes + pluginBytes
                }
                else -> bytes
            }
        }
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
