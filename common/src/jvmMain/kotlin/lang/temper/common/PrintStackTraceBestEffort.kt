package lang.temper.common

actual fun Throwable.printStackTraceBestEffort() {
    this.printStackTrace()
}
