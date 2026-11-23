using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace NullableGenerics;

public static class NullableGenericsGlobal
{
    public static Optional<T> FirstOrNull<T>(IReadOnlyList<T> ls)
    {
        if (ls.Count == 0)
        {
            return Optional<T>.None;
        }
        else
        {
            return ls[0];
        }
    }

    public static T WithDefault<T>(T x, Optional<T> y = default)
    {
        T y2 = y.HasValue ? y.Value : x;
        return y2;
    }

    static NullableGenericsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("NullableGenerics");
        IReadOnlyList<string?> strings = Listed.CreateReadOnlyList<string?>(null, "b");
        IReadOnlyList<string?> noStrings = Listed.CreateReadOnlyList<string?>();
        IReadOnlyList<int?> ints = Listed.CreateReadOnlyList<int?>(null, 2);
        string? a = Optional.OrNull<string?>(FirstOrNull(strings));
        string? b = Optional.OrNull<string?>(FirstOrNull(noStrings));
        int? c = Optional.ToNullable<int>(FirstOrNull(ints));
        console.Log((a == null ? "true" : "false") + " " + (b == null ? "true" : "false") + " " + (c == null ? "true" : "false"));
        string? subject = Optional.OrNull<string>(FirstOrNull(Listed.CreateReadOnlyList<string>("x")));
        console.Log(subject == null ? "none" : subject);
        int? subject2 = Optional.ToNullable<int>(FirstOrNull(Listed.Slice(ints, 1, 2)));
        int? subject3 = Optional.ToNullable<int>(FirstOrNull(Listed.CreateReadOnlyList<int>(3)));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{(subject2 == null ? -1 : subject2.Value)} {(subject3 == null ? -1 : subject3.Value)}"));
        Slot<string?> slot = new Slot<string?>(null);
        console.Log((Optional.OrNull<string?>(slot.Kept(true)) == null ? "true" : "false") + " " + (Optional.OrNull<string?>(slot.Kept(false)) == null ? "true" : "false"));
        string? subject4 = Optional.OrNull<string?>(new Slot<string?>("s").Kept(true));
        console.Log(subject4 == null ? "none" : subject4);
        IReadOnlyList<Named?> nameds = Listed.CreateReadOnlyList<Named?>((Named?)null);
        IReadOnlyList<Named> noNameds = Listed.CreateReadOnlyList<Named>();
        Named? n = Optional.OrNull<Named>(FirstOrNull(Listed.CreateReadOnlyList<Named>(new Named("n"))));
        string @string = (Optional.OrNull<Named?>(FirstOrNull(nameds)) == null ? "true" : "false") + " " + (Optional.OrNull<Named>(FirstOrNull(noNameds)) == null ? "true" : "false") + " ";
        string? subject5 = n == null ? null : n.Name;
        console.Log(@string + (subject5 == null ? "none" : subject5));
        string? subject6;
        Named? subject7 = Optional.OrNull<Named>(FirstOrNull(noNameds));
        subject6 = subject7 == null ? null : subject7.Name;
        console.Log(subject6 == null ? "none" : subject6);
        string? none = null;
        string? subject8 = WithDefault("a", none);
        console.Log(subject8 == null ? "null" : subject8);
        string? subject9 = WithDefault("a", "b");
        console.Log(subject9 == null ? "null" : subject9);
        string? subject10 = WithDefault("a");
        console.Log(subject10 == null ? "null" : subject10);
        int? subject11 = WithDefault<int?>(1, null);
        console.Log((subject11 == null ? -1 : subject11.Value).ToString(CultureInfo.InvariantCulture));
        console.Log(WithDefault("c") + " " + WithDefault(4, Optional.Of<int>(5)).ToString(CultureInfo.InvariantCulture));
    }
}
