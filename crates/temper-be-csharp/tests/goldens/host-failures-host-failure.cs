using System.Globalization;
using TemperLang.Core;

namespace HostFailuresHostFailure;

public static class HostFailuresHostFailureGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresHostFailureConnected.HostLookup(key);
    }

    static HostFailuresHostFailureGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresHostFailure");
        console.Log("before");
        int n;
        try
        {
            n = HostLookup("z");
        }
        catch (NotFound e)
        {
            console.Log("caught " + e.Key);
            n = -1;
        }
        string @string = "got " + n.ToString(CultureInfo.InvariantCulture) + " ";
        int t1;
        try
        {
            t1 = HostLookup("a");
        }
        catch (NotFound)
        {
            t1 = 0;
        }
        console.Log(@string + t1.ToString(CultureInfo.InvariantCulture));
        HostLookup("y");
        console.Log("after");
    }
}
