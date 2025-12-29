package lang.temper.common

actual fun showGraphvizFileBestEffort(
    @Suppress("UnusedPrivateMember") dotContent: String
) {
    // We could `execlp("xdot", ...)` but in the meantime use the JVM version.
}
