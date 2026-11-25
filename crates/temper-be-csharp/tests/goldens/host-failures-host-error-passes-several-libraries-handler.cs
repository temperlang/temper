using CountFailures;
using System;
using System.Globalization;
using TemperLang.Core;

namespace HostFailuresHostErrorPassesSeveralLibrariesHandler;

public static class HostFailuresHostErrorPassesSeveralLibrariesHandlerGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresHostErrorPassesSeveralLibrariesHandlerConnected.HostLookup(key);
    }

    static HostFailuresHostErrorPassesSeveralLibrariesHandlerGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresHostErrorPassesSeveralLibrariesHandler");
        console.Log("before");
        int h;
        try
        {
            h = CountFailuresGlobal.ParseCount("two") + HostLookup("bug");
        }
        catch (Exception caught1) when (caught1 is IFailure)
        {
            IFailure e = (IFailure)caught1;
            console.Log("caught " + e.Message);
            h = -1;
        }
        console.Log("after " + h.ToString(CultureInfo.InvariantCulture));
    }
}
