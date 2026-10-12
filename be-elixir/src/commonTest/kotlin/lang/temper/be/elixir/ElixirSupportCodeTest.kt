package lang.temper.be.elixir

import kotlin.test.Test
import kotlin.test.assertTrue

class ElixirSupportCodeTest {
    @Test
    fun connectedTableBuilds() {
        assertTrue("core.type Int32.toString()" in elixirConnected, elixirConnected.keys.toString())
        assertTrue("core.getConsole()" in elixirConnected)
    }
}
