package lang.temper.common

import lang.temper.log.filePath
import lang.temper.name.ModuleName

val testCodeLocation = filePath("test", "test.temper")
val testModuleName = ModuleName(
    sourceFile = testCodeLocation.dirName(),
    libraryRootSegmentCount = 1,
    isPreface = false,
)
