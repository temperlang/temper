package lang.temper.kcodegen.functests

import lang.temper.common.jsonEscaper
import lang.temper.common.putMulti
import lang.temper.common.toStringViaBuilder
import lang.temper.kcodegen.CodeGenerator
import lang.temper.kcodegen.KotlinCodeGenerator
import lang.temper.kcodegen.globScanBestEffort
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.name.identifiers.IdentStyle

object FunctionalTestSuitener : CodeGenerator("functional-test-suite") {
    override val sourcePrefix: String
        get() = "$GENERATED_FILE_PREFIX(\"FunctionalTestSuitener\")"
    override val languageTags: Set<String>
        get() = setOf("resources", "kotlin")
    override val fileExtensions: Set<String>
        get() = setOf(".temper.md", ".kt")

    override fun generateSources(): List<GeneratedSource> {
        val fileGlob = "**/resources/**/*{.temper.md,.temper}"
        val files = globScanBestEffort(subProject, emptyList(), fileGlob).map { (parts, text) ->
            filePath(parts) to text
        }
        val dirsWithTestFiles = buildSet {
            files.mapTo(this) { (filePath) -> filePath.dirName() }
        }
        // Create a test module for each directory that contains Temper source files and which is not a
        // subdirectory of such a directory.
        val tests = buildMap<FilePath, TestModule> {
            checkOneDir@
            for (dir in dirsWithTestFiles) {
                if (dir == FilePath.emptyPath) { continue }
                // If it's a descendant of a root, then it's not a root.
                var p = dir
                while (p != FilePath.emptyPath) {
                    p = p.dirName()
                    if (p in dirsWithTestFiles) {
                        continue@checkOneDir
                    }
                }
                this[dir] = TestModule(dir)
            }
        }
        for ((path, text) in files) {
            // Scan upwards to find the test.
            val testDir = path.ancestors(skipThis = true).first { it in tests }
            val testMod = tests.getValue(testDir)
            testMod.accept(path.last(), text)
        }
        val testMods = tests.entries.sortedBy { it.key }.map { it.value }

        val ftInterface = toStringViaBuilder {
            it.appendLine(sourcePrefix)
            it.append(FT_INTER_HEADER)
            var before = ""
            for (mod in testMods) {
                if (!mod.skipEntirely) {
                    it.append(before)
                    before = "\n"
                    it.ftInterMethod(mod)
                }
            }
            it.append(FT_INTER_FOOTER)
        }
        val ftEnum = toStringViaBuilder {
            it.appendLine(sourcePrefix)
            it.append(FT_ENUM_HEADER)
            for (mod in testMods) {
                if (!mod.skipEntirely) {
                    it.ftEnumEntry(mod)
                }
            }
            it.append(FT_ENUM_FOOTER)
        }
        val configTemper = toStringViaBuilder {
            it.appendLine("<!-- $sourcePrefix -->")
            it.append(CONFIG_HEADER)
            for (mod in testMods) {
                if (!mod.skipEntirely) {
                    it.ctmImport(mod)
                }
            }
            it.append(CONFIG_FOOTER)
        }
        return listOf(
            KotlinCodeGenerator.GeneratedKotlinSource(
                packageNameParts = listOf("lang", "temper", "tests"),
                baseName = "FunctionalTestSuiteI",
                content = ftInterface,
            ),
            KotlinCodeGenerator.GeneratedKotlinSource(
                packageNameParts = listOf("lang", "temper", "tests"),
                baseName = "FunctionalTests",
                content = ftEnum,
            ),
            GeneratedSource.create(
                listOf("resources"),
                baseName = "config",
                ext = ".temper.md",
                content = configTemper,
                contentHasErrors = false,
            ),
        )
    }
}

internal class TestModule(
    /**
     * A path like: commonMain/resources/foo-bar/qux/wat-okay
     * Generate a pair of names (fooBarQuxWatOkay, FooBarQuxWatOkay)
     */
    val path: FilePath,
) {
    val relToResources: FilePath
    val methodName: String
    val enumName: String
    init {
        require(path.isDir) { "Path $path is not a directory" }
        val seg = path.segments
        val idx = seg.indexOfFirst { it.fullName == "resources" }
        require(idx >= 0) { "Path $path is not under resources" }
        val afterResources = seg.subList(idx + 1, seg.size)
        relToResources = FilePath(afterResources, isDir = true)
        val fullDash = afterResources.joinToString("-") { it.baseName }
        methodName = IdentStyle.Dash.convertTo(IdentStyle.Camel, fullDash)
        enumName = IdentStyle.Dash.convertTo(IdentStyle.Pascal, fullDash)
    }

    private val expectTemperBase: String get() = path.last().baseName

    private val temperFiles: MutableList<FilePathSegment> = mutableListOf()

    private val correctTemperFile get() = temperFiles.any {
        it.temperBaseName == expectTemperBase
    }
    private val hasOneTemperFile get() = temperFiles.size == 1

    val testFile: FilePathSegment get() = when (temperFiles.size) {
        1 -> temperFiles[0]
        0 -> null
        else -> temperFiles.firstOrNull { it.temperBaseName == expectTemperBase }
    } ?: error("no well-defined test file")

    // Various bits of metadata
    private val metadata = mutableMapOf<String, MutableList<String>>()

    // Arguments to the markdown function
    val args get() = metadata.getOrElse("arg", ::listOf).toList()

    // Arguments to the markdown function
    val todos get() = metadata.getOrElse("todo", ::listOf).toList()

    val warning get() = when {
        correctTemperFile -> null
        else -> "did not find ${expectTemperBase}.temper.md in $path"
    }
    val disabled get() = !correctTemperFile && !hasOneTemperFile
    val skipEntirely get() = temperFiles.isEmpty()

    fun accept(file: FilePathSegment, text: () -> String) {
        temperFiles.add(file)
        for (line in text().lineSequence()) {
            metadataMatch.matchEntire(line) ?. let {
                metadata.putMulti(it.groups[1]!!.value, it.groups[2]!!.value, ::mutableListOf)
            }
        }
    }
}

private val metadataMatch = Regex("""^@meta:([a-z]+):? (.*)\s*$""")

/** Quoting a path for Kotlin */
fun FilePath.quoted(): String = jsonEscaper.escape(this.join())

/** Clean up .temper.md and .temper */
val FilePathSegment.temperBaseName get() = this.withTemperAwareExtension("").fullName
