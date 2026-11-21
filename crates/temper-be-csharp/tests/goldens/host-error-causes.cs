using System.Globalization;
using TemperLang.Core;

namespace HostErrorCauses;

public static class HostErrorCausesGlobal
{
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
        catch (global::TemperLang.Core.Bubble bubble1)
        {
            throw Core.Panic("panic", bubble1);
        }
        console.Log("after " + h.ToString(CultureInfo.InvariantCulture));
    }
}
