package lang.temper.fs

import lang.temper.log.FilePath
import lang.temper.log.last
import lang.temper.log.plus
import kotlin.reflect.KClass

/**
 * A resource loaded from, typically, the 'commonMain/resources' directory. See [declareResources].
 */
data class ResourceDescriptor(
    private val loader: ResourceLoader,
    private val basePath: FilePath,
    /** the relative path for this resource to be loaded into */
    val rsrcPath: FilePath,
    /** the encoding for the resource text */
    val charset: KCharset,
) {
    init {
        require(basePath.isDir) { "Resource base must be a directory" }
        require(rsrcPath.isFile) { "Resource must be a file" }
    }

    val fileName: String get() = rsrcPath.last().fullName

    /** The full path to load this resource */
    private val loadPath: FilePath get() = basePath + rsrcPath

    /** a url that loads the resource */
    fun url(): Url = loader.url(loadPath)!!

    /** load the contents of the resource as text */
    fun load(): String = loader.load(loadPath, charset)!!

    /** load the contents of the resource as a byte array */
    fun loadBinary(): ByteArray = loader.loadBinary(loadPath)!!
}

/**
 * Create a list of resources that are under a common base path.
 *
 * Note that this is an extension method on `Any` to pick the local class loader.
 */
fun Any.declareResources(
    /** the common base path to load resources from */
    base: FilePath,
    /** relative to the base path, and relative to the destination */
    vararg rsrcs: FilePath,
    charset: KCharset = KCharsets.utf8,
): List<ResourceDescriptor> {
    return declareResources(base, rsrcs.toList(), charset)
}

/**
 * Create a list of resources that are under a common base path.
 *
 * Note that this is an extension method on `Any` to pick the local class loader.
 */
fun Any.declareResources(
    /** the common base path to load resources from */
    base: FilePath,
    /** relative to the base path, and relative to the destination */
    rsrcs: List<FilePath>,
    charset: KCharset = KCharsets.utf8,
): List<ResourceDescriptor> {
    val loader = this::class.loader()
    return rsrcs.map { rsrc ->
        ResourceDescriptor(loader = loader, basePath = base, rsrcPath = rsrc, charset = charset)
    }
}

expect class Url
expect abstract class ResourceLoader

expect fun KClass<*>.loader(): ResourceLoader
expect fun ResourceLoader.url(path: FilePath): Url?
expect fun ResourceLoader.load(path: FilePath, charset: KCharset): String?
expect fun ResourceLoader.loadBinary(path: FilePath): ByteArray?
