package lang.temper.kcodegen.vscode.syntax

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured

// Some references:
// https://code.visualstudio.com/api/language-extensions/syntax-highlight-guide
// https://macromates.com/manual/en/language_grammars
// https://github.com/microsoft/vscode/blob/main/extensions/typescript-basics/syntaxes/TypeScript.tmLanguage.json

data class Language(
    /** Top-level rules, usually references into the repository. */
    val patterns: List<Rule>,

    /** A dictionary of rules that can be referenced. */
    val repository: Map<String, Rule>,

    /** The scope name for the language as a whole. */
    val scope: String,

    /** The name or title of this grammar or language, meant for humans. */
    val title: String,

    // This might be nice to have, but it's awkward for the current context and not really needed.
    // /** A git commit for example. MS gives a full github commit link. */
    // val version: String,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        // Use key names as defined for TextMate or otherwise as used by Microsoft in their grammars.
        // This is the order usually used by Microsoft.
        // Skip for now: key("version") { value(version) }
        // And yes, MS uses "name" here, perhaps based on prior usage by others.
        // Don't know, but it's not in original docs or schema, where "name" usually means what MS calls "scope".
        key("name") { value(title) }
        key("scopeName") { value(scope) }
        key("patterns") { value(patterns) }
        key("repository") { sorted(repository) }
    }
}

data class Scoped(val scope: String) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("name") { value(scope) }
    }
}

sealed interface Rule : Structured

sealed interface Pattern : Rule {
    /** Called "name" in the json schema but "scope" in the vscode docs, perhaps for clarity. */
    val scope: String?
}

data class Choice(val patterns: List<Rule>) : Rule {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("patterns") { value(patterns) }
    }
}

data class Flat(
    override val scope: String? = null,

    /** Either [scope] or [captures] or both should be present. */
    val captures: Map<Int, Scoped>? = null,

    val match: String,
) : Pattern {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        scope?.let { key("name") { value(it) } }
        key("match") { value(match) }
        captures?.let { key("captures") { sorted(it) } }
    }
}

data class Nest(
    override val scope: String? = null,

    val begin: String,

    val beginCaptures: Map<Int, Scoped>? = null,

    /** Scope for the content excluding begin and end. */
    val contentScope: String? = null,

    val end: String,

    val endCaptures: Map<Int, Scoped>? = null,

    /** Patterns to match inside the content. */
    val patterns: List<Rule>? = null,
) : Pattern {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        scope?.let { key("name") { value(it) } }
        key("begin") { value(begin) }
        beginCaptures?.let { key("beginCaptures") { sorted(it) } }
        key("end") { value(end) }
        endCaptures?.let { key("endCaptures") { sorted(it) } }
        contentScope?.let { key("contentName") { value(it) } }
        patterns?.let { key("patterns") { value(it) } }
    }
}

/** A reference to a rule in the current language's repository. */
data class Ref(
    /** The repository rule key without the '#', which is added on destructure. */
    val key: String,
) : Rule {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("include") { value("#$key") }
    }
}

// Additional reference types that we won't need.
// data class Lang(val scopeName: String) : Rule
// object Self : Rule
