using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace CoreNumbers;

public static class CoreNumbersGlobal
{
    internal static readonly IReadOnlyList<int> digits;

    internal static int From(int index)
    {
        return Enumerable.Aggregate(Enumerable.Skip(digits, index), 100, (int s, int n) => s * 10 + n);
    }

    static CoreNumbersGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CoreNumbers");
        int one = 1;
        int big = 2147483647 * one;
        int small = -2147483648 * one;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{big + 1} {small - 1} {one + 1} {one - 1} {small + 1} {big - 1}"));
        long one64 = (long)one;
        long big64 = 9223372036854775807L * one64;
        long small64 = -big64 - one64;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{big64 + 1L} {small64 - 1L} {one64 - 1L}"));
        int zero = one - one;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Math.Sign(big)} {Math.Sign(small)} {Math.Sign(zero)} {Math.Sign(one)} {Math.Sign(zero - one)} {Math.Sign(small + 1)}"));
        digits = Listed.CreateReadOnlyList<int>(1, 2, 3);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{From(0)} {From(1)} {From(3)} {From(7)}"));
        double inf = 1e308 * 10.0;
        double nan = inf - inf;
        double ninf = 0.0 - inf;
        console.Log((Float64.Near(nan, nan) ? "true" : "false") + " " + (Float64.Near(inf, inf) ? "true" : "false") + " " + (Float64.Near(ninf, ninf) ? "true" : "false") + " " + (Float64.Near(nan, 1.0) ? "true" : "false") + " " + (Float64.Near(1.0, nan) ? "true" : "false"));
        console.Log((Float64.Near(inf, 1.0) ? "true" : "false") + " " + (Float64.Near(inf, ninf) ? "true" : "false") + " " + (Float64.Near(inf, 1.0, 0.0, 0.0) ? "true" : "false") + " " + (Float64.Near(inf, inf, 0.0, 0.0) ? "true" : "false") + " " + (Float64.Near(inf, 1.0, 1.0, inf) ? "true" : "false") + " " + (Float64.Near(1.0, ninf, 1.0, inf) ? "true" : "false"));
        console.Log((Float64.Near(1.0, 1.5, nan, 1.0) ? "true" : "false") + " " + (Float64.Near(1.0, 1.5, 1.0, nan) ? "true" : "false") + " " + (Float64.Near(1.0, 1.0, nan, nan) ? "true" : "false") + " " + (Float64.Near(-0.0, 0.0, 0.0, 0.0) ? "true" : "false"));
        console.Log((Float64.Near(1.0, 1.0 + 1e-10) ? "true" : "false") + " " + (Float64.Near(1.0, 1.1) ? "true" : "false") + " " + (Float64.Near(1.0, 1.1, 0.2) ? "true" : "false") + " " + (Float64.Near(0.0, 1e-300, 1e-9, 1e-12) ? "true" : "false"));
    }
}
