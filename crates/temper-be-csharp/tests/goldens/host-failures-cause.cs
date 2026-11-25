using TemperLang.Core;

namespace HostFailuresCause;

public static class HostFailuresCauseGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresCauseConnected.HostLookup(key);
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

    static HostFailuresCauseGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresCause");
        console.Log("before");
        Setting("port");
        console.Log("after");
    }
}
