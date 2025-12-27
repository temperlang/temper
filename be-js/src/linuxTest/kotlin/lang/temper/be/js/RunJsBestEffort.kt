package lang.temper.be.js

import kotlin.math.min
import kotlin.native.Platform
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import lang.temper.common.toStringViaBuilder
import platform.posix.chmod
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.mkdtemp
import platform.posix.mode_t
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.stat
import platform.posix.system
import platform.posix.unlink

private val fileSeparator = when (Platform.osFamily) {
    OsFamily.WINDOWS -> '\\'
    else -> '/'
}

private data class File(val path: String) {
    constructor(parent: File?, name: String) : this("${parent?.path ?: "."}$fileSeparator$name")

    val name get() = path.substring(path.lastIndexOf(fileSeparator) + 1)

    val isDirectory: Boolean
        get() {
            val fd = opendir(path) ?: return false
            closedir(fd)
            return true
        }

    fun listFiles(): List<File>? {
        val fd = opendir(path) ?: return null
        try {
            val files = mutableListOf<File>()
            while (true) {
                // dirent is owned by readdir.
                val dirent = readdir(fd) ?: break
                val name = dirent.pointed.d_name.toKString()
                if (name != "." && name != "..") {
                    files.add(File(this@File, name))
                }
            }
            return files.toList()
        } finally {
            closedir(fd)
        }
    }

    @Suppress("unused")
    fun exists(): Boolean = memScoped {
        val memScope = this
        val statBuf = memScope.alloc<stat>()
        when (stat(path, statBuf.ptr)) {
            0 -> true
            else -> false
        }
    }

    fun delete() {
        unlink(path)
    }

    override fun toString(): String = path
}

private object Files {
    @ExperimentalUnsignedTypes
    fun writeString(file: File, content: String) {
        val bytes = content.encodeToByteArray()
        val nBytes = bytes.size.toULong()
        // memScoped {
        val fd = fopen(file.path, "w") ?: error(file.path)
        try {
            fwrite(bytes.toCValues(), 1, nBytes, fd)
        } finally {
            fclose(fd)
        }
        // }
    }

    @ExperimentalUnsignedTypes
    fun readString(file: File): String {
        val bytes = ByteArray(4096)
        var accumulated = ByteArray(4096)
        var nRead = 0

        memScoped {
            val fd = fopen(file.path, "r") ?: error(file.path)
            try {
                while (true) {
                    val n = bytes.usePinned { bytesPinned ->
                        fread(bytesPinned.addressOf(0), 1, 4096, fd)
                    }.toInt()
                    if (n <= 0) {
                        break
                    }
                    val nReadAfter = nRead + n
                    if (nReadAfter > accumulated.size) {
                        val bigger = ByteArray(nReadAfter * 2)
                        accumulated.copyInto(bigger, 0, 0, nRead)
                        accumulated = bigger
                    }
                    bytes.copyInto(accumulated, nRead, 0, n)
                    nRead = nReadAfter
                }
            } finally {
                fclose(fd)
            }
        }

        val trimmed = ByteArray(nRead)
        accumulated.copyInto(trimmed, 0, 0, nRead)
        return trimmed.decodeToString()
    }

    fun createTempDirectory(prefix: String): File {
        val tempDirName = memScoped {
            val memScope = this
            val tmpDir = (
                    getenv("TMPDIR")
                        ?: getenv("TMP")
                        ?: getenv("TEMP")
                    )?.toKString() ?: "/tmp"
            val template = "$tmpDir$fileSeparator${prefix}XXXXXX"
            val templateStr = template.cstr.getPointer(memScope)
            mkdtemp(templateStr) ?: error(prefix)
            val temporary = templateStr.toKString()
            temporary
        }
        return File(tempDirName)
    }
}

@ExperimentalUnsignedTypes
internal actual fun runJsBestEffort(
    jsCode: String
): Triple<String, Boolean, (cleanup: Boolean) -> String> {
    val tempDirectory = Files.createTempDirectory("temper-test")
    val mainFile = File(tempDirectory, "main.mjs")
    Files.writeString(mainFile, jsCode)
    Files.writeString(
        File(tempDirectory, "_index_.mjs"),
        """
        import x from './main.mjs';
        console.log('----------------');
        console.log(x ? x[1] : 'Fail');
        """.trimIndent()
    )

    val runScriptName = when (Platform.osFamily) {
        OsFamily.WINDOWS -> "run.bat"
        else -> "run.sh"
    }

    val outFile = File(tempDirectory, "_out_.txt")
    val runScript = File(tempDirectory, runScriptName)
    val node = which(node)

    Files.writeString(
        runScript,
        when (Platform.osFamily) {
            OsFamily.WINDOWS ->
                """
                cd /D "%~dp0" || exit /b
                $node --experimental-modules _index_.mjs > _out_.txt 2>&1 || exit /b
                """.trimIndent()
            else ->
                """
                #!/bin/bash
                set -e

                cd "$(dirname "$0")"
                $node --experimental-modules _index_.mjs > _out_.txt 2>&1
                """.trimIndent()
        }
    )
    when (Platform.osFamily) {
        OsFamily.WINDOWS -> {}
        else -> { // Make runScript executable
            val mode: mode_t = "750".toUInt(8)
            val result = chmod(runScript.path, mode)
            require(result == 0) { "chmod ${runScript.path} -> $result" }
        }
    }

    val exitCode = system(runScript.path)

    val stdout = Files.readString(outFile)
    val filteredStdout = toStringViaBuilder { sb ->
        var pos = 0
        var i = 0
        val len = stdout.length
        while (true) {
            if (i == len || stdout[i] == '\n') {
                val line = stdout.substring(pos, min(len, i + 1))
                pos = i + 1
                if ("ExperimentalWarning: The ESM module loader is experimental." !in line) {
                    sb.append(line)
                }
                if (i == len) {
                    break
                }
            }
            i += 1
        }
    }

    return Triple(
        filteredStdout,
        exitCode == 0
    ) { cleanup ->
        if (cleanup) {
            rmrf(tempDirectory)
        }
        """
        To debug, try:
        $ cd $tempDirectory
        $ ./$runScriptName
        $ cat _out_.txt
        """.trimIndent()
    }
}

@Suppress("MayBeConst") // expect is not const
internal actual val runJsWorks = true
