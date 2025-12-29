package lang.temper.be.lua

import lang.temper.be.names.asciiNameRegex
import lang.temper.be.names.unicodeToAscii
import lang.temper.be.tmpl.TmpL
import lang.temper.common.putMultiList
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary

val luaKeywords = setOf(
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if",
    "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
)

data class LuaName(val text: String) {
    init {
        require(text.matches(asciiNameRegex))
    }
}

fun fixName(name: String): String = if (luaKeywords.contains(name)) {
    // If it was a keyword, it doesn't have bad chars.
    "${name}_"
} else {
    // Otherwise, it might.
    unicodeToAscii(name)
}

class LuaNames {
    private var nameCounter = 0
    private var labelCounter = 0

    private val map = mutableMapOf<ResolvedName, LuaName>()
    private val onBreaks = mutableMapOf<String, LuaName>()
    private val onContinues = mutableMapOf<String, LuaName>()
    private val pendingImports = mutableMapOf<Pair<ModuleName, ResolvedName>, MutableList<(LuaName) -> Unit>>()
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

    fun alias(key: ResolvedName, value: LuaName) {
        map[key] = value
    }

    fun importAsNeeded(name: ResolvedName, onImportCallback: (LuaName) -> Unit) {
        pendingImports.putMultiList(currentModuleName!! to name, onImportCallback)
    }

    fun name(
        name: ResolvedName,
    ): LuaName {
        val luaName = map.getOrPut(name) {
            when (name) {
                is ExportedName -> LuaName(fixName(name.baseName.nameText))
                is SourceName -> LuaName(fixName("${name.baseName.nameText}__${name.uid}"))
                is Temporary -> LuaName(fixName("${name.nameHint}_${nameCounter++}"))
                is BuiltinName -> LuaName(fixName(name.builtinKey))
            }
        }
        val addImportCallbacks = pendingImports.remove(currentModuleName to name)
        if (addImportCallbacks != null) {
            for (addImportCallback in addImportCallbacks) {
                addImportCallback(luaName)
            }
        }
        return luaName
    }

    fun name(
        id: TmpL.Id,
    ): LuaName = name(id.name)

    fun onBreak(
        key: String,
    ): LuaName = onBreaks.getOrElse(key) {
        val ret = LuaName("break_${labelCounter++}")
        onBreaks[key] = ret
        return ret
    }

    fun onContinue(
        key: String,
    ): LuaName = onContinues.getOrElse(key) {
        val ret = LuaName("continue_${labelCounter++}")
        onContinues[key] = ret
        return ret
    }

    fun gensym(): LuaName {
        return LuaName("local_${nameCounter++}")
    }
}

internal fun name(name: String): LuaName {
    return LuaName(name)
}

internal fun safeName(name: String): LuaName {
    return LuaName(fixName(name))
}
