using System;

namespace HostFailuresSeveralLibraries
{
    // A specification: `NotFound` is the library's own failure class, in
    // its namespace, as the C# backend names it.
    static class HostFailuresSeveralLibrariesConnected
    {
        internal static int HostLookup(string key)
        {
            if (key == "bug")
            {
                throw new InvalidOperationException("host bug");
            }
            if (key == "a")
            {
                return 1;
            }
            throw new NotFound(key);
        }
    }
}
