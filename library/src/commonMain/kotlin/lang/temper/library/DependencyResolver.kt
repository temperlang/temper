package lang.temper.library

import lang.temper.common.json.JsonValue
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import lang.temper.name.BackendId
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName

interface DependencyResolver {
    fun libraryRootFor(
        loc: CodeLocation,
    ): FilePath? = (loc as? ModuleName)?.libraryRoot()

    fun resolve(loc: ModuleLocation, backendId: BackendId, logSink: LogSink): JsonValue?
}
