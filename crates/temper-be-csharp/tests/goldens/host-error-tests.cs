namespace HostErrorTests;

public static class HostErrorTestsGlobal
{
    /// <exception cref="Oops">Where it fails with Oops.</exception>
    public static int HostFails(int n)
    {
        return HostErrorTestsConnected.HostFails(n);
    }

    public static void HostFailsTest()
    {
        HostErrorTestsConnected.HostFailsTest();
    }
}
