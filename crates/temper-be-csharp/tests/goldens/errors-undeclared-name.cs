using System.Globalization;
using TemperLang.Core;

namespace ErrorsUndeclaredName;

public static class ErrorsUndeclaredNameGlobal
{
    public static int Good(int x)
    {
        return x + 1;
    }

    internal static int Undeclared()
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

    static ErrorsUndeclaredNameGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ErrorsUndeclaredName");
        Box box = new Box(3);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Good(1)} {UnknownType(1)} {AfterUnclosed(2)} {box.Get()}"));
        Undeclared();
        console.Log("after");
    }
}
