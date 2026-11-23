using System.Globalization;
using TemperLang.Core;

namespace SealedWhens;

public static class SealedWhensGlobal
{
    internal static IShape ShapeOf(int n)
    {
        if (n > 1)
        {
            return new Line(n);
        }
        else
        {
            return new Dot(n);
        }
    }

    internal static string Named(IShape s)
    {
        string name;
        string @string;
        if (s is Dot)
        {
            @string = "dot";
        }
        else
        {
            string value;
            if (s is Line)
            {
                value = "line";
            }
            else
            {
                throw Core.Panic("panic");
            }
            @string = value;
        }
        name = @string;
        return name;
    }

    internal static string Pair(IShape s, IShape t)
    {
        {
            if (s is Dot)
            {
                if (t is Dot)
                {
                    return "dd";
                }
                else if (t is Line)
                {
                    return "dl";
                }
                else
                {
                    throw Core.Panic("panic");
                }
            }
            else if (s is Line)
            {
                return "l";
            }
            else
            {
                throw Core.Panic("panic");
            }
        }
    }

    internal static int Doubled(int n)
    {
        return n * 2;
    }

    internal static int Code(IShape s, bool early)
    {
        if (early)
        {
            int int32;
            if (s is Dot)
            {
                int32 = 1;
            }
            else
            {
                int value;
                if (s is Line)
                {
                    value = 2;
                }
                else
                {
                    throw Core.Panic("panic");
                }
                int32 = value;
            }
            return int32;
        }
        if (s.Size > 1)
        {
            {
                if (s is Dot)
                {
                    return 10;
                }
                else if (s is Line)
                {
                    return 20;
                }
                else
                {
                    throw Core.Panic("panic");
                }
            }
        }
        else
        {
            int int321;
            if (s is Dot)
            {
                int321 = 3;
            }
            else
            {
                int value;
                if (s is Line)
                {
                    value = 4;
                }
                else
                {
                    throw Core.Panic("panic");
                }
                int321 = value;
            }
            return Doubled(int321);
        }
    }

    static SealedWhensGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("SealedWhens");
        IShape dot = ShapeOf(1);
        IShape line = ShapeOf(2);
        console.Log(Named(dot) + " " + Named(line) + " " + Pair(dot, line) + " " + Pair(line, dot));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Code(line, true)} {Code(dot, false)} {Code(line, false)} {new Namer().Name(line)}"));
    }
}
