package lang.temper.fs

import lang.temper.common.RResult
import lang.temper.common.WrappedByteArray
import lang.temper.common.subListToEnd
import lang.temper.log.FilePath

interface FileFilterRules {
    /**
     * If path is a file, true iff that file is ignored.
     *
     * If path is a directory, true if every file that could be in that directory is ignored,
     * but could be false if we need to peek at files in that directory to see if they need to
     * be excluded based, for example, on complex negative rules.
     */
    fun isIgnored(path: FilePath): Boolean

    data object Allow : FileFilterRules {
        override fun isIgnored(path: FilePath): Boolean = false
    }

    data class StripSegments(
        private val rules: FileFilterRules,
        private val pathToStrip: FilePath,
    ) : FileFilterRules {
        override fun isIgnored(path: FilePath): Boolean {
            if (!pathToStrip.isAncestorOf(path)) { return false }
            return rules.isIgnored(
                path.copy(segments = path.segments.subListToEnd(path.segments.size)),
            )
        }
    }

    data class Prepending(
        private val rules: FileFilterRules,
        private val pathToPrepend: FilePath,
    ) : FileFilterRules {
        override fun isIgnored(path: FilePath): Boolean =
            rules.isIgnored(pathToPrepend.resolve(path))
    }

    companion object {
        fun eitherIgnores(
            a: FileFilterRules,
            b: FileFilterRules,
        ): FileFilterRules = when {
            b is Allow -> a
            a is Allow -> b
            else -> ExcludeBoth(a, b)
        }
    }

    private data class ExcludeBoth(
        val a: FileFilterRules,
        val b: FileFilterRules,
    ) : FileFilterRules {
        override fun isIgnored(path: FilePath): Boolean =
            a.isIgnored(path) || b.isIgnored(path)
    }
}

/**
 * Parse file filter rules based on the format described at
 * git-scm.com/docs/gitignore#_pattern_format
 */
expect fun fileFilterRulesFromIgnoreFile(
    content: WrappedByteArray,
): RResult<FileFilterRules, Throwable>

/**
 * Parse file filter rules based on the format described at
 * git-scm.com/docs/gitignore#_pattern_format
 */
fun fileFilterRulesFromIgnoreFile(
    content: String,
) = fileFilterRulesFromIgnoreFile(
    WrappedByteArray.build {
        append(content.encodeToByteArray())
    },
)
