package host_error_tests;

public final class HostErrorTestsGlobal {
    private HostErrorTestsGlobal() {}

    public static int hostFails(int n) throws Oops {
        return HostErrorTestsConnected.hostFails(n);
    }

    public static void hostFailsTest() {
        HostErrorTestsConnected.hostFailsTest();
    }
}
