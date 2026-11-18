using System.Globalization;
using TemperLang.Core;

namespace Testing;

public static class TestingGlobal
{
    public static int Twice(int n)
    {
        return n * 2;
    }

    static TestingGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Testing");
        console.Log("init " + Twice(1).ToString(CultureInfo.InvariantCulture));
    }
}
