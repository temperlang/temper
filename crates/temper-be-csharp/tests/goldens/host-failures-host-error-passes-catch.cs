using System.Globalization;
using TemperLang.Core;

namespace HostFailuresHostErrorPassesCatch;

public static class HostFailuresHostErrorPassesCatchGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresHostErrorPassesCatchConnected.HostLookup(key);
    }

    static HostFailuresHostErrorPassesCatchGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresHostErrorPassesCatch");
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
