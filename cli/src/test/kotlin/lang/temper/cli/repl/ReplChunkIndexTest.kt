package lang.temper.cli.repl

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("MagicNumber") // chunk indices
class ReplChunkIndexTest {
    @Test
    fun fromReversesModuleName() {
        val constructed = ReplChunkIndex(123)
        val derived = ReplChunkIndex.from(constructed.moduleName)
        assertEquals(constructed, derived)
    }

    @Test
    fun fromWorksWithFiveDigits() {
        val constructed = ReplChunkIndex(12_345)
        val derived = ReplChunkIndex.from(constructed.moduleName)
        assertEquals(constructed, derived)
    }
}
