using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace ReduceEmpty;

public static class ReduceEmptyGlobal
{
    internal static int Sum(IReadOnlyList<int> values)
    {
        return Listed.Reduce(values, (int a, int b) => a + b);
    }

    static ReduceEmptyGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ReduceEmpty");
        console.Log(Sum(Listed.CreateReadOnlyList<int>(1, 2, 3)).ToString(CultureInfo.InvariantCulture));
        console.Log("before");
        console.Log(Sum(Listed.CreateReadOnlyList<int>()).ToString(CultureInfo.InvariantCulture));
        console.Log("not reached");
    }
}
