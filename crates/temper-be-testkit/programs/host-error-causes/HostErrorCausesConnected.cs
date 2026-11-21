using System;

namespace HostErrorCauses
{
    static class HostErrorCausesConnected
    {
        internal static int HostFails(int n)
        {
            throw new InvalidOperationException("host bug " + n);
        }
    }
}
