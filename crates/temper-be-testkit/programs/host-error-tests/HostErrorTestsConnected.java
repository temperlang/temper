package host_error_tests;

class HostErrorTestsConnected {
    static int hostFails(int n) {
        throw new IllegalStateException("host bug " + n);
    }
}
