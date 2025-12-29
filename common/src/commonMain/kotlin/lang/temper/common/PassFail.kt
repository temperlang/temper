package lang.temper.common

enum class PassFail(val passed: Boolean, val failed: Boolean) {
    Fail(passed = false, failed = true),
    Pass(passed = true, failed = false),
}
