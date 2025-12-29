package lang.temper.common

import java.nio.file.Files

actual fun showGraphvizFileBestEffort(dotContent: String, title: String?) {
    val titleSuffix = title // "Foo: bar" => "Foo-bar"
        ?.replace(notAlphanumeric, "-")?.let { "-$it" }
        ?: ""
    val tempDotFile = Files.createTempFile("debug", "$titleSuffix.dot")
    val tempOutFile = tempDotFile.parent.resolve(
        "${tempDotFile.fileName.toString().removeSuffix(".dot")}.png",
    )
    console.info("Creating graphviz at $tempDotFile writing to $tempOutFile")
    Files.writeString(tempDotFile, dotContent, Charsets.UTF_8)
    val pb = ProcessBuilder(
        "dot",
        "-Tpng",
        "${tempDotFile.toAbsolutePath()}",
        "-o${tempOutFile.toAbsolutePath()}",
    )
    val exitValue = pb.start().waitFor()
    if (exitValue != 0) {
        console.error("Could not open image generated from $tempDotFile: exitValue $exitValue")
        return
    }

    console.info("Opening image $tempOutFile")
    val openExitValue = ProcessBuilder(
        "open",
        "${tempOutFile.toAbsolutePath()}",
    ).start().waitFor()
    if (openExitValue != 0) {
        console.error(
            "Could not open image generated from $tempDotFile: exitValue $openExitValue",
        )
    }
}

private val notAlphanumeric = Regex("[^a-zA-Z0-9]+")
