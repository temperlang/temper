package lang.temper.value

import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.common.toStringViaBuilder
import lang.temper.log.Position
import lang.temper.name.Symbol
import kotlin.test.Test

class ActualsTest {
    @Test
    fun cherrypick() {
        val keys = listOf(
            null,
            null,
            Symbol("b"),
            Symbol("c"),
        )
        val values = listOf(
            Value(0, TInt),
            Value(1, TInt),
            Value(2, TInt),
            Value(3, TInt),
        )
        val positions = listOf(
            Position(testCodeLocation, 0, 0),
            Position(testCodeLocation, 1, 1),
            Position(testCodeLocation, 2, 2),
            Position(testCodeLocation, 3, 3),
        )

        val testActuals = object : ActualValues {
            override fun result(index: Int, computeInOrder: Boolean): Value<*> = values[index]
            override val size: Int = values.size
            override fun key(index: Int) = keys[index]
            override fun pos(index: Int) = positions[index]
            override fun peekType(index: Int) = values[index].typeTag
            override fun clearResult(index: Int) {
                require(index in indices)
            }
        }

        assertStringsEqual(
            """
            ╔═╦═══╦════════╦══════════════════╗
            ║i║key║result  ║pos               ║
            ╠═╬═══╬════════╬══════════════════╣
            ║0║   ║0: Int32║test/test.temper+0║
            ╠═╬═══╬════════╬══════════════════╣
            ║1║   ║1: Int32║test/test.temper+1║
            ╠═╬═══╬════════╬══════════════════╣
            ║2║b  ║2: Int32║test/test.temper+2║
            ╠═╬═══╬════════╬══════════════════╣
            ║3║c  ║3: Int32║test/test.temper+3║
            ╚═╩═══╩════════╩══════════════════╝
            """.trimIndent(),
            str(tabularForm(testActuals)),
        )

        // Given (0, 1, b = 2, c = 3), remap it to
        // (a = 0, b = 2, c = 3, 1)
        val viewAbc = testActuals.cherryPicker
            .addRekeyed(0, Symbol("a"))
            .add(2..3)
            .add(1)
            .build()

        assertStringsEqual(
            """
            ╔═╦═══╦════════╦══════════════════╗
            ║i║key║result  ║pos               ║
            ╠═╬═══╬════════╬══════════════════╣
            ║0║a  ║0: Int32║test/test.temper+0║
            ╠═╬═══╬════════╬══════════════════╣
            ║1║b  ║2: Int32║test/test.temper+2║
            ╠═╬═══╬════════╬══════════════════╣
            ║2║c  ║3: Int32║test/test.temper+3║
            ╠═╬═══╬════════╬══════════════════╣
            ║3║   ║1: Int32║test/test.temper+1║
            ╚═╩═══╩════════╩══════════════════╝
            """.trimIndent(),
            str(tabularForm(viewAbc)),
        )

        val aAndB = viewAbc.cherryPicker
            .add(0)
            .add(1)
            .build()

        assertStringsEqual(
            """
            ╔═╦═══╦════════╦══════════════════╗
            ║i║key║result  ║pos               ║
            ╠═╬═══╬════════╬══════════════════╣
            ║0║a  ║0: Int32║test/test.temper+0║
            ╠═╬═══╬════════╬══════════════════╣
            ║1║b  ║2: Int32║test/test.temper+2║
            ╚═╩═══╩════════╩══════════════════╝
            """.trimIndent(),
            str(tabularForm(aAndB)),
        )
    }

    private fun tabularForm(actuals: ComputedActuals): MultilineOutput = TextTable(
        listOf(
            MultilineOutput.of("i"),
            MultilineOutput.of("key"),
            MultilineOutput.of("result"),
            MultilineOutput.of("pos"),
        ),
        actuals.indices.map { index ->
            listOf(
                MultilineOutput.of("$index"),
                MultilineOutput.of(actuals.key(index)?.text ?: ""),
                MultilineOutput.of(actuals.result(index).toString()),
                MultilineOutput.of(actuals.pos(index).toString()),
            )
        },
    )

    private fun str(mlo: MultilineOutput) = toStringViaBuilder {
        val ls = mutableListOf<String>()
        mlo.addOutputLines(ls)
        var first = true
        for (line in ls) {
            if (first) {
                first = false
            } else {
                it.append('\n')
            }
            it.append(line)
        }
    }
}
