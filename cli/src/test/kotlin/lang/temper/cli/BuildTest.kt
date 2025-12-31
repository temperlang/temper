package lang.temper.cli

import kotlinx.cli.ExperimentalCli
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.csharp.CSharpBackend
import lang.temper.be.java.JavaBackend
import lang.temper.be.js.JsBackend
import lang.temper.be.lua.LuaBackend
import lang.temper.be.names.NameSelectionFile
import lang.temper.be.py.PyBackend
import lang.temper.be.rust.RustBackend
import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.Style
import lang.temper.common.TeeAppendable
import lang.temper.common.console
import lang.temper.common.json.JsonObject
import lang.temper.common.structure.StructureParser
import lang.temper.fs.TEMPER_KEEP_NAME
import lang.temper.fs.TEMPER_OUT_NAME
import lang.temper.fs.fileTree
import lang.temper.fs.list
import lang.temper.fs.listKidNames
import lang.temper.fs.removeDirRecursive
import lang.temper.fs.runWithTemporaryDirCopyOf
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.MessageTemplate
import lang.temper.name.BackendId
import lang.temper.name.QName
import lang.temper.supportedBackends.defaultSupportedBackendList
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.BuildResult
import lang.temper.tooling.buildrun.doBuild
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// Setting this to true can help debug these tests
private const val DUMP_FILE_TREE_TO_CONSOLE = false

class BuildTest {
    @Test
    @Timeout(PY_TIMEOUT_SECONDS) // Semi-arbitrary, but does seem to need a bit longer than default sometimes.
    fun allBackends() = runBuildTest("AllBackends", defaultSupportedBackendList)

    @Test
    fun csharpBackend() = runBuildTest("CSharpBackend") { topDir ->
        runCase(topDir, listOf(CSharpBackend.Factory.backendId))
        topDir.withTextOf("temper.out/csharp/lib-a/src/A/AGlobal.cs") { text ->
            assertContains(text, "HelperGlobal.Twice")
        }
        topDir.withTextOf("temper.out/csharp/std/src/TemperLang.Std.csproj") { text ->
            assertContains(text, "<PackageLicenseExpression>Apache-2.0 OR MIT</PackageLicenseExpression>")
            assertContains(text, "<RootNamespace>TemperLang.Std</RootNamespace>")
        }
    }

    @Test
    fun csharpBackendDirs() = runBuildTest("CSharpBackendDirs", path = "/buildDirs") { topDir ->
        runBuild(backends = listOf(CSharpBackend.Factory.backendId), workRoot = topDir)
        topDir.withTextOf("temper.keep/csharp/apple/name-selection.json") { text ->
            val selections = NameSelectionFile.fromJson(text).selectionsAsMap()
            assertEquals("Name", selections[QName.fromString("apple/avocado.type Person.name").result!!])
            // We expect 12 at the moment, but be a little flexible. We definitely don't expect an explosion in number.
            // We only save publicly visible names for now in be-csharp.
            @Suppress("MagicNumber")
            assertTrue(selections.size in 12..20)
            // And for public symbols in this test workspace, we don't expect any underscores.
            assertTrue(selections.values.none { it.contains("_") })
        }
        topDir.withTextOf("temper.out/csharp/apple/src/AppleGlobal.cs") { text ->
            assertContains(text, """public static int Thrice""")
        }
        topDir.withTextOf("temper.out/csharp/apple/src/Avocado/Person.cs") { text ->
            assertContains(text, """public class Person""")
        }
        topDir.withTextOf("temper.out/csharp/apple/src/Avocado/Artichoke/ArtichokeGlobal.cs") { text ->
            assertContains(text, """public static string Repeated""")
        }
        topDir.withTextOf("temper.out/csharp/banana/src/BananaGlobal.cs") { text ->
            assertContains(text, """public static double Twice""")
        }
        topDir.withTextOf("temper.out/csharp/banana/src/Qualified.Banana.csproj") { text ->
            assertNotContains(text, "TemperLang.Std.Testing")
            assertContains(text, """<RootNamespace>Qualified.Banana</RootNamespace>""")
            assertNotContains(text, "nobodyWantsMe")
        }
        topDir.withTextOf("temper.out/csharp/banana/tests/BananaTests.cs") { text ->
            assertContains(text, """TemperLang.Std.Testing""")
            assertContains(text, """public void twiceWorks""")
            assertContains(text, """internal static void halveValueIn""")
            assertNotContains(text, "nobodyWantsMe")
        }
        assertTrue(
            topDir.resolve("temper.out/csharp/banana/tests/Something.cs").exists(),
        )
    }

    @ExperimentalCli
    @Test
    fun csharpBackendRepeatLow() = runBuildTest("RepeatBuild", path = "/buildDirs") { topDir ->
        // Make an ignore file because we want to test that.
        Files.writeString(topDir.resolve(".gitignore"), "temper.out/")
        // Run a build.
        runBuild(backends = listOf(CSharpBackend.Factory.backendId), workRoot = topDir)
        // Now run through cli in a subdir, for which our previous temper.out will do.
        val subdir = topDir.resolve(TEMPER_OUT_NAME)
        assert(subdir.exists())
        try {
            object : DummyMain() {
                override val cwd: Path get() {
                    return subdir
                }
            }.run(arrayOf("build", "-b", "csharp"))
        } catch (_: Fake0Exit) {} // letting error exits bubble
        // And make sure we didn't create a nested out dir.
        assert(!subdir.resolve(TEMPER_OUT_NAME).exists())
    }

    @Test
    fun javaBackend() = runBuildTest("JavaBackend") { topDir ->
        runCase(topDir, listOf(JavaBackend.Java17.backendId))
        topDir.withTextOf("temper.out/java/lib-a/pom.xml") { text ->
            assertContains(text, """<groupId>lib_a</groupId>""")
            assertContains(text, """<artifactId>lib-a</artifactId>""")
            // These are from dependencies rather than for the library itself.
            assertContains(text, """<artifactId>lib-b</artifactId>""")
            assertContains(text, """<artifactId>temper-std</artifactId>""")
        }
        topDir.withTextOf("temper.out/java/lib-a/src/main/java/lib_a/a/AGlobal.java") { text ->
            assertContains(text, """import lib_b.helper.HelperGlobal;""")
        }
        topDir.withTextOf("temper.out/java/std/pom.xml") { text ->
            assertContains(text, """<groupId>dev.temperlang</groupId>""")
            assertContains(text, """<artifactId>temper-std</artifactId>""")
        }
    }

    @Test
    fun javaBackendsBoth() = runBuildTest("JavaBackendsBoth") { topDir ->
        // Just check against errors for now.
        runCase(topDir, listOf(JavaBackend.Java8.backendId, JavaBackend.Java17.backendId))
    }

    @Test
    fun javaBackendDirs() = runBuildTest("JavaBackendDirs", path = "/buildDirs") { topDir ->
        runBuild(backends = listOf(JavaBackend.Java17.backendId), workRoot = topDir)
        topDir.withTextOf("temper.out/java/apple/src/main/java/apple/AppleGlobal.java") { text ->
            assertContains(text, """public static int thrice""")
        }
        topDir.withTextOf("temper.out/java/apple/src/main/java/apple/avocado/Person.java") { text ->
            assertContains(text, """public final class Person""")
        }
        topDir.withTextOf(
            "temper.out/java/apple/src/main/java/apple/avocado/artichoke/ArtichokeGlobal.java",
        ) { text ->
            assertContains(text, """public static String repeated""")
        }
        topDir.withTextOf("temper.out/java/banana/src/main/java/banana/BananaGlobal.java") { text ->
            assertNotContains(text, "temper.std.testing")
            assertContains(text, """public static double twice""")
            assertNotContains(text, "nobodyWantsMe")
        }
        topDir.withTextOf("temper.out/java/banana/src/test/java/banana/BananaTest.java") { text ->
            assertContains(text, """temper.std.testing""")
            assertContains(text, """Test public void twiceWorks""")
            assertContains(text, """static void halveValueIn""")
            assertNotContains(text, "nobodyWantsMe")
        }
        assertTrue(
            topDir.resolve("temper.out/java/banana/src/test/java/banana/Something.java").exists(),
        )
    }

    @Test
    fun jsBackend() = runBuildTest("JsBackend") { topDir ->
        runCase(topDir, listOf(JsBackend.Factory.backendId))
        // Look just a bit at files and imports.
        topDir.withTextOf("temper.out/js/lib-a/a.js") { text ->
            assertContains(text, """} from "lib-b/helper";""")
            assertContains(text, """} from "@temperlang/std/regex";""")
        }
        // Also some package.json, where we expect dependencies in sorted order.
        topDir.withTextOf("temper.out/js/lib-a/package.json") { pkgText ->
            val pkg = StructureParser.parseJson(pkgText) as JsonObject
            val deps = (pkg["dependencies"] as JsonObject).properties.map { it.key }
            val expectedDeps = listOf("@temperlang/core", "@temperlang/std", "lib-b")
            assertContentEquals(expectedDeps, deps)
        }
        // And temper library metadata, too.
        topDir.withTextOf("temper.out/js/std/package.json") { pkgText ->
            assertContains(pkgText, "Apache-2.0")
        }
        // And unit tests.
        topDir.withTextOf("temper.out/js/test/package.json") { pkgText ->
            assertContains(pkgText, "mocha")
        }
    }

    @Test
    fun jsBackendDirs() = runBuildTest("JsBackendDirs", path = "/buildDirs") { topDir ->
        val result = runBuild(backends = listOf(JsBackend.Factory.backendId), workRoot = topDir).first
        topDir.withTextOf("temper.out/js/apple/apple.js") { text ->
            assertContains(text, "export function thrice")
        }
        topDir.withTextOf("temper.out/js/apple/avocado.js") { text ->
            assertContains(text, "export class Person")
        }
        topDir.withTextOf("temper.out/js/apple/avocado/artichoke.js") { text ->
            assertContains(text, "export function repeated")
        }
        topDir.withTextOf("temper.out/js/banana/banana.js") { text ->
            assertNotContains(text, "@temperlang/std/testing")
            assertContains(text, "export function twice")
            assertNotContains(text, "nobodyWantsMe")
        }
        topDir.withTextOf("temper.out/js/banana/test/banana.js") { text ->
            assertContains(text, "@temperlang/std/testing")
            assertContains(text, "it(\"twice works\", function ()")
            assertContains(text, "class Something")
            assertContains(text, "function halveValueIn")
            assertNotContains(text, "nobodyWantsMe")
        }
        // Probably should check this on at least one backend.
        assertTrue(result.ok)
    }

    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun jsBackendTwice() {
        val backends = listOf(JsBackend.Factory.backendId)
        runBuildTest("JsBackendTwice") { topDir ->
            runCase(topDir, backends)
            runCase(topDir, backends)
        }
    }

    @Test
    fun luaBackend() = runBuildTest("LuaBackend") { topDir ->
        runCase(topDir, listOf(LuaBackend.Lua51.backendId))
        // This is mostly just here for manual inspection at the moment, but this at least checks non-crashing.
    }

    @Test
    fun luaBackendDirs() = runBuildTest("LuaBackendDirs", path = "/buildDirs") { topDir ->
        runBuild(backends = listOf(LuaBackend.Lua51.backendId), workRoot = topDir)
        assertTrue(topDir.resolve("temper.out/lua/apple/apple-dev-1.rockspec").exists())
        assertTrue(topDir.resolve("temper.out/lua/apple/apple.zip").exists())
        topDir.withTextOf("temper.out/lua/apple/init.lua") { text ->
            assertContains(text, "thrice = function")
        }
        topDir.withTextOf("temper.out/lua/apple/avocado.lua") { text ->
            assertContains(text, "Person = temper.type")
            // Used to say '-work/banana' here while working this test, so include the corrected version.
            assertContains(text, "twice = temper.import('banana',")
        }
        topDir.withTextOf("temper.out/lua/apple/avocado/artichoke.lua") { text ->
            assertContains(text, "repeated = function")
        }
        assertTrue(topDir.resolve("temper.out/lua/banana/banana-dev-1.rockspec").exists())
        assertTrue(topDir.resolve("temper.out/lua/banana/banana.zip").exists())
        topDir.withTextOf("temper.out/lua/banana/init.lua") { text ->
            assertContains(text, "twice = function")
            assertNotContains(text, "nobodyWantsMe")
        }
        topDir.withTextOf("temper.out/lua/banana/tests/init-test.lua") { text ->
            // Our lua currently avoids import std for testing, so no such import to check for.
            assertContains(text, "temper.test('twice works',")
            assertContains(text, Regex("""Something\w+ = temper.type"""))
            assertContains(text, Regex("""halveValueIn\w+ = function"""))
            assertNotContains(text, "nobodyWantsMe")
        }
        // Check some luarocks prep work for temper-core.
        assertTrue(topDir.resolve("temper.out/lua/temper-core/temper-core.zip").exists())
        topDir.resolve("temper.out/lua/temper-core").list().find { path ->
            val name = path.name
            // The "-dev-" part should have been changed out by now.
            name.endsWith(".rockspec") && "-dev-" !in name
        }!!.readText().let { text ->
            // This is in the template but shouldn't be in the final product.
            assertTrue(!text.contains("file://temper-core.zip"))
        }
    }

    @Ignore // The Timeout is ignored, and it takes too long to process std with mypyc
    @Test // The check_mypy_produces_binaries integration test does cover this.
    @Timeout(MYPYC_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun mypycBackend() = runBuildTest("MypycBackend") { topDir ->
        runCase(topDir, listOf(PyBackend.MypyC.backendId))
        val backendDir = topDir.resolve("temper.out/mypyc")
        // Check that we get native compilation for at least these libs.
        // Note that "test" fails to compile because the failing fake test runs in the prod
        // module, which mypyc tries to run when compiling, but it raises AssertionError
        // because we assert a false value in it.
        // for (libDirName in listOf("lib-a", "lib-b", "std", "temper-core", "test")) {
        for (libDirName in listOf("lib-a", "lib-b", "std", "temper-core")) {
            val topKids = backendDir.resolve(libDirName).listDirectoryEntries()
            val nativeLibExtensions = setOf("pyd", "so")
            val nativeLibs = topKids.filter { it.extension in nativeLibExtensions }
            assertTrue(nativeLibs.isNotEmpty(), "expected $libDirName to have native libs")
        }
    }

    @Test
    fun noBackends() = runBuildTest("NoBackends", emptyList())

    @Test
    fun pyBackend() = runBuildTest("PyBackend") { topDir ->
        runCase(topDir, listOf(PyBackend.Python3.backendId))
        // Look just a bit at files and imports.
        topDir.withTextOf("temper.out/py/lib-a/lib_a/a.py") { text ->
            // TODO Once we have better run tests and test tests, we might can remove some of this here.
            assertContains(text, "from py_lib_b.helper import twice")
            // Both of these imports are likely on the same line, but check twice for order independence.
            assertContains(text, Regex("""from temper_std\.regex import .*CodePoints"""))
            assertContains(text, Regex("""from temper_std\.regex import .*one_or_more"""))
            // Also check for local `CodePoints__\d+` alias usage and as type annotation.
            assertContains(text, " = CodePoints")
            assertContains(text, ": 'CodePoints")
        }
        // Also some pyproject.toml.
        topDir.withTextOf("temper.out/py/lib-a/pyproject.toml") { text ->
            for (dep in listOf("temper-core", "temper-std", "py-lib-b")) {
                assertContains(text, dep)
            }
        }
        // And temper library things, too.
        topDir.withTextOf("temper.out/py/std/pyproject.toml") { text ->
            assertContains(text, "Apache-2.0")
        }
        topDir.withTextOf("temper.out/py/std/temper_std/regex.py") { text ->
            assertContains(text, Regex("""^begin:""", RegexOption.MULTILINE))
            assertContains(text, "class Regex:")
        }
    }

    @Test
    fun pyBackendDirs() = runBuildTest("PyBackendDirs", path = "/buildDirs") { topDir ->
        runBuild(backends = listOf(PyBackend.Python3.backendId), workRoot = topDir)
        topDir.withTextOf("temper.out/py/apple/apple/apple.py") { text ->
            assertContains(text, "def thrice")
        }
        topDir.withTextOf("temper.out/py/apple/apple/avocado/__init__.py") { text ->
            assertContains(text, "class Person")
        }
        topDir.withTextOf("temper.out/py/apple/apple/avocado/artichoke.py") { text ->
            assertContains(text, "def repeated")
        }
        topDir.withTextOf("temper.out/py/banana/banana/banana.py") { text ->
            assertNotContains(text, "temper_std.testing")
            assertContains(text, "def twice")
            assertNotContains(text, "nobody_wants_me")
        }
        topDir.withTextOf("temper.out/py/banana/tests/test_banana.py") { text ->
            assertContains(text, "temper_std.testing")
            assertContains(text, "def test___twiceWorks")
            assertContains(text, "class Something")
            assertContains(text, "def halve_value_in")
            assertNotContains(text, "nobody_wants_me")
        }
    }

    @Test
    fun rustBackend() = runBuildTest("RustBackend") { topDir ->
        runCase(topDir, listOf(RustBackend.Factory.backendId))
        // This is mostly just here for manual inspection at the moment, but this at least checks non-crashing.
    }

    @Test
    fun rustBackendDirs() = runBuildTest("RustBackendDirs", path = "/buildDirs") { topDir ->
        runBuild(backends = listOf(RustBackend.Factory.backendId), workRoot = topDir)
        assertTrue(topDir.resolve("temper.out/rust/apple/src/lib.rs").exists())
        assertTrue(topDir.resolve("temper.out/rust/banana/src/lib.rs").exists())
        topDir.withTextOf("temper.out/rust/std/Cargo.toml") { text ->
            assertContains(text, "version = ")
            assertContains(text, "license = \"Apache-2.0 OR MIT\"")
            assertContains(text, "description = ")
            assertContains(text, "homepage = \"https://temperlang.dev/\"")
            assertContains(text, "repository = \"https://github.com/temperlang/temper\"")
            assertContains(text, "authors = [\"Temper Contributors\"]")
        }
    }

    companion object {
        private val executorService get() = ForkJoinPool.commonPool()

        private fun resourcePath(resource: String): Path {
            return BuildTest::class.java.getResource(resource)!!.toURI().toPath()
        }

        private fun runBuildTest(
            testName: String,
            backends: List<BackendId> = emptyList(),
            path: String = "/build",
            action: (topDir: Path) -> Unit = { runCase(it, backends) },
        ) {
            val fullPath = resourcePath(path)
            // In case someone ran a build in the source tree and that got copied to build,
            // clean up before we run the test.
            removeDirRecursive(fullPath.resolve(TEMPER_OUT_NAME))
            runWithTemporaryDirCopyOf(testName, fullPath) { action(it) }
        }

        private fun runCase(
            topDir: Path,
            backends: List<BackendId>,
            workRoot: Path = topDir,
        ): BuildResult {
            // Use a convention that test files here starting with
            // "orphan" are expected to be excluded.
            // For now, this is just the single file "orphan.temper"
            // in the test resources.
            val inputASourceFiles = listKidNames(topDir.resolve("input/a"))
                .filter { !it.startsWith("orphan") }
                .map { "${BuildHarness.workDir}input/a/$it" }
                .toSet()
            Files.createDirectories(workRoot)

            val (result, outputBuffer) = runBuild(backends, workRoot)
            val outFiles = mutableListOf<FilePath>()
            fun findOutFiles(javaPath: Path, abstractPath: FilePath) {
                if (Files.isRegularFile(javaPath)) {
                    outFiles.add(abstractPath)
                } else if (Files.isDirectory(javaPath)) {
                    val ls = Files.list(javaPath)
                    for (childJavaPath in ls) {
                        findOutFiles(
                            childJavaPath,
                            abstractPath.resolve(
                                FilePathSegment("${childJavaPath.fileName}"),
                                isDir = Files.isDirectory(childJavaPath),
                            ),
                        )
                    }
                }
            }
            findOutFiles(topDir.resolve(TEMPER_OUT_NAME), FilePath.emptyPath)

            // Make sure that some input files are actually read,
            // unless marked as orphaned (see filter above).
            val loadedSources = preStagingModuleRegex.findAll(outputBuffer).flatMap { match ->
                match.groupValues[1].split(", ").map {
                    var moduleNameStr = it
                    if (moduleNameStr.endsWith(":preface")) {
                        moduleNameStr = moduleNameStr.dropLast(":preface".length)
                    }
                    // The // separates the library root from the relative path, but we
                    // just want a FilePath
                    moduleNameStr.replace("//", "/")
                }
            }.toSet()
            // Check that CannotTranslate is not emitted for any of the backends.
            val cannotTranslateMatch = cannotTranslateRegex.find(outputBuffer)
            if (cannotTranslateMatch != null) {
                fail("Expected all translatable but got `${cannotTranslateMatch.groupValues[0]}`")
            }

            val notLoaded = inputASourceFiles.filter { it !in loadedSources }
            if (notLoaded.isNotEmpty()) {
                fail("Did not load $notLoaded.  Loaded $loadedSources")
            }

            assertTrue(result.ok)
            return result
        }

        private fun runBuild(
            backends: List<BackendId>,
            workRoot: Path,
        ): Pair<BuildResult, StringBuilder> {
            val outputBuffer = StringBuilder()

            val testConsole = Console(
                AppendingTextOutput(
                    TeeAppendable(listOf(outputBuffer, System.err)),
                    isTtyLike = false,
                ),
                logLevel = Log.Fine,
            )
            val shellPreferences = ShellPreferences.default(testConsole)
                .copy(verbosity = ShellPreferences.Verbosity.Verbose)

            val result: BuildResult = doBuild(
                executorService = executorService,
                backends = backends,
                ignoreFile = null,
                workRoot = workRoot,
                shellPreferences = shellPreferences,
            )

            if (DUMP_FILE_TREE_TO_CONSOLE) {
                workRoot.resolve(TEMPER_KEEP_NAME).fileTree(console)
                workRoot.resolve(TEMPER_OUT_NAME).fileTree(console)
            }

            return result to outputBuffer
        }
    }
}

private val preStagingModuleRegex = Regex(
    // Look for the file names in "[<module-name>]: Pre-staging module from [<file-name>, ...]" messages.
    """(?:^|\n)\[[^\u005d]*]: \Q${
        MessageTemplate.PreStagingModule.formatString.replace("%s", """\E\[(.*)\]\Q""")
    }\E(?:$|(?=\n))""",
)
private val cannotTranslateRegex = Regex(
    """(?:^|\n)\[[^\u005d]*]: \Q${
        MessageTemplate.CannotTranslate.formatString.replace("%s", """\E.*\Q""")
    }\E(?:$|(?=\n))""",
)

// Allows assertions on text content with a message pointing back to the file that
// should have that content.
fun <T> Path.withTextOf(relativePath: String, action: ContextualizedFileContent.(String) -> T): T {
    val cfc = ContextualizedFileContent(relativePath)
    return cfc.action(this.resolve(relativePath).readText())
}
data class ContextualizedFileContent(
    val source: String,
) {
    fun assertContains(
        text: String,
        wantedSubstring: String,
        ignoreCase: Boolean = false,
    ) {
        assertContains(text, wantedSubstring, ignoreCase = ignoreCase, message = "Content from $source")
    }

    fun assertNotContains(
        text: String,
        wantedSubstring: String,
        ignoreCase: Boolean = false,
    ) {
        val index = text.indexOf(wantedSubstring, ignoreCase = ignoreCase)
        if (index >= 0) {
            console.log(
                "Expected the char sequence from <$source> to not contain the substring${
                    if (ignoreCase) ", case insensitively" else ""
                }",
            )
            console.group("CharSequence") {
                val n = wantedSubstring.length
                console.textOutput.emitLineChunk(text.substring(0, index))
                console.textOutput.startStyle(Style.ErrorToken)
                console.textOutput.emitLineChunk(text.substring(index, index + n))
                console.textOutput.endStyle()
                console.textOutput.emitLineChunk(text.substring(index + n))
            }
            console.group("Substring") {
                console.log(wantedSubstring)
            }
            fail("\nContent from source <$source> contains substring it shouldn't")
        }
    }
}
