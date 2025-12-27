package lang.temper.be.util

import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.replaceSubList

/**
 * An item of backend-specific configuration from a command line interface (CLI).
 *
 * A user may pass extra config like
 *
 *     temper repl -b 'js:node --extra --arg="foo bar" --more 123.0'
 *
 * The content after the `:` there is extra configuration meant for,
 * in this example, the JS backend.
 *
 * [parseConfigFromCli] below tries to turn that into values for
 * convenience but still leave enough information for error reporting
 * and for exact (ignoring whitespace details) access to the original
 * text.
 *
 * In that case, the values retrieved would be:
 *
 * | Raw text          | JSON Value                    |
 * | ----------------- | ----------------------------- |
 * | `node`            | `JsonString("node")`          |
 * | `--extra`         | `JsonString("--extra")`       |
 * | `--arg="foo bar"` | `JsonString("--arg=foo bar")` |
 * | `--more`          | `JsonString("--more")`        |
 * | `123.0`           | `JsonNumber(123.0D)`          |
 *
 * Note that the "123.0" is still available even though converted to
 * a numeric JSON value, and that the quotes around `foo bar` are not
 * present in the JSON value because they serve only to include the
 * space as part of the flag value.
 */
data class ConfigFromCli(
    /** The items such that the concatenation of their raw text is the original flag */
    val configItems: List<ConfigItem>,
) {
    sealed class ConfigItem { abstract val rawText: String }
    data class SpaceConfigItem(override val rawText: String) : ConfigItem()
    data class NonSpaceConfigItem(
        override val rawText: String,
        val parsedValue: RResult<JsonValue, IllegalArgumentException>,
    ) : ConfigItem()

    override fun toString(): String {
        // Makes any errors apparent
        return configItems.joinToString("") {
            when (it) {
                is SpaceConfigItem -> it.rawText
                is NonSpaceConfigItem -> when (it.parsedValue) {
                    is RSuccess -> it.rawText
                    is RFailure -> "#ERROR:${it.parsedValue.failure.message}#"
                }
            }
        }
    }

    companion object {
        val empty: ConfigFromCli = ConfigFromCli(emptyList())
    }
}

fun parseConfigFromCli(argText: String): ConfigFromCli {
    // Parsing proceeds thus:
    // 1. Split into tokens and classify as space or non-space and decode double-quoted strings.
    // 2. Merge adjacent non-space strings so that `--arg="has spaces"suffix` combines to a single
    //    non-breaking chunk with spaces in it.
    // 3. If something was not quoted, and it looks like a numeric or keyword value, parse it.
    val tokensAndWasQuoted = mutableListOf<Pair<ConfigFromCli.ConfigItem, Boolean>>()
    argPartPattern.findAll(argText).mapTo(tokensAndWasQuoted) { match ->
        val tokenText = match.groupValues[1] // Space tokens not in group 1
        if (tokenText.isEmpty()) {
            ConfigFromCli.SpaceConfigItem(match.value) to false
        } else {
            val (result, wasQuoted) =
                if (tokenText.startsWith('"')) {
                    JsonValue.parse(tokenText).mapResult { it as JsonString } to true
                } else {
                    RSuccess(JsonString(tokenText)) to false
                }
            ConfigFromCli.NonSpaceConfigItem(tokenText, result) to wasQuoted
        }
    }

    // Merge adjacent non-breaking strings
    var indexBeforeMerged = tokensAndWasQuoted.size
    while (indexBeforeMerged > 0) {
        val end = indexBeforeMerged // exclusive
        var start = indexBeforeMerged
        while (start > 0) {
            val index = start - 1
            val (atStart, _) = tokensAndWasQuoted[index]
            val parsedValue =
                (atStart as? ConfigFromCli.NonSpaceConfigItem)?.parsedValue
            if (parsedValue?.result is JsonString) {
                start = index
            } else {
                break
            }
        }
        if (end - start >= 2) {
            val raw = StringBuilder()
            val cooked = StringBuilder()
            var anyQuoted = false
            for (index in start until end) {
                val (item, wasQuoted) = tokensAndWasQuoted[index]
                check(item is ConfigFromCli.NonSpaceConfigItem)
                raw.append(item.rawText)
                cooked.append((item.parsedValue.result as JsonString).s)
                anyQuoted = anyQuoted || wasQuoted
            }

            // More than 1 to merge
            tokensAndWasQuoted.replaceSubList(
                start,
                end,
                listOf(
                    ConfigFromCli.NonSpaceConfigItem(
                        "$raw",
                        RSuccess(JsonString("$cooked")),
                    ) to anyQuoted,
                ),
            )
        }

        indexBeforeMerged = start - 1
    }

    // Parse any keyword or numeric values
    val items = tokensAndWasQuoted.map { (item, wasQuoted) ->
        if (!wasQuoted) {
            val jsonStr =
                ((item as? ConfigFromCli.NonSpaceConfigItem)?.parsedValue as? RSuccess)
                    ?.result as? JsonString
            if (jsonStr != null && startsLikeJsonSpecial.find(jsonStr.s) != null) {
                return@map item.copy(parsedValue = JsonValue.parse(jsonStr.s))
            }
        }
        item
    }

    return ConfigFromCli(items)
}

private val argPartPattern = Regex(
    // One of
    // - a run of space chars
    // - a run of non-space chars and non-quotes
    // - a quoted string with an optional closing quote
    // The closing quote is optional so that this pattern is guaranteed to
    // partition a string when used with .findAll instead of leaving some
    // characters outside any match.
    """[\t\n\r ]+|([^\t\n\r "]+|"(?:[^\u0022\\]|\\.?)*"?)""",
    RegexOption.DOT_MATCHES_ALL,
)

private val startsLikeJsonSpecial = Regex("""^[+\-]?[.0-9]|^(?:false|null|true)$""")
