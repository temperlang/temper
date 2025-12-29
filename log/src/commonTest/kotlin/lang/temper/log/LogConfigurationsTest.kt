package lang.temper.log

import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.Style
import lang.temper.common.TextOutput
import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class LogConfigurationsTest {
    private class BufferingTextOutput : TextOutput() {
        val buffer = StringBuilder()

        override val isTtyLike: Boolean get() = false

        override fun emitLineChunk(text: CharSequence) {
            buffer.append(text)
        }

        override fun endLine() {
            buffer.append('\n')
        }

        override fun flush() {
            // Already on buffer
        }

        override fun startStyle(style: Style) {
            // No-op
        }

        override fun endStyle() {
            // No-op
        }
    }

    private object ConfigKeyA : ConfigurationKey {
        override fun toString() = "A"
    }

    private object ConfigKeyB : ConfigurationKey {
        override fun toString() = "B"
    }

    @Test
    fun configureAndCaptureOutputForDifferentLocations() {
        val bufferingTextOutputA = BufferingTextOutput()
        val consoleA = Console(bufferingTextOutputA)
        Debug.configure(ConfigKeyA, consoleA)

        val bufferingTextOutputB = BufferingTextOutput()
        val consoleB = Console(bufferingTextOutputB)
        Debug.configure(ConfigKeyB, consoleB)

        Debug(ConfigKeyA).log("Foo")
        Debug(ConfigKeyB).log("Bar")

        assertStringsEqual("Foo\n", bufferingTextOutputA.buffer.toString())
        assertStringsEqual("Bar\n", bufferingTextOutputB.buffer.toString())
    }

    @Test
    fun configureOnlyErrors() {
        val bufferingTextOutput = BufferingTextOutput()
        val consoleA = Console(bufferingTextOutput)
        consoleA.setLogLevel(Log.Error)
        Debug.configure(ConfigKeyA, consoleA)

        val c = Debug(ConfigKeyA)
        c.log("Info 1")
        c.warn("Warning 1")
        c.error("Error 1")

        assertStringsEqual(
            "Error 1\n",
            bufferingTextOutput.buffer.toString(),
        )

        c.setLogLevel(Log.Warn)
        c.log("Info 2")
        c.warn("Warning 2")
        c.error("Error 2")

        assertStringsEqual(
            "Error 1\nWarning 2\nError 2\n",
            bufferingTextOutput.buffer.toString(),
        )
    }

    @Test
    fun configureParentAndLogToChild() {
        val bufferingTextOutput = BufferingTextOutput()

        Debug.Frontend.configure(
            ConfigKeyA,
            Console(bufferingTextOutput),
        )

        Debug.Frontend.TypeStage(ConfigKeyA).log("Postcards from typeStage")

        assertStringsEqual(
            "Postcards from typeStage\n",
            bufferingTextOutput.buffer.toString(),
        )
    }

    @Test
    fun configureChildThenParent() {
        val childTextOutput = BufferingTextOutput()
        val parentTextOutput = BufferingTextOutput()

        Debug.Frontend.TypeStage.configure(ConfigKeyA, Console(childTextOutput))
        Debug.Frontend.configure(ConfigKeyA, Console(parentTextOutput))

        Debug.Frontend.TypeStage(ConfigKeyA).log("TS")
        Debug.Frontend(ConfigKeyA).log("FE")

        assertStringsEqual("FE\n", parentTextOutput.buffer.toString())
        assertStringsEqual("TS\n", childTextOutput.buffer.toString())
    }

    @Test
    fun configureParentThenChild() {
        val childTextOutput = BufferingTextOutput()
        val parentTextOutput = BufferingTextOutput()

        Debug.Frontend.configure(ConfigKeyA, Console(parentTextOutput))
        Debug.Frontend.TypeStage.configure(ConfigKeyA, Console(childTextOutput))

        Debug.Frontend.TypeStage(ConfigKeyA).log("TS")
        Debug.Frontend(ConfigKeyA).log("FE")

        assertStringsEqual("TS\n", childTextOutput.buffer.toString())
        assertStringsEqual("FE\n", parentTextOutput.buffer.toString())
    }

    @Test
    fun undoChildConfiguration() {
        val childTextOutput = BufferingTextOutput()
        val parentTextOutput = BufferingTextOutput()

        Debug.Frontend.configure(ConfigKeyA, Console(parentTextOutput))
        Debug.Frontend.TypeStage.configure(ConfigKeyA, Console(childTextOutput))

        Debug.Frontend(ConfigKeyA).log("Parent before un-configure")
        Debug.Frontend.TypeStage(ConfigKeyA).log("Child before un-configure")

        Debug.Frontend.TypeStage.configure(ConfigKeyA, null)

        Debug.Frontend(ConfigKeyA).log("Parent after un-configure")
        Debug.Frontend.TypeStage(ConfigKeyA).log("Child after un-configure")

        assertStringsEqual(
            "Child before un-configure\n",
            childTextOutput.buffer.toString(),
        )

        assertStringsEqual(
            """
                |Parent before un-configure
                |Parent after un-configure
                |Child after un-configure
                |
            """.trimMargin(),
            parentTextOutput.buffer.toString(),
        )
    }
}
