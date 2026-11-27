using System.Globalization;
using TemperLang.Core;

namespace ErrorsMissingOperator;

public static class ErrorsMissingOperatorGlobal
{
    public static int Good(int x)
    {
        return x + 1;
    }

    internal static int UnknownType(object x)
    {
        return 1;
    }

    internal static int AfterUnclosed(int x)
    {
        return x * 3;
    }

    internal static string Dropped(int cents)
    {
        throw Core.Panic("panic");
    }

    static ErrorsMissingOperatorGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ErrorsMissingOperator");
        Box box = new Box(3);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Good(1)} {UnknownType(1)} {AfterUnclosed(2)} {box.Get()}"));
        Dropped(1);
        console.Log("after");
    }
}
