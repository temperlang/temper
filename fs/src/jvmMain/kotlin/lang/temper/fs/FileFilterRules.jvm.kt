package lang.temper.fs

import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.subListToEnd
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.bannedPathSegmentNames
import lang.temper.log.isProblematicInFilePathSegment
import org.eclipse.jgit.ignore.FastIgnoreRule
import org.eclipse.jgit.ignore.IgnoreNode
import java.io.ByteArrayInputStream
import java.io.IOException

actual fun fileFilterRulesFromIgnoreFile(
    content: WrappedByteArray,
): RResult<FileFilterRules, Throwable> {
    val ignoreFile = IgnoreNode()
    try {
        ByteArrayInputStream(content.copyOf()).use { bytes ->
            ignoreFile.parse(bytes)
        }
    } catch (ex: IOException) {
        return RFailure(ex)
    }
    return RSuccess(EclipseGitIgnoreRules(ignoreFile))
}

private class EclipseGitIgnoreRules(
    private val ignoreNode: IgnoreNode,
) : FileFilterRules {
    // If there are negative rules, then we need to be careful
    // before concluding everything under a directory that matches
    // is ignorable.
    /**
     * If all negative rules fall into a set of enumerable prefixes
     * then we can conclude that directories that match ignore rules
     * are completely ignorable after checking here.
     *
     * Null if not all negative rules have a known prefix.
     * If all do, relates prefixes to false if there is no wildcard rule under them,
     * true if the directory matches a path prefix but does not have wildcards under it.
     */
    private val dirsThatContainNegativeRuleMatches: Map<FilePath, Boolean>?

    init {
        var allNegationsRooted = true
        val negativeRulePrefixes = mutableMapOf<FilePath, Boolean>()
        for (rule: FastIgnoreRule in ignoreNode.rules) {
            if (!rule.negation) {
                continue
            }
            val pattern = "$rule"
            val patternParts = pattern.split(FastIgnoreRule.PATH_SEPARATOR)
            if (patternParts.size < 2 || patternParts.first() != "!") {
                // We have a rule like `!something` instead of `!/something`
                // so we need to fall back to full directory scanning :(
                allNegationsRooted = false
                break
            }
            var followedByWildcard = false
            val prefixSegments = buildList {
                for (part in patternParts.subListToEnd(1)) {
                    val hasWildcardOrProblematicChar = part in bannedPathSegmentNames ||
                        part.any {
                            when (it) {
                                // Meta-characters for gitignore GLOB syntax
                                '*', '?', '{', '}' -> true
                                else -> isProblematicInFilePathSegment(it)
                            }
                        }
                    if (hasWildcardOrProblematicChar) {
                        followedByWildcard = true
                        break
                    }
                    add(FilePathSegment(part))
                }
            }
            if (prefixSegments.isEmpty()) {
                // Rules like the below match here which doesn't allow for
                // reliable directory exclusion.
                // !/**/foo
                // !/./**/foo
                allNegationsRooted = false
                break
            }
            for (n in 1..prefixSegments.size) {
                val wildcardy = n == prefixSegments.size && followedByWildcard
                val key = FilePath(prefixSegments.subList(0, n), isDir = true)
                if (key !in negativeRulePrefixes || wildcardy) {
                    negativeRulePrefixes[key] = wildcardy
                }
            }
        }
        this.dirsThatContainNegativeRuleMatches = if (allNegationsRooted) {
            negativeRulePrefixes.toMap()
        } else {
            null
        }
    }

    @Synchronized // IgnoreNode is stateful and not thread-safe
    override fun isIgnored(path: FilePath): Boolean {
        val isDir = path.isDir
        if (isDir) {
            // If it's a directory, can we ignore everything in it?
            val dirMap = this.dirsThatContainNegativeRuleMatches
                ?: return false // Worst case assumption.
            if (path in dirMap) {
                return false
            }
            var ancestor = path
            while (true) {
                ancestor = ancestor.dirName()
                if (ancestor.segments.isEmpty()) { break }
                when (dirMap[ancestor]) {
                    false -> break
                    true -> return false
                    null -> {}
                }
            }
        }

        return isIgnored(path.segments.map { it.fullName }, isDir = isDir, negated = false)
    }

    private fun isIgnored(
        parts: List<String>,
        isDir: Boolean,
        negated: Boolean,
    ): Boolean {
        val pathString = parts.joinToString(RULE_PATH_SEPARATOR)
        return negated xor
            when (ignoreNode.isIgnored(pathString, isDir)) {
                IgnoreNode.MatchResult.IGNORED -> true
                IgnoreNode.MatchResult.NOT_IGNORED -> false
                IgnoreNode.MatchResult.CHECK_PARENT ->
                    parts.isNotEmpty() &&
                        isIgnored(dirname(parts), isDir = true, negated = false)
                IgnoreNode.MatchResult.CHECK_PARENT_NEGATE_FIRST_MATCH ->
                    parts.isEmpty() ||
                        isIgnored(dirname(parts), isDir = true, negated = true)
                null -> error(pathString)
            }
    }
}

private const val RULE_PATH_SEPARATOR = "${FastIgnoreRule.PATH_SEPARATOR}"

private fun dirname(pathParts: List<String>) = pathParts.subList(0, pathParts.lastIndex)
