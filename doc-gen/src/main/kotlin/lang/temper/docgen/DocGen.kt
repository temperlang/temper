package lang.temper.docgen

import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.docgen.anticorruption.CompilerImpl
import lang.temper.docgen.anticorruption.UserMessageSink
import lang.temper.docgen.parsers.HtmlDocumentParser
import lang.temper.docgen.parsers.MarkdownDocumentParser
import lang.temper.docgen.transformations.CodeTransformer
import lang.temper.docgen.transformations.Transformer
import lang.temper.fs.ignoredPaths
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.isDirectory

class DocGen(
    private val parsers: Collection<DocumentParser<*, *, *>>,
    private val transformations: Collection<Transformer>,
    private val cancelGroup: CancelGroup,
) {
    fun processDocTree(inputDirectory: Path, outputDirectory: Path) {
        require(inputDirectory.isDirectory()) { "$inputDirectory is not a directory" }

        val inputFile = inputDirectory.toFile()
        inputFile.walk().onEnter { it.name !in ignoredPaths }.forEach { file ->
            if (file.isFile) {
                val doc = processFile(file)
                val outputLocation = outputDirectory.resolve(file.relativeTo(inputFile).path)

                if (doc != null) {
                    val outputFile = outputLocation.toFile()
                    outputFile.parentFile.mkdirs()
                    FileWriter(outputFile).use { doc.writeTo(it) }
                } else {
                    file.copyTo(outputLocation.toFile())
                }
            }
        }
    }

    fun processFile(file: File, open: () -> Reader = { FileReader(file) }) =
        parsers.firstOrNull { it.canParse(file) }?.let { parser ->
            open().use { input ->
                parser.parse(input).also { doc -> transformations.forEach { it.transform(doc) } }
            }
        }

    override fun toString(): String = "DocGen(parsers=$parsers, transformations=$transformations)"
}

fun prepDocGen(
    libraryRoot: FilePath,
    logSink: LogSink,
    backends: List<BackendId>,
    cancelGroup: CancelGroup,
): DocGen {
    val config = LibraryConfiguration(
        libraryName = DashedIdentifier("placeholder"),
        libraryRoot = libraryRoot,
        // TODO: allow customization
        classifyTemperSource = ::defaultClassifyTemperSource,
        supportedBackendList = backends,
    )

    val parsers = listOf(HtmlDocumentParser(), MarkdownDocumentParser())

    val compiler = CompilerImpl(config, cancelGroup = cancelGroup)
    val transformers = listOf(
        CodeTransformer(compiler, ConsoleUserMessageSink, logSink),
    )
    return DocGen(parsers, transformers, cancelGroup = cancelGroup)
}

object ConsoleUserMessageSink : UserMessageSink {
    override fun message(message: String) {
        console.info(message)
    }
}
