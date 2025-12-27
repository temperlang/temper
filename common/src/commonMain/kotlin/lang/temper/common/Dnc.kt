package lang.temper.common

/**
 * Allows writing debugging helper code like
 * `console.doNotCommit.log(...)` or `val DEBUG = true.doNotCommit`
 * which will work as if the `.doNotCommit` were not there, but
 * which will also trigger the Git pre-commit hooks to remind
 * you to remove it.
 *
 * See `scripts/init-workspace.sh` for the pre-commit hooks
 * normally installed.
 */
@Suppress("unused") // Meant to be used temporarily.
inline val <T> T.doNotCommit get() = this
