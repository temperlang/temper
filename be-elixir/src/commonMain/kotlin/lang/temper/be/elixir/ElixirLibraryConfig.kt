package lang.temper.be.elixir

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.staging.buildConfigType
import lang.temper.library.LibraryConfiguration
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogSink
import lang.temper.name.Symbol
import lang.temper.value.BlockTree
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value

/**
 * A library's `elixir` config, which `config.temper.md` declares as
 *
 *     export let elixir = {
 *       class: ElixirConfig,
 *       dependencies: ["decimal ~> 3.1"],
 *     };
 *
 * Each dependency is a Hex package name and a Mix version requirement,
 * which become `{:decimal, "~> 3.1"}` in the library's `mix.exs`, for its
 * `_connected.ex` to use.
 */
internal class ElixirLibraryConfig(private val config: LibraryConfiguration) {
    /**
     * The library's Hex dependencies, or the reasons they cannot be, one per
     * entry that is not one. An `elixir` export that is not an `ElixirConfig`
     * is a problem too: other backends log it and build as if it were absent,
     * which drops every dependency it meant to declare.
     */
    fun hexDependencies(): Pair<List<HexDependency>, List<String>> {
        val export = config.configExports[Symbol(ElixirConfigKeys.CONFIG)] ?: return listOf<HexDependency>() to listOf()
        val properties = config.extractProperties(ElixirConfigKeys.CONFIG, ElixirConfigKeys.CONFIG_CLASS_NAME)
            ?: return listOf<HexDependency>() to listOf(
                "`${ElixirConfigKeys.CONFIG}` must be an ${ElixirConfigKeys.CONFIG_CLASS_NAME}, not $export",
            )
        val entries = TList.unpackOrNull(properties[ElixirConfigKeys.DEPENDENCIES]) ?: listOf()
        val dependencies = mutableListOf<HexDependency>()
        val problems = mutableListOf<String>()
        for (entry in entries) {
            when (val parsed = HexDependency.parse(TString.unpack(entry))) {
                is Either.Left -> dependencies.add(parsed.item)
                is Either.Right -> problems.add(parsed.item)
            }
        }
        dependencies.groupBy { it.name }.filterValues { it.size > 1 }.keys.forEach { name ->
            problems.add("Hex package `$name` is declared more than once")
        }
        return dependencies to problems
    }
}

/** `decimal ~> 3.1`: a Hex package and the versions of it the library accepts. */
internal data class HexDependency(val name: String, val requirement: String) {
    /** The entry in `mix.exs`'s `deps/0`. The requirement has no `"` or `\`, so it needs no escaping. */
    val mixDep get() = "{:$name, \"$requirement\"}"

    companion object {
        /**
         * A Hex package name: lowercase, digits and underscores, starting with
         * a letter. It becomes an atom, `:decimal`, so anything else would need
         * quoting that Hex would reject anyway.
         */
        private val name = Regex("[a-z][a-z0-9_]*")

        private const val NUMBER = "(?:0|[1-9][0-9]*)"
        private const val IDENTIFIER = "(?:$NUMBER|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
        private const val PRE = "(?:-$IDENTIFIER(?:\\.$IDENTIFIER)*)?"
        private const val BUILD = "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"

        /**
         * One clause of a Mix requirement, as `Version.parse_requirement/1`
         * accepts it: `~>` may name a major and minor only (`~> 3.1`), every
         * other operator needs all three parts. `!=` is left out: Elixir 1.18
         * accepts it with a deprecation warning.
         */
        private val clause = Regex(
            "(?:~>\\s*$NUMBER\\.$NUMBER(?:\\.$NUMBER)?|(?:==|>=|<=|>|<)?\\s*$NUMBER\\.$NUMBER\\.$NUMBER)$PRE$BUILD",
        )
        private val requirement = Regex("${clause.pattern}(?:\\s+(?:and|or)\\s+${clause.pattern})*")

        /** A [HexDependency], or why [text] is not one. */
        fun parse(text: String): Either<HexDependency, String> {
            val trimmed = text.trim()
            val split = trimmed.indexOfFirst { it.isWhitespace() }
            val expected = "expected a Hex package and a Mix version requirement, like \"decimal ~> 3.1\""
            if (split < 0) return Either.Right("Elixir dependency \"$text\": $expected")
            val packageName = trimmed.substring(0, split)
            val versions = trimmed.substring(split).trim()
            return when {
                !name.matches(packageName) -> Either.Right(
                    "Elixir dependency \"$text\": `$packageName` is not a Hex package name, which is lowercase letters, digits and _",
                )
                !requirement.matches(versions) -> Either.Right(
                    "Elixir dependency \"$text\": `$versions` is not a Mix version requirement, such as ~> 3.1 or >= 3.1.0",
                )
                else -> Either.Left(HexDependency(packageName, versions))
            }
        }
    }
}

internal object ElixirConfigKeys {
    /** The config export that holds the backend's config instance. */
    const val CONFIG = ElixirBackend.BACKEND_ID

    /** The class of that instance. */
    const val CONFIG_CLASS_NAME = "ElixirConfig"

    /** Hex packages, each `"name requirement"`. */
    const val DEPENDENCIES = "dependencies"
}

/** Declares `ElixirConfig` in every config module, as each backend declares its own. */
object ElixirConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = ElixirConfigKeys.CONFIG_CLASS_NAME,
                properties = mapOf(
                    ElixirConfigKeys.DEPENDENCIES to {
                        Call(BuiltinFuns.angleFn) {
                            V(Value(Types.list))
                            V(Value(Types.string))
                        }
                    },
                ),
            )
        }
    }
}

internal enum class ElixirConfigMessage(override val formatString: String) : LeveledMessageTemplate {
    BadConfig("%s"),
    ;

    override val suggestedLevel: Log.Level = Log.Error
}
