using TemperLang.Core;

namespace HostDeclaredErrorsCause;

public static class HostDeclaredErrorsCauseGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostDeclaredErrorsCauseConnected.HostLookup(key);
    }

    /// <exception cref="LookupFailed">Where it fails with LookupFailed.</exception>
    internal static int Setting(string name)
    {
        try
        {
            return HostLookup(name);
        }
        catch (NotFound failure)
        {
            throw new LookupFailed(name, failure);
        }
    }

    static HostDeclaredErrorsCauseGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostDeclaredErrorsCause");
        console.Log("before");
        Setting("port");
        console.Log("after");
    }
}
