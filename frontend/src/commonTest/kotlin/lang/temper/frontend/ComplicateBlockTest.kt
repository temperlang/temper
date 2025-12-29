package lang.temper.frontend

import lang.temper.common.TestDocumentContext
import lang.temper.common.testCodeLocation
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.value.Document
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.toLispy
import lang.temper.value.vLabelSymbol
import kotlin.test.Test
import kotlin.test.assertEquals

class ComplicateBlockTest {
    @Test
    fun labeledBlock() {
        val doc = Document(TestDocumentContext())
        val block = doc.treeFarm.grow(Position(testCodeLocation, 0, 0)) {
            Block {
                V(vLabelSymbol)
                Ln(doc.nameMaker.unusedSourceName(ParsedName("label")))
                V(Value(123, TInt))
            }
        }
        structureBlock(block)

        assertEquals(
            """
                |(Block
                |  (stmt-block
                |    (labeled label__0
                |      (stmt-block
                |        (V 123))))
            """.trimMargin(),
            block.toLispy(multiline = true),
        )
    }
}
