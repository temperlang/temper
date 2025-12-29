package lang.temper.kcodegen

// Best effort actual implementation
@Suppress("FunctionOnlyReturningConstant", "UnusedPrivateMember")
internal actual fun findExistingGeneratedSourcesBestEffort(
    subProject: String
): List<KotlinCodeGenerator.GeneratedKotlinSource>? = null

@Suppress("UnusedPrivateMember")
internal actual fun updateExistingGeneratedSourcesBestEffort(
    subProject: String,
    generatedKotlinSources: List<KotlinCodeGenerator.GeneratedKotlinSource>
) {
    // Do nothing on JS since no reliable file-system access.
}
