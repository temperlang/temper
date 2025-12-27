package lang.temper.langserver

import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import lang.temper.common.emptyIntArray
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.languageConfigForExtension
import lang.temper.log.UnknownCodeLocation
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.tooling.Def
import lang.temper.tooling.FileState
import lang.temper.tooling.LiveFileHandler
import lang.temper.tooling.LocPos
import lang.temper.tooling.ToolToken
import lang.temper.tooling.sequenceComboToolTokens
import lang.temper.tooling.sequenceToolTokens
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensEdit
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.lang.Integer.min
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

typealias SemanticTokensResultId = String

/**
 * The document service is responsible for keeping track of document content by processing
 * open/close/change events.
 *
 * This provides semantic syntax highlighting for Temper files.
 * We do this instead of using a *TextMate* grammar.  It may be possible to get a *TextMate* grammar
 * that does well for Temper's recursive lexical constructs like template strings via language
 * embedding tricks, but the semantic syntax handling features allow us to just fire up Temper's
 * lexer.
 */
@DelicateCoroutinesApi
internal class TemperTextDocumentServiceImpl(
    private val languageServer: TemperLanguageServerImpl,
) : TextDocumentService {
    private val console = languageServer.console
    private val documents = ConcurrentHashMap<DocumentUri, DocumentState>()
    private var mayUseMultilineTokens = false

    private suspend fun awaitFlushed(documentState: DocumentState): Boolean {
        documentState.handler?.let { handler ->
            if (handler.hasChanges.value) {
                handler.hasChanges.first { !it }
                return@awaitFlushed true
            }
        }
        return false
    }

    private fun flushHandler(documentState: DocumentState): Boolean {
        val handler = documentState.handler ?: return false
        return handler.flush {
            // Expect changes on actual flush, which means we'll end up waiting for module stability.
            languageServer.workspaceManager.builderManagerFor(documentState.uri.uri)?.let {
                // A bit brave to runBlocking here, but we expect state update to be fast.
                runBlocking {
                    it.manager.expectChanges(it.manager.toFilePath(it.path))
                }
            }
        }
    }

    private suspend fun awaitFlushed(uri: String) = documents[DocumentUri(uri)]?.let { awaitFlushed(it) } ?: false

    private fun flushHandler(uri: String) = documents[DocumentUri(uri)]?.let { flushHandler(it) } ?: false

    private inline fun debugSemanticTokens(msg: () -> String) {
        if (DEBUG_SEMANTIC_TOKENS) {
            console.info(msg())
        }
    }

    fun useMultilineTokens(multilineTokenSupport: Boolean) {
        this.mayUseMultilineTokens = multilineTokenSupport
    }

    @Suppress("MagicNumber") // Yep.
    private val semanticTokensResultCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build<SemanticTokensResultId, IntArray>()

    private fun makeDocumentState(uri: DocumentUri): DocumentState {
        // Track non-temper files, even if we don't do most tooling for them.
        val handler = languageServer.workspaceManager.builderManagerFor(uri.uri, allowNonTemper = true)?.let {
            LiveFileHandler(
                coroutineScope = it.manager.serverBuildScope,
                fileSystem = it.manager.liveFileSystem,
                // The live file system is already for the workroot portion only.
                path = it.manager.toFilePath(it.path, workPrefixed = false),
            )
        }
        val state = DocumentState(handler = handler, uri = uri)
        handler?.start(state = state)
        return state
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        if (params == null) { return }
        val textDocument = params.textDocument
        val uri = DocumentUri(textDocument.uri)
        val version = DocumentVersion(textDocument.version)
        val text = textDocument.text
        console.log("TextDocumentService didOpen $uri:$version")
        val documentState = documents.computeIfAbsent(uri) { makeDocumentState(it) }
        documentState.updateText(version, text)
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        if (params == null) { return }
        val textDocument = params.textDocument
        val uri = DocumentUri(textDocument.uri)
        val version = DocumentVersion(textDocument.version)
        console.log(
            "TextDocumentService didChange ${params.textDocument} + ${params.contentChanges}",
        )
        val documentState = documents.computeIfAbsent(uri) { makeDocumentState(it) }
        documentState.applyChanges(version, params.contentChanges)
        if (params.contentChanges.any { it.text.endsWith(".") }) {
            // In certain contexts, prep early for better completion.
            flushHandler(documentState)
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        if (params == null) { return }
        val uri = DocumentUri(params.textDocument.uri)
        documents.remove(uri)?.close()
        console.log("TextDocumentService didClose ${params.textDocument}")
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        if (params == null) { return }
        // Flush on save seems a nice touch.
        flushHandler(params.textDocument.uri)
        console.log("TextDocumentService didSave ${params.textDocument} -> ${params.text}")
    }

    private fun fetchDocumentContent(
        documentIdentifier: TextDocumentIdentifier,
    ): Pair<DocumentUri, String>? {
        val documentUri = DocumentUri(documentIdentifier.uri)
        val documentState = documents[documentUri]
        if (documentState != null) {
            return documentUri to documentState.documentText.fullText
        }
        // Fall back to looking at file system
        val path = pathForUri(documentUri) ?: return null
        return documentUri to toStringViaBuilder { sb ->
            Files.newBufferedReader(
                path,
                Charsets.UTF_8, // Temper files must be UTF-8
            ).use { r ->
                val chars = CharArray(READ_CHUNK_SIZE)
                while (true) {
                    val n = r.read(chars)
                    if (n <= 0) { break }
                    sb.appendRange(chars, 0, n)
                }
            }
        }
    }

    override fun completion(
        params: CompletionParams?,
    ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> = params?.let {
        languageServer.workspaceManager.builderManagerFor(params.textDocument.uri)?.let { (manager, path) ->
            // We can't distinguish just typing ("24x7 code complete") from explicit request (both have triggerKind 1),
            // so don't trigger update.
            // Instead, rely on client behavior during typing to naturally filter lists sent earlier.
            // flushHandler(params.textDocument.uri)
            languageServer.handleAsJdkFuture(params, "LSP completion task") { cancelled ->
                val defs = runBlocking {
                    val filePath = manager.toFilePath(path)
                    manager.readStable(
                        filePath,
                        cancelled = cancelled,
                        default = { null },
                        label = "completion",
                        suggestMissingConfigDelay = SuggestionDelay.Delayed,
                    ) { data ->
                        data?.findCompletions(LocPos(filePath, params.position.toTemper()))
                    }
                } ?: emptyList()
                // Just retain our server-side sort order using padded ints for `sortText`.
                val padSize = padSizeBase10(defs)
                Either.forLeft(
                    defs.mapIndexed { index, def ->
                        CompletionItem().apply {
                            kind = when (def.pos.loc) {
                                // These aren't all even pseudo-keywords, but it's some distinction for now.
                                ImplicitsCodeLocation, UnknownCodeLocation -> CompletionItemKind.Keyword
                                else -> CompletionItemKind.Variable
                            }
                            label = def.text
                            sortText = index.toString().padStart(padSize, '0')
                        }
                    }.toMutableList(),
                )
            }
        }
    } ?: CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))

    override fun definition(
        params: DefinitionParams?,
    ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> = params?.let {
        languageServer.workspaceManager.builderManagerFor(params.textDocument.uri)?.let { (manager, path) ->
            flushHandler(params.textDocument.uri)
            languageServer.handleAsJdkFuture(params, "LSP definition task") { cancelled ->
                val pos = runBlocking {
                    val filePath = manager.toFilePath(path)
                    val label = "definition"
                    manager.accessStoreForStable(
                        filePath,
                        cancelled = cancelled,
                        label = label,
                        suggestMissingConfigDelay = SuggestionDelay.Immediate,
                    )?.findDefPos(LocPos(filePath, params.position.toTemper()), awaitFinish = true, label = label)
                }
                Either.forLeft(
                    pos?.let {
                        val defPos = pos.pos.toLsp()
                        manager.resolveModuleName(pos.loc)?.let { moduleName ->
                            mutableListOf(
                                Location().apply {
                                    uri = moduleName.toUri().toString()
                                    range = Range(defPos, defPos)
                                },
                            )
                        }
                    } ?: mutableListOf(),
                )
            }
        }
    } ?: CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))

    override fun hover(params: HoverParams?): CompletableFuture<Hover> = params?.let {
        languageServer.workspaceManager.builderManagerFor(params.textDocument.uri)?.let { (manager, path) ->
            flushHandler(params.textDocument.uri)
            languageServer.handleAsJdkFuture(params, "LSP hover task") { cancelled ->
                val decl = runBlocking {
                    val filePath = manager.toFilePath(path)
                    manager.readStable(
                        filePath,
                        cancelled = cancelled,
                        default = { null },
                        label = "hover",
                    ) { data ->
                        data?.findDecl(LocPos(filePath, params.position.toTemper()))
                    }
                }
                decl.toHover()
            }
        }
    } ?: CompletableFuture.completedFuture(Hover().apply { contents = Either.forLeft(listOf()) })

    override fun semanticTokensFull(params: SemanticTokensParams?): CompletableFuture<SemanticTokens> {
        console.log("tokenizing for ${params?.textDocument}")
        val textDocument = params?.textDocument
            ?: run {
                return@semanticTokensFull CompletableFuture.completedFuture(
                    SemanticTokens(null, emptyList()),
                )
            }
        return languageServer.handleAsJdkFuture(
            params,
            "LSP semantic tokens full task",
        ) { cancelled ->
            computeSemanticTokens(
                textDocument,
                cancelled,
            ) { resultId, data ->
                SemanticTokens(resultId, data.asList())
            }
        }
    }

    private fun <T> computeSemanticTokens(
        textDocument: TextDocumentIdentifier,
        cancelled: AtomicBoolean,
        withTokenData: (SemanticTokensResultId?, IntArray) -> T,
    ) = try {
        computeSemanticTokensThrowing(
            textDocument = textDocument,
            cancelled = cancelled,
            withTokenData = withTokenData,
        )
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        console.error(e.stackTraceToString())
        withTokenData(null, emptyIntArray)
    }

    private fun <T> computeSemanticTokensThrowing(
        textDocument: TextDocumentIdentifier,
        cancelled: AtomicBoolean,
        withTokenData: (SemanticTokensResultId?, IntArray) -> T,
    ): T {
        val docUri = DocumentUri(textDocument.uri)
        // Look for an extension to figure out what kind of Temper we're parsing.
        val lang = languageConfigForExtension(docUri.extension)
        return languageServer.workspaceManager.builderManagerFor(textDocument.uri)?.let { (manager, path) ->
            runBlocking {
                // We don't want to flush on every edit, but we also don't want to report positions in a stale file.
                val filePath = manager.toFilePath(path)
                if (awaitFlushed(textDocument.uri)) {
                    manager.awaitUnstable(filePath)
                }
                manager.readStable(
                    filePath,
                    cancelled = cancelled,
                    default = { null },
                    label = "tokens",
                ) tree@{ data ->
                    val (_, content) = fetchDocumentContent(textDocument) ?: return@tree null
                    data?.sequenceComboToolTokens(
                        filePath = filePath,
                        content = content,
                        lang = lang,
                        additiveOnly = languageServer.options.tokenMode == TokenMode.Add,
                    )?.let { tokens ->
                        computeSemanticTokens(
                            tokens,
                            content = content,
                            cancelled = cancelled,
                            withTokenData = withTokenData,
                        )
                    }
                }
            }
        } ?: run lexer@{
            val (_, content) = fetchDocumentContent(textDocument) ?: return@lexer null
            // None of the basic lexer tokens are additive to the client grammar.
            languageServer.options.tokenMode == TokenMode.Add && return@lexer null
            val tokens = sequenceToolTokens(
                codeLocation = TextDocumentCodeLocation(DocumentUri(textDocument.uri)),
                content = content,
                lang = lang,
            )
            computeSemanticTokens(tokens, content = content, cancelled = cancelled, withTokenData = withTokenData)
        } ?: withTokenData(null, emptyIntArray)
    }

    private fun <T> computeSemanticTokens(
        tokens: Sequence<ToolToken>,
        content: String,
        cancelled: AtomicBoolean,
        withTokenData: (SemanticTokensResultId?, IntArray) -> T,
    ): T {
        if (cancelled.get()) {
            return withTokenData(null, emptyIntArray)
        }
        val data = computeSemanticTokens(
            tokens = tokens,
            content = content,
            mayUseMultilineTokens = mayUseMultilineTokens,
            cancelled = cancelled,
        )
        val resultId = if (cancelled.get()) {
            null
        } else {
            @Suppress("SpellCheckingInspection")
            languageServer.mintUniqueTokenId("toks")
        }
        debugSemanticTokens {
            toStringViaBuilder { sb ->
                sb.append(". resultId=$resultId\n")
                sb.append(". packed data: [")
                data.forEachIndexed { i, x ->
                    if (i != 0) {
                        sb.append(if ((i % PACKED_INT_FIELD_COUNT) == 0) { " ; " } else { ", " })
                    }
                    sb.append(x)
                }
                sb.append(']')
            }
        }
        if (resultId != null) {
            semanticTokensResultCache.put(resultId, data)
        }
        return withTokenData(resultId, data)
    }

    override fun semanticTokensFullDelta(
        params: SemanticTokensDeltaParams?,
    ): CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> {
        console.log("tokenizing delta for ${params?.textDocument}")
        val textDocument = params?.textDocument
            ?: return CompletableFuture.completedFuture(
                Either.forLeft(SemanticTokens(null, emptyList())),
            )
        val prior = semanticTokensResultCache.getIfPresent(params.previousResultId)
        return languageServer.handleAsJdkFuture(
            params,
            "LSP semantic tokens full delta task",
        ) { cancelled ->
            computeSemanticTokens(textDocument, cancelled) { resultId, data ->
                if (prior == null) {
                    Either.forLeft(SemanticTokens(resultId, data.asList()))
                } else {
                    val left = commonPrefixSize(prior, data)
                    val edits: List<SemanticTokensEdit> =
                        if (prior.size == data.size && left == data.size) {
                            listOf()
                        } else {
                            val right = commonSuffixSize(prior, data, limit = left)
                            val replacements = data.asList().subList(left, data.size - right)
                            val deleteCount = (prior.size - right) - left
                            // Consider two cases:
                            // (1) Reduction
                            //     before: A A A A A X X X A A A A A
                            //     after:  A A A A A Y Y   A A A A A
                            //     common left = 5
                            //     common right = 5
                            //     a.size = 13
                            //     b.size = 12
                            //     deleteCount = 13 - 5 - 5 = 3 (all X's)
                            //     replacements = data.subList(5, 7) = [Y, Y]
                            //     start = 5
                            // (2) Expansion
                            //     before: A A A A A X X   A A A A A
                            //     after:  A A A A A Y Y Y A A A A A
                            //     common left = 5
                            //     common right = 5
                            //     a.size = 12
                            //     b.size = 13
                            //     deleteCount = 12 - 5 - 5 = 2 (all X's)
                            //     replacements = data.subList(5, 8) = [Y, Y, Y]
                            //     start = 5
                            listOf(
                                SemanticTokensEdit(
                                    /* start */
                                    left,
                                    /* deleteCount */
                                    deleteCount,
                                    /* data */
                                    replacements,
                                ),
                            )
                        }

                    Either.forRight(
                        SemanticTokensDelta(edits, resultId),
                    )
                }
            }
        }
    }
}

/** A document URI. */
// @JvmInline // TODO: I would make this a value class but they break ktLint
internal data class DocumentUri(val uri: String) {
    override fun toString(): String = uri

    /** Any file extension, with the leading dot, or the empty string if none is present. */
    val extension: String get() {
        var strippedUriText = uri
        // Strip any fragment from the URL since it's not part of the file path
        val fragmentStart = strippedUriText.indexOf('#')
        if (fragmentStart >= 0) {
            strippedUriText = strippedUriText.substring(0, fragmentStart)
        }
        // Strip any query from the URL since it's not part of the file path
        val queryStart = strippedUriText.indexOf('?')
        if (queryStart >= 0) {
            strippedUriText = strippedUriText.substring(0, queryStart)
        }
        // If there is a slash after the last dot then it is not part of the basename
        val lastSegment = strippedUriText.lastIndexOf('/')
        if (lastSegment >= 0) {
            strippedUriText = strippedUriText.substring(lastSegment + 1)
        }
        // Finally, look for a dot.
        val lastDot = strippedUriText.lastIndexOf('.')
        return if (lastDot >= 0) {
            strippedUriText.substring(lastDot)
        } else {
            ""
        }
    }
}

/** A version number for a document. */
// @JvmInline
internal data class DocumentVersion(val version: Int) : Comparable<DocumentVersion> {
    override fun compareTo(other: DocumentVersion): Int =
        this.version.compareTo(other.version)

    override fun toString(): String = "$version"
}

private val DEFAULT_TRIGGER_DELAY = 2.seconds

internal data class DocumentState(
    val uri: DocumentUri,
    val handler: LiveFileHandler? = null,
) : FileState {
    private var latestVersion: DocumentVersion? = null
    val documentText = DocumentText(TextDocumentCodeLocation(uri))

    override fun toByteArray() = when (latestVersion) {
        null -> null
        else -> documentText.fullText.toByteArray()
    }

    @Synchronized
    fun close() {
        updateOrCloseText(version = null, text = "")
    }

    @Synchronized
    fun updateText(version: DocumentVersion, text: String) {
        updateOrCloseText(version = version, text = text)
    }

    @Synchronized
    private fun updateOrCloseText(version: DocumentVersion?, text: String) {
        val priorVersion = this.latestVersion
        if (priorVersion == null || version == null || version > priorVersion) {
            this.latestVersion = version
            synchronized(documentText) {
                documentText.replace(documentText.wholeTextRange, text)
            }
            handler?.trigger(version?.let { DEFAULT_TRIGGER_DELAY } ?: kotlin.time.Duration.ZERO)
        }
    }

    @Synchronized
    fun applyChanges(version: DocumentVersion, changes: List<TextDocumentContentChangeEvent>) {
        val priorVersion = this.latestVersion
        if (priorVersion != null && version > priorVersion) {
            synchronized(documentText) {
                for (change in changes) {
                    val range = change.range
                    val textDelta = change.text
                    documentText.replace(range, textDelta)
                }
            }
            this.latestVersion = version
            handler?.trigger(DEFAULT_TRIGGER_DELAY)
        }
    }
}

private fun commonPrefixSize(a: IntArray, b: IntArray): Int {
    val minSize = min(a.size, b.size)
    var n = 0
    while (n < minSize && a[n] == b[n]) {
        n += 1
    }
    return n
}

private fun commonSuffixSize(a: IntArray, b: IntArray, limit: Int = 0): Int {
    val aSize = a.size
    val aLast = aSize - 1
    val bSize = b.size
    val bLast = bSize - 1
    val maxN = min(aSize, bSize) - limit
    var n = 0
    while (n < maxN && a[aLast - n] == b[bLast - n]) {
        n += 1
    }
    return n
}

/** When reading file content, read it in chunks about the size of a page. */
private const val READ_CHUNK_SIZE = 4096

private fun padSizeBase10(defs: List<Def>) = floor(log10(max(1, defs.size).toDouble())).toInt() + 1
