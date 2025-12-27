package lang.temper.be.js

import lang.temper.common.compatRemoveLast
import lang.temper.common.sprintf
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.NamingContext
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary

/**
 * Responsible for allocating JavaScript identifiers and keeping track of relationships between
 * common resources and JS names.
 */
internal class JsNames {
    private val nameExclusion = jsReservedWordsAndNames.toMutableSet()
    private var nameCounter = 0
    private val availableAliases = mutableMapOf<ResolvedName, (JsIdentifierName) -> Unit>()
    private val localAliases = mutableMapOf<ResolvedName, JsIdentifierName>()
    private val tmpLNameToJsName = mutableMapOf<ResolvedName, JsIdentifierName>()
    private val tmpLNameToPrivateName = mutableMapOf<ResolvedName, JsIdentifierName>()
    var origin: NamingContext? = null
        private set

    fun <T> forOrigin(newOrigin: NamingContext?, f: () -> T): T {
        check(origin == null)
        origin = newOrigin
        try {
            return f()
        } finally {
            origin = null
            localAliases.clear()
        }
    }

    fun useAlias(temperName: ResolvedName, jsName: JsIdentifierName) {
        localAliases[temperName] = jsName
    }

    /**
     * Like [useAlias] but allocates a local name on demand and passes it back via the
     * callback so that an import declaration can be generated.
     * Should a name be allocated, [useAlias] will be called for each [forOrigin] that
     * needs it.
     */
    fun useAliasAsNeeded(temperName: ResolvedName, importNeeded: (JsIdentifierName) -> Unit) {
        availableAliases[temperName] = importNeeded
    }

    /** A name that has not been returned by a previous call to this name generator. */
    fun unusedName(formatString: String): JsIdentifierName {
        require("%d" in formatString)
        val unused = sprintf(formatString, listOf(nameCounter++))
        require(unused !in nameExclusion)
        nameExclusion.add(unused)
        return JsIdentifierName(unused)
    }

    fun isThisName(name: ResolvedName) = name in thisNameSet

    /**
     * `null` if [name] is a special name and [useThisNameStack] or otherwise a name uniquely
     * associated with the given Temper name.
     */
    fun jsName(name: ResolvedName, useThisNameStack: Boolean = true): JsIdentifierName? {
        return if (useThisNameStack && name in thisNameSet) {
            if (name in monitoredCalls) {
                monitoredCalls[name] = monitoredCalls[name]!! + 1
            }
            val index = thisNameStack.indexOfLast { name == it.declaredName }
            val stackElement = thisNameStack[index]
            if (index == thisNameStack.lastIndex) {
                // In the same function.  Return null to signal that the keyword `this` is
                // appropriate.
                null
            } else {
                var unmaskedName = stackElement.unmaskedName
                if (unmaskedName == null) {
                    unmaskedName = unusedName("this%d")
                    stackElement.unmaskedName = unmaskedName
                }
                unmaskedName
            }
        } else {
            jsNameNotThis(name)
        }
    }

    fun jsNameNotThis(name: ResolvedName): JsIdentifierName {
        if (name in monitoredCalls) {
            monitoredCalls[name] = monitoredCalls[name]!! + 1
        }
        return when (name) {
            is ExportedName if name.comesFrom(origin) -> {
                JsIdentifierName.escaped(name.baseName.nameText)
            }
            in localAliases -> localAliases.getValue(name)
            in availableAliases -> {
                val localName = unusedName(toSafePattern(name))
                useAlias(name, localName)
                availableAliases.getValue(name)(localName)
                localName
            }
            else -> tmpLNameToJsName.getOrPut(name) {
                unusedName(toSafePattern(name))
            }
        }
    }

    /** A `#name` style private member name suitable for [Js.PrivateName]. */
    fun privateName(name: ResolvedName): JsIdentifierName = tmpLNameToPrivateName.getOrPut(name) {
        unusedName(toSafePattern(name))
    }

    /**
     * An sprintf format string suitable for [unusedName] that is like the input name but, when
     * the `%d` is replaced with ASCII digits, matches [JsIdentifierGrammar].
     */
    private fun toSafePattern(name: ResolvedName): String {
        val unsafePrefix = name.prefix()
        val safePrefix = JsIdentifierGrammar.massageJsIdentifier(unsafePrefix)
        return "${safePrefix}_%d"
    }

    private val thisNameStack = mutableListOf<ThisNames>()

    /** All declared names on the stack. */
    private val thisNameSet = mutableSetOf<ResolvedName>()

    /**
     * Runs [f] in a scope where calls to [jsName]\([thisName]\) == `null` so that
     * [JsTranslator.translateId] produces [Js.ThisExpression].
     *
     * @return The result of `f()` paired with the name by which generated code referred to `this`
     * within this scope in nested scopes where `this` might have been masked.
     */
    internal fun <T> withLocalNameForThis(
        thisName: ResolvedName?,
        f: () -> T,
    ): Pair<T, JsIdentifierName?> {
        var updatedSet = false
        if (thisName != null && thisName !in thisNameSet) {
            updatedSet = true
            thisNameSet.add(thisName)
        }
        thisNameStack.add(ThisNames(thisName))
        val result = f()
        val top = thisNameStack.compatRemoveLast()
        if (updatedSet) {
            thisNameSet.remove(thisName)
        }
        return result to top.unmaskedName
    }

    /**
     * Lets us know if a name is used when translating a scope where we're substituting one name
     * for another, so we can recover by creating an alias after the fact between the names.
     *
     * This happens in method definitions, where we might translate the TmpL method
     *
     *     @method(\foo) @fromType(C__0) fn foo_123() {}
     *
     * which has a name `foo_123` but we want to primarily expose it in JavaScript as
     *
     *     class C {
     *         foo() {}
     *     }
     *
     * via the name `this.foo` which, because it has to be used as a dot name, will not mask any
     * `foo` in an enclosing scope.
     *
     * @return the result of [f]\(\) paired with a boolean that is true when [jsName]\([name]\)
     *     was called during the call to [f].
     */
    internal fun <T> monitorUsesOfName(
        name: ResolvedName,
        f: () -> T,
    ): Pair<T, Boolean> {
        var beforeF = monitoredCalls[name]
        val needToRemoveFromMonitoredAfterF = beforeF == null
        if (needToRemoveFromMonitoredAfterF) {
            beforeF = 0
            monitoredCalls[name] = 0
        }

        val result = f()

        val afterF = monitoredCalls[name]
        if (needToRemoveFromMonitoredAfterF) {
            monitoredCalls.remove(name)
        }
        return result to (beforeF != afterF)
    }

    private val monitoredCalls = mutableMapOf<ResolvedName, Int>()
}

private class ThisNames(val declaredName: ResolvedName?) {
    var unmaskedName: JsIdentifierName? = null
}

fun ResolvedName.prefix(): String = when (this) {
    is BuiltinName -> this.builtinKey
    is Temporary -> this.nameHint
    is SourceName -> this.baseName.nameText
    is ExportedName -> this.baseName.nameText
}
