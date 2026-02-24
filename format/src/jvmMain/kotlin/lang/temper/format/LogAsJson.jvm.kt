package lang.temper.format
import java.util.concurrent.atomic.AtomicInteger

private val globalLogAsJsonBit = AtomicInteger(0)

actual val logAsJson: Boolean get() = globalLogAsJsonBit.get() > 0

actual fun <T> withLogAsJson(logAsJson: Boolean, body: () -> T): T {
    if (logAsJson) {
        globalLogAsJsonBit.incrementAndGet()
    }
    try {
        return body()
    } finally {
        if (logAsJson) {
            globalLogAsJsonBit.decrementAndGet()
        }
    }
}
