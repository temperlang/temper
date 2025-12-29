package lang.temper.be.util

import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonDouble
import lang.temper.common.json.JsonLong
import lang.temper.common.json.JsonNull
import lang.temper.common.json.JsonString
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseConfigFromCliTest {
    private fun assertParsedConfig(
        want: ConfigFromCli,
        argText: String,
    ) {
        val got = parseConfigFromCli(argText = argText)
            .configItems
            .map {
                when (it) {
                    is ConfigFromCli.NonSpaceConfigItem -> when (val pv = it.parsedValue) {
                        is RSuccess -> it
                        is RFailure -> it.copy(parsedValue = RFailure(AssertCompatibleException(pv.failure.message)))
                    }
                    is ConfigFromCli.SpaceConfigItem -> it
                }
            }
        assertEquals(
            want.configItems,
            got,
            message = argText,
        )
    }

    @Test
    fun empty() = assertParsedConfig(
        ConfigFromCli(emptyList()),
        "",
    )

    @Test
    fun spaceSeparated() = assertParsedConfig(
        ConfigFromCli(
            listOf(
                ConfigFromCli.NonSpaceConfigItem("foo", RSuccess(JsonString("foo"))),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem("bar", RSuccess(JsonString("bar"))),
                ConfigFromCli.SpaceConfigItem("  "),
                ConfigFromCli.NonSpaceConfigItem("baz-boo", RSuccess(JsonString("baz-boo"))),
            ),
        ),
        "foo bar  baz-boo",
    )

    @Test
    fun someNumbersIncludingOneThatDoesNotParse() = assertParsedConfig(
        ConfigFromCli(
            listOf(
                ConfigFromCli.NonSpaceConfigItem("1", RSuccess(JsonLong(1L))),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem("-2.0", RSuccess(JsonDouble(-2.0))),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem(
                    "3.x",
                    RFailure(AssertCompatibleException("Expected '0x' or '0X' prefix")),
                ),
            ),
        ),
        "1 -2.0 3.x",
    )

    @Test
    fun keywordValues() = assertParsedConfig(
        ConfigFromCli(
            listOf(
                ConfigFromCli.NonSpaceConfigItem("false", RSuccess(JsonBoolean.valueFalse)),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem("null", RSuccess(JsonNull)),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem("true", RSuccess(JsonBoolean.valueTrue)),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem("nulll", RSuccess(JsonString("nulll"))),
            ),
        ),
        "false null true nulll",
    )

    @Test
    fun mergeAroundQuotes() = assertParsedConfig(
        ConfigFromCli(
            listOf(
                ConfigFromCli.NonSpaceConfigItem(
                    "--arg=\"foo\\u0022bar\"baz",
                    RSuccess(JsonString("--arg=foo\"barbaz")),
                ),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem("-", RSuccess(JsonString("-"))),
            ),
        ),
        "--arg=\"foo\\u0022bar\"baz -",
    )

    @Test
    fun thatAChunkLooksNumericDoesNotPreventMerging() = assertParsedConfig(
        ConfigFromCli(
            listOf(
                ConfigFromCli.NonSpaceConfigItem(
                    "--arg",
                    RSuccess(JsonString("--arg")),
                ),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem(
                    "\"x=0 y=\"1\" z=2\"",
                    RSuccess(JsonString("x=0 y=1 z=2")),
                ),
            ),
        ),
        "--arg \"x=0 y=\"1\" z=2\"",
    )

    @Test
    fun missingCloseQuote() = assertParsedConfig(
        ConfigFromCli(
            listOf(
                ConfigFromCli.NonSpaceConfigItem(
                    "--arg",
                    RSuccess(JsonString("--arg")),
                ),
                ConfigFromCli.SpaceConfigItem(" "),
                ConfigFromCli.NonSpaceConfigItem(
                    "\"",
                    RFailure(AssertCompatibleException("Expected `\"` found ``")),
                ),
            ),
        ),
        "--arg \"",
    )
}

private class AssertCompatibleException(message: String?) : IllegalArgumentException(message) {
    override fun equals(other: Any?): Boolean = other is AssertCompatibleException && this.message == other.message
    override fun hashCode(): Int = message?.hashCode() ?: 0
}
