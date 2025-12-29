package lang.temper.be.names

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.NullDependencyResolver
import lang.temper.be.inputFileMapFromJson
import lang.temper.be.tmpl.TestBackend
import lang.temper.be.tmpl.TestCompiler
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.defaultTestSupportNetwork
import lang.temper.common.assertStringsEqual
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.frontend.Module
import lang.temper.fs.NullSystemAccess
import lang.temper.fs.OutDir
import lang.temper.library.LibraryConfigurations
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import kotlin.test.Test
import kotlin.test.fail

class NameTest {
    @Test
    fun topLevelFunction() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "let whateverBoatsYour(): Float64 { 42.0 }"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf("whateverBoatsYour" to "test-library/foo.whateverBoatsYour()"),
        )
    }

    @Test
    fun innerName() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "let outer(): Int { let inner: Int = 42; return inner; }"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf(
                "inner" to "test-library/foo.outer().inner=",
                "outer" to "test-library/foo.outer()",
            ),
        )
    }

    @Test
    fun topLevelName() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "let what = 42"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf("what" to "test-library/foo.what"),
        )
    }

    @Test
    fun exportedTopLevelFunctionAndName() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "export let function(): Int { 33 }"
                    |  },
                    |  bar: {
                    |    bar.temper: "export let magicString = 'alakazam'"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf(
                "`test-library/foo/`.function" to "test-library/foo.function()",
                "`test-library/bar/`.magicString" to "test-library/bar.magicString",
            ),
        )
    }

    @Test
    fun exportedInnerName() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "export let outer(): Int { let inner: Int = 42; return inner; }"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf(
                "inner" to "test-library/foo.outer().inner=",
                "`test-library/foo/`.outer" to "test-library/foo.outer()",
            ),
        )
    }

    @Test
    fun exportedClassAndMethod() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "export class Bob { public affair(): Void { void } }"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf(
                "`test-library/foo/`.Bob" to "test-library/foo.type Bob",
                "affair" to "test-library/foo.type Bob.affair()",
                "constructor" to "test-library/foo.type Bob.constructor()",
            ),
        )
    }

    @Test
    fun innerFunction() {
        assertNames(
            inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    foo.temper: "let outer(): Int { let inner(): Int { 23 }; inner() + 2 }"
                    |  }
                    |}
                """.trimMargin(),
            ),
            listOf(
                "inner" to "test-library/foo.outer().inner()",
                "outer" to "test-library/foo.outer()",
            ),
        )
    }

    private fun assertNames(
        inputs: List<Pair<FilePath, String>>,
        want: List<Pair<String, String>>,
    ) {
        object : TestCompiler(inputs, moduleNeedsResult = true) {
            var nameLookup: NameLookup = NameLookup.empty

            override fun onBackendsComplete(outputRoot: OutDir) {
                val got = nameLookup.qNameMappings().entries
                    .sortedWith(compareBy(resolvedNameComparator) { it.key.second })
                    .flatMap { (namePair, qName) ->
                        if (qName == null) {
                            listOf()
                        } else {
                            listOf(simpleStringify(namePair.second) to "$qName")
                        }
                    }
                if (want != got) {
                    fun format(ls: List<Pair<String, String>>) =
                        ls.joinToString("\n\n") { (a, b) -> "$a -> $b" }
                    val wantStr = format(want)
                    val gotStr = format(got)
                    assertStringsEqual(wantStr, gotStr, "compiled name lookup")
                    fail(gotStr)
                }
            }

            override fun backend(
                libraryConfigurations: LibraryConfigurations,
                modules: List<Module>,
                outDir: OutDir,
            ): Backend<*> {
                val cancelGroup = makeCancelGroupForTest()
                val buildFileCreator = outDir.systemAccess(cancelGroup)
                val keepFileCreator = NullSystemAccess(dirPath(), cancelGroup)
                return object : TestBackend(
                    defaultTestSupportNetwork,
                    BackendSetup(
                        libraryConfigurations.currentLibraryConfiguration.libraryName,
                        Dependencies.Builder(libraryConfigurations.toBundle()),
                        modules,
                        buildFileCreator,
                        keepFileCreator,
                        logSink,
                        NullDependencyResolver,
                        Config.bundled,
                    ),
                ) {
                    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
                        val visitor = LookupNameVisitor()
                        visitor.visit(finished)
                        nameLookup = visitor.toLookup()
                        return super.translate(finished)
                    }
                }
            }
        }.compile()
    }
}

/** Stringify resolved names without numeric IDs */
private fun simpleStringify(name: ResolvedName): String = when (name) {
    is ExportedName -> "$name"
    is SourceName -> name.baseName.nameText
    is Temporary -> name.nameHint
    is BuiltinName -> name.builtinKey
}
