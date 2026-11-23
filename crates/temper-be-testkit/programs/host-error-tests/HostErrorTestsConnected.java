package host_error_tests;

class HostErrorTestsConnected {
    static int hostFails(int n) {
        switch (n) {
            case 1: throw new IllegalStateException("host bug " + n);
            case 2: return (new int[0])[n];
            default: return ((String) null).length();
        }
    }

    // JUnit's own failure, as its assertions throw, found where the test
    // run has it.
    static void hostFailsTest() {
        Error failure;
        try {
            failure = (Error) Class.forName("org.opentest4j.AssertionFailedError")
                .getConstructor(String.class)
                .newInstance("host test failure");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw failure;
    }
}
