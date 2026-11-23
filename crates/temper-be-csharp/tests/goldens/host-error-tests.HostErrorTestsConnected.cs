using System;
using System.Linq;

namespace HostErrorTests
{
    static class HostErrorTestsConnected
    {
        internal static int HostFails(int n)
        {
            switch (n)
            {
                case 1: throw new InvalidOperationException("host bug " + n);
                case 2: return Array.Empty<int>()[n];
                default: return ((string)null!).Length;
            }
        }

        // MSTest's own failure, as `Assert.Fail` throws, found where the
        // test run loaded it.
        internal static void HostFailsTest()
        {
            var type = AppDomain.CurrentDomain.GetAssemblies()
                .Select(a => a.GetType("Microsoft.VisualStudio.TestTools.UnitTesting.AssertFailedException"))
                .First(t => t != null)!;
            throw (Exception)Activator.CreateInstance(type, "host test failure")!;
        }
    }
}
