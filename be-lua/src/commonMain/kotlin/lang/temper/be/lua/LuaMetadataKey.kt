package lang.temper.be.lua

import lang.temper.be.MetadataKey
import lang.temper.log.FilePath
import lang.temper.name.BackendId

abstract class LuaMetadataKey<VALUE> : MetadataKey<LuaBackend, VALUE>() {
    override val backendId: BackendId
        get() = LuaBackend.Lua51.backendId

    data object MainFilePath : LuaMetadataKey<FilePath>()
}
