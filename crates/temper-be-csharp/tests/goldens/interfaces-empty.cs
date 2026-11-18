using TemperLang.Core;

namespace Empty;

public static class EmptyGlobal
{
    internal static readonly ILoggingConsole console;

    static EmptyGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Empty");
        IGreeter greeter = new HiGreeter();
        Core.Cast<HiGreeter>(greeter).Greet();
    }
}
