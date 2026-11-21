package host_error_causes;

class HostErrorCausesConnected {
    static int hostFails(int n) {
        throw new IllegalStateException("host bug " + n);
    }
}
