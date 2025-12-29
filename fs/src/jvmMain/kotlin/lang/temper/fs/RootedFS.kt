package lang.temper.fs

import kotlin.io.path.writeBytes

actual fun loadResource(root: Any, resource: String): String {
    return root::class.java.classLoader.getResourceAsStream(resource)!!
        .readAllBytes().decodeToString()
}

actual fun loadResourceBinary(root: Any, resource: String): ByteArray {
    return root::class.java.classLoader.getResourceAsStream(resource)!!.readAllBytes()
}

actual fun copyResources(
    resources: List<ResourceDescriptor>,
    destinationDir: NativePath,
) {
    require(destinationDir.isAbsolute) { "$destinationDir is not an absolute path" }

    destinationDir.mkdir()
    resources.forEach { rsrc ->
        destinationDir.resolve(rsrc.rsrcPath.dirName()).mkdir()
        destinationDir.resolve(rsrc.rsrcPath).writeBytes(rsrc.loadBinary())
    }
}
