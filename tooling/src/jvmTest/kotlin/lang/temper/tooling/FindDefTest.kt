package lang.temper.tooling

import lang.temper.frontend.ModuleSource
import kotlin.test.Test

internal open class FindDefTest {
    companion object {
        val dirContext = DirModuleDataTestContext(
            sources = listOf(
                ModuleSource(
                    fetchedContent = """
                        |let /*1@+0*/a = /*6@+0*/5;
                        |let b = 10;
                    """.trimMargin(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |let f(/*3@+0*/b/*5*/: Int, c: String): Int {
                        |    return /*0@+0*/a + /*2@+0*/b/*4*/;
                        |}
                        |f(b/*7*/, "thing");
                    """.trimMargin(),
                ),
            ),
        )

        val fileContext = dirContext.mergedFileContext()
    }

    open val context: ModuleDataTestContext get() = fileContext

    @Test
    fun findExternalA() {
        assertFound("/*0@+0*/" to "/*1@+0*/")
    }

    @Test
    fun findInternalB() {
        assertFound("/*2@+0*/" to "/*3@+0*/")
    }

    @Test
    fun findFromEnd() {
        assertFound("/*4*/" to "/*3@+0*/")
    }

    @Test
    fun findDefItself() {
        // Might change this later to finding refs.
        assertFound("/*5*/" to "/*3@+0*/")
    }

    @Test
    fun failToFindIntLiteralDef() {
        assertFound("/*6@+0*/" to null)
    }

    @Test
    fun failToFindThisWithoutClassName() {
        val goodContext = FileModuleDataTestContext("""class /*1@+0*/Hi { let hi(): Void { this/*0*/; } }""")
        goodContext.assertFound(refDef = "/*0*/" to "/*1@+0*/")
        // TODO(tjp, tooling): Anonymous classes are allowed, so this isn't bad code, and we might should handle it.
        val badContext = FileModuleDataTestContext(goodContext.moduleSource.replace("Hi", ""))
        badContext.assertFound(refDef = "/*0*/" to null)
    }

    private fun assertFound(refDef: Pair<String, String?>) = context.assertFound(refDef)
}

internal class FindDefDirTest : FindDefTest() {
    override val context get() = dirContext
}
