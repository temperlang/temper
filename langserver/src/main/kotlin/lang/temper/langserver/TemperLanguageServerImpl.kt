package lang.temper.langserver

import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.common.Console
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.currents.asJdkFuture
import lang.temper.common.ignore
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.WorkDoneProgressParams
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

internal class LSConfiguration(options: TemperLanguageServerOptions) {
    val temperServerCapabilities = ServerCapabilities().apply {
        completionProvider = CompletionOptions().apply {
            triggerCharacters = listOf(".")
        }
        definitionProvider = Either.forLeft(true)
        hoverProvider = Either.forLeft(true)
        if (options.tokenMode != TokenMode.None) {
            semanticTokensProvider = semanticTokensProviderOptions
        }
        textDocumentSync = Either.forLeft(TextDocumentSyncKind.Incremental)
        workspace = WorkspaceServerCapabilities().apply {
            workspaceFolders = WorkspaceFoldersOptions().apply {
                changeNotifications = Either.forRight(true)
                supported = true
            }
        }
        // TODO: trigger formatting on right curlies and semicolons
        // documentOnTypeFormattingProvider = DocumentOnTypeFormattingOptions( TODO() )
    }

    val temperServerInfo = ServerInfo("Temper Language Server", "0.1")
}

internal typealias CancellationToken = Either<String, Int>

@DelicateCoroutinesApi
internal class TemperLanguageServerImpl(
    internal val console: Console,
    private val onExit: (Int) -> Unit,
    internal val options: TemperLanguageServerOptions = TemperLanguageServerOptions(),
) : TemperLanguageServer {
    val executorService = Executors.newCachedThreadPool()
    val workspaceManager = WorkspaceManager(this)
    var updatePublisher = UpdatePublisher(languageServer = this)

    internal var client: TemperLanguageClient? = null
        private set
    private val textDocumentService = TemperTextDocumentServiceImpl(this)
    private val workspaceService = TemperWorkspaceServiceImpl(this)
    private var exitCode = -1
    private val onCancelCallbacks = ConcurrentHashMap<CancellationToken, () -> Unit>()

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        if (params != null) {
            console.log(
                """
                |TemperLanguageServerImpl.initialize
                |    workspaceFolders=${params.workspaceFolders}
                |    client=${params.clientInfo?.name} version=${params.clientInfo?.version}
                |    initializationOptions=${params.initializationOptions}
                |    workDoneToken=${params.workDoneToken}
                """.trimMargin(),
            )
            textDocumentService.useMultilineTokens(
                params.capabilities.textDocument.semanticTokens.multilineTokenSupport == true,
            )
            workspaceManager.update(params.workspaceFolders.orEmpty())
        } else {
            console.log("TemperLanguageServerImpl.initialize no params")
        }
        val configuration = LSConfiguration(options)
        return CompletableFuture.completedFuture(
            InitializeResult(configuration.temperServerCapabilities, configuration.temperServerInfo),
        )
    }

    override fun cancelProgress(params: WorkDoneProgressCancelParams?) {
        if (params != null) {
            val cancellationToken: CancellationToken = params.token
            onCancelCallbacks.remove(cancellationToken)?.invoke()
        }
    }

    /**
     * Called by services to respond to a cancellable request.
     */
    private fun <T : Any> handle(
        /** The parameters to the cancellable request. */
        params: WorkDoneProgressParams,
        taskDescription: String,
        /**
         * Computes the result, stopping and producing a garbage result should
         * *cancelled* becomes true.
         */
        task: (cancelled: AtomicBoolean) -> T,
    ): RFuture<T, Nothing> {
        val cancelled = AtomicBoolean(false)

        @Suppress("TooGenericExceptionCaught") // It legit uses RTE(InvocationTargetExn)
        val cancelKey = try {
            params.workDoneToken
        } catch (ex: RuntimeException) {
            ignore(ex)
            // workDoneToken can be null, but LSP4J doesn't recognize this.
            // TODO: Is this code based on a fundamental misunderstanding of cancellation?
            // Do servers have to mint the cancel token as part of the WorkDoneProgressBegin flow?
            // Or can clients mint them?
            null
        }

        if (cancelKey != null) {
            onCancelCallbacks[cancelKey] = { cancelled.set(true) }
        }
        return UnmanagedFuture.newComputedResultFuture(taskDescription, executorService) {
            try {
                RSuccess(task(cancelled))
            } finally {
                if (cancelKey != null) {
                    onCancelCallbacks.remove(cancelKey)
                }
            }
        }
    }

    /**
     * Called by services to respond to a cancellable request.
     */
    internal fun <T : Any> handleAsJdkFuture(
        /** The parameters to the cancellable request. */
        params: WorkDoneProgressParams,
        taskDescription: String,
        /**
         * Computes the result, stopping and producing a garbage result should
         * *cancelled* becomes true.
         */
        task: (cancelled: AtomicBoolean) -> T,
    ): CompletableFuture<T> {
        return handle(params, taskDescription, task).asJdkFuture()
    }

    override fun shutdown(): CompletableFuture<Any> {
        console.log("Temper Language Server shutdown")
        exitCode = 0 // Client requested exit
        workspaceManager.update(listOf())
        updatePublisher.channel.trySend(LanguageServerClose) // should succeed because unlimited
        thread {
            // Attempt doing all this *after* we've replied, so the client gets a response.
            // TODO Ideally, we explicitly wait on knowing shutdown has been received by the client.
            TimeUnit.SECONDS.sleep(1)
            console.log("Temper Language Server shutdown complete")
            // We seem to get here fairly reliably but not back to our `onExit` handler in `Serve`.
            // Some coroutine scopes might not always be finishing, either.
            // Unfortunately, the vscode server log also seems to close before logging everything, too, which makes
            // diagnosis a bit harder.
            // So just exit process here for now, and revisit cleanup later.
            exit()
        }
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        console.log("Temper Language Server exit")
        onExit(exitCode)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    fun connect(client: TemperLanguageClient) {
        console.log("Got connect request")
        this.client = client
        client.echo("Hello client from TemperLanguageServerImpl")
        UnmanagedFuture.runLater("LSP publishing updates", executorService) {
            updatePublisher.publishUpdates()
        }
    }

    private val tokenCounter = AtomicLong()
    fun mintUniqueTokenId(prefix: String): String {
        val n = tokenCounter.getAndIncrement()
        return "$prefix:$n"
    }

    @JsonNotification
    override fun docGenSubscribe(request: DocGenKey) {
        workspaceManager.builderManagerFor(request.uri, allowNonTemper = true)?.let { (manager, path) ->
            manager.docGen.subscribe(key = request, path = path)
        }
    }

    @JsonNotification
    override fun docGenUnsubscribe(request: DocGenKey) {
        workspaceManager.builderManagerFor(request.uri, allowNonTemper = true)?.let { (manager, path) ->
            manager.docGen.unsubscribe(key = request, path = path)
        }
    }
}

data class TemperLanguageServerOptions(
    val tokenMode: TokenMode = TokenMode.Add,
)

@JsonSegment("temper")
interface TemperLanguageServer : LanguageServer {
    @JsonNotification
    fun docGenSubscribe(request: DocGenKey)

    @JsonNotification
    fun docGenUnsubscribe(request: DocGenKey)
}

@JsonSegment("temper")
interface TemperLanguageClient : LanguageClient {
    @JsonNotification
    fun docGen(message: DocGenUpdate)
}

fun TemperLanguageClient?.echo(msg: String) {
    console.info("Client console: $msg")
    this?.let { c ->
        val message = MessageParams(
            MessageType.Log,
            msg,
        )
        c.logMessage(message)
    }
}
