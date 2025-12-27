package lang.temper.tests

import lang.temper.fs.NativePath
import lang.temper.fs.filePathSegment
import lang.temper.fs.list
import lang.temper.fs.read
import lang.temper.fs.resolve
import lang.temper.fs.temperRoot
import lang.temper.lexer.isTemperFile
import lang.temper.log.FilePath
import lang.temper.log.last
import lang.temper.log.resolveFile
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import kotlin.io.path.isDirectory

/**
 * A test case that can easily be defined as a singleton `object`.
 *
 * Instances should be part of [all] so that backends can find tests.
 *
 * Tests should have a class name that is the same as the basename of the file in
 * which they are defined so that the "there are no tests that we forget to run"
 * test doesn't get confused.
 */
abstract class FunctionalTestBase {
    abstract val testName: String

    /**
     * This is the root of the constructed Temper library for this test.
     * Not the same as [FunctionalTestBase.projectPath].
     */
    val projectRoot: FilePath get() = ModulePaths.defaultProjectRoot

    /**
     * Really, "testFileSourcePath". Path relative to [temperRoot] specifying where to find source files
     * for this test.
     *
     * If it is a directory, then we look for a [Temper file][isTemperFile] that starts with `main.`
     * in that directory.
     * If it is not a directory, then it specifies a path to the [main file][mainFile].
     */
    abstract val projectPath: FilePath

    /**
     * The directory, relative to the Temper project root, in which Temper source files related
     * to the test are found.
     */
    private val directoryPath: FilePath
        get() = if (projectPath.isDir) { projectPath } else { projectPath.dirName() }

    private val localDirPath: NativePath
        get() = temperRoot.resolve(directoryPath)

    /**
     * The name of the constructed Temper library for this test.
     */
    val libraryName: DashedIdentifier get() = projectRoot.let { root ->
        require(root.isDir)
        DashedIdentifier(root.last().baseName)
    }

    /**
     * Maps file paths to the textual content of files.
     *
     * Functional test runners are allowed to process these files in order, so exporters should
     * precede their importers.
     *
     * Keys should be under [projectRoot].
     */
    fun gatherFiles(keep: (FilePath) -> Boolean): Map<FilePath, String> = buildMap {
        fun addFiles(localPath: NativePath, projectPath: FilePath) {
            if (localPath.isDirectory()) {
                for (item in localPath.list().sorted()) {
                    addFiles(
                        item,
                        projectPath.resolve(item.filePathSegment, isDir = item.isDirectory()),
                    )
                }
            } else if (keep(projectPath)) {
                val content = localPath.read()
                put(projectPath, content)
            }
        }
        addFiles(localDirPath, projectRoot)
    }

    /**
     * Maps file paths to content per [gatherFiles] but cached and for Temper files only.
     */
    val temperFiles: Map<FilePath, String> by lazy {
        gatherFiles { it.isTemperFile }.also { files ->
            if (files.isEmpty()) {
                error("Could not find test files at $directoryPath; looking at: $localDirPath")
            }
        }
    }

    /** The test entry point; this should be a key within [temperFiles]. */
    val mainFile: FilePath
        get() = this.projectPath.let { path ->
            if (path.isDir) {
                temperFiles.keys.last {
                    it.segments.lastOrNull()?.fullName?.startsWith("main.") == true
                }
            } else {
                projectRoot.resolveFile(path.segments.last())
            }
        }

    val mainModuleName: ModuleName
        get() {
            // The rule enforced by FunctionalTestSuitener is that
            // the library root is the directory containing the main file.
            val dirContainingMainFile = this.mainFile.dirName()
            return ModuleName(
                sourceFile = dirContainingMainFile,
                libraryRootSegmentCount = dirContainingMainFile.segments.size,
                isPreface = false,
            )
        }

    /** The expected output from running [mainFile]. */
    abstract val expectedOutput: String

    /**
     * Which compile-time errors are allowed, as identified by message template names.
     * By default, none are, but some tests are for behavior even in the face of errors.
     */
    open val allowedErrors: Set<String> get() = emptySet()

    /**
     * The test path within functional-test-suite, either to the Kotlin code or the temper source file.
     * Quick tests, such as those defined directly in [FunctionalTests] may define this in terms of their main
     * test file.
     */
    abstract val sourcePath: FilePath

    /**
     * If the contents of the test is temper test code not application code
     */
    open val runAsTest: Boolean get() = false

    /**
     * If running for simple output, do we expect run failure?
     */
    open val expectRunFailure: Boolean get() = false

    /**
     * If [runAsTest] the names of the tests that are expected to fail
     */
    open val expectedTestFailures: Map<String, String> get() = emptyMap()
}
