using CountErrors;
using System;
using System.Globalization;
using TemperLang.Core;

namespace HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandler;

public static class HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerConnected.HostLookup(key);
    }

    static HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandler");
        console.Log("before");
        int h;
        try
        {
            h = CountErrorsGlobal.ParseCount("two") + HostLookup("bug");
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
