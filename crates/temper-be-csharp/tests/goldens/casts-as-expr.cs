using TemperLang.Core;

namespace AsExpr;

public static class AsExprGlobal
{
    public static readonly object Thing0;
    public static readonly object Thing1;
    public static readonly object Thing2;
    public static readonly object Thing3;

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
        Thing0 = false;
        Thing1 = 1;
        Thing2 = "two";
        Thing3 = 3.14;
    }
}
