package lang.temper.common

/**
 * Determine the JVM major version. This should only be used to work
 * around breaking changes in the Java API.
 *
 * Example: [lang.temper.regex.RegexMatchTest.matchRegexKt]
 */
expect fun jvmMajorVersion(): Int?
