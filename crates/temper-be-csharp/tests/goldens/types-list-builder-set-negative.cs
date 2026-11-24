using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace BuilderSetNegative;

public static class BuilderSetNegativeGlobal
{
    internal static void Put(IList<int> builder, int i, int value)
    {
        Listed.Set(builder, i, value);
    }

    static BuilderSetNegativeGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("BuilderSetNegative");
        IList<int> builder = new List<int>();
        builder.Add(1);
        builder.Add(2);
        Put(builder, 1, 5);
        console.Log(Listed.Join(builder, ", ", (int n) => n.ToString(CultureInfo.InvariantCulture)));
        console.Log("before");
        Put(builder, -1, 0);
        console.Log("not reached");
    }
}
