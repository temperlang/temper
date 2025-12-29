package lang.temper.cli

import lang.temper.common.Console
import lang.temper.library.LibraryConfiguration
import lang.temper.tooling.chimericToDash
import lang.temper.tooling.dashToTitle
import lang.temper.tooling.initConfigContent
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

fun doInit(outputDirectory: Path, cliConsole: Console) = try {
    val dashName = initProject(outputDirectory)
    cliConsole.info("""Initialized new Temper project "$dashName" in ${outputDirectory.toRealPath()}""")
    true
} catch (failure: ReportableException) {
    cliConsole.error(failure.message!!)
    false
}

// TODO(tjp, tooling): If useful, move this to some more general location?
class ReportableException(message: String) : RuntimeException(message)

/**
 * Initializes a project skeleton in [projectDir] if absent or empty, and throws an exception
 * if either a config file or a src dir is already present.
 */
fun initProject(projectDir: Path): String {
    ensureDirExists(projectDir)
    // Use the real path in case it already existed, and we want correct casing on case-insensitive systems.
    val dirName = projectDir.toRealPath().name
    val srcDir = projectDir.resolve("src")
    // Validate that structure doesn't already seem present.
    if (srcDir.exists()) {
        throw ReportableException("Directory src already exists")
    }
    if (projectDir.resolve(LibraryConfiguration.fileName.fullName).exists()) {
        throw ReportableException("Temper config already exists")
    }
    // Init library.
    ensureDirExists(srcDir)
    val dashNameOrig = dirName.chimericToDash()
    val dashName = if (dashNameOrig == "config") {
        // So we don't conflict with config.temper.md, add a suffix for this case.
        "$dashNameOrig-lib"
    } else {
        dashNameOrig
    }
    val title = dashName.dashToTitle()
    generateConfig(dir = srcDir, title = title, dashName = dashName)
    val modulePath = srcDir.resolve("$dashName.temper.md")
    generateModule(path = modulePath, title = title)
    return dashName
}

private fun ensureDirExists(outputDirectory: Path) {
    runCatching {
        outputDirectory.createDirectories()
    }.getOrElse {
        // The user probably is ok without further details. They probably have something messed up.
        throw ReportableException("Failed to create project directory")
    }
}

private fun generateConfig(dir: Path, title: String, dashName: String) {
    val content = initConfigContent(title, dashName)
    val configPath = dir.resolve(LibraryConfiguration.fileName.fullName)
    if (configPath.exists()) {
        // Just let them manage this situation manually.
        throw ReportableException("Temper config already exists")
    }
    configPath.writeText(content)
}

private fun generateModule(path: Path, title: String) {
    val content = """
        |# Implementation for $title
        |
        |Library discussion goes here.
        |
        |    // Library implementation code goes here.
        |
        |Additional documentation and code blocks may follow.
        |
    """.trimMargin()
    path.writeText(content, options = arrayOf(StandardOpenOption.CREATE_NEW))
}
