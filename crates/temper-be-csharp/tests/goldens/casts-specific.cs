using System.Collections.Generic;
using TemperLang.Core;

namespace Specific;

public static class SpecificGlobal
{
    internal static readonly ILoggingConsole console;

    internal static void CheckB(IAlphaInterface instance)
    {
        string t1;
        try
        {
            Core.Cast<BravoClass>(instance);
            t1 = "yep";
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = "nope";
        }
        console.Log(t1);
    }

    internal static void CheckIfB(IAlphaInterface instance)
    {
        console.Log(instance is BravoClass ? "yep" : "nope");
    }

    static SpecificGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Specific");
        CheckB(new BravoClass());
        CheckB(new CharlieClass());
        IReadOnlyList<IAlphaInterface> items = Listed.CreateReadOnlyList<IAlphaInterface>(new BravoClass(), new CharlieClass());
        CheckB(items[0]);
        CheckB(items[1]);
        CheckIfB(items[0]);
        CheckIfB(items[1]);
    }
}
