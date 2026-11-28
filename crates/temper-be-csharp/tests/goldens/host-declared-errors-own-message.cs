using TemperLang.Core;

namespace HostDeclaredErrorsOwnMessage;

public static class HostDeclaredErrorsOwnMessageGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostDeclaredErrorsOwnMessageConnected.HostLookup(key);
    }

    /// <exception cref="Expired">Where it fails with Expired.</exception>
    internal static void Check(int days)
    {
        if (days > 0)
        {
            throw new Expired(days);
        }
    }

    static HostDeclaredErrorsOwnMessageGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostDeclaredErrorsOwnMessage");
        console.Log("before");
        Check(0);
        Check(3);
        console.Log("after");
    }
}
