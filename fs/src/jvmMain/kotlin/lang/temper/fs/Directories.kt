package lang.temper.fs

import net.harawata.appdirs.AppDirsFactory
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * A suite of standard directories for Linux/Mac/Win platforms. Update this and [getDirectories] for other values.
 */
data class Directories(
    val userCacheDir: Path,
    val userDataDir: Path,
)

/** The name of the temper application. */
const val APP_NAME = "temper"

/** Major version; we can set this if configuration changes. */
val APP_VERSION: String? = null

/** Used on Windows; convention is to use an informal name. */
const val AUTHORSHIP = "Temper Contributors"

/** Directories where we can put user specific stuff, appropriate for the current OS. */
fun getDirectories(fileSystem: FileSystem = FileSystems.getDefault()): Directories {
    val appDirs = AppDirsFactory.getInstance()

    return Directories(
        userCacheDir = fileSystem.getPath(appDirs.getUserCacheDir(APP_NAME, APP_VERSION, AUTHORSHIP)),
        userDataDir = fileSystem.getPath(appDirs.getUserDataDir(APP_NAME, APP_VERSION, AUTHORSHIP)),
    )
}

/**
 * Create a suite of test directories under some given base. These shouldn't conform to any OS standard, to avoid
 * "works on my laptop" syndrome.
 */
fun getTestDirectories(base: Path): Directories =
    Directories(
        userCacheDir = base.resolve("cache"),
        userDataDir = base.resolve("data"),
    )
