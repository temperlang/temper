using TemperLang.Core;

namespace HostFailuresOwnMessage;

public static class HostFailuresOwnMessageGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresOwnMessageConnected.HostLookup(key);
    }

    /// <exception cref="Expired">Where it fails with Expired.</exception>
    internal static void Check(int days)
    {
        if (days > 0)
        {
            throw new Expired(days);
        }
    }

    static HostFailuresOwnMessageGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresOwnMessage");
        console.Log("before");
        Check(0);
        Check(3);
        console.Log("after");
    }
}
