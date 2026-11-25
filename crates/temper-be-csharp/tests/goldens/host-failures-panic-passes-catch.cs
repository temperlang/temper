using System.Globalization;
using TemperLang.Core;

namespace HostFailuresPanicPassesCatch;

public static class HostFailuresPanicPassesCatchGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresPanicPassesCatchConnected.HostLookup(key);
    }

    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    internal static int Risky(int n)
    {
        if (n > 1)
        {
            throw Core.Panic("panic");
        }
        if (n > 0)
        {
            throw new NotFound("n");
        }
        return n;
    }

    static HostFailuresPanicPassesCatchGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresPanicPassesCatch");
        console.Log("before");
        int r;
        try
        {
            r = Risky(2);
        }
        catch (NotFound)
        {
            r = -1;
        }
        console.Log("after " + r.ToString(CultureInfo.InvariantCulture));
    }
}
