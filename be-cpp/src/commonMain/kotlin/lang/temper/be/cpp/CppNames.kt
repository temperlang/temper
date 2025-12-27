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
val cppKeywords = setOf(
    "alignas", "alignof", "and", "and_eq", "asm", "atomic_cancel", "atomic_commit", "atomic_noexcept", "auto",
    "bitand", "bitor", "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class",
    "compl", "concept", "const", "consteval", "constexpr", "constinit", "const_cast", "continue", "contract_assert",
    "co_await", "co_return", "co_yield", "", "decltype", "default", "delete", "do", "double", "dynamic_cast", "else",
    "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int", "long",
    "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private",
    "protected", "public", "", "reflexpr", "register", "reinterpret_cast", "requires", "return", "short", "signed",
    "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "synchronized", "template", "this",
    "thread_local", "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual",
    "void", "volatile", "wchar_t", "while", "xor", "xor_eq",
)

class CppName(val text: String, allowKey: Boolean = false) {
    init {
        require(text.matches(asciiNameRegex)) {
            "not valid c++ name: `$text`"
        }
        if (!allowKey) {
            require(!text.matches(reservedRegex))
            require(!cppKeywords.contains(text))
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

    fun name(
        name: ResolvedName,
    ): CppName {
        val cppName = map.getOrPut(name) {
            when (name) {
                is ExportedName -> CppName(fixName("$prefix${name.baseName.nameText}"))
                is SourceName -> CppName(fixName("${name.baseName.nameText}__${name.uid}"))
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

internal fun nameForBuiltinKey(key: String): CppName {
    TODO("name for builtinKey = \"$key\"")
}

internal fun name(name: String): CppName {
    return CppName(name)
}

internal fun safeName(name: String): CppName {
    return CppName(fixName(name))
}
