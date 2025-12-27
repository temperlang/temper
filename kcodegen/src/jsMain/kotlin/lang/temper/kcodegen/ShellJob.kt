package lang.temper.kcodegen

class JsNofilesystem(cantDo: String) : Exception("$cantDo: Javascript has no file system")

class FakePath(val path: String) : Path {
    override fun toString(): String {
        return "FakePath: $path"
    }
}

actual fun packPartsAsPath(subproject: String, parts: Iterable<String>): Path {
    return FakePath((listOf(subproject) + parts).join("/"))
}

actual fun rm(paths: Iterable<Path>) {
    if (paths.iterator().hasNext()) {
        throw JsNofilesystem("Can't remove files")
    }
}

actual fun checkOutdated(st: SrcTgt): TaskStatus {
    return TaskStatus.UP_TO_DATE
}

actual fun which(name: String): Interpreter {
    return object : Interpreter {
        override fun run(st: SrcTgt) {
            throw JsNofilesystem("Can't run $name")
        }
    }
}
