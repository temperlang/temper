using System.Globalization;
using TemperLang.Core;

namespace FailureCauses;

public static class FailureCausesGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int Lookup(string key)
    {
        if (key == "a")
        {
            return 1;
        }
        throw new NotFound(key);
    }

    /// <exception cref="Wrapped">Where it fails with Wrapped.</exception>
    public static int WrapLookup(string name)
    {
        try
        {
            return Lookup(name);
        }
        catch (NotFound failure)
        {
            throw new Wrapped("outer", new Wrapped("inner", null), failure);
        }
    }

    /// <exception cref="Retry">Where it fails with Retry.</exception>
    public static int Retried(string name, int n)
    {
        try
        {
            return Lookup(name);
        }
        catch (NotFound failure)
        {
            if (n > 1)
            {
                throw new Retry(name, n, failure);
            }
            throw new Retry(name, null, failure);
        }
    }

    internal static string FirstCause(string name)
    {
        try
        {
            return WrapLookup(name).ToString(CultureInfo.InvariantCulture);
        }
        catch (Wrapped e)
        {
            Wrapped? first = e.First;
            string inner = first != null && Core.FailureCause(first) == null ? "none" : "some";
            string @string1 = e.Label + " ";
            string? subject1;
            IFailure? subject2 = Core.FailureCause(e);
            subject1 = subject2 == null ? null : subject2.Message;
            string? subject3 = first == null ? null : first.Label;
            return @string1 + (subject1 == null ? "none" : subject1) + ", " + (subject3 == null ? "-" : subject3) + " " + inner;
        }
    }

    internal static string RetryCause(string name, int n)
    {
        try
        {
            return Retried(name, n).ToString(CultureInfo.InvariantCulture);
        }
        catch (Retry e)
        {
            string @string2 = e.Reason + " " + e.Tries.ToString(CultureInfo.InvariantCulture) + " ";
            string? subject4;
            IFailure? subject2 = Core.FailureCause(e);
            subject4 = subject2 == null ? null : subject2.Message;
            return @string2 + (subject4 == null ? "none" : subject4);
        }
    }

    static FailureCausesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("FailureCauses");
        console.Log(FirstCause("x"));
        console.Log(RetryCause("y", 1) + "; " + RetryCause("z", 3));
    }
}
