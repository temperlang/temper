using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace PanicCausesPanicPassingOrelsePanic;

public static class PanicCausesPanicPassingOrelsePanicGlobal
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

    /// <exception cref="NotPositive">Where it fails with NotPositive.</exception>
    internal static int Read(IReadOnlyList<int> ls, int i)
    {
        Positive(i + 1);
        return Listed.Get(ls, i);
    }

    static PanicCausesPanicPassingOrelsePanicGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesPanicPassingOrelsePanic");
        console.Log("before");
        int r;
        try
        {
            r = Read(Listed.CreateReadOnlyList<int>(1, 2), 5);
        }
        catch (NotPositive failure1)
        {
            throw Core.Panic("panic", failure1);
        }
        console.Log("after " + r.ToString(CultureInfo.InvariantCulture));
    }
}
