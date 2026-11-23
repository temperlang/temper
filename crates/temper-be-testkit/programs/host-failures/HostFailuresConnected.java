package host_failures;

// A specification: `NotFound` is the library's own failure class, in its
// package, as the Java backend will name it.
class HostFailuresConnected {
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
