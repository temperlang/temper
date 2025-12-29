package lang.temper.frontend.staging

import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.log.LogSink
import lang.temper.log.filePath
import lang.temper.name.ModuleName
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryNameTest {
    // Explicit name tested in BuildConductorTest. Other cases tested here.

    @Test
    fun markdownTitle() {
        val name = libraryNameWithDefault(module = null, textContent = "# Hi There!")
        assertEquals("hi-there", name.text)
    }

    @Test
    fun modulePath() {
        val module = Module(
            console = console,
            continueCondition = { true },
            loc = ModuleName(
                sourceFile = filePath("source", "path", "config.temper.md"),
                libraryRootSegmentCount = 2,
                isPreface = false,
            ),
            projectLogSink = LogSink.devNull,
        )
        val name = libraryNameWithDefault(module = module, textContent = "")
        assertEquals("source-path", name.text)
    }

    @Test
    fun unnamed() {
        val name = libraryNameWithDefault(module = null, textContent = "")
        assertEquals("unnamed", name.text)
    }
}
