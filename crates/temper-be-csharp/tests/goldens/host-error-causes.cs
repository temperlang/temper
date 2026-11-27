using System.Globalization;
using TemperLang.Core;

namespace HostErrorCauses;

public static class HostErrorCausesGlobal
{
    /// <exception cref="Oops">Where it fails with Oops.</exception>
    public static int HostFails(int n)
    {
        return HostErrorCausesConnected.HostFails(n);
    }

    static HostErrorCausesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorCauses");
        console.Log("before");
        int h;
        try
        {
            h = HostFails(1);
        }
        catch (Oops failure1)
        {
            throw Core.Panic("panic", failure1);
        }
        console.Log("after " + h.ToString(CultureInfo.InvariantCulture));
    }
}
