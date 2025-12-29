package lang.temper.be.cpp

import lang.temper.be.MetadataKey
import lang.temper.log.FilePath
import lang.temper.name.BackendId

abstract class CppMetadataKey<VALUE> : MetadataKey<CppBackend, VALUE>() {
    override val backendId: BackendId
        get() = CppBackend.Cpp11.backendId

    data object MainFilePath : CppMetadataKey<FilePath>()
}
