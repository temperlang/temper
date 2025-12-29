package lang.temper.name.identifiers

import java.nio.file.Files
import java.nio.file.Path

actual fun loadTextResource(first: String, vararg rest: String): String? = Files.readString(Path.of(first, *rest))
