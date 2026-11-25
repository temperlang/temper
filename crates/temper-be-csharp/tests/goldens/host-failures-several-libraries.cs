using CountFailures;
using System;
using System.Globalization;
using TemperLang.Core;

namespace HostFailuresSeveralLibraries;

public static class HostFailuresSeveralLibrariesGlobal
{
    internal static readonly ILoggingConsole console;

    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    public static int HostLookup(string key)
    {
        return HostFailuresSeveralLibrariesConnected.HostLookup(key);
    }

    internal static int Total(string key, string count)
    {
        try
        {
            return HostLookup(key) + CountFailuresGlobal.ParseCount(count);
        }
        catch (Exception caught1) when (caught1 is IFailure)
        {
            IFailure e = (IFailure)caught1;
            console.Log("caught " + e.Message);
            return e is NotFound ? -1 : -2;
        }
    }

    /// <exception cref="Unreadable">Where it fails with Unreadable.</exception>
    internal static int Reading(string key, string count)
    {
        try
        {
            return HostLookup(key) + CountFailuresGlobal.ParseCount(count);
        }
        catch (Exception caught2) when (caught2 is IFailure)
        {
            IFailure failure = (IFailure)caught2;
            throw new Unreadable(key, (Exception)failure);
        }
    }

    internal static int Ratio(string key, string count)
    {
        try
        {
            return Core.Div(HostLookup(key), CountFailuresGlobal.ParseCount(count));
        }
        catch (Exception failure1) when (failure1 is global::TemperLang.Core.Bubble || failure1 is IFailure)
        {
            return -9;
        }
    }

    static HostFailuresSeveralLibrariesGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostFailuresSeveralLibraries");
        console.Log("before");
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Total("a", "two")} {Total("z", "two")} {Total("a", "x")}"));
        int r;
        try
        {
            r = Reading("a", "x");
        }
        catch (Unreadable e)
        {
            string @string = e.Message + ", caused by ";
            string? subject;
            IFailure? subject2 = Core.FailureCause(e);
            subject = subject2 == null ? null : subject2.Message;
            console.Log(@string + (subject == null ? "nothing" : subject));
            r = 0;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{r} {Ratio("a", "two")} {Ratio("a", "zero")} {Ratio("a", "x")} {Ratio("z", "two")}"));
        CountFailuresGlobal.ParseCount("x");
        console.Log("after");
    }
}
