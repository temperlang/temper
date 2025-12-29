package lang.temper.fs

/**
 * Testing file operations is a PITA so this lets us create a temporary directory
 * muck around in it.
 *
 * Callback arguments are a usable output root and a system path that's usable for providing
 * to commands, where applicable.
 *
 * If the test passes, then it cleans up the directory.
 * If it doesn't, it prints a message to STDERR so the user can go and look at the files.
 */
expect fun <T> runWithTemporaryOutputRoot(
    testName: String,
    testAction: (OutputRoot, String) -> T,
): T
