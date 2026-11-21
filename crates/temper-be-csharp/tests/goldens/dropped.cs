using System.Globalization;
using TemperLang.Core;

namespace Dropped;

public static class DroppedGlobal
{
    internal static int Go(ILexer lexer, bool b, int d)
    {
        if (b)
        {
            lexer.Scan("x");
        }
        if (b)
        {
            _ = lexer.Count;
        }
        if (b)
        {
            {
                string? subject = lexer.Scan("y");
                if (subject == null)
                {
                }
            }
        }
        if (b)
        {
            Core.Div(7, d);
        }
        if (b)
        {
        }
        if (b)
        {
        }
        lexer.Scan("x");
        return 0;
    }

    static DroppedGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Dropped");
        L l = new L();
        try
        {
            Go(l, true, 1);
        }
        catch (global::TemperLang.Core.Bubble bubble1)
        {
            throw Core.Panic("panic", bubble1);
        }
        console.Log(l.N.ToString(CultureInfo.InvariantCulture));
        int t1;
        try
        {
            t1 = Go(l, true, 0);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = -1;
        }
        console.Log(t1.ToString(CultureInfo.InvariantCulture));
        console.Log(l.N.ToString(CultureInfo.InvariantCulture));
    }
}
