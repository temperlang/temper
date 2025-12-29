package lang.temper.be.py

import lang.temper.be.MetadataKey
import lang.temper.log.FilePathSegment
import lang.temper.name.BackendId

abstract class PyMetadataKey<VALUE_TYPE>(
    val name: String,
    private val pyVersion: PythonVersion,
) : MetadataKey<PyBackend, VALUE_TYPE>() {
    override val backendId: BackendId
        get() = pyVersion.backendId

    /** The library name as a directory name like "my-example" */
    class PyLibraryBaseDir(
        pyVersion: PythonVersion,
    ) : PyMetadataKey<FilePathSegment>("PyLibraryBaseDir", pyVersion)

    /** The library name as a Python identifier like "my_example" */
    class PyLibraryName(
        pyVersion: PythonVersion,
    ) : PyMetadataKey<PyIdentifierName>("PyLibraryName", pyVersion)

    override fun toString(): String = name

    override fun equals(other: Any?): Boolean =
        other is PyMetadataKey<*> && name == other.name && backendId == other.backendId

    override fun hashCode(): Int = name.hashCode() + 31 * backendId.hashCode()
}
