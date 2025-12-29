package lang.temper.docbuild

internal val snippetExtractors: List<SnippetExtractor> = listOf(
    KotlinCommentExtractor,
    GrammarProductionExtractor,
    TemperCommentExtractor,
    TypeShapeExtractor,
    BuiltinEnvironmentExtractor,
    TemperCodeSnippetExtractor,
)
