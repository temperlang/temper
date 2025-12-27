package lang.temper.tests

import lang.temper.common.asciiTitleCase
import lang.temper.fs.NativePath
import lang.temper.fs.temperRoot
import lang.temper.supportedBackends.testedBackends
import java.lang.Integer.max
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet
import kotlin.io.path.writer
import kotlin.test.Test
import kotlin.test.fail

class AllTestsTest {
    private val functionalTestSuiteRoot: NativePath =
        temperRoot.resolve("functional-test-suite")
    private val functionalTestMatrixPath: NativePath =
        temperRoot.resolve("functional-test-matrix.md")
    private val repoBaseUrl = "https://github.com/temperlang/temper"
    private val testSourceDirUrl = "$repoBaseUrl/blob/main/"

    /**
     * Java's lazy class loading makes it impossible for tests to auto-register with some central
     * list of tests, so we hand-code tests in [all] and do a file-walk here to double-check.
     *
     * `scripts/add-functional-test.perl` automates much of updating that list and test suite
     * interfaces.
     */
    @Test
    fun allHasAnEntryPerFile() {
        val ktFileBaseNames = mutableListOf<String>()
        val srcDir = functionalTestSuiteRoot.resolve("src")
        Files.walkFileTree(
            srcDir,
            EnumSet.of(FileVisitOption.FOLLOW_LINKS),
            Integer.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path?, attrs: BasicFileAttributes): FileVisitResult {
                    val fileName = file?.fileName?.toString()
                    if (fileName != null && fileName.endsWith(".kt")) {
                        ktFileBaseNames.add(fileName.substring(0, fileName.length - ".kt".length))
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        ktFileBaseNames.sort()

        val filesThatDoNotSpecifyTests = setOf(
            "AllTestsTest",
            "AssertTestingTestFromJunit",
            "FunctionalTestBase",
            "FunctionalTestSuiteI",
            "FunctionalTests",
            "FunctionalTestStatus",
            "Helpers",
            "MarkdownFileBasedFunctionalTestBase",
            "ModulePaths",
            "PreparedFunctionalTest",
            "QuickTests",
            "SingleFileFunctionalTestBase",
        )

        val testNames = FunctionalTests.entries.map { it.test::class.simpleName }

        val missing = ktFileBaseNames.filter {
            it !in filesThatDoNotSpecifyTests && it !in testNames
        }

        if (missing.isNotEmpty()) {
            fail(
                """
                Expected list of all tests to contain names: $missing.

                Either regenerate the list of tests via `gradle kcodegen:updateGeneratedCode`
                or add it to `filesThatDoNotSpecifyTests` which lists files that do not
                specify test cases in ${this::class}.
                """.trimIndent(),
            )
        }
    }

    @Test
    fun updateFunctionalTestMatrix() {
        val backendIds = testedBackends.sorted()

        // Not a test, more of a comment.
        functionalTestMatrixPath.writer(Charsets.UTF_8).use { out ->
            out.write(
                """
                    # Functional Test Matrix

                    | Test | ${backendIds.joinToString(" | ") { it.uniqueId.asciiTitleCase() }} |
                    | ---- | ${backendIds.joinToString(" | ") { mdTableDelimiter(it.uniqueId) }} |

                """.trimIndent(),
            )
            val links = mutableMapOf<String, String>()
            val allSorted = FunctionalTests.entries.sortedBy { it.name }
            for (testEnum in allSorted) {
                val test = testEnum.test
                val testName = testEnum.name

                val testSourceUrl = test.sourcePath
                out.write(
                    "| [$testName][${markdownLink(links, testName, testSourceDirUrl + testSourceUrl)}] |",
                )

                for (backendId in backendIds) {
                    out.write(" ")
                    when (val bugs = testEnum.disposition(backendId)) {
                        Disposition.Run -> out.write("✅")
                        is Disposition.Skip -> {
                            out.write("❌")
                            if (bugs.issues.isNotEmpty()) {
                                bugs.issues.joinTo(out, ", ", "<sup>", "</sup>") { bug ->
                                    val tag = markdownLink(links, bug.toString(), "$repoBaseUrl/issues/$bug")
                                    "[$bug][$tag]"
                                }
                            }
                        }
                    }
                    out.write(" |")
                }
                out.write("\n")
            }
            out.write("\n")
            for ((url, tag) in links.entries.sortedBy { p -> p.value }) {
                out.write("[$tag]: $url\n")
            }
        }
    }
}

private fun markdownLink(map: MutableMap<String, String>, tag: String, url: String): String {
    val uTag = when (val exTag = map[url]) {
        null -> {
            map[url] = tag
            tag
        }
        else -> exTag
    }
    return if (uTag == tag) "" else uTag
}

/**
 * IntelliJ's markdown preview breaks when a cell has 2 or less, e.g. when the column title "js"
 * is followed by a delimiter cell "--".
 */
const val minDashesInMarkdownTableDelimiterRowCell = 3
private fun mdTableDelimiter(tableColumnHeader: String): String {
    val n = max(tableColumnHeader.length, minDashesInMarkdownTableDelimiterRowCell)
    return "-".repeat(n)
}
