package lang.temper.common

import kotlin.test.Test

class AssertsActualTest {
    @Test
    fun exerciseDiff() {
        // This used to cause an exception on Windows. It should now work on all.
        // Give enough lines for multiple in common, just to see effect.
        tryRunDiff("a\nb\nc\nd\n", "a\nb\nc2\nd\n")
    }
}
