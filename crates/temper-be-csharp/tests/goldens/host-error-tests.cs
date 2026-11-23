namespace HostErrorTests;

public static class HostErrorTestsGlobal
{
    public static int HostFails(int n)
    {
        return HostErrorTestsConnected.HostFails(n);
    }
}
