using TemperLang.Core;

namespace AsExpr;

public static class AsExprGlobal
{
    public static readonly object Thing2;
    public static readonly object Thing1;
    public static readonly object Thing0;
    public static readonly object Thing3;

    internal static string Describe(object? v)
    {
        if (v != null && v is Fuji)
        {
            return "Fuji " + ((Fuji)v).Name;
        }
        {
            if (v != null && v is IApple)
            {
                return ((IApple)v).Name;
            }
            else
            {
                return "other";
            }
        }
    }

    static AsExprGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("AsExpr");
        {
            IApple? x;
            x = new Fuji();
            console.Log(Core.CastToNonNull<IApple>(x).Name);
            console.Log(Core.CastToNonNull<Fuji>(x).Name);
            x = new Gala();
            console.Log(x != null && x is Fuji ? Core.CastToNonNull<Fuji>(x).Name : "Cast failed!");
            object? y = x;
            console.Log(Core.CastToNonNull<IApple>(y).Name);
            console.Log(Core.CastToNonNull<Gala>(y).Name);
            x = null;
            console.Log(x != null ? "Tasty nothing." : "Was null!");
        }
        Thing2 = "two";
        Thing1 = 1;
        {
            console.Log(Describe(new Fuji()));
            console.Log(Describe(new Gala()));
            console.Log(Describe(Thing2));
            console.Log(Describe(Thing1));
            console.Log(Describe(null));
        }
        Thing0 = false;
        Thing3 = 3.14;
    }
}
