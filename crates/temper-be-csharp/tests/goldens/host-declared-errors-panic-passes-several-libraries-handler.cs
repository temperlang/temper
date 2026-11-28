using CountErrors;
using System;
using System.Globalization;
using TemperLang.Core;

namespace HostDeclaredErrorsPanicPassesSeveralLibrariesHandler;

public static class HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerConnected.HostLookup(key);
    }

    static HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostDeclaredErrorsPanicPassesSeveralLibrariesHandler");
        console.Log("before");
        int p;
        try
        {
            int n = CountErrorsGlobal.ParseCount("two");
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
