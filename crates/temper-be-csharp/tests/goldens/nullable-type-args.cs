using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace NullableTypeArgs;

public static class NullableTypeArgsGlobal
{
    internal static KeyValuePair<A, B?> HalfNamed<A, B>(A a)
        where B : Named
    {
        return new KeyValuePair<A, B?>(a, null);
    }

    internal static KeyValuePair<A, B?> HalfShown<A, B>(A a, B? b)
        where B : class, IShown
    {
        return new KeyValuePair<A, B?>(a, b);
    }

    internal static string ShownOr<T>(T? x)
        where T : class, IShown
    {
        if (x == null)
        {
            return "-";
        }
        else
        {
            return ((IShown)x).Shown;
        }
    }

    internal static T Known<T>(Optional<T> x)
    {
        return x.Value;
    }

    internal static T KnownShown<T>(T? x)
        where T : class, IShown
    {
        return Core.CastToNonNull<T>(x);
    }

    static NullableTypeArgsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("NullableTypeArgs");
        KeyValuePair<int, Named?> halfThree = HalfNamed<int, Named>(3);
        KeyValuePair<int, Tag?> halfTag = HalfShown(1, new Tag("t"));
        Shelf<Tag> shelf = new Shelf<Tag>(null);
        IReadOnlyList<Tag?> empty = shelf.All();
        shelf.Top = new Tag("u");
        IReadOnlyList<Tag?> full = shelf.All();
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{halfThree.Key} {(halfThree.Value == null ? "true" : "false")} {ShownOr(halfTag.Value)} {ShownOr(empty[0])} {ShownOr(full[0])} {full.Count}"));
        Named? named = null;
        int? none = null;
        Named got;
        try
        {
            got = Known(Optional.Of<Named>(named));
        }
        catch
        {
            got = new Named("bubbled");
        }
        Tag? tag = null;
        Tag missing;
        try
        {
            missing = KnownShown(tag);
        }
        catch
        {
            missing = new Tag("absent");
        }
        int minus;
        try
        {
            minus = Known(Optional.Of<int>(none));
        }
        catch
        {
            minus = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Known(Optional.Of<Named>(new Named("n"))).Name} {got.Name} {Known(Optional.Of<int>(2))} {minus} {KnownShown(new Tag("k")).Shown} {missing.Shown}"));
    }
}
