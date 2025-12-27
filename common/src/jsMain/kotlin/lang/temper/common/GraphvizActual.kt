package lang.temper.common

actual fun showGraphvizFileBestEffort(dotContent: String, title: String?) {
    val mkdtempSync = js("require('fs').mkdtempSync")
    val dirName = mkdtempSync("dot")

    val writeFileSync = js("require('fs').writeFileSync")
    val dotFileName = "$dirName/dot.dot"
    writeFileSync(dotFileName, dotContent)

    val execSync = js("require('child_process').execSync")
    execSync("xdot '$dotFileName'")
}
