using System.Globalization;
using TemperLang.Core;

namespace Errors;

public static class ErrorsGlobal
{
    public static int Good(int x)
    {
        return x + 1;
    }

    internal static int Mismatched(int x)
    {
        return Core.Bubble<int>();
    }

    internal static int Undeclared()
    {
        return Core.Bubble<int>();
    }

    internal static int Misspelled(string s)
    {
        return Core.Bubble<int>();
    }

    internal static int MisspelledMethod(string s)
    {
        return Core.Bubble<int>();
    }

    internal static int WrongArity()
    {
        return Core.Bubble<int>();
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
        return Core.Bubble<string>();
    }

    internal static string Stored(int x)
    {
        return Core.Bubble<string>();
    }

    internal static int Returned()
    {
        return Core.Bubble<int>();
    }

    static ErrorsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Errors");
        int a;
        try
        {
            a = Mismatched(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            a = -1;
        }
        int b;
        try
        {
            b = Undeclared();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            b = -1;
        }
        int c;
        try
        {
            c = Misspelled("a");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            c = -1;
        }
        int d;
        try
        {
            d = MisspelledMethod("a");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            d = -1;
        }
        int e;
        try
        {
            e = WrongArity();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            e = -1;
        }
        Box box = new Box(3);
        string f;
        try
        {
            f = box.Bad();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            f = "caught";
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Good(1)} {a} {b} {c} {d} {e}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{UnknownType(1)} {AfterUnclosed(2)} {box.Get()} {f}"));
        string g;
        try
        {
            g = Stored(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            g = "caught";
        }
        int h;
        try
        {
            h = Returned();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            h = -1;
        }
        int i;
        try
        {
            i = Core.Bubble<int>();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            i = -1;
        }
        string j;
        try
        {
            j = Dropped(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            j = "dropped";
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{g} {h} {i} {j}"));
    }
}
