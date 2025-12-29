package lang.temper.tooling

import kotlin.test.Test
import kotlin.test.assertEquals

internal open class HoverTest {
    open val findDefContext: ModuleDataTestContext get() = FindDefTest.fileContext
    open val findMembersContext: ModuleDataTestContext get() = FindMembersTest.fileContext

    @Test
    fun hoverClass() {
        val expected = DeclInfo(kind = DeclKind.Class, name = "Banana", type = null)
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*13*/")
    }

    @Test
    fun hoverConstructor() {
        // TODO: Should identify constructor, not surrounding class
        // See github.com/temperlang/temper/issues/1577
        // val expected = DeclInfo(kind = DeclKind.Constructor, name = "Banana", type = "fn (Banana, Int): Void")
        val expected = DeclInfo(kind = DeclKind.Class, name = "Banana", type = null)
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*9*/")
    }

    @Test
    fun hoverInferredInt() {
        val expected = DeclInfo(kind = DeclKind.Let, name = "b", type = "Int32")
        assertDeclInfo(context = findDefContext, expected = expected, spot = "/*7*/")
    }

    @Test
    fun hoverInsideMethodDef() = hoverInsideMethod(spot = "/*11@+0*/")

    @Test
    fun hoverInsideMethodRef() = hoverInsideMethod(spot = "/*10*/")

    @Test
    fun hoverLet() {
        val expected = DeclInfo(kind = DeclKind.Let, name = "cart", type = "Cart")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*8@+0*/")
    }

    @Test
    fun hoverNullableTypedName() {
        // If we simplify these types, we'll need separate cases for simplifiable and not.
        val expected = DeclInfo(kind = DeclKind.Let, name = "crazy", type = "Apple?")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*14*/")
    }

    @Test
    fun hoverNullableTypePart() {
        val expected = DeclInfo(kind = DeclKind.Class, name = "Apple", type = null)
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*15*/")
    }

    @Test
    fun hoverParam() = hoverParamB(spot = "/*2@+0*/")

    @Test
    fun hoverParamAfter() {
        // In vscode, for both typescript and python, the character after a name still works, and that's the easy thing,
        // but assert the behavior anyway.
        hoverParamB(spot = "/*4*/")
    }

    @Test
    fun hoverPropertyDef() {
        val expected = DeclInfo(kind = DeclKind.Property, box = "Apple", name = "seedCount", type = "Int32")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*4@+0*/")
    }

    @Test
    fun hoverMethod() {
        // TODO(tjp, tooling): Get type descriptions for functions and methods.
        val expected = DeclInfo(kind = DeclKind.Method, box = "Apple", name = "isSour", type = "fn (Apple): Boolean")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*12@+0*/")
    }

    @Test
    fun hoverProperty() {
        val expected = DeclInfo(kind = DeclKind.Property, box = "Apple", name = "seedCount", type = "Int32")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*0@+1*/")
    }

    @Test
    fun hoverThis() {
        val expected = DeclInfo(kind = DeclKind.Parameter, name = "this", type = "Apple")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*17*/")
    }

    @Test
    fun hoverVar() {
        val expected = DeclInfo(kind = DeclKind.Var, name = "apple", type = "Banana")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = "/*7@+0*/")
    }

    private fun hoverInsideMethod(spot: String) {
        val expected = DeclInfo(kind = DeclKind.Let, name = "threshold", type = "Float64")
        assertDeclInfo(context = findMembersContext, expected = expected, spot = spot)
    }

    private fun hoverParamB(spot: String) {
        val expected = DeclInfo(kind = DeclKind.Parameter, name = "b", type = "Int32")
        assertDeclInfo(context = findDefContext, expected = expected, spot = spot)
    }

    private fun assertDeclInfo(context: ModuleDataTestContext, expected: DeclInfo, spot: String) {
        // Unlike completions and def, hover counts the char index we're over, not between.
        // TODO(tjp, tooling): Add a mode to require less than range end.
        val decl = context.moduleData.findDecl(context.findOffsetLocPos(spot))
        assertEquals(expected, decl)
    }
}

internal class HoverDirTest : HoverTest() {
    override val findDefContext get() = FindDefTest.dirContext
    override val findMembersContext get() = FindMembersTest.dirContext
}
