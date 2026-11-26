using System.Collections.Generic;
using TemperLang.Core;

namespace Values.Exporter;

public static class ExporterGlobal
{
    public static readonly int MagicNumber;

    internal static IReadOnlyList<int> Foo()
    {
        return Listed.CreateReadOnlyList<int>(unchecked(21 + 21), 42);
    }

    static ExporterGlobal()
    {
        int a = Listed.Get(Foo(), 0);
        MagicNumber = a;
    }
}
