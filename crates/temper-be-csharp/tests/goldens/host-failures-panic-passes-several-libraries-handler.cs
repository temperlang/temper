using CountFailures;
using System;
using System.Globalization;
using TemperLang.Core;

namespace HostFailuresPanicPassesSeveralLibrariesHandler;

public static class HostFailuresPanicPassesSeveralLibrariesHandlerGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresPanicPassesSeveralLibrariesHandlerConnected.HostLookup(key);
    }

    static HostFailuresPanicPassesSeveralLibrariesHandlerGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresPanicPassesSeveralLibrariesHandler");
        console.Log("before");
        int p;
        try
        {
            int n = CountFailuresGlobal.ParseCount("two");
            int m = HostLookup("a");
            if (n + m > 2)
            {
                throw Core.Panic("panic");
            }
            p = Core.Div(10, n - m);
        }
        catch (Exception failure1) when (failure1 is IFailure)
        {
            p = -1;
        }
        console.Log("after " + p.ToString(CultureInfo.InvariantCulture));
    }
}
