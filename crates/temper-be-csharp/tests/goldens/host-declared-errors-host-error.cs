using System.Globalization;
using TemperLang.Core;

namespace HostDeclaredErrorsHostError;

public static class HostDeclaredErrorsHostErrorGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostDeclaredErrorsHostErrorConnected.HostLookup(key);
    }

    static HostDeclaredErrorsHostErrorGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostDeclaredErrorsHostError");
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
