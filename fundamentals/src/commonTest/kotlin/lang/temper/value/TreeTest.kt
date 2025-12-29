package lang.temper.value

import lang.temper.common.TestDocumentContext
import lang.temper.log.Position
import lang.temper.name.ParsedName
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeTest {
    @Test
    fun nodesHashByIdentity() {
        // Reference resolution code needs to hash NameLeaf nodes in different locations to
        // different ResolvedNames.  This would break if hashing were structural.
        val context = TestDocumentContext()
        val doc = Document(context)
        val pos = Position(context.loc, 0, 0)
        val content = ParsedName("foo")
        val nameLeafA = RightNameLeaf(doc, pos, content)
        val nameLeafB = RightNameLeaf(doc, pos, content)
        val nameLeafC = RightNameLeaf(doc, pos, content)
        val map = mutableMapOf(
            nameLeafA to 1,
            nameLeafB to 2,
            nameLeafC to 3,
        )
        assertEquals(1, map[nameLeafA])
        assertEquals(2, map[nameLeafB])
        assertEquals(3, map[nameLeafC])
        assertEquals(null, map[RightNameLeaf(doc, pos, content)])
    }
}
