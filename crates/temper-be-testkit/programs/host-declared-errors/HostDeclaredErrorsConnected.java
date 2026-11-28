package host_declared_errors;

// A specification: `NotFound` is the library's own error class, in its
// package, as the Java backend names it.
class HostDeclaredErrorsConnected {
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
