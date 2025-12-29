package lang.temper.kcodegen

import lang.temper.common.Log
import lang.temper.common.abbreviate
import lang.temper.common.console
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment

/**
 * Can be run to generate Kotlin classes from sources.
 *
 * Trying to get multi-platform gradle to work with generated sources is a headache.
 * Instead, we define code generators in Kotlin, check in the code, and use the test runner to
 * ensure that they stay up-to-date.
 */
abstract class CodeGenerator(internal val subProject: String) {

    /**
     * Text that must appear in the first line of every file owned by this code generator.
     * This is used when scanning files under [subProject].
     *
     * This must start with [GENERATED_FILE_PREFIX].
     */
    internal abstract val sourcePrefix: String

    /**
     * Under .../[subProject]/src/[GeneratedSource.group]` there is a directory that specifies
     * the language.
     * For example, Kotlin files go under `.../mySubProjects/src/commonMain/kotlin` so the language
     * tag is `kotlin`.
     * Each code generator should own files under one or more language tags.
     * `resources` is a valid language tag.
     */
    abstract val languageTags: Set<String>

    /**
     * Extension of generated files to consider.
     * Every [GeneratedSource]'s [GeneratedSource.ext] must be in this for [bringUpToDate] to work.
     * Like that field, the extension should include the `.` if any.
     */
    abstract val fileExtensions: Set<String>

    abstract class GeneratedSource protected constructor(
        /**
         * Textual content of the file or `null` to indicate that the file doesn't/shouldn't exist.
         */
        val content: String?,
    ) {
        abstract val directoryNameParts: List<String>
        abstract val baseName: String
        abstract val contentHasErrors: Boolean

        /** The file extension with preceding `.` or empty string if no extension. */
        abstract val ext: String
        open val group: String get() = DEFAULT_SRC_GROUP

        /** Relative to *subProject*`/src` */
        val path: FilePath
            get() = FilePath(
                (listOf(group) + directoryNameParts + listOf("$baseName$ext"))
                    .map { FilePathSegment(it) },
                isDir = false,
            )

        final override fun hashCode(): Int = (content?.hashCode() ?: 0) + 31 * path.hashCode()
        final override fun equals(other: Any?): Boolean = other is GeneratedSource &&
            this.path == other.path && this.content == other.content

        override fun toString(): String = "GeneratedSource(${path.join()})"

        init {
            require(content?.firstLine?.contains(GENERATED_FILE_PREFIX) != false) {
                """
                No generated file prefix on
                $content
                """.trimIndent()
            }
        }

        companion object {
            fun create(
                /** Relative to `<projectRoot>/src/<group>` */
                directoryNameParts: List<String>,
                baseName: String,
                /** The file extension with preceding `.` or empty string if no extension. */
                ext: String,
                content: String?,
                contentHasErrors: Boolean,
                group: String = DEFAULT_SRC_GROUP,
            ): GeneratedSource = GeneratedSourceData(
                directoryNameParts = directoryNameParts,
                baseName = baseName,
                ext = ext,
                content = content,
                contentHasErrors = contentHasErrors,
                group = group,
            )
        }
    }

    abstract fun generateSources(): List<GeneratedSource>

    private fun beforeAndAfter(): Pair<Map<FilePath, GeneratedSource>, Map<FilePath, GeneratedSource>>? {
        val before = findExistingGeneratedSourcesBestEffort(this) ?: return null
        val after = generateSources()

        val beforeMap = before.associateBy({ it.path }, { it })
        val afterMap = after.associateBy({ it.path }, { it })

        return beforeMap to afterMap
    }

    /** Checks that the generated sources are up-to-date.  This should be the basis for a `@Test` */
    fun assertUpToDate(stringsDiffer: (want: String, got: String, context: FilePath) -> Nothing) {
        val (beforeMap, afterMap) = beforeAndAfter() ?: return

        for (key in beforeMap.keys + afterMap.keys) {
            val beforeSource = beforeMap[key]
            val afterSource = afterMap[key]

            // Preprocess out the imports so that diff also excludes them, in case it gives wrong idea about diff.
            val want = beforeSource?.content?.replace(IMPORT_RE, "") ?: "<NO SUCH GENERATED FILE>"
            val got = afterSource?.content?.replace(IMPORT_RE, "") ?: "<DOES NOT EXIST>"
            if (want.replace(WS_RE, " ") != got.replace(WS_RE, " ")) {
                // Retain whitespace for diff, since that's easier to handle for readers.
                stringsDiffer(want, got, key)
            }
        }
    }

    /** May be exposed via a `main` method to allow manual running when [assertUpToDate] fails. */
    fun bringUpToDate() {
        val (beforeMap, afterMap) = beforeAndAfter() ?: return

        val updates = mutableListOf<GeneratedSource>()
        val allKeys: Set<FilePath> = beforeMap.keys + afterMap.keys
        for (key in allKeys) {
            val before = beforeMap[key]
            val after = afterMap[key]
            if (before != after) {
                if (before?.contentHasErrors == false && after?.contentHasErrors == true) {
                    console.group(
                        "Not updating $key due to errors generating its replacement",
                        level = Log.Error,
                    ) {
                        console.error(
                            abbreviate(after.content ?: "<NULL>", maxlen = MAX_CONTENT_ABBREV_LEN),
                        )
                    }
                    continue
                }
                updates.add(
                    afterMap[key]
                        ?: beforeMap.getOrElse(key) { error(key) }.let {
                            GeneratedSourceData(
                                directoryNameParts = it.directoryNameParts,
                                baseName = it.baseName,
                                ext = it.ext,
                                content = null,
                                contentHasErrors = it.contentHasErrors,
                                group = it.group,
                            )
                        },
                )
            }
        }

        if (updates.isNotEmpty()) {
            updateExistingGeneratedSourcesBestEffort(subProject, updates.toList())
        }
    }

    companion object {
        /** A prefix that must be on [GeneratedSource.content] */
        const val GENERATED_FILE_PREFIX = "@file:lang.temper.common.Generated"

        /** Matching whitespace for a whitespace insensitive comparison. */
        private val WS_RE = Regex("\\s+")

        /** Skip imports because sometimes we don't need all the automatics, and we have to please linters. */
        private val IMPORT_RE = Regex("^import .*\n", RegexOption.MULTILINE)
    }
}

private class GeneratedSourceData(
    override val directoryNameParts: List<String>,
    override val baseName: String,
    override val ext: String,
    content: String?,
    override val contentHasErrors: Boolean,
    override val group: String,
) : CodeGenerator.GeneratedSource(content = content)

internal expect fun findExistingGeneratedSourcesBestEffort(
    codeGenerator: CodeGenerator,
): List<CodeGenerator.GeneratedSource>?

internal expect fun updateExistingGeneratedSourcesBestEffort(
    subProject: String,
    generatedSources: List<CodeGenerator.GeneratedSource>,
)

/**
 * May be used to scan for inputs to the code generator.
 */
internal expect fun globScanBestEffort(
    /** Temper subproject */
    subProject: String,
    /** Relative to the source root of [subProject] */
    startPath: List<String>,
    /**
     * A [glob](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob) like
     * <code>**\/\*.foo</code>
     */
    glob: String,
): Iterable<Pair<List<String>, () -> String>>

internal expect fun subProjectsMatchingBestEffort(pattern: Regex): Iterable<String>

internal val (String).firstLine: String get() {
    for (i in indices) {
        val c = this[i]
        if (c == '\n' || c == '\r') {
            return substring(0, i)
        }
    }
    return this
}

const val DEFAULT_SRC_GROUP = "commonMain"
private const val MAX_CONTENT_ABBREV_LEN = 1000 // Chars
