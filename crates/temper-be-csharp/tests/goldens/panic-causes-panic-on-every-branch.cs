using TemperLang.Core;

namespace PanicCausesPanicOnEveryBranch;

public static class PanicCausesPanicOnEveryBranchGlobal
{
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    static PanicCausesPanicOnEveryBranchGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesPanicOnEveryBranch");
        console.Log("before");
        int k;
        try
        {
            k = Positive(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            k = 0;
        }
        if (k > 0)
        {
            throw Core.Panic("panic");
        }
        else
        {
            try
            {
                Positive(-1);
            }
            catch (global::TemperLang.Core.Bubble bubble1)
            {
                throw Core.Panic("panic", bubble1);
            }
            throw Core.Panic("panic");
        }
    }
}
