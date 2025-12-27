package lang.temper.kcodegen.vscode.syntax

import lang.temper.common.structure.FormattingStructureSink
import lang.temper.kcodegen.CodeGenerator

object VscodeGrammarGenerator : CodeGenerator("langserver") {
    // No comments in json, so use object field.
    override val sourcePrefix = "\"$GENERATED_FILE_PREFIX\": \"VscodeGrammarGenerator\""

    private const val LANGUAGE_TAG = "js"
    override val languageTags = setOf(LANGUAGE_TAG)

    private const val FILE_EXTENSION = ".tmLanguage.json"
    override val fileExtensions = setOf(FILE_EXTENSION)

    override fun generateSources(): List<GeneratedSource> {
        val text = FormattingStructureSink.toJsonString(language.value) + "\n"
        val prefixedText = text.replaceFirst("{", "{ $sourcePrefix,")
        val grammarGeneration = GeneratedSource.create(
            baseName = "temper",
            content = prefixedText,
            contentHasErrors = false,
            directoryNameParts = listOf(LANGUAGE_TAG, "temper-language-support", "syntaxes"),
            ext = FILE_EXTENSION,
            group = "main",
        )
        return listOf(grammarGeneration)
    }
}
