package lang.temper.docbuild

import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.abbreviate
import lang.temper.common.isMarkdown
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.jsonEscaper
import lang.temper.common.splitAfter
import lang.temper.common.structure.StructureSink
import lang.temper.common.toStringViaBuilder
import lang.temper.common.withCapturingConsole
import lang.temper.format.ValueSimplifyingLogSink
import lang.temper.frontend.Module
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.lexer.Lexer
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.TokenType
import lang.temper.lexer.children
import lang.temper.log.CodeLocationKey.FilePositionsKey
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.toReadablePosition
import lang.temper.name.ModuleName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.value.BlockTree
import lang.temper.value.Fail
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.TBoolean
import lang.temper.value.Value
import lang.temper.value.void
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node

/**
 * Extract and run Temper code blocks.
 *
 * This finds
 *
 *     ```temper
 *     someTemperCode()
 *     ```
 *
 * and rewrites them to
 *
 *     ```temper
 *     someTemperCode()
 *     // ✅ void
 *     ```
 *
 * if the code worked, or
 *
 *     ```temper
 *     someTemperCode()
 *     // ❌ FAIL
 *     ```
 *
 * if it failed.
 *
 * We can express what the snippet should log with `//!outputs` comments.
 *
 *     ```temper
 *     console.log("Foo"); //!outputs "Foo"
 *     // ✅
 *     ```
 *
 * Each `//!outputs` directive implies a newline ('`\n`') at the end of the input string.
 * If you have an `//!outputs ["array", "of", "lines"]` directive, a newline is implied after each
 * element.
 *
 * If we have no output, and give no hint as to what the code block should produce, this assumes
 * that it produces the value true, so you can assert what code does.
 *
 *     ```temper
 *     1 + 1 == 2
 *     // ✅
 *     ```
 *
 * We can express a specific, expected value:
 *
 *     ```temper 2
 *     1 + 1
 *     // ✅ 2
 *     ```
 *
 * And there's an expanded form that allows specifying the type explicitly.
 *
 *     ```temper {"type":"Int","stateVector":2}
 *     1 + 1
 *     // ✅ { "type": "Int", "stateVector": 2 }
 *     ```
 *
 * Mismatched expectations are expressed via [Snippet.problems].
 * If the actual result does not match expectations, that is not communicated via a '❌'.
 * The '❌' only expresses whether the code snippet should be bad code:
 * an example of what not to do.
 *
 * Sometimes you may wish to have a Temper code block that is not run.  Add `inert` after the
 * language tag.
 *
 *     ```temper inert
 *     justDont()
 *     ```
 */
internal object TemperCodeSnippetExtractor : SnippetExtractor() {
    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        if (content !is MarkdownContent) {
            return
        }
        if (!SkeletalDocsFiles.root.isAncestorOf(from)) {
            return
        }
        extractSnippets(from, content, onto)
    }

    override fun extractNestedSnippets(from: Snippet, onto: MutableCollection<Snippet>) {
        if (!from.mimeType.isMarkdown) {
            return
        }
        val content = when (val docContent = from.content) {
            is ByteDocContent -> return
            is ShellCommandDocContent -> return
            is TextDocContent -> MarkdownContent(docContent.text)
        }
        extractSnippets(from.id.filePath, content, onto)
    }

    private fun extractSnippets(
        from: FilePath,
        content: MarkdownContent,
        onto: MutableCollection<Snippet>,
    ) {
        val meta = content.simpleMeta()
        if (meta["temper-extract"] == "false") {
            return
        }
        var snippetInFileIndex = 0

        val codeBlocks = mutableListOf<FencedCodeBlock>()
        scanForTemperCodeBlocks(content, codeBlocks)
        for (codeBlock in codeBlocks) {
            val snippet = CodeFenceProcessor(
                from = from,
                range = content.range(codeBlock),
                fenceMeta = codeBlock.info,
                indexOfSnippetInFile = snippetInFileIndex,
                markdownContent = content,
                block = codeBlock,
            ).process()
            if (snippet != null) {
                onto.add(snippet)
                snippetInFileIndex += 1
            }
        }
    }

    override fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException> {
        val shortId = inserted.id.shortCanonString(false)

        val derivation = inserted.derivation
        if (derivation !is ExtractedAndReplacedBack) {
            return RFailure(IllegalStateException("Snippet $shortId was not extracted"))
        }
        val extracted = derivation.extracted

        if (extracted !is TextDocContent) {
            return RFailure(
                IllegalStateException("Expected textual content for $shortId"),
            )
        }
        val inlined = MarkdownContent(readInlined().text)

        val extractedCodeBlocks = mutableListOf<FencedCodeBlock>()
        val inlinedCodeBlocks = mutableListOf<FencedCodeBlock>()
        scanForTemperCodeBlocks(MarkdownContent(extracted.text), extractedCodeBlocks)
        scanForTemperCodeBlocks(inlined, inlinedCodeBlocks)

        if (extractedCodeBlocks.size != 1) {
            return RFailure(
                IllegalStateException("Expected one code block from $shortId\n${extracted.text}"),
            )
        }
        if (inlinedCodeBlocks.size != 1) {
            return RFailure(
                IllegalStateException("Expected one code block from $shortId:\n${inlined.fileContent}"),
            )
        }

        val extractedCodeBlock = extractedCodeBlocks[0]
        val inlinedCodeBlock = inlinedCodeBlocks[0]

        // Normally, we'll have an HTML comment snippet marker
        // We should strip those off since, if they're inlined back,
        // they'll end up breaking the containing snippet in two and
        // co-opt following content.
        val fenceRange = inlined.range(inlinedCodeBlock)
        val nToStripFromStart = fenceRange.first
        val nToStripFromEnd = inlined.fileContent.length - (fenceRange.last + 1)

        // Issue an error if there is any Markdown content besides
        // inlinedCodeBlock, and HTML comments, and space tokens.
        fun findNonCodeContent2(node: Node): Node? {
            if (node === inlinedCodeBlock) { return null }
            val text = inlined.text(node)
            if (isIgnorableHtmlContent(text)) { return null }
            return node
        }
        val extraneous = inlined.root.children().asSequence().firstNotNullOfOrNull { findNonCodeContent2(it) }
        if (extraneous != null) {
            return RFailure(
                IllegalStateException(
                    "$shortId contains extraneous information that${
                        ""
                    } cannot be back-ported to an inlineable code block: ${
                        inlined.text(extraneous)
                    }",
                ),
            )
        }

        val extractedMeta = extractedCodeBlock.info

        val adjusted = StringBuilder(inlined.fileContent)
        val statusLine2 = inlinedCodeBlock.sourceSpans.asReversed().asSequence().drop(1).map { sourceSpan ->
            inlined.range(sourceSpan)
        }.firstOrNull { range ->
            val text = inlined.fileContent.slice(range)
            adjustedCodeMarkers.any { text.startsWith(it) }
        }
        if (statusLine2 != null) {
            // Remove the line containing the generated status marker
            removeLineContaining(adjusted, statusLine2)
        }
        // Replace the generic code fence metadata `temper` with the original content.
        val startRange = inlined.range(inlinedCodeBlock.sourceSpans.first())
        val startText = inlined.fileContent.slice(startRange)
        adjusted.replace(
            startRange.first + startText.indexOf(inlinedCodeBlock.info),
            startRange.last + 1,
            extractedMeta,
        )
        return RSuccess(
            TextDocContent(
                adjusted.substring(nToStripFromStart, adjusted.length - nToStripFromEnd),
            ),
        )
    }

    override fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean {
        // This extracts inlined snippets.  They should have been handled
        // earlier in the process.
        problemTracker.error("Cannot back-port snippet $snippet")
        return false
    }

    private fun scanForTemperCodeBlocks(markdownContent: MarkdownContent, out: MutableList<FencedCodeBlock>) {
        markdownContent.root.accept(
            object : AbstractVisitor() {
                override fun visit(fencedCodeBlock: FencedCodeBlock) {
                    // In commonmark-java, they unescape backslash things, but we want raw, so dig that up.
                    val firstRange = markdownContent.range(fencedCodeBlock.sourceSpans.first())
                    val infoRange = firstRange.first + fencedCodeBlock.fenceLength..firstRange.last
                    // And just mutate the data to what we want. TODO Build some other structure of our own instead?
                    fencedCodeBlock.info = markdownContent.fileContent.slice(infoRange).trim()
                    // Now store it.
                    out.add(fencedCodeBlock)
                }
            },
        )
    }
}

private class CodeFenceProcessor(
    private val from: FilePath,
    /** The range for the whole code fence */
    private val range: IntRange,
    /** The meta-information including any language tag. */
    private val fenceMeta: String,
    private val indexOfSnippetInFile: Int,
    private val markdownContent: MarkdownContent,
    /**
     * In a code fence like
     *
     *     > Markdown quotation
     *     >
     *     > ```foo
     *     > Line 1
     *     > Line 2
     *     > ```
     *
     * we need to treat `Line 1` and `Line 2` as code content, and treat the newline
     * as significant but the `>`'s that establish the quotation should not.
     */
    private val block: FencedCodeBlock,
) {
    private val markdownText = markdownContent.fileContent

    /**
     * A prefix of [markdownText] including the fenced code, but with characters that are not
     * part of the fence's content replaced with whitespace, so that applying a Temper lexer to it
     * will get file positions right, but not see characters that are not code.
     */
    private val adjustedText: String

    /**
     * The language tag. So if [fenceMeta] is `"temper extra stuff"`, `"temper"` is here and
     * `"extra stuff"` is in [fenceExtra].
     */
    private val fenceLang: String
    private val fenceExtra: String

    /** Minimal range of chars in [adjustedText]/[markdownText] of code characters. */
    private val codeStart: Int
    private val codeEnd: Int

    private val problems = mutableListOf<String>()

    init {
        when (val spaceIndex = fenceMeta.indexOf(' ')) {
            in 0 until Int.MAX_VALUE -> {
                fenceLang = fenceMeta.substring(0, spaceIndex)
                fenceExtra = fenceMeta.substring(spaceIndex + 1)
            }
            else -> {
                fenceLang = fenceMeta
                fenceExtra = ""
            }
        }

        // Now, find the actual code.
        val allRanges = buildList {
            var start = 0
            // The first span starts the fence, so get code starting at second.
            // And presume we've closed all our fences, so end before the last.
            for (index in 1 until block.sourceSpans.size - 1) {
                val range = markdownContent.range(block.sourceSpans[index])
                add(start until range.first to false)
                add(range.first..range.last to true)
                start = range.last + 1
            }
            add(start..markdownContent.range(block.sourceSpans.last()).last to false)
        }
        // Replace non-space characters outside code fence content with spaces.
        // So given
        //     > ```temper
        //     > foo()
        //     > ```
        // becomes
        //
        //       foo()
        //
        var codeStart = Int.MAX_VALUE
        var codeEnd = -1
        adjustedText = toStringViaBuilder { sb ->
            var afterLastProcessed = 0
            allRanges.forEach { (range, isCodeContent) ->
                val first = range.first
                val last = range.last
                check(first == afterLastProcessed) {
                    "Non-contiguous ranges: $allRanges"
                }
                if (isCodeContent) {
                    if (first < codeStart) { codeStart = first }
                    if (last > codeEnd) { codeEnd = last + 1 }
                    sb.append(markdownText, first, last + 1)
                } else {
                    for (i in range) {
                        val c = markdownText[i]
                        sb.append(
                            if (isMarkdownSpaceChar(markdownText[i])) {
                                c
                            } else {
                                ' '
                            },
                        )
                    }
                }
                afterLastProcessed = last + 1
            }
        }
        this.codeStart = codeStart
        this.codeEnd = codeEnd
    }

    fun process(): Snippet? {
        if (fenceLang != TEMPER_LANGUAGE_TAG) { return null }

        // If a snippet can be extracted from a snippet, then we need to be idempotent, so that we
        // do not recursively add snippets.
        // Look for the tweaks that we add below.
        if (adjustedCodeMarkers.any { it in adjustedText }) {
            return null
        }

        var expectedResultJson: JsonValue? = null
        var expectFailure = false
        var runCode = true
        when (val fenceExtraTrimmed = fenceExtra.trim()) {
            "FAIL" -> expectFailure = true
            MarkdownLanguageConfig.INERT_TAG -> runCode = false
            "" -> Unit
            else -> {
                when (val jsonResult = JsonValue.parse(fenceExtraTrimmed)) {
                    is RSuccess -> expectedResultJson = jsonResult.result
                    is RFailure -> throw IllegalArgumentException(
                        "$from+${range.first}: ${jsonResult.failure.message}",
                    )
                }
            }
        }

        val (isBadCode, resultDescription) = if (runCode) {
            runSnippetCode(expectedResultJson, expectFailure = expectFailure, problems = problems)
        } else {
            false to null
        }

        val replacedRange = markdownContent.range(block)
        val indent = markdownIndentationLevel(MarkdownContent(markdownText), replacedRange.first)
        val startRange = markdownContent.range(block.sourceSpans.first())
        val endStart = markdownContent.range(block.sourceSpans.last()).first
        val betweenTwoFences = markdownContent.fileContent.slice(
            // Also skip indent on the end line by backtracking to newline.
            startRange.last + 1..markdownContent.fileContent.lastIndexOf("\n", endStart),
        )

        // Weak validation of complete fenced block.
        if (markdownContent.fileContent[replacedRange.first] != markdownContent.fileContent[endStart]) {
            error(
                "No fence end corresponding to $from:${startRange.first}\n\t${
                    abbreviate(markdownText.substring(startRange.first), maxlen = 40)
                }",
            )
        }

        val changedContent = toStringViaBuilder { sb ->
            sb.append(betweenTwoFences)
            if (betweenTwoFences.last() != '\n') { sb.append('\n') }
            sb.append(indent)
            sb.append(
                when {
                    !runCode -> NOT_RUN_MARKER
                    isBadCode -> BAD_CODE_MARKER
                    else -> GOOD_CODE_MARKER
                },
            )
            if (!resultDescription.isNullOrEmpty()) {
                sb.append(' ').append(resultDescription)
            }
            sb.append('\n')
        }

        val replacement = toStringViaBuilder { sb ->
            val fence = run {
                var tickRunLength = MIN_TICKS_IN_CODE_FENCE
                var i = 0
                while (true) {
                    i = changedContent.indexOf('`', i)
                    if (i < 0) { break }
                    var end = i + 1
                    while (end < changedContent.length && changedContent[end] == '`') {
                        end += 1
                    }
                    val nTicks = end - i
                    if (nTicks > tickRunLength) { tickRunLength = nTicks }
                    i = end
                }
                "`".repeat(tickRunLength)
            }

            sb.append("$fence$TEMPER_LANGUAGE_TAG")
            // De-indent lines so that the snippet stands alone.
            // When a snippet insertion appears in a list or other indented
            // context, we take care to indent the whole.
            for (line in changedContent.splitAfter(crLfOrLfPattern)) {
                val deIndentedLine = if (line.startsWith(indent)) {
                    line.substring(indent.length)
                } else {
                    line
                }
                sb.append(deIndentedLine)
            }
            sb.append(fence)
        }

        val snippetIdParts = mutableListOf("temper-code")
        from.segments.forEach { snippetIdParts.add(it.fullName) }
        snippetIdParts.add("$indexOfSnippetInFile")

        return Snippet(
            SnippetId(snippetIdParts.toList(), MD_EXTENSION),
            shortTitle = null,
            sourceStartOffset = range.first,
            source = from,
            mimeType = MimeType.markdown,
            content = TextDocContent(replacement),
            isIntermediate = false,
            derivation = ExtractedAndReplacedBack(
                TemperCodeSnippetExtractor,
                replaceBackRange = replacedRange,
                extracted = TextDocContent(markdownText.substring(replacedRange)),
            ),
            problems = problems.toList(),
        )
    }

    private fun makeLexer(logSink: LogSink) = Lexer(
        codeLocation = from,
        logSink = logSink,
        sourceText = adjustedText.substring(codeStart, codeEnd),
        offset = codeStart,
    )

    private fun runSnippetCode(
        expectedResultJsonFromMeta: JsonValue?,
        expectFailure: Boolean,
        problems: MutableList<String>,
    ): Pair<Boolean, String?> {
        var expectedResultJson = expectedResultJsonFromMeta

        var hasExpectedOutput = false

        // Lex once looking for `//!outputs "..."` comments
        val expectedOutput = toStringViaBuilder { sb ->
            for (token in makeLexer(LogSink.devNull)) {
                if (token.tokenType == TokenType.Comment) {
                    val text = token.tokenText
                    if (text.startsWith(OUTPUT_MARKER_PREFIX)) {
                        val rest = text.substring(OUTPUT_MARKER_PREFIX.length)
                        val json = JsonValue.parse(rest).result
                        var unexpectedJson: JsonValue? = null
                        when (json) {
                            is JsonString -> sb.append(json.s).append('\n')
                            is JsonArray -> { // An array of lines
                                for (element in json.elements) {
                                    if (element is JsonString) {
                                        sb.append(element.s).append('\n')
                                    } else {
                                        unexpectedJson = element
                                        break
                                    }
                                }
                            }
                            else -> unexpectedJson = json
                        }
                        if (unexpectedJson != null) {
                            problems.add(
                                "$from+${
                                    range.first
                                }: Malformed JSON in `${
                                    OUTPUT_MARKER_PREFIX
                                }` in Temper code block: ${unexpectedJson.toJsonString(indent = false)}",
                            )
                            return@runSnippetCode false to null
                        }
                        hasExpectedOutput = true
                    }
                }
            }
        }

        if (expectedResultJson == null && !hasExpectedOutput && !expectFailure) {
            // If we have no success criteria, expect the module to express a predicate that
            // is expected to be true.
            expectedResultJson = JsonBoolean.valueTrue
        }

        // Now deliver the module content and stage the module
        val logSink = ListBackedLogSink()
        val formattingLogSink = ValueSimplifyingLogSink(logSink, nameSimplifying = true)
        val (module, consoleOutput) = withCapturingConsole { runConsole ->
            val moduleName = ModuleName(
                sourceFile = from,
                libraryRootSegmentCount = 0,
                isPreface = false,
            )
            var moduleToHook: Module? = null
            val advancer = ModuleAdvancer(
                formattingLogSink,
                moduleConfig = ModuleConfig(
                    moduleCustomizeHook = { module, _ ->
                        if (module === moduleToHook && module.stageCompleted == Stage.Parse) {
                            // Wrap it early for sequential evaluation.
                            module.hookTree { BlockTree.wrap(it) }
                        }
                    },
                ),
            )
            val module = advancer.createModule(moduleName, runConsole, mayRun = true)
            @Suppress("AssignedValueIsNeverRead") // Read by moduleCustomizeHook
            moduleToHook = module
            val lexer = makeLexer(formattingLogSink)
            module.deliverContent(lexer, FilePositions.fromSource(from, markdownText))
            module.addEnvironmentBindings(extraBindingsForSnippetCodeModules)

            try {
                advancer.advanceModules()
            } catch (_: Panic) {
                // Later code will notice that we did not successfully reach Stage.Run
            }

            module
        }

        return checkModuleResultAgainstExpectations(
            module = module,
            logSink = logSink,
            expectFailure = expectFailure,
            expectedResultJson = expectedResultJson,
            expectedOutput = if (hasExpectedOutput) {
                expectedOutput
            } else {
                null
            },
            consoleOutput = consoleOutput,
        )
    }

    private fun checkModuleResultAgainstExpectations(
        module: Module,
        logSink: ListBackedLogSink,
        expectFailure: Boolean,
        expectedResultJson: JsonValue?,
        expectedOutput: String?,
        consoleOutput: String,
    ): Pair<Boolean, String?> {
        var isBadCode = false
        val resultDescription: String?
        val runResult = module.runResult

        val hasErrors = logSink.allEntries.any { it.level >= Log.Error }
        val nProblemsBefore = problems.size
        when {
            // Staging failed
            module.stageCompleted != Stage.Run || !module.ok || hasErrors -> {
                isBadCode = true
                resultDescription = if (expectFailure) {
                    logSink.allEntries
                        .filter { it.level >= Log.Error }
                        .toSet()
                        .joinToString { it.messageText }
                } else {
                    problems.add("Staging failed for Temper code snippet")
                    logSink.allEntries.forEach {
                        if (it.level >= Log.Warn) {
                            val pos = it.pos
                            val (loc, left, right) = pos
                            val filePositions = module.sharedLocationContext[loc, FilePositionsKey]
                            val posStr = if (filePositions != null) {
                                (
                                    filePositions.filePositionAtOffset(left) to
                                        filePositions.filePositionAtOffset(right)
                                    )
                                    .toReadablePosition("$loc")
                            } else {
                                "$pos"
                            }
                            problems.add("$posStr: ${it.messageText}")
                        }
                    }
                    null
                }
            }
            // If there is no result, that's a problem.
            runResult == null -> {
                problems.add("No result for Temper code snippet")
                isBadCode = true
                resultDescription = "No result"
                logSink.allEntries.forEach {
                    if (it.level >= Log.Error) {
                        problems.add("${it.pos}: ${it.messageText}")
                    }
                }
            }
            else -> {
                if (expectFailure) {
                    problems.add("Expected failure for Temper code snippet that passed")
                }
                // If the sample says what stdout should be, check that
                if (expectedOutput != null) {
                    val output = toStringViaBuilder { stdout ->
                        logSink.allEntries.forEach {
                            if (
                                it.template == MessageTemplate.StandardOut &&
                                it.values.size == 1
                            ) {
                                stdout.append(it.values[0]).append('\n')
                            }
                        }
                    }

                    if (output != expectedOutput) {
                        problems.add(
                            "Expected output ${
                                jsonEscaper.escape(expectedOutput)
                            } does not match actual output ${
                                jsonEscaper.escape(output)
                            }",
                        )
                    }
                }

                if (expectedResultJson != null || expectFailure) {
                    val jsonValueBuilder = JsonValueBuilder()
                    when (runResult) {
                        is Fail -> Fail.destructure(jsonValueBuilder)
                        NotYet -> NotYet.destructure(jsonValueBuilder)
                        is Value<*> -> {
                            val wantedShape =
                                (expectedResultJson as? JsonObject)
                                    ?.properties?.map { it.key }?.toSet()
                                    ?: emptySet()
                            if (wantedShape == setOf("type", "stateVector")) {
                                jsonValueBuilder.obj {
                                    key("type") {
                                        runResult.typeTag.destructure(jsonValueBuilder)
                                    }
                                    key("stateVector") {
                                        destructureValue(runResult, jsonValueBuilder)
                                    }
                                }
                            } else {
                                destructureValue(runResult, jsonValueBuilder)
                            }
                        }
                    }
                    val got = jsonValueBuilder.getRoot()
                    val isResultNotable = when {
                        // `true` is the default when the body expects a predicate
                        got == JsonBoolean.valueTrue -> false
                        // `void` is not noteworthy when the body is logging output
                        expectedOutput != null && runResult == void -> false
                        else -> true
                    }
                    resultDescription = if (isResultNotable) {
                        val resultJson = got.toJsonString(indent = false)
                        // If we have a notable result, not the default, where
                        resultJson
                    } else {
                        null
                    }

                    if (got != expectedResultJson) {
                        problems.add(
                            "Temper code snippet produced ${
                                got.toJsonString(indent = false)
                            }, not ${
                                expectedResultJson?.toJsonString(indent = false)
                                    ?: "failure"
                            }",
                        )
                    }
                } else {
                    resultDescription = null
                }
            }
        }

        if (problems.size != nProblemsBefore) {
            if (consoleOutput.isNotEmpty()) {
                problems.add("CONSOLE OUTPUT FOLLOWS:$consoleOutput")
            }
        }

        return isBadCode to resultDescription
    }
}

private fun <V : Any> destructureValue(v: Value<V>, structureSink: StructureSink) =
    v.typeTag.destructureValue(
        value = v.stateVector,
        structureSink = structureSink,
        typeInfoIsRedundant = true,
    )

private const val TEMPER_LANGUAGE_TAG = "temper"

private const val BAD_CODE_MARKER = "// ❌"
private const val GOOD_CODE_MARKER = "// ✅"
private const val NOT_RUN_MARKER = "// ⏸️"
private val adjustedCodeMarkers = setOf(BAD_CODE_MARKER, GOOD_CODE_MARKER, NOT_RUN_MARKER)

// `` is not a valid code fence
private const val MIN_TICKS_IN_CODE_FENCE = 3

private const val OUTPUT_MARKER_PREFIX = "//!outputs"

internal fun isMarkdownSpaceChar(c: Char) = when (c) {
    ' ', '\n', '\r', '\t' -> true
    else -> false
}

private fun removeLineContaining(buffer: StringBuilder, range: IntRange) {
    var start = range.first
    var end = range.last + 1
    while (start != 0) {
        val c = buffer[start - 1]
        if (c == '\n' || c == '\r') { break }
        start -= 1
    }
    while (end < buffer.length) {
        val c = buffer[end]
        end += 1
        if (c == '\n') { break }
        if (c == '\r') {
            if (end < buffer.length && buffer[end] == '\n') {
                end += 1
            }
            break
        }
    }
    buffer.delete(start, end)
}

private val extraBindingsForSnippetCodeModules = mapOf<TemperName, Value<*>>(
    StagingFlags.moduleResultNeeded to TBoolean.valueTrue,
)
