using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace ModuleState;

public static class ModuleStateGlobal
{
    public static readonly string Greeting;
    internal static volatile int count;
    internal static readonly int limit;
    public static volatile int Total;
    public static volatile int Ticks;
    internal static readonly IReadOnlyList<string> prefixes;
    internal static readonly Palette paletteWarm;
    public static readonly Palette Favorite;
    private static readonly object moduleLock = new();

    internal static int Bump()
    {
        lock (moduleLock)
        {
            count = count + 1;
        }
        Total = Total + count;
        return count;
    }

    public static void Tick()
    {
        lock (moduleLock)
        {
            Ticks = Ticks + 1;
        }
    }

    public static string Report()
    {
        return string.Create(CultureInfo.InvariantCulture, $"{Greeting} {Bump()} of {limit} {Total}");
    }

    static ModuleStateGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ModuleState");
        Greeting = "hi";
        count = 0;
        limit = 3;
        Total = 10;
        Ticks = 0;
        int local = 1;
        prefixes = Listed.CreateReadOnlyList<string>("p-");
        paletteWarm = Palette.Named("warm");
        Favorite = paletteWarm;
        local = local + limit;
        console.Log(Report());
        console.Log(Report());
        console.Log("local " + local.ToString(CultureInfo.InvariantCulture));
        console.Log(paletteWarm.Name + " " + Favorite.Name);
    }
}
