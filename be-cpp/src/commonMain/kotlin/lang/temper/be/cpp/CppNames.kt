package lang.temper.be.cpp

import lang.temper.be.names.asciiNameRegex
import lang.temper.be.names.unicodeToAscii
import lang.temper.be.tmpl.TmpL
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import lang.temper.name.identifiers.IdentStyle

private val reservedRegex = Regex("^_[A-Z]|__")

// cppref: https://en.cppreference.com/w/cpp/keyword
// Public so the be-cppv backend (a separate module) can share the keyword set.
val cppKeywords = setOf(
    "alignas", "alignof", "and", "and_eq", "asm", "atomic_cancel", "atomic_commit", "atomic_noexcept", "auto",
    "bitand", "bitor", "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class",
    "compl", "concept", "const", "consteval", "constexpr", "constinit", "const_cast", "continue", "contract_assert",
    "co_await", "co_return", "co_yield", "decltype", "default", "delete", "do", "double", "dynamic_cast", "else",
    "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int", "long",
    "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private",
    "protected", "public", "reflexpr", "register", "reinterpret_cast", "requires", "return", "short", "signed",
    "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "synchronized", "template", "this",
    "thread_local", "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual",
    "void", "volatile", "wchar_t", "while", "xor", "xor_eq",
)

class CppName(val text: String, allowKey: Boolean = false, raw: Boolean = false) {
    init {
        if (!raw) {
            require(text.matches(asciiNameRegex)) {
                "not valid c++ name: `$text`"
            }
            if (!allowKey) {
                require(!text.matches(reservedRegex))
                require(!cppKeywords.contains(text))
            }
        }
    }

    override fun equals(other: Any?) = other is CppName && text == other.text
    override fun hashCode() = text.hashCode()
    override fun toString() = "CppName($text)"
}

fun fixName(name: String): String = if (cppKeywords.contains(name)) {
    // If it was a keyword, it doesn't have bad chars.
    "${name}_"
} else {
    // Otherwise, it might.
    unicodeToAscii(name)
}

class CppNames {
    private val prefixParts = mutableListOf<String>()

    private var nameCounter = 0

    private val map = mutableMapOf<ResolvedName, CppName>()
    private val bareNameOwners = mutableMapOf<String, ResolvedName>()
    private val pendingImports = mutableMapOf<Pair<ModuleName, ResolvedName>, MutableList<(CppName) -> Unit>>()
    private var currentModuleName: ModuleName? = null

    fun <T> forModule(moduleName: ModuleName, action: () -> T): T {
        val prior = currentModuleName
        currentModuleName = moduleName
        try {
            return action()
        } finally {
            currentModuleName = prior
        }
    }

    val prefix: String
        get() = prefixParts.joinToString("") { "${it}_" }

    private fun claimBareName(bare: String, owner: ResolvedName): Boolean {
        val existing = bareNameOwners[bare]
        if (existing == null) {
            bareNameOwners[bare] = owner
            return true
        }
        return existing === owner
    }

    /**
     * Builds a unique, valid C++ identifier for [owner] when its bare name is taken or reserved.
     *
     * The name's [uid] is appended after a single underscore, which keeps the result readable
     * (e.g. `myLocal_7` rather than the reserved double-underscore form `myLocal__7`). Because
     * `uid` is unique per resolved name this is almost always already distinct; in the rare case
     * two different bases plus uids still render the same text (or `base` itself ended in `_`, so
     * the underscore run had to be collapsed) a small counter is appended until [claimBareName]
     * confirms the text is free. This guarantees distinct resolved names get distinct identifiers.
     */
    private fun disambiguatedName(base: String, uid: Int, owner: ResolvedName): CppName {
        fun candidate(suffix: String): String {
            val joined = fixName("${base}_$suffix")
            // A trailing `_` on `base` (or anything fixName left) could create a `__` run, which
            // C++ reserves; collapse runs of underscores so the identifier stays legal.
            return if (joined.contains("__")) joined.replace(Regex("_+"), "_") else joined
        }
        var text = candidate("$uid")
        var attempt = 2
        while (!claimBareName(text, owner)) {
            text = candidate("${uid}_$attempt")
            attempt++
        }
        return CppName(text)
    }

    fun name(
        name: ResolvedName,
    ): CppName {
        val cppName = map.getOrPut(name) {
            when (name) {
                is ExportedName -> {
                    // Exported names are the public API, so they are authoritative: claim
                    // the bare name so a same-named local (e.g. a lifted local class that
                    // shares its name with an exported singleton value) is forced to a
                    // distinct, mangled name instead of colliding.
                    val bare = fixName("$prefix${name.baseName.nameText}")
                    claimBareName(bare, name)
                    CppName(bare)
                }
                is SourceName -> {
                    val bare = fixName(name.baseName.nameText)
                    if (!cppKeywords.contains(bare) && claimBareName(bare, name)) {
                        CppName(bare)
                    } else {
                        disambiguatedName(name.baseName.nameText, name.uid, name)
                    }
                }
                is Temporary -> CppName(fixName("${name.nameHint}_${nameCounter++}"))
                is BuiltinName -> CppName(fixName(name.builtinKey))
            }
        }
        val addImportCallbacks = pendingImports.remove(currentModuleName to name)
        if (addImportCallbacks != null) {
            for (addImportCallback in addImportCallbacks) {
                addImportCallback(cppName)
            }
        }
        return cppName
    }

    fun name(
        id: TmpL.Id,
    ): CppName = name(id.name)

    fun tmp(base: String = "tmp"): CppName {
        return CppName("${base}_${nameCounter++}")
    }

    fun library(name: String): CppName {
        return CppName(IdentStyle.Camel.convertTo(IdentStyle.Snake, name))
    }
}

