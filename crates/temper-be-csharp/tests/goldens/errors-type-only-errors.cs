using System.Globalization;
using TemperLang.Core;

namespace ErrorsTypeOnlyErrors;

public static class ErrorsTypeOnlyErrorsGlobal
{
    public static int Good(int x)
    {
        return x + 1;
    }

    internal static int Mismatched(int x)
    {
        throw Core.Panic("panic");
    }

    internal static int Misspelled(string s)
    {
        throw Core.Panic("panic");
    }

    internal static int UnknownType(object x)
    {
        return 1;
    }

    internal static int AfterUnclosed(int x)
    {
        return x * 3;
    }

    internal static string Stored(int x)
    {
        throw Core.Panic("panic");
    }

    internal static int Returned()
    {
        throw Core.Panic("panic");
    }

    static ErrorsTypeOnlyErrorsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ErrorsTypeOnlyErrors");
        Box box = new Box(3);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Good(1)} {UnknownType(1)} {AfterUnclosed(2)} {box.Get()}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Mismatched(1)} {Stored(1)} {Returned()} {Core.ThrowPanic<int>("panic")} {box.Bad()}"));
        Misspelled("a");
        console.Log("after");
    }
}
