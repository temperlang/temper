package lang.temper.cli

import kotlinx.cli.ArgType
import kotlinx.cli.CLIEntity
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Option
import lang.temper.common.console
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Path

internal class PathArgType(
    private val fs: FileSystem,
    private val wantAbsolute: kotlin.Boolean = false,
) : ArgType<Path>(hasParameter = true) {
    override val description = "Path"

    override fun convert(value: kotlin.String, name: kotlin.String): Path {
        var path = fs.getPath(value)
        if (wantAbsolute) {
            path = path.toAbsolutePath()
        }
        return path
    }
}

/**
 * Allow us to map an option's value on demand; presumably once the argument parser has executed.
 * This needs to be the last method on the chain since it doesn't try to be a real [Option] subclass.
 */
class LazyOpt<T, U>(private val source: CLIEntity<T>, block: (T) -> U) {
    val value by lazy { block(source.value) }
}

/** Convenience method to construct a [LazyOpt] */
internal fun <T, U> CLIEntity<T>.lazilyMapValue(block: (T) -> U): LazyOpt<T, U> = LazyOpt(this, block)

@ExperimentalCli
internal inline fun <T> Main.lazyIo(problem: String, crossinline func: () -> (T)): Lazy<T> = lazy {
    try {
        func()
    } catch (e: IOException) {
        console.error("$problem: ${e.message}")
        exitProcess(-1)
    }
}

@ExperimentalCli
fun Main.cleanUpAndExit(block: () -> Unit): Nothing {
    try {
        block()
    } catch (
        // We're about to exit so nothing else will catch and log.
        @Suppress("TooGenericExceptionCaught")
        e: Exception,
    ) {
        console.error(e)
    } finally {
        exitProcess(0)
    }
}
