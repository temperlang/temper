using System;

namespace HostErrorTests
{
    static class HostErrorTestsConnected
    {
        internal static int HostFails(int n)
        {
            throw new InvalidOperationException("host bug " + n);
        }
    }
}
