package lang.temper.langserver

import lang.temper.tooling.DeclInfo
import lang.temper.tooling.DeclKind
import kotlin.test.Test
import kotlin.test.assertTrue

class DisplayHoverTest {
    @Test
    fun hoverClass() {
        // This more is a documentation of current behavior than something set in stone.
        val decl = DeclInfo(kind = DeclKind.Class, name = "Banana")
        assertTrue(textFor(decl).contains("class Banana"))
    }

    @Test
    fun hoverConstructor() {
        // This more is a documentation of current behavior than something set in stone.
        val decl = DeclInfo(kind = DeclKind.Constructor, name = "Banana", type = "fn (Banana, Int): Void")
        assertTrue(textFor(decl).contains("(constructor) Banana: fn (Banana, Int): Void"))
    }
}

private fun textFor(decl: DeclInfo) = decl.toHover().contents.right.value
