package lang.temper.kcodegen

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.StandardCharsets

// TODO fold this into fs ?

// functions for working with Path objects.
data class FilePath(val impl: File) : Path {
    override fun toString(): String = impl.toString()
}

private fun unpackPath(path: Path): File = (path as FilePath).impl

actual fun packPartsAsPath(subproject: String, parts: Iterable<String>): Path {
    var node = getSubprojectRoot(subproject)
    for (part in parts) {
        node = File(node, part)
    }
    return FilePath(node)
}

actual fun rm(paths: Iterable<Path>) {
    for (path in paths) {
        val file = unpackPath(path)
        if (file.exists()) {
            file.delete()
        }
    }
}

actual fun ensureDir(path: Path, isFile: Boolean) {
    var file = unpackPath(path)
    if (isFile) {
        file = file.parentFile
    }
    file.mkdirs()
}

actual fun checkOutdated(st: SrcTgt): TaskStatus {
    val srcFiles = st.sources.map { unpackPath(it) }
    val tgtFile = unpackPath(st.target)
    val tgtMod = tgtFile.lastModified()
    return when {
        !tgtFile.exists() -> TaskStatus.TARGET_MISSING
        !srcFiles.all { it.exists() } -> TaskStatus.SOURCE_MISSING
        srcFiles.any { it.lastModified() > tgtMod } -> TaskStatus.TARGET_OLD
        else -> TaskStatus.UP_TO_DATE
    }
}

actual fun which(name: String): Interpreter {
    val path = System.getenv("PATH")
    val pathElements = path.split(File.pathSeparator)
    for (pathElement in pathElements) {
        val candidate = File(File(pathElement), name)
        if (candidate.exists()) {
            return ProcessInterpreter(candidate)
        }
    }
    throw FileNotFoundException("No interpreter $name found on PATH.")
}

private fun readStream(stream: InputStream): String {
    val wrt = ByteArrayOutputStream()
    stream.copyTo(wrt)
    return wrt.toString(StandardCharsets.UTF_8)
}

private fun shellEscape(args: Iterable<String>): String {
    return args.joinToString(" ")
}

class ProcessInterpreter(private val cmd: File) : Interpreter {
    override fun run(st: SrcTgt) {
        val command = listOf(
            cmd.toString(),
            st.script.toString(),
            st.source.toString(),
            st.target.toString(),
        )
        val pb = ProcessBuilder()
        pb.command(command)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        try {
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                val out = readStream(proc.inputStream) // sic
                throw JobReportsFailure("${shellEscape(command)} exited with $exitCode.\n$out")
            }
        } finally {
            proc.destroy()
        }
    }

    override fun toString(): String {
        return "ProcessInterpreter(cmd=$cmd)"
    }
}
