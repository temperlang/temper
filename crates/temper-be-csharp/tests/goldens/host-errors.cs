using System;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrors;

public static class HostErrorsGlobal
{
    internal static int Read(IReadOnlyList<int> ls, int i)
    {
        if (i < 0)
        {
            return Core.Bubble<int>();
        }
        return ls[i];
    }

    static HostErrorsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrors");
        console.Log("reading");
        int r;
        try
        {
            r = Read(Listed.CreateReadOnlyList<int>(1, 2), 5);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("read past the end " + r.ToString(CultureInfo.InvariantCulture));
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }
}
