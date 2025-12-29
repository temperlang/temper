package lang.temper.cli

import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.library.LibraryConfiguration
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class DoInitTest {
    @Test
    fun failToInitProjectInDirWithConfig() {
        failToInit("FailToInitProjectInDirWithConfig") { projectDir ->
            projectDir.resolve(LibraryConfiguration.fileName.fullName).writeText("Hi there!")
        }
    }

    @Test
    fun failToInitProjectInDirWithSrc() {
        failToInit("FailToInitProjectInDirWithSrc") { projectDir ->
            projectDir.resolve("src").createDirectories()
        }
    }

    @Test
    fun initProjectInExistingDirWithContent() {
        runWithTemporaryDirectory("InitProjectInExistingDir") { parent ->
            val projectDir = parent.resolve("something")
            // Existing is ok if doesn't have a config yet.
            projectDir.toFile().mkdir()
            val dummyContent = "Hi there!"
            projectDir.resolve("whatever.txt").writeText(dummyContent)
            initAndAssert(projectDir)
        }
    }

    @Test
    fun initProjectInNewDir() {
        runWithTemporaryDirectory("InitProjectInNewDir") { parent ->
            // If the directory is missing, init should make it.
            initAndAssert(parent.resolve("something"))
        }
    }

    @Test
    fun initProjectWithNameConfig() {
        runWithTemporaryDirectory("InitProjectWithNameConfig") { parent ->
            // If the directory is missing, init should make it.
            initAndAssert(parent.resolve("config"), libName = "config-lib")
        }
    }
}

private fun assertExists(path: Path) {
    assertTrue(path.exists(), "Expected $path to exist")
}

private fun failToInit(testName: String, prepare: (Path) -> Unit) {
    runWithTemporaryDirectory(testName) { parent ->
        val projectDir = parent.resolve("something")
        projectDir.toFile().mkdir()
        prepare(projectDir)
        // We error if the init dir isn't empty.
        projectDir.resolve(LibraryConfiguration.fileName.fullName).writeText("Hi there!")
        try {
            initProject(projectDir)
            fail("Expected error")
        } catch (_: ReportableException) {
            // Expected behavior.
        }
    }
}

private fun initAndAssert(projectDir: Path, libName: String = projectDir.name) {
    initProject(projectDir)
    val srcDir = projectDir.resolve("src")
    assertTrue(!projectDir.resolve(LibraryConfiguration.fileName.fullName).exists())
    assertExists(srcDir.resolve(LibraryConfiguration.fileName.fullName))
    assertExists(srcDir.resolve("$libName.temper.md"))
    // TODO(tjp, tooling): Also assert building without errors and/or initial placeholder behavior?
    // TODO(tjp, tooling): Maybe skip until BuildTest/RunTest becomes more reliable.
}
