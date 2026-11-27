using TemperLang.Core;

namespace TypeCheckedLocals;

public static class TypeCheckedLocalsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static void Yay()
    {
        console.Log("yay");
    }

    internal static void Meh()
    {
        console.Log("meh");
    }

    internal static void Boo()
    {
        console.Log("boo");
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int F(int x)
    {
        return x;
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int G(int x)
    {
        return x;
    }

    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int I(int x)
    {
        return x;
    }

    static TypeCheckedLocalsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("TypeCheckedLocals");
        console.Log("Checkpoint 0");
        int a;
        throw Core.Panic("panic");
    }
}
