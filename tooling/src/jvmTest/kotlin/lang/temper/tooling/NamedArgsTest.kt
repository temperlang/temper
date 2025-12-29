package lang.temper.tooling

import lang.temper.frontend.ModuleSource
import kotlin.test.Test

internal open class NamedArgsTest {
    companion object {
        val dirContext = DirModuleDataTestContext(
            sources = listOf(
                ModuleSource(
                    fetchedContent = """
                        |class /*22@+0*/Person(
                        |  public /*0@+0*/name: String,
                        |  public /*12@+0*/age: Int = 0,
                        |) {}
                    """.trimMargin(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |class /*23@+0*/More {
                        |  public /*1@+0*/what: Int;
                        |  public constructor(/*2@+0*/what: Int) {
                        |    this.what/*11*/ = twice(what/*3*/);
                        |  }
                        |}
                        |let twice(/*4@+0*/what: Int, /*13@+0*/factor: Int = 2): Int { what * factor }
                    """.trimMargin(),
                ),
                ModuleSource(
                    fetchedContent = """
                        |let a1 = { name/*5*/: "Alice", age/*14*/: 30 };
                        |let b2 = { class: Person/*20*/, name/*6*/: "Bob" };
                        |let c3 = { what/*7*/: 1 };
                        |let d4 = { class: More/*21*/, what/*8*/: 2 };
                    """.trimMargin(),
                ),
            ),
        )

        val fileContext = dirContext.mergedFileContext()
    }

    open val context: ModuleDataTestContext get() = fileContext

    @Test
    fun findClassFromPropertyBag() {
        assertFound("/*20*/" to "/*22@+0*/")
        assertFound("/*21*/" to "/*23@+0*/")
    }

    @Test
    fun findConstructorArgNotProperty() {
        // assertFound("/*7*/" to "/*1@+0*/") // property wrong
        assertFound("/*7*/" to "/*2@+0*/") // constructor arg right
    }

    @Test
    fun findConstructorArgNotPropertyForConstructorCall() {
        // assertFound("/*8*/" to "/*1@+0*/") // property wrong
        assertFound("/*8*/" to "/*2@+0*/") // constructor arg right
    }

    @Test
    fun findPropertyExplicit() {
        // Tested previously elsewhere, but fun here too.
        assertFound("/*11*/" to "/*1@+0*/")
    }

    @Test
    fun findPropertyRef() {
        assertFound("/*5*/" to "/*0@+0*/")
    }

    @Test
    fun findPropertyRefForConstructorCall() {
        assertFound("/*6*/" to "/*0@+0*/")
    }

    @Test
    fun findPropertyWithDefault() {
        assertFound("/*14*/" to "/*12@+0*/")
    }

    @Test
    fun findValueRefDoppelganger() {
        // I don't really expect trouble here, but it's fun to check.
        assertFound("/*3*/" to "/*2@+0*/")
    }

    private fun assertFound(refDef: Pair<String, String?>) = context.assertFound(refDef)
}

internal class NamedArgsDirTest : NamedArgsTest() {
    override val context get() = dirContext
}
