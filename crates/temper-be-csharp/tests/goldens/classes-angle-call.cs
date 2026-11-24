using System;
using System.Collections.Generic;
using TemperLang.Core;

namespace AngleCall;

public static class AngleCallGlobal
{
    internal static readonly ILoggingConsole console;

    internal static Type Whatever(int n)
    {
        if (n == 0)
        {
            console.Log("0");
        }
        return typeof(void);
    }

    internal static T LastOf<T>(IReadOnlyList<T> things)
    {
        T thing = Listed.Get(things, things.Count - 1);
        return thing;
    }

    static AngleCallGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("AngleCall");
        Whatever(1);
        IReadOnlyList<string> someThings = Listed.CreateReadOnlyList<string>("a", "b");
        string first = Classy.First(someThings);
        string second = new Classy(1).Indexed(someThings);
        console.Log("Some things: " + first + ", " + second);
        console.Log("Picked: " + new Picker().Pick(someThings));
    }
}
