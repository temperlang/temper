package lang.temper.tooling

import lang.temper.frontend.ModuleSource
import kotlin.test.Ignore
import kotlin.test.Test

internal open class FindMembersTest {
    companion object {
        val dirContext = DirModuleDataTestContext(
            sources = listOf(
                ModuleSource(
                    fetchedContent = """
                        |interface Fruit {}
                        |class /*19@+0*/Apple extends Fruit {
                        |  public color: String;
                        |  public /*4@+0*/seedCount: Int;
                        |  public /*20@+0*/sourness: Double;
                        |  public /*12@+0*/isSour(): Boolean {
                        |    let /*11@+0*/threshold = 0.5;
                        |    this/*17*/./*18@+0*/sourness >= threshold/*10*/;
                        |  }
                        |}
                        |class Banana/*13*/ extends Fruit {
                        |  public /*6@+0*/spotCount: Int;
                        |}
                    """.trimMargin(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |class Cart {
                        |  public apple: Apple;
                        |  public banana: Banana;
                        |}
                    """.trimMargin(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |// Purposely use poor name, so name alone is useless for type.
                        |var apple = new Banana/*9*/(7);
                        |/*7@+0*/apple.spotCount/*5*/;
                        |let cart = new Cart(new Apple("green", 8, 0.9), apple);
                        |/*8@+0*/cart.apple./*0*//*0@+1*/seedCount;
                        |cart. /*2*/ apple;
                        |cart./*1*/ /*3*/;
                        |let crazy/*14*/: Apple/*15*/? = apple;
                        |let boring/*16*/: Apple = cart.apple;
                        |builtins./*21*/;
                    """.trimMargin(),
                ),
            ),
        )

        val fileContext = dirContext.mergedFileContext()
    }

    open val context: ModuleDataTestContext get() = fileContext

    @Test
    fun findInsideMethodDef() {
        assertFound("/*10*/" to "/*11@+0*/")
    }

    @Test
    fun findMemberDef() {
        assertFound("/*0@+1*/" to "/*4@+0*/")
    }

    @Test
    fun findMemberDefSingle() {
        // This test matters because a class with one member gets a different tree than a class with multiple.
        // For example, the others get a BlockTree surrounding their members, but this one doesn't.
        assertFound("/*5*/" to "/*6@+0*/")
    }

    @Test
    fun findMemberDefItself() {
        assertFound("/*6@+0*/" to "/*6@+0*/")
    }

    @Test
    fun findNested() {
        assertCompletions(spot = "/*0*/", expected = listOf("color", "isSour", "seedCount", "sourness"))
    }

    @Test
    fun findNestedPrefixed() {
        assertCompletions(spot = "/*0@+1*/", expected = listOf("seedCount", "sourness"))
    }

    @Test
    fun findSpaced() {
        assertCompletions(spot = "/*2*/", expected = listOf("apple", "banana"))
    }

    @Test
    fun findThis() {
        assertFound("/*17*/" to "/*19@+0*/")
    }

    @Test
    fun findThisMember() {
        assertFound("/*18@+0*/" to "/*20@+0*/")
    }

    @Test
    fun findThisMembers() {
        assertCompletions(spot = "/*18@+0*/", expected = listOf("color", "isSour", "seedCount", "sourness"))
    }

    @Test
    fun findTrailing() {
        assertCompletions(spot = "/*1*/", expected = listOf("apple", "banana"))
    }

    @Ignore("This still should be in the member context, but it doesn't currently work.")
    @Test
    fun findTrailingSpaced() {
        assertCompletions(spot = "/*3*/", expected = listOf("apple", "banana"))
    }

    @Ignore // See issue #1334
    @Test
    fun findBuiltins() {
        // This currently only works for just `builtins.` with some constraint on the right (like `;`).
        // It's a start. Doing more well might require serious plumbing in the frontend code or else CST handling.
        // TODO(tjp, tooling): See issues #641 and #642 on above matters.
        assertCompletions(
            spot = "/*21*/",
            expected = builtinWordDefs.value.keys.sorted(),
            includeBuiltins = true,
        )
    }

    private fun assertFound(refDef: Pair<String, String?>) = context.assertFound(refDef)

    private fun assertCompletions(spot: String, expected: List<String>, includeBuiltins: Boolean = false) =
        context.assertCompletions(spot, expected, includeBuiltins = includeBuiltins)
}

internal class FindMembersDirTest : FindMembersTest() {
    override val context get() = dirContext
}
