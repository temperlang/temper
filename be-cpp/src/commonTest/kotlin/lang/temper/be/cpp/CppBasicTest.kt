package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.generateCode
import lang.temper.common.ListBackedLogSink
import lang.temper.common.writeAFileBestEffort
import lang.temper.fs.MemoryFileSystem
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.filePath
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.Test

class CppBasicTest {
    @Test
    fun run() {
        val logSink = ListBackedLogSink()

        val temper = """

export interface BinaryTree<Type> {
  public toList(): List<Type>;
}

export class Node<Type>(
  public left: BinaryTree<Type>,
  public right: BinaryTree<Type>,
) extends BinaryTree<Type> {
  public toList(): List<Type> {
    let ret = new ListBuilder<Type>();
    ret.addAll(left.toList()) orelse do {};
    ret.addAll(right.toList()) orelse do {};
    return ret.toList();
  }
}

export class Leaf<Type>(
  public value: Type,
) extends BinaryTree<Type> {
  public toList(): List<Type> {
    return [value];
  }
}

export let example = {
  left: {
    left: {
      value: 5,
    },
    right: {
      value: 10,
    },
  },
  right: {
    value: 20,
  },
};

        """.trimMargin()

        val result = generateCode(
            backendConfig = Backend.Config.production,
            factory = CppBackend.Cpp11,
            inputs = listOf(
                filePath("something", "something.temper") to temper,
            ),
            genre = Genre.Library,
            moduleResultNeeded = true,
            logSink = logSink,
        )

        val memfs = result.fs as MemoryFileSystem

        val files = mutableMapOf<FilePath, String>()

        fun add(file: MemoryFileSystem.FileOrDirectory) {
            when (file) {
                is MemoryFileSystem.File -> files[file.absolutePath] = file.textContent
                is MemoryFileSystem.SubDirectory -> file.ls().forEach(::add)
            }
        }

        memfs.root.ls().forEach(::add)

        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        files.forEach { (key, value) ->
            val name = "$key"
            if (name.endsWith(".map")) {
                return@forEach
            }
            writer.println("#line 1 \"$key\"")
            writer.println(value)
        }

        // ouch
        writeAFileBestEffort("$stringWriter", "cpp.log")
    }
}
