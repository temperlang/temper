package lang.temper.builtin

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class BuiltinFunsTest {
    @Test
    fun sigsForPlus() {
        assertStringsEqual(
            """
                |[(Int32, Int32) -> Int32], callMayFailPerSe=false
                |[(Int32) -> Int32], callMayFailPerSe=false
                |[(Int64, Int64) -> Int64], callMayFailPerSe=false
                |[(Int64) -> Int64], callMayFailPerSe=false
                |[(Float64, Float64) -> Float64], callMayFailPerSe=false
                |[(Float64) -> Float64], callMayFailPerSe=false
            """.trimMargin(),
            BuiltinFuns.plusFn.covered.joinToString("\n") {
                "${it.sigs}, callMayFailPerSe=${it.callMayFailPerSe}"
            },
        )
    }

    @Test
    fun sigsForDiv() {
        assertStringsEqual(
            """
                |[(Int32, Int32) -> Result<Int32, Bubble>], callMayFailPerSe=true
                |[(Int64, Int64) -> Result<Int64, Bubble>], callMayFailPerSe=true
                |[(Float64, Float64) -> Result<Float64, Bubble>], callMayFailPerSe=true
            """.trimMargin(),
            BuiltinFuns.divFn.covered.joinToString("\n") {
                "${it.sigs}, callMayFailPerSe=${it.callMayFailPerSe}"
            },
        )
    }
}
