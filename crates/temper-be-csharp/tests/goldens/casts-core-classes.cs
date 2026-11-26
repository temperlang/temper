using System.Collections;
using System.Collections.Generic;
using TemperLang.Core;

namespace CoreClasses;

public static class CoreClassesGlobal
{
    internal static string Describe(object x)
    {
        if (x is Thing)
        {
            return "thing";
        }
        try
        {
            _ = (Thing)x;
            return "thing, as";
        }
        catch
        {
            return "other";
        }
    }

    internal static bool IsThing(object x)
    {
        return x is Thing;
    }

    static CoreClassesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CoreClasses");
        BitArray bits = new BitArray(8);
        IReadOnlyList<int> ints = Listed.CreateReadOnlyList<int>(1, 2);
        console.Log(Describe(new Thing()));
        console.Log(Describe(new KeyValuePair<string, int>("a", 1)));
        console.Log(Describe(bits));
        console.Log(Describe("s"));
        console.Log(Describe(1));
        console.Log(Describe(ints));
        console.Log(Describe(Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<string, int>>(new KeyValuePair<string, int>("m", 2)))));
        console.Log(Describe(Listed.AsReadOnly(new List<int>())));
        console.Log(Describe(new Queue<int>()));
        console.Log(IsThing(new Thing()) ? "true" : "false");
        console.Log(IsThing(bits) ? "true" : "false");
    }
}
