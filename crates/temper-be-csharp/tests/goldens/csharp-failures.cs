using System;
using System.Globalization;
using TemperLang.Core;

namespace CsharpFailures;

public static class CsharpFailuresGlobal
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

    /// <exception cref="SignException">Where it fails with SignException.</exception>
    public static int Sign(string text)
    {
        {
            if (text == "+")
            {
                return 1;
            }
            else if (text == "-")
            {
                return -1;
            }
            else if (text == "")
            {
                throw new EmptyText();
            }
            else
            {
                throw new BadSign(text);
            }
        }
    }

    /// <exception cref="LookupFailed">Where it fails with LookupFailed.</exception>
    public static int Setting(string name)
    {
        try
        {
            return Lookup(name);
        }
        catch (NotFound failure)
        {
            throw new LookupFailed(name, failure);
        }
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

    /// <exception cref="LookupFailed">Where it fails with LookupFailed.</exception>
    public static int MadeBefore(string name)
    {
        LookupFailed failure = new LookupFailed(name);
        try
        {
            return Lookup(name);
        }
        catch (NotFound failure2)
        {
            throw Core.WithCause(failure, failure2);
        }
    }

    /// <exception cref="LookupFailed">Where it fails with LookupFailed.</exception>
    public static int ThrownAgain(string name)
    {
        try
        {
            return Setting(name);
        }
        catch (LookupFailed e)
        {
            try
            {
                return Lookup("q");
            }
            catch (NotFound failure)
            {
                throw Core.WithCause(e, failure);
            }
        }
    }

    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int Risky(int n)
    {
        if (n > 1)
        {
            throw Core.Panic("panic");
        }
        return Lookup("a");
    }

    public static string Explained(string name)
    {
        try
        {
            return Setting(name).ToString(CultureInfo.InvariantCulture);
        }
        catch (LookupFailed e)
        {
            IFailure? cause = Core.FailureCause(e);
            string why = cause != null ? cause.Message : "nothing";
            return e.Message + ", from " + why;
        }
    }

    public static string SignMessage(string text)
    {
        try
        {
            return Sign(text).ToString(CultureInfo.InvariantCulture);
        }
        catch (SignException e)
        {
            return e.Message;
        }
    }

    public static string EarlyCause(string name)
    {
        try
        {
            return MadeBefore(name).ToString(CultureInfo.InvariantCulture);
        }
        catch (LookupFailed e)
        {
            IFailure? cause = Core.FailureCause(e);
            return cause != null ? cause.Message : "nothing";
        }
    }

    public static int Mixed(string key, int n)
    {
        try
        {
            return Lookup(key) + Core.Div(10, n);
        }
        catch (Exception failure1) when (failure1 is global::TemperLang.Core.Bubble || failure1 is NotFound)
        {
            return -1;
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

    static CsharpFailuresGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CsharpFailures");
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Lookup("a")} {Sign("-")} {Setting("a")}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Mixed("a", 0)} {Mixed("z", 1)} {Mixed("a", 5)}"));
        console.Log(Explained("port") + "; " + Explained("a") + "; " + SignMessage("x"));
        console.Log(EarlyCause("soon"));
        console.Log(FirstCause("x"));
        console.Log(RetryCause("y", 1) + "; " + RetryCause("z", 3));
    }
}
