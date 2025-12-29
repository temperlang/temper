package lang.temper.regex

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.Specifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.composing
import lang.temper.be.cli.print
import lang.temper.be.csharp.DotnetCommand
import lang.temper.be.js.NodeSpecifics
import lang.temper.be.py.helper.PythonSpecifics
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonNull
import lang.temper.common.jvmMajorVersion
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureParser
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.log.filePath
import lang.temper.name.BackendId
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexMatchTest {
    /** Just a direct test of dotnet regex patterns for exploration ... */
    @Test
    fun matchRegexDotnet() {
        val dot = """(?:.|[\uD800-\uDBFF][\uDC00-\uDFFF])"""
        val word = """[A-Za-z0-9_]"""
        val wordBoundary = """(?:(?<!$word)(?=$word)|(?<=$word)(?!$word))"""
        val checksMap = commonChecks + mapOf(
            // Nothing on grapheme clusters.
            checkResult("graphemeCluster", null),
            checkResult("graphemeClustersExtra", null),
            checkResult("graphemeClustersMulti", null),
            // Dotnet supports inline options but nothing about Unicode.
            checkResult("unicodeSwitch", null),
            checkResult("unicodeGroup", null),
            // Dotnet uses 16-bit Unicode for \w. ECMAScript mode doesn't but also doesn't support Unicode at all.
            checkResult("asciiVsUnicodeWords", true),
            // Dotnet has no direct support for surrogate ranges at all.
            checkResult("surrogateRange", null),
            // Fails perhaps due to difference in \w.
            checkResult("manualWordBoundary", false),
            checkResult("moreBoundary", false),
            checkResult("surrogateCodePoint", false),
            checkResult("multiple16", false),
            // And again 16-bit Unicode for \d.
            checkResult("digitsNonAscii", true),
            // Work around things.
            "surrogateCodePointUgly" to RegexCheck(true, RegexMatch("""a${dot}b""", "a🌊b")),
            "surrogateCodePointUglyAscii" to RegexCheck(true, RegexMatch("""a${dot}b""", "a+b")),
            "surrogateRangeUgly" to RegexCheck(
                true,
                // Split the range into multiple parts.
                RegexMatch("""(?:[a]|[\uD83C-\uD83C][\uDF09-\uDF0A])+""", "aa\uD83C\uDF09"),
            ),
            "asciiVsUnicodeWordsUgly" to RegexCheck(false, RegexMatch(word, "궐")),
            "manualWordBoundaryUgly" to
                RegexCheck(true, RegexMatch("""ø${wordBoundary}8$wordBoundary""", "ø8")),
            "moreBoundaryUgly" to
                RegexCheck(true, RegexMatch("""(?!$word)ø${wordBoundary}8""", "ø8")),
            "digitsNonAsciiUgly" to RegexCheck(false, RegexMatch("""[0-9]+""", "\u0662")),
        )
        checkMatchDotnet(checksMap.values)
    }

    /** Just a direct test of JS regex patterns for exploration ... */
    @Test
    fun matchRegexJs() {
        val checksMap = commonChecks + mapOf(
            // \X as grapheme cluster isn't easy in JS.
            // - https://stackoverflow.com/questions/53198407
            // - https://stackoverflow.com/questions/62023206
            checkResult("graphemeCluster", null),
            checkResult("graphemeClustersExtra", null),
            checkResult("graphemeClustersMulti", null),
            // No inline modifiers in JS.
            checkResult("unicodeSwitch", null),
            checkResult("unicodeGroup", null),
            // Happily, `\b` works as expected here.
            checkResult("wordBoundary", true),
        )
        checkMatchJs(checksMap.values)
    }

    /**
     * Just a direct test of Kotlin regex patterns for exploration of behavior
     * and comparison with other languages.
     */
    @Test
    fun matchRegexKt() {
        val checks = if ((jvmMajorVersion() ?: 0) >= 19) {
            val adjusted = commonChecks.toMutableMap()
            // As of 19, Unicode handling is made more consistent, which breaks this test.
            // https://www.oracle.com/java/technologies/javase/19all-relnotes.html#JDK-8264160
            adjusted.remove("wordBoundary")
            adjusted.values
        } else {
            commonChecks.values
        }
        checkMatchKotlin(checks)
    }

    /** Just a direct test of Python regex patterns for exploration ... */
    @Test
    fun matchRegexPy() {
        val checksMap = commonChecks + mapOf(
            // TODO(tjp, regex): How to make \X in Python? Use `regex` instead of `re`?
            checkResult("graphemeCluster", null),
            checkResult("graphemeClustersExtra", null),
            checkResult("graphemeClustersMulti", null),
            // Syntax and rules for mode switches are different in Python.
            // Simple switches are only top level and `(?u)` fails for contradicting `re.ASCII` arg.
            "unicodeSwitch" to RegexCheck(null, RegexMatch("""(?u)\w""", "궐")),
            "unicodeGroup" to RegexCheck(true, RegexMatch("""(?u:\w)""", "궐")),
            // Happily, `\b` works as expected here.
            checkResult("wordBoundary", true),
        )
        checkMatchPython(checksMap.values)
    }
}

object FSharpSpecifics : Specifics, RunnerSpecifics {
    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> = cliEnv.composing(this) {
        write(code, filePath("script.fsx"))
        val command = Command(listOf("fsi", "script.fsx"), env = DotnetCommand.defaultEnv + env, aux = aux)
        this[DotnetCommand].run(command)
    }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> = cliEnv.composing(this) {
        val libraryName = (request as RunLibraryRequest).libraryName
        val main = dependencies.filesPerLibrary.getValue(libraryName)
            .first { "Main" in "${it.filePath}" }
            .filePath
        copyOutputRoot(code, FilePath.emptyPath)
        val command = Command(listOf("fsi", envPath(main)), env = DotnetCommand.defaultEnv)
        val result = this[DotnetCommand].run(command)
        listOf(ToolchainResult(libraryName = libraryName, result = result))
    }

    override val tools: List<ToolSpecifics> = listOf(DotnetCommand)
    override val backendId: BackendId = BackendId("dotnet")
}

internal fun checkActuals(checks: Iterable<RegexCheck>, actuals: List<Boolean?>) {
    var failCount = 0
    var firstFail = ""
    checks.zip(actuals).forEach { (check, actual) ->
        val checkText = FormattingStructureSink.toJsonString { value(check.regexMatch) }
        if (check.result != actual) {
            failCount += 1
            firstFail = "Expected ${check.result} for $checkText but got $actual"
        }
    }
    if (failCount > 0) {
        val failFraction = failCount / actuals.size.toDouble()
        assertEquals(0, failCount, "Fail fraction: $failFraction, first: $firstFail")
    }
}

internal fun checkMatchDotnet(checks: Iterable<RegexCheck>) {
    val script = (
        """
        open System
        open System.Text.Json
        open System.Text.Json.Serialization
        open System.Text.RegularExpressions
        type PatternMatch = {
            pattern: string
            [<JsonPropertyName("match")>]
            matchText: string
        }
        let results =
            List.map
                (fun pair ->
                    try
                        Nullable<bool>(Regex.IsMatch(pair.matchText, pair.pattern))
                    with
                        | _ -> Nullable())
                (JsonSerializer.Deserialize<List<PatternMatch>>
                    (Environment.GetEnvironmentVariable "PATTERN_MATCHES"))
        Console.WriteLine(JsonSerializer.Serialize results)
        """.trimIndent()
        )
    checkMatchExternal(
        checks = checks,
        script = script,
        specifics = FSharpSpecifics,
    )
}

internal fun checkMatchJs(checks: Iterable<RegexCheck>) = checkMatchExternal(
    checks = checks,
    script = (
        // Use single-quotes inside source because doubles don't seem to get escaped on Windows.
        """
        let result = JSON.parse(process.env.PATTERN_MATCHES).map(
            ({ pattern, match }) => {
                try {
                    return match.match(new RegExp(pattern, 'u')) != null;
                } catch {
                    return null;
                }
            }
        )
        console.log(JSON.stringify(result))
        """.trimIndent()
        ),
    specifics = NodeSpecifics,
)

internal fun checkMatchKotlin(checks: Iterable<RegexCheck>) {
    val actuals = checks.map { Regex(it.regexMatch.regex).matches(it.regexMatch.match) }
    checkActuals(checks, actuals)
}

internal fun checkMatchPython(checks: Iterable<RegexCheck>) = checkMatchExternal(
    checks = checks,
    script = (
        // Use single-quotes inside source because doubles don't seem to get escaped on Windows.
        """
        import json, os, re, warnings
        warnings.filterwarnings('error') # Turn regex FutureWarning to error.
        result = []
        for pair in json.loads(os.environ['PATTERN_MATCHES']):
            try:
                result.append(re.match(pair['pattern'], pair['match'], re.ASCII) is not None)
            except Exception:
                result.append(None)
        print(json.dumps(result))
        """.trimIndent()
        ),
    specifics = PythonSpecifics,
)

/** Batch check a number of regex matches to finish faster than individual processes. */
internal fun checkMatchExternal(
    checks: Iterable<RegexCheck>,
    script: String,
    specifics: RunnerSpecifics,
) {
    val cancelGroup = makeCancelGroupForTest()
    val patternMatches = checks.map { it.regexMatch }
    val env = mapOf("PATTERN_MATCHES" to FormattingStructureSink.toJsonString { value(patternMatches) })
    val result = CliEnv.using(specifics, ShellPreferences.default(console), cancelGroup) {
        specifics.runSingleSource(this, script, env = env)
    }
    when (result) {
        is RFailure -> result.print(console)
        is RSuccess -> {
            val stdout = result.result.stdout

            val actuals = (StructureParser.parseJson(stdout) as JsonArray).map { value ->
                when (value) {
                    is JsonBoolean -> value.content
                    JsonNull -> null
                    else -> error("Unexpected: $value")
                }
            }
            checkActuals(checks, actuals)
        }
    }
}

private fun checkResult(name: String, result: Boolean?) =
    name to commonChecks.getValue(name).copy(result = result)

internal data class RegexMatch(val regex: String, val match: String) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("pattern") { value(regex) }
        key("match") { value(match) }
    }
}

internal data class RegexCheck(val result: Boolean?, val regexMatch: RegexMatch)

private val manualWordBoundary = KotlinRegexFormatter.wordBoundary

/** Started in Kotlin, so these are Kotlin-biased. */
private val commonChecks = mapOf(
    // Prove basics work.
    "simple" to RegexCheck(true, RegexMatch("""\w+""", "hi")),
    "simpleFalse" to RegexCheck(false, RegexMatch("""\w\d""", "hi")),
    // Test some hand-selected cases, though based some on generated.
    // The boxed 5 is also from LexerTest.
    "graphemeCluster" to RegexCheck(true, RegexMatch("""\X""", "5\uFE0F\u20E3")),
    "graphemeClustersExtra" to RegexCheck(false, RegexMatch("""\X""", "ab")),
    "graphemeClustersMulti" to RegexCheck(true, RegexMatch("""\d+\X""", "345\uFE0F\u20E3")),
    // Check both inline switch and grouped switch.
    "unicodeSwitch" to RegexCheck(true, RegexMatch("""(?U)\w""", "궐")),
    "unicodeGroup" to RegexCheck(true, RegexMatch("""(?U:\w)""", "궐")),
    "asciiVsUnicodeWords" to RegexCheck(false, RegexMatch("""\w""", "궐")),
    "surrogateRange" to
        RegexCheck(true, RegexMatch("[a\uD83C\uDF09-\uD83C\uDF0A]+", "aa\uD83C\uDF09")),
    // Unfortunately word boundary is different in Kotlin from Python and JS, so check alternatives.
    "wordBoundary" to RegexCheck(false, RegexMatch("""ø\b8\b""", "ø8")),
    "manualWordBoundary" to
        RegexCheck(true, RegexMatch("""ø${manualWordBoundary}8$manualWordBoundary""", "ø8")),
    "moreBoundary" to RegexCheck(true, RegexMatch("""(?!\w)ø(?<!\w)(?=\w)8""", "ø8")),
    "surrogateCodePoint" to RegexCheck(true, RegexMatch("""a.b""", "a\uD83C\uDF0Ab")),
    "multiple16" to RegexCheck(false, RegexMatch("""a.b""", "a\uD7FF\uE000b")),
    "digits" to RegexCheck(true, RegexMatch("""\d+""", "01234567890")),
    // Unicode decimal digit but not ascii. For example: `regex.match(r"\p{Nd}", "\u0662") is not None`
    // Same for `re.match(r"\d", "\u0662")` (w/o re.ASCII), but for current plans, we just want the ascii digits.
    "digitsNonAscii" to RegexCheck(false, RegexMatch("""\d+""", "\u0662")),
)
