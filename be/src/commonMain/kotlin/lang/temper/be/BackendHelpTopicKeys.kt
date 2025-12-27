package lang.temper.be

typealias BackendHelpTopicKey = String

/**
 * Symbol names for common backend-specific help topics.
 *
 * Each key is human-readable and will appear if the backend is recognized
 * by the REPL when the basic REPL help function is used.
 *
 * So if two backends, bar and foo, each support the "about" key, you
 * might get the following REPL session.
 *
 * ```sh
 * $ temper repl
 * > help()
 * help
 *   The help command blah blah blah.  The topics are:
 *       help: this help topic
 *       ...
 *       bar/about: about the bar backend
 *       foo/about: about the foo backend
 * > █
 * ```
 *
 * Note that the key is prefixed with
 * ([BackendId][lang.temper.name.BackendId] + "/" + key)
 * so treating the key as a '/' separated hierarchical identifier is fine.
 */
object BackendHelpTopicKeys {
    /** high level information */
    const val ABOUT = "about"

    /**
     * information about what happens when the user runs
     * `temper repl -b someid` for the someid backend
     * or `temper repl -b someid:...` where the ... is extra
     * information that may customize the REPL experience.
     */
    const val REPL = "repl"
}
