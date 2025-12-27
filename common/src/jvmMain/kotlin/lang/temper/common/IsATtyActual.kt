package lang.temper.common

import org.fusesource.jansi.internal.CLibrary

// Changes to the environment checks here should also be reflected in the JS version.
actual fun isatty(fd: Int): Boolean {
    if (fd !in 1..2) { return false }

    // IntelliJ's terminal reports !isatty.  I think this is because it's just running gradle and
    // tailing a pipe that gets the test output rather than actually running an interactive shell.
    // But IntelliJ's terminal also interprets TTY codes nicely.
    // This allows an override.
    // See the project README for how to configure IntelliJ's Gradle Run Configuration Templates.
    if (System.getenv("IDEA_INITIAL_DIRECTORY") != null ||
        "intellij" in (System.getenv("__CFBundleIdentifier") ?: "")
    ) {
        return true
    }

    if (
        // See https://man7.org/linux/man-pages/man7/term.7.html for dumb terminals
        System.getenv("TERM") == "dumb" ||
        // Continuous integration systems set the CI environment variable.
        // https://github.community/t/have-the-ci-environment-variable-set-by-default/16288
        // explains which environment variables are set by which CI tool pipelines.
        System.getenv("CI") != null ||
        System.getenv("TF_BUILD") != null
    ) {
        return false
    }

    if (System.getProperty("org.gradle.console") == "rich") {
        // If we're run from within a gradle process with rich logging,
        // then act as if 1 and 2 are TTY.
        return true
    }

    return CLibrary.isatty(fd) != 0
}
