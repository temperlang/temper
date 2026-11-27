using System.Globalization;
using TemperLang.Core;

namespace PanicCausesPanicInOrelseWhoseBodyReturns;

public static class PanicCausesPanicInOrelseWhoseBodyReturnsGlobal
{
    /// <exception cref="NotPositive">Where it fails with NotPositive.</exception>
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            throw new NotPositive(n);
        }
        return n;
    }

    internal static int Checked(int n)
    {
        try
        {
            int p = Positive(n);
            if (p == 1)
            {
                return 0;
            }
        }
        catch (NotPositive failure1)
        {
            throw Core.Panic("panic", failure1);
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
