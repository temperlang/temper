package lang.temper.docgen.anticorruption

import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.docgen.SimpleCodeFragment
import lang.temper.library.LibraryConfiguration
import lang.temper.log.dirPath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains

class CompilerImplTest {

    private val compiler: Compiler
    private val testBackends: List<BackendId>

    init {
        val projectRoot = dirPath("work")
        testBackends = listOf(BackendId("js"), BackendId("py"))
        val config = LibraryConfiguration(
            libraryName = DashedIdentifier("the-foo-project"),
            libraryRoot = projectRoot,
            supportedBackendList = testBackends,
            classifyTemperSource = { error("classifyTemperSource not needed for test") },
        )

        val cancelGroup = makeCancelGroupForTest()
        compiler = CompilerImpl(config, cancelGroup = cancelGroup)
    }

    @Test
    fun works() {
        val fragment = code("""export let foo = "this works";""")

        val result = compiler.compile(listOf(fragment))

        result.assertNoErrors()
        result.resultingCode.replacements.getValue(fragment).values.forEach { actual ->
            assertContains(actual, "foo")
            assertContains(actual, "this works")
        }
    }

    @Test
    fun halfAThingErrors() {
        val fragment = code("export class Foo {")

        val result = compiler.compile(listOf(fragment))

        assert(result.errors.isNotEmpty())
    }

    @Ignore // TODO(mikesamuel, integrated-doc-gen): fix `this` in doc TmpL translation
    @Test
    fun twoFragmentsMakeAClass() {
        val fragment1 = code("export class Foo {")
        val fragment2 = code("}")

        val result = compiler.compile(listOf(fragment1, fragment2))

        result.assertNoErrors()
        // TODO fill in an assert once it is clearer what will be here
    }

    private fun code(input: String): SimpleCodeFragment {
        return object : SimpleCodeFragment {
            override val isTemperCode: Boolean
                get() = true
            override val sourceText: CharSequence
                get() = input
        }
    }
}

fun <T : SimpleCodeFragment> CompilationResult<T>.assertNoErrors() {
    assert(this.errors.isEmpty()) { this.errors.joinToString("\n") }
}
