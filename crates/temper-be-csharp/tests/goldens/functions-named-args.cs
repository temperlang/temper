using System;
using System.Globalization;
using TemperLang.Core;

namespace NamedArgs;

public static class NamedArgsGlobal
{
    internal static readonly ILoggingConsole console;
    internal static volatile string message;
    internal static volatile bool repeat;

    internal static string ChangeRepeat()
    {
        repeat = true;
        return message;
    }

    internal static bool ChangeMessage()
    {
        message = "Namaste!";
        return repeat;
    }

    internal static T Calc<T>(int n, Func<int, T> transform, CalcOptions? options = null)
    {
        CalcOptions options2 = options ?? new CalcOptions();
        int origin = options2.Origin;
        int scale = options2.Scale;
        return transform((n - origin) * scale);
    }

    static NamedArgsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("NamedArgs");
        _ = new Greet();
        _ = new Greet("Bye!");
        _ = new Greet("Hola!", true);
        _ = new Greet("Salut!");
        _ = new Greet(null, true);
        _ = new Greet(null, true);
        {
            bool arg = true;
            _ = new Greet("Ni hao!", arg);
        }
        message = "Hi!";
        repeat = false;
        {
            bool arg2 = ChangeMessage();
            string arg3 = ChangeRepeat();
            _ = new Greet(arg3, arg2);
        }
        console.Log(new Person().ToString());
        console.Log(new Person("Bob").ToString());
        console.Log(new Person("Carrie", 50).ToString());
        new Person("Dan").Greet("Hallo");
        console.Log(Calc(2, (int n) => n.ToString(CultureInfo.InvariantCulture)));
        console.Log(Calc(2, (int n) => n.ToString(CultureInfo.InvariantCulture), new CalcOptions(3, 4)));
        console.Log(Calc(2, (int n) => n.ToString(CultureInfo.InvariantCulture), new CalcOptions(3)));
        console.Log(Calc(2, (int n) => n.ToString(CultureInfo.InvariantCulture), new CalcOptions(null, 4)));
        console.Log(Calc(2, (int n) => "<" + n.ToString(CultureInfo.InvariantCulture) + ">", new CalcOptions(5)));
        Tuple<object?> fn6(int n)
        {
            console.Log(n.ToString(CultureInfo.InvariantCulture));
            return Core.Empty();
        }
        Calc(2, (Func<int, Tuple<object?>>)fn6, new CalcOptions(-2));
    }
}
