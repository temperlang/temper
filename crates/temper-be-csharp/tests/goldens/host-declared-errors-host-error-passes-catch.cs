using System.Globalization;
using TemperLang.Core;

namespace HostDeclaredErrorsHostErrorPassesCatch;

public static class HostDeclaredErrorsHostErrorPassesCatchGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostDeclaredErrorsHostErrorPassesCatchConnected.HostLookup(key);
    }

    static HostDeclaredErrorsHostErrorPassesCatchGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostDeclaredErrorsHostErrorPassesCatch");
        console.Log("before");
        int h;
        try
        {
            h = HostLookup("bug");
        }
        catch (NotFound)
        {
            h = -1;
        }
        console.Log("after " + h.ToString(CultureInfo.InvariantCulture));
    }
}
