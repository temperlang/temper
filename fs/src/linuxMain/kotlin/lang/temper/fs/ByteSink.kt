package lang.temper.fs

@Suppress(
    "UnnecessaryAbstractClass", // Necessary for java.io.OutputStream compat.
    "EmptyDefaultConstructor" // Implicit constructors are not actual.
)
actual abstract class ByteSink actual constructor() {
    actual open fun write(bytes: ByteArray, off: Int, len: Int) {
        error("This need overriding. It's not abstract for compatibility with OutputStream")
    }
}
