package lang.temper.kcodegen

/**
 * Can be run to generate Kotlin classes from sources.
 *
 * Trying to get multi-platform gradle to work with generated sources is a headache.
 * Instead, we define code generators in Kotlin, check in the code, and use the test runner to
 * ensure that they stay up-to-date.
 */
abstract class KotlinCodeGenerator(subProject: String) : CodeGenerator(subProject) {

    class GeneratedKotlinSource(
        val packageNameParts: List<String>,
        override val baseName: String,
        content: String?,
        override val group: String = DEFAULT_SRC_GROUP,
        override val contentHasErrors: Boolean = false,
    ) : GeneratedSource(content = content) {
        override val directoryNameParts: List<String>
            get() = listOf(LANGUAGE_TAG) + packageNameParts

        override val ext get() = EXT
    }

    override val fileExtensions: Set<String> get() = extSet

    override val languageTags: Set<String> get() = languageTagSet

    abstract override fun generateSources(): List<GeneratedKotlinSource>

    companion object {
        /** Extension auto appended after [GeneratedKotlinSource.baseName] */
        const val EXT = ".kt"
        val extSet = setOf(EXT)
        const val LANGUAGE_TAG = "kotlin"
        val languageTagSet = setOf(LANGUAGE_TAG)
    }
}
