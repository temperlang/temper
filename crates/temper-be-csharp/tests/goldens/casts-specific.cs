using System.Collections.Generic;
using TemperLang.Core;

namespace Specific;

public static class SpecificGlobal
{
    internal static readonly ILoggingConsole console;

    internal static void CheckB(IAlphaInterface instance)
    {
        console.Log(instance is BravoClass ? "yep" : "nope");
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
        CheckB(Listed.Get(items, 0));
        CheckB(Listed.Get(items, 1));
        CheckIfB(Listed.Get(items, 0));
        CheckIfB(Listed.Get(items, 1));
    }
}
