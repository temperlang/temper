package lang.temper.docgen

import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.docgen.anticorruption.CompilationResult
import lang.temper.docgen.anticorruption.Compiler
import lang.temper.docgen.anticorruption.CompilerImpl
import lang.temper.docgen.anticorruption.ResultingCode
import lang.temper.docgen.anticorruption.UserMessageSink
import lang.temper.docgen.parsers.HtmlDocumentParser
import lang.temper.docgen.parsers.MarkdownDocumentParser
import lang.temper.docgen.transformations.CodeTransformer
import lang.temper.fs.loadResource
import lang.temper.library.LibraryConfiguration
import lang.temper.log.LogSink
import lang.temper.log.dirPath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import org.junit.jupiter.api.Timeout
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DocGenAcceptanceTest {
    private val testedBackends = listOf(BackendId("js"), BackendId("py"))

    private var _cancelGroup: CancelGroup? = null
    private val cancelGroup: CancelGroup get() = _cancelGroup!!

    @BeforeTest
    fun setUp() {
        _cancelGroup = makeCancelGroupForTest()
    }

    @AfterTest
    fun tearDown() {
        _cancelGroup = null
    }

    private fun compilerImpl() = CompilerImpl(config, cancelGroup = cancelGroup)

    @Test
    fun examples() {
        val outputLocation = helper(Paths.get("src", "test", "resources", "examples"), FakeCompiler())
        assertEquals(2, outputLocation.toFile().walk().filter { it.isFile }.count())
    }

    @Ignore // TODO(mikesamuel, integrated-doc-gen): need to define `foo` in preface
    @Test
    fun isEmpty() {
        val outputLocation = helper(Paths.get("src", "test", "resources", "isEmpty"), compilerImpl())
        val text = outputLocation.resolve("IsEmpty.md").readText()
        assert(text.isNotEmpty())
        assertContains(text, "let obj = new Foo().isEmpty", message = "Failed to generate object is empty code")
        assertContains(text, "let str = ! \"\"", message = "Failed to generate string is empty code")
        assertContains(text, "let l = ![].length", message = "Failed to generate list is empty code")
        assertContains(text, "let hasSome = ![3, 4].length", message = "Failed is empty for non-empty list")
        assertContains(
            text,
            "bar.isEmpty",
            message = "Failed to have generate the object case for a random variable",
        )
    }

    @Test
    fun diffDocs() {
        val outputLocation = helper(Paths.get("src", "test", "resources", "diffDocs"), compilerImpl())

        val text = outputLocation.resolve("DiffDocs.md").readText()
        assert(text.isNotEmpty())
        // TODO better asserts
    }

    @Test
    fun textAfterCodeBlock() {
        val docGen = prepDocGen(
            libraryRoot = dirPath("hi", "there"),
            logSink = LogSink.devNull,
            backends = testedBackends,
            cancelGroup = cancelGroup,
        )
        var text = loadResource(this, "textAfterCode/TextAfterCode.md")
        // An error happens on the given input, but only with crlf rather than just lf.
        text = text.replace("\n", "\r\n")

        docGen.processFile(file = File("something.md")) { StringReader(text) }
    }

    @Test
    @Timeout(LONGER_TIMEOUT) // Because repeated unexplained timeouts.
    fun indentedCodeBlocksStayIndented() {
        val outputLocation = helper(
            Paths.get("src", "test", "resources", "indentedCodeBlocks"),
            compilerImpl(),
        )
        val text = outputLocation.resolve("example.md").readText()
        assertContains(text, "  const a = 5;")
        assertContains(text, "> const b = 6;")
    }

    @Ignore // TODO(mikesamuel, integrated-doc-gen): decide if we need ternary support in doc lang subset
    @Test
    fun ternary() {
        val docGen = prepDocGen(
            libraryRoot = dirPath("hi", "there"),
            logSink = LogSink.devNull,
            backends = testedBackends,
            cancelGroup = cancelGroup,
        )
        val text = loadResource(this, "ternary/test.md")

        val out = docGen.processFile(file = File("something.md")) { StringReader(text) }
        val outText = buildString {
            out?.writeTo(this)
        }
        assertContains(outText, "Nontranslatable: ternary operator")
    }

    // TODO: make sure that foo and its type's members are defined so we don't depend on invalid types.
    @Ignore
    @Test
    fun methodVsProperty() {
        val outputLocation = helper(Paths.get("src", "test", "resources", "methodVProp"), compilerImpl())

        val text = outputLocation.resolve("test.md").readText()

        assertContains(
            text,
            """
            |```js
            |foo.bar();
            |foo.baz;
            |foo.bar();
            |
            |```
            """.trimMargin(),
        )
        assertContains(
            text,
            """
            |```py
            |foo.bar()
            |foo.baz
            |foo.bar()
            |
            |```
            """.trimMargin(),
        )
    }

    private val projectRoot = dirPath("root")
    private val config = LibraryConfiguration(
        libraryName = DashedIdentifier("the-foo-project"),
        libraryRoot = projectRoot,
        supportedBackendList = testedBackends,
        classifyTemperSource = { error("classifyTemperSource not needed for test") },
    )

    private fun helper(inputPath: Path, compiler: Compiler): Path {
        val parsers = listOf(HtmlDocumentParser(), MarkdownDocumentParser())

        val transformers = listOf(
            CodeTransformer(
                compiler,
                BlackHoleUserMessageSink(),
                LogSink.devNull,
            ),
        )
        val docGen = DocGen(parsers, transformers, cancelGroup)
        val outputLocation = kotlin.io.path.createTempDirectory()

        docGen.processDocTree(inputPath, outputLocation)

        return outputLocation
    }
}

class FakeCompiler : Compiler {
    override fun <T : SimpleCodeFragment> compile(fragments: List<T>): CompilationResult<T> {
        return CompilationResult(
            emptyList(),
            // Need to associate the fragment to something otherwise it never stops
            ResultingCode(emptyMap(), fragments.associateWith { (emptyMap()) }),
            emptyList(),
        )
    }
}

class BlackHoleUserMessageSink : UserMessageSink {
    @Suppress("EmptyFunctionBlock")
    override fun message(message: String) {
    }
}

const val LONGER_TIMEOUT = 60L
