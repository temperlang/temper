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
            string t1;
            try
            {
                t1 = Core.CastToNonNull<Fuji>(x).Name;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                t1 = "Cast failed!";
            }
            console.Log(t1);
            object? y = x;
            string t2;
            try
            {
                t2 = Core.CastToNonNull<IApple>(y).Name;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                t2 = "Cast from AnyValue failed!";
            }
            console.Log(t2);
            string t3;
            try
            {
                t3 = Core.CastToNonNull<Gala>(y).Name;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                t3 = "Cast from AnyValue failed!";
            }
            console.Log(t3);
            string t4;
            try
            {
                t4 = Core.CastToNonNull<Fuji>(y).Name;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                t4 = "Cast from AnyValue failed!";
            }
            console.Log(t4);
            x = null;
            string t5;
            try
            {
                Core.CastToNonNull<IApple>(x);
                t5 = "Tasty nothing.";
            }
            catch (global::TemperLang.Core.Bubble)
            {
                t5 = "Was null!";
            }
            console.Log(t5);
        }
        Thing0 = false;
        Thing1 = 1;
        Thing2 = "two";
        Thing3 = 3.14;
    }
}
