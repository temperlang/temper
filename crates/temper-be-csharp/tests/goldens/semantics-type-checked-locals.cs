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

    internal static int F(int x)
    {
        return x;
    }

    internal static int G(int x)
    {
        return x;
    }

    internal static int I(int x)
    {
        return x;
    }

    static TypeCheckedLocalsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("TypeCheckedLocals");
        console.Log("Checkpoint 0");
        try
        {
            int a;
            Core.Bubble();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Yay();
        }
        console.Log("Checkpoint 1");
        int a2 = 1;
        Yay();
        console.Log("Checkpoint 2");
        {
            int a3 = 1;
            try
            {
                Core.Bubble();
            }
            catch (global::TemperLang.Core.Bubble)
            {
                Meh();
            }
            if (a3 == 1)
            {
                Meh();
            }
            else
            {
                Meh();
            }
        }
        console.Log("Checkpoint 3");
        int a4 = 1;
        a4 = 2;
        Yay();
        console.Log("Checkpoint 4");
        try
        {
            F(0);
            Yay();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Boo();
        }
        try
        {
            Core.Bubble();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Meh();
        }
        console.Log("Checkpoint 5");
        try
        {
            G(0);
            Yay();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Boo();
        }
        try
        {
            Core.Bubble();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Meh();
        }
        console.Log("Checkpoint 6");
        try
        {
            I(0);
            Yay();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Boo();
        }
        try
        {
            Core.Bubble();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            Meh();
        }
    }
}
