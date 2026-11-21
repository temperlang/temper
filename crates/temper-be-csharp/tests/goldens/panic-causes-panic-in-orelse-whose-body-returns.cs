using System.Globalization;
using TemperLang.Core;

namespace PanicCausesPanicInOrelseWhoseBodyReturns;

public static class PanicCausesPanicInOrelseWhoseBodyReturnsGlobal
{
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    internal static int Checked(int n)
    {
        try
        {
            if (n == 0)
            {
                return 0;
            }
            Positive(n);
        }
        catch (global::TemperLang.Core.Bubble bubble1)
        {
            throw Core.Panic("panic", bubble1);
        }
        return n;
    }

    static PanicCausesPanicInOrelseWhoseBodyReturnsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesPanicInOrelseWhoseBodyReturns");
        console.Log("before");
        console.Log("after " + Checked(-1).ToString(CultureInfo.InvariantCulture));
    }
}
