using System.Globalization;
using TemperLang.Core;

namespace GenericNullChecks;

public static class GenericNullChecksGlobal
{
    public static bool IsNull<T>(T x)
    {
        return x == null;
    }

    public static T OrElse<T>(T x, T y)
    {
        {
            if (x == null)
            {
                return y;
            }
            else
            {
                return x;
            }
        }
    }

    static GenericNullChecksGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("GenericNullChecks");
        console.Log((IsNull<string?>(null) ? "true" : "false") + " " + (IsNull("a") ? "true" : "false") + " " + (IsNull("a") ? "true" : "false"));
        console.Log((IsNull<int?>(null) ? "true" : "false") + " " + (IsNull(1) ? "true" : "false") + " " + (IsNull(1) ? "true" : "false"));
        console.Log((IsNull<Named?>(null) ? "true" : "false") + " " + (IsNull(new Named("n")) ? "true" : "false"));
        string? subject = OrElse<string?>(null, "y");
        console.Log(subject == null ? "null" : subject);
        string @string = OrElse("x", "y") + " ";
        int? subject2 = OrElse<int?>(null, 2);
        console.Log(@string + (subject2 == null ? -1 : subject2.Value).ToString(CultureInfo.InvariantCulture));
    }
}
