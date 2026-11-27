using TemperLang.Core;

namespace PanicCausesPanicOnEveryBranch;

public static class PanicCausesPanicOnEveryBranchGlobal
{
    /// <exception cref="NotPositive">Where it fails with NotPositive.</exception>
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            throw new NotPositive(n);
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
        catch (NotPositive)
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
            catch (NotPositive failure1)
            {
                throw Core.Panic("panic", failure1);
            }
            throw Core.Panic("panic");
        }
    }
}
