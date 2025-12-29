package lang.temper.common

actual fun isatty(fd: Int): Boolean {
    return fd in 0..2 && (
        // tty is defined in Node.js
        // https://nodejs.org/api/tty.html#tty_tty_isatty_fd
        // It's better than process.stdout.isTTY which is always true.
        eval(
            """
            !!(typeof process === 'undefined' && process.env &&
               process.env.IS_INTELLIJ_TERMINAL || (
                   typeof require === 'function' && require('tty')?.isatty?.($fd) &&
                   process.env.TERM !== 'dumb' &&
                   !process.env.CI && !process.env.TF_BUILD))
            """.trimIndent(),
        )
        // See the JVM version's comments for the reasons for the env variable checks.
        // Changes to the environment checks should be reflected in the JVM version.
        ) as Boolean
}
