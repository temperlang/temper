@file:Suppress("MagicNumber")

package lang.temper.tooling

import lang.temper.frontend.ModuleSource
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

internal open class FindCompletionsTest {
    companion object {
        val dirContext = DirModuleDataTestContext(
            sources = listOf(
                ModuleSource(
                    fetchedContent = """
                        |// Leading comment for fun.
                        |let apple = 5;
                        |a/*0*/ + /*1*/b/*2*/;
                        |let banana = 10;
                        |let carrot = "orange";
                    """.trimMargin(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |let eat(bamboo: Int, carrot: String): Int {
                        |    let grape1(): Void {}
                        |    g/*11*/();
                        |    let grape2(): Void {}
                        |    let c/*3*/ = card/*4@-1*/ + d;
                        |    return a + bam/*5@-1*/;
                        |}
                        |eat(a + bam/*6@-1*/, "ba")
                        |let durian = /*8@+1*//*9@+1*/"something";
                        |class Fish {}
                        |let fish: String = new /*7@+1*/Fish();
                        |/*10@+10*/ // And a trailing comment for fun.
                    """.trimMargin(),
                ),
            ),
        )

        val fileContext = dirContext.mergedFileContext()
    }

    open val context: ModuleDataTestContext get() = fileContext

    private val topLevels = listOf("Fish", "apple", "banana", "carrot", "durian", "eat", "fish")

    @Test
    fun findAppleAfterA() {
        assertCompletions(spot = "/*0*/", expected = listOf("apple"))
    }

    @Test
    fun findAllTopsBeforeB() {
        assertCompletions(spot = "/*1*/", expected = topLevels)
    }

    @Test
    fun findAppleBeforeBWithBuiltins() {
        val builtins = builtinWordDefs.value.keys
        // Check some core builtins that aren't likely to change, just to make sure we have things here.
        assertTrue(builtins.containsAll(setOf("String", "for", "console")))
        assertCompletions(spot = "/*1*/", expected = topLevels + builtins.sorted(), includeBuiltins = true)
    }

    @Test
    fun findForwardTopBananaAfterB() {
        assertCompletions(spot = "/*2*/", expected = listOf("banana"))
    }

    @Test
    fun findNoForwardLocals() {
        assertCompletions(spot = "/*11*/", expected = listOf("grape1"))
    }

    @Test
    fun findNothingInDef() {
        assertCompletions(spot = "/*3*/", expected = listOf())
    }

    @Test
    fun findCarrotAsSecondParam() {
        assertCompletions(spot = "/*4@-1*/", expected = listOf("carrot"))
    }

    @Test
    fun findMultipleAfterBa() {
        assertCompletions(spot = "/*5@-1*/", expected = listOf("bamboo", "banana"))
    }

    @Test
    fun findBananaAfterLaterBa() {
        assertCompletions(spot = "/*6@-1*/", expected = listOf("banana"))
    }

    @Test
    fun findClass() {
        assertCompletions(spot = "/*7@+1*/", expected = listOf("Fish"))
    }

    @Test
    fun findNothingInComment() {
        assertCompletions(spot = "/*8@+1*/", expected = listOf())
    }

    @Ignore // TODO(mikesamuel): fix this.
    @Test
    fun findNothingInString() {
        assertCompletions(spot = "/*9@+1*/", expected = listOf())
        assertCompletions(spot = "/*10@+10*/", expected = listOf())
    }

    private fun assertCompletions(spot: String, expected: List<String>, includeBuiltins: Boolean = false) =
        context.assertCompletions(spot, expected, includeBuiltins = includeBuiltins)
}

internal class FindCompletionsDirTest : FindCompletionsTest() {
    override val context get() = dirContext
}
