package host_declared_errors_host_error_passes_several_libraries_handler;

// A specification: `NotFound` is the library's own error class, in its
// package, as the Java backend names it.
class HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerConnected {
    static int hostLookup(String key) {
        if (key.equals("bug")) {
            throw new IllegalStateException("host bug");
        }
        if (key.equals("a")) {
            return 1;
        }
        throw new NotFound(key);
    }
}
