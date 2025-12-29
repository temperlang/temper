package lang.temper.be.py

import lang.temper.be.Backend
import lang.temper.be.generateCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStructure
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.OutputRoot
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import kotlin.test.Test
import kotlin.test.assertFalse

class PyModuleStructureTest {
    private fun assertPyModuleStructure(
        input: String,
        want: String,
        /** The path to the directory under the output dir expressed in [want]. */
        rootPathWanted: FilePath = FilePath.emptyPath,
    ) {
        val logSink = ListBackedLogSink()
        val outputRoot: OutputRoot = generateCode(
            inputFileMapFromJson(input),
            PyBackend.Python3,
            Backend.Config.production,
            genre = Genre.Library,
            moduleResultNeeded = false,
            logSink = logSink,
        )
        assertFalse(logSink.hasFatal)

        val fs = outputRoot.fs as MemoryFileSystem
        val root = fs.lookup(rootPathWanted) as MemoryFileSystem.DirectoryOrRoot

        assertStructure(want, PyModuleStructureDump(root))
    }

    @Test
    fun emptyLibrary() = assertPyModuleStructure(
        input = """
            |{}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py": "",
            |      },
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun aLibrary() = assertPyModuleStructure(
        input = """
            |{
            |  foo: {
            |    foo.temper:
            |      ```
            |      console.log("Hello, World!");
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py":
            |          ```
            |          import my_test_library.foo as _0
            |          ```,
            |        "foo.py": "...",
            |      },
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun oneModuleAtRootDir() = assertPyModuleStructure(
        input = """
            |{
            |  foo: {
            |    foo.temper: "export let foo = \"foo\";",
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py":
            |          ```
            |          import my_test_library.foo as _0
            |          ```,
            |        "foo.py": "...",
            |      },
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun twoDirModulesAtRootDir() = assertPyModuleStructure(
        input = """
            |{
            |  foo: {
            |    "foo.temper": "export let foo = \"foo\";",
            |  },
            |  bar: {
            |    "bar.temper": "export let bar = \"bar\";",
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py":
            |          ```
            |          import my_test_library.bar as _0
            |          import my_test_library.foo as _1
            |          ```,
            |        "bar.py": "...",
            |        "foo.py": "...",
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun twoModulesInSubDir() = assertPyModuleStructure(
        input = """
            |{
            |  "d": {
            |    foo: { "foo.temper": "export let foo = \"foo\";" },
            |    bar: { "bar.temper": "export let bar = \"bar\";" },
            |  },
            |}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py":
            |          ```
            |          import my_test_library.d.bar as _0
            |          import my_test_library.d.foo as _1
            |          ```,
            |        "d": {
            |          "__init__.py": "",
            |          "bar.py": "...",
            |          "foo.py": "...",
            |        },
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun oneModuleInSubDir() = assertPyModuleStructure(
        input = """
            |{
            |  "d": {
            |    foo: { "foo.temper": "export let foo = \"foo\";" },
            |  },
            |}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py":
            |          ```
            |          import my_test_library.d.foo as _0
            |          ```,
            |        "d": {
            |          "__init__.py": "",
            |          "foo.py": "...",
            |        },
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun lotsaFiles() = assertPyModuleStructure(
        input = """
            |{
            |  "d": {
            |    foo: {
            |      "foo.temper": "export let foo = \"foo\";",
            |    },
            |    bar: {
            |      "bar.temper": "export let bar = \"bar\";",
            |    },
            |  },
            |  baz: {
            |    "baz.temper": "export let baz = \"baz\";",
            |  },
            |  boo: {
            |    "boo.temper": "export let boo = \"boo\";",
            |  },
            |}
        """.trimMargin(),
        want = """
            |{
            |  "py": {
            |    "my-test-library": {
            |      "pyproject.toml": "...",
            |      "my_test_library": {
            |        "__init__.py":
            |          ```
            |          import my_test_library.baz as _0
            |          import my_test_library.boo as _1
            |          import my_test_library.d.bar as _2
            |          import my_test_library.d.foo as _3
            |          ```,
            |        "baz.py": "...",
            |        "boo.py": "...",
            |        "d": {
            |          "__init__.py": "",
            |          "bar.py": "...",
            |          "foo.py": "...",
            |        },
            |      },
            |    },
            |  }
            |}
        """.trimMargin(),
    )
}

private class PyModuleStructureDump(val rootDir: MemoryFileSystem.DirectoryOrRoot) : Structured {
    override fun destructure(structureSink: StructureSink) {
        fun walkBuildingStructure(sink: StructureSink, f: MemoryFileSystem.FileOrDirectoryOrRoot) {
            when (f) {
                is MemoryFileSystem.DirectoryOrRoot -> sink.obj {
                    f.ls().forEach { child ->
                        val name = child.entry.name
                        if (name.extension != ".map") {
                            key(name.fullName) {
                                walkBuildingStructure(this, child)
                            }
                        }
                    }
                }
                is MemoryFileSystem.File -> sink.value(
                    if (f.entry.name.baseName == DUNDER_INIT) {
                        // We're interested in the content of __init__ files but
                        // not the generated Python in other source files.
                        f.textOrBinaryContent.leftOrNull?.trimEnd()
                    } else {
                        null
                    } ?: "...",
                )
            }
        }
        walkBuildingStructure(structureSink, rootDir)
    }
}
