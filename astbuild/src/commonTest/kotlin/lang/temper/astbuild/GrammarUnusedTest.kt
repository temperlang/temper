package lang.temper.astbuild

import lang.temper.common.assertStringsEqual
import kotlin.test.Test
import kotlin.test.fail

class GrammarUnusedTest {
    @Test
    fun noProductionsUnreachableFromTop() {
        val reachable = mutableSetOf<Combinator>()
        val all = grammar.productionNames

        fun walk(c: Combinator) {
            if (c !in reachable) {
                reachable.add(c)
                for (child in c.children) {
                    walk(child)
                }
                if (c is Ref) {
                    walk(
                        grammar.getProduction(c.name)
                            ?: fail("Undefined production ${c.name}"),
                    )
                }
            }
        }
        walk(Ref("Root"))
        walk(Ref("Json"))

        val reachableRefs = reachable.mapNotNull {
            if (it is Ref) it.name else null
        }.toSet()

        assertStringsEqual(
            all.toList().sorted().joinToString("\n"),
            reachableRefs.toList().sorted().joinToString("\n"),
        )
    }
}
