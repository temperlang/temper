using System.Globalization;
using TemperLang.Core;

namespace IndexAtEnd;

public static class IndexAtEndGlobal
{
    internal static int CodeAt(string s, int i)
    {
        return StringUtil.Get(s, i);
    }

    static IndexAtEndGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("IndexAtEnd");
        string text = "abc";
        console.Log(CodeAt(text, 0).ToString(CultureInfo.InvariantCulture));
        console.Log("before");
        console.Log(CodeAt(text, text.Length).ToString(CultureInfo.InvariantCulture));
        console.Log("not reached");
    }
}
