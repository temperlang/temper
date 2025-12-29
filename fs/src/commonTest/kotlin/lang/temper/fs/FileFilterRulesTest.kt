package lang.temper.fs

import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals

class FileFilterRulesTest {
    private fun assertIgnored(
        ignored: Boolean,
        rules: FileFilterRules,
        path: FilePath,
    ) {
        assertEquals(
            expected = ignored,
            actual = rules.isIgnored(path),
            message = "$path should${if (ignored) "" else "not "} be ignored",
        )
    }

    @Test
    fun simpleRules() {
        val rules = fileFilterRulesFromIgnoreFileStrict(
            """
                |*.log
                |\#*
            """.trimMargin(),
        )
        assertIgnored(true, rules, filePath("logs", "0.log"))
        assertIgnored(true, rules, filePath("src", "#foo.temper"))
        assertIgnored(false, rules, filePath("src", "foo.temper"))
    }

    @Test
    fun wholeDirectory() {
        val rules = fileFilterRulesFromIgnoreFileStrict(
            """
                |/temper.out
                |/other-dir
            """.trimMargin(),
        )
        assertIgnored(true, rules, dirPath("temper.out"))
        assertIgnored(true, rules, dirPath("other-dir"))
        assertIgnored(true, rules, filePath("temper.out", "js", "index.js"))
        assertIgnored(false, rules, filePath("other", "file.js"))
    }

    @Test
    fun wholeDirectoryWithNegativeRuleInside() {
        val rules = fileFilterRulesFromIgnoreFileStrict(
            """
                |/temper.out
                |/other-dir
                |!/temper.out/specific/file.ext
            """.trimMargin(),
        )
        assertIgnored(false, rules, dirPath("temper.out"))
        assertIgnored(false, rules, dirPath("temper.out", "specific"))
        assertIgnored(false, rules, filePath("temper.out", "specific", "file.ext"))
        // This directory clearly does not contain a negative rule
        assertIgnored(true, rules, dirPath("temper.out", "other"))

        assertIgnored(true, rules, dirPath("other-dir"))
        assertIgnored(true, rules, filePath("temper.out", "js", "index.js"))
        assertIgnored(false, rules, filePath("other", "file.js"))
    }

    @Test
    fun wholeDirectoryWithNegativeWildcardRule() {
        val rules = fileFilterRulesFromIgnoreFileStrict(
            """
                |/temper.out
                |!/temper.out/sub1/**/*.keep
            """.trimMargin(),
        )
        assertIgnored(false, rules, dirPath("temper.out")) // Need to peek in here
        assertIgnored(true, rules, dirPath("temper.out", "sub2")) // But not here
        assertIgnored(false, rules, dirPath("temper.out", "sub1")) // But yes in here
        assertIgnored(false, rules, dirPath("temper.out", "sub1", "more")) // Also here

        assertIgnored(false, rules, filePath("temper.out", "sub1", "more", "file.keep"))
        assertIgnored(true, rules, filePath("temper.out", "sub2", "more", "file.keep"))
    }
}

private fun fileFilterRulesFromIgnoreFileStrict(
    text: String,
): FileFilterRules {
    val bytes = WrappedByteArray.build {
        append(text.encodeToByteArray())
    }
    return when (val r = fileFilterRulesFromIgnoreFile(bytes)) {
        is RFailure<Throwable> -> throw r.failure
        is RSuccess -> r.result
    }
}
