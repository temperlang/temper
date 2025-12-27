@file:JvmName("Serve")

package lang.temper.langserver

import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.PrefixingTextOutput
import lang.temper.common.consoleOutput
import lang.temper.common.ignore
import lang.temper.common.prefixTimestamp
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import sun.misc.Signal
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.Writer
import java.net.Socket
import kotlin.system.exitProcess

// It seems backwards that a Language**Server** reaches out to connect to a Language**Client**
// but that's how it's done in
// github.com/adamvoss/vscode-languageserver-java-example/blob/master/server/src/main/java/App.java

@DelicateCoroutinesApi
fun doServe(port: Int, tokenMode: TokenMode) {
    // TODO Only go to file on request, so we don't double fill the drive? Does this temp file get cleaned?
    val logFile = File.createTempFile("temper-lang-server-", ".log")
    // In vscode, stderr also gets logged to a file. In the vscode extension, we rotate logs explicitly.
    System.err.println("Logging to $logFile")
    val logWriter = BufferedWriter(FileWriter(logFile, Charsets.UTF_8))
    // Dump to both stderr and the log file.
    val combinedWriter = PrintWriter(
        object : Writer() {
            private val outputs = listOf(logWriter, PrintWriter(System.err))
            override fun close() = logWriter.close()
            override fun flush() = outputs.forEach { it.flush() }
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                outputs.forEach {
                    it.write(cbuf, off, len)
                }
            }
        },
    )
    val console = Console(
        textOutput = PrefixingTextOutput(AppendingTextOutput(combinedWriter), ::prefixTimestamp),
        logLevel = Log.Fine,
    )
    consoleOutput.emitPrefix = ::prefixTimestamp

    console.log("Starting Temper language server")

    fun teardown(signal: Signal?) {
        ignore(signal)
        combinedWriter.flush()
        console.log("Temper Language Server shutting down.  Bye bye")
        combinedWriter.close()
    }
    Signal.handle(Signal("INT"), ::teardown)
    // handle(QUIT) leads to IllegalArgumentException("Signal already used by VM or OS: SIGQUIT")

    val langServer = TemperLanguageServerImpl(
        console,
        onExit = { statusCode ->
            teardown(null)
            exitProcess(statusCode)
        },
        options = TemperLanguageServerOptions(tokenMode = tokenMode),
    )

    val socket = Socket("localhost", port)

    val inputStream = socket.getInputStream()
    val outputStream = socket.getOutputStream()

    console.log("Listening on $port")

    val launcher = createServerLauncher(
        server = langServer,
        input = inputStream,
        output = outputStream,
        trace = combinedWriter,
    )
    langServer.connect(launcher.remoteProxy)
    launcher.startListening().get()

    console.log("End of main")
}

private fun createServerLauncher(
    server: LanguageServer?,
    input: InputStream?,
    output: OutputStream?,
    trace: PrintWriter?,
): Launcher<TemperLanguageClient> = LSPLauncher.Builder<TemperLanguageClient>().run {
    setLocalService(server)
    setRemoteInterface(TemperLanguageClient::class.java)
    setInput(input)
    setOutput(output)
    validateMessages(true)
    traceMessages(trace)
    create()
}
