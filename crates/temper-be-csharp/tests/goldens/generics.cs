using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace Generics;

public static class GenericsGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly IDictionary<string, int> tally;

    internal static T Tagged<S, T>(T x)
    {
        return x;
    }

    internal static int CountOf<T>(int n)
    {
        IList<T> b = new List<T>();
        return b.Count + n;
    }

    internal static T Relayed<S, T>(T x)
        where S : IScene
    {
        return Tagged<S, T>(x);
    }

    internal static bool NoneIn(int length)
    {
        return length == 0;
    }

    public static T First<T>(IReadOnlyList<T> items, T fallback)
    {
        if (NoneIn(items.Count))
        {
            return fallback;
        }
        else
        {
            return Listed.Get(items, 0);
        }
    }

    internal static T Held<T>(IHolder<T> h)
    {
        return h.Held;
    }

    internal static int Total(IReadOnlyList<int> ns)
    {
        int sum = 0;
        {
            int i = 0;
            while (i < ns.Count)
            {
                sum = sum + Listed.Get(ns, i);
                i = i + 1;
            }
        }
        return sum;
    }

    internal static void Indexed()
    {
        IList<int> counts = new List<int>();
        counts.Add(1);
        counts.Add(10);
        int at = 0;
        {
            int k = at;
            Listed.Set(counts, k, Listed.Get(counts, k) + 4);
        }
        {
            Listed.Set(counts, 1, Listed.Get(counts, 1) * 3);
        }
        int was;
        int k2;
        int t = at;
        at = t + 1;
        k2 = t;
        int t2 = Listed.Get(counts, k2);
        Listed.Set(counts, k2, t2 + 1);
        was = t2;
        int pre;
        int k3 = at;
        int t3 = Listed.Get(counts, k3) + 1;
        Listed.Set(counts, k3, t3);
        pre = t3;
        int post;
        int t4 = Listed.Get(counts, 0);
        Listed.Set(counts, 0, t4 - 1);
        post = t4;
        int down;
        int t5 = Listed.Get(counts, 0) - 1;
        Listed.Set(counts, 0, t5);
        down = t5;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{was} {pre} {post} {down} {at}"));
    }

    internal static void Both()
    {
        int x = 1;
        int y = 10;
        int t1 = x;
        x = t1 + 1;
        int t21 = y;
        y = t21 + 1;
        int locals = t1 + t21;
        Counter p = new Counter(100);
        Counter q = new Counter(1000);
        int t31 = p.N;
        p.N = t31 + 1;
        int t41 = q.N;
        q.N = t41 + 1;
        int fields = t31 + t41;
        int t51 = p.N;
        p.N = t51 + 1;
        int t6 = p.N;
        p.N = t6 + 1;
        int same = t51 + t6;
        IList<int> a = new List<int>();
        a.Add(3);
        IList<int> b = new List<int>();
        b.Add(30);
        int i = 0;
        int k1 = i;
        int t7 = Listed.Get(a, k1);
        Listed.Set(a, k1, t7 + 1);
        int k21 = i;
        int t8 = Listed.Get(b, k21);
        Listed.Set(b, k21, t8 + 1);
        int items = t7 + t8;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{locals} {x} {y} {fields} {same} {p.N} {q.N} {items} {Listed.Get(a, 0)} {Listed.Get(b, 0)}"));
    }

    internal static int Bump(string key)
    {
        {
            tally[key] = Mapped.Get(tally, key) - 1;
        }
        int before;
        int t = Mapped.Get(tally, key);
        tally[key] = t + 1;
        before = t;
        int after;
        int t2 = Mapped.Get(tally, key) + 1;
        tally[key] = t2;
        after = t2;
        return before + after;
    }

    internal static bool FirstSame(IReadOnlyList<int> a, IReadOnlyList<int> b)
    {
        return Listed.Get(a, 0) == Listed.Get(b, 0);
    }

    static GenericsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Generics");
        IReadOnlyList<int> ns = Listed.CreateReadOnlyList<int>(1, 2, 3);
        IReadOnlyList<int> none = Listed.CreateReadOnlyList<int>();
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Total(ns)} {First(ns, 0)} {First(none, -1)}"));
        console.Log(First(Listed.CreateReadOnlyList<string>("a", "b"), "z"));
        Box<string> box = new Box<string>("boxed");
        console.Log(Held(box));
        KeyValuePair<string, int> pair = box.PairWith(7);
        console.Log(pair.Key + " " + pair.Value.ToString(CultureInfo.InvariantCulture));
        Shelf<Named> shelf = new Shelf<Named>(Listed.CreateReadOnlyList<Named>(new Named("ann"), new Named("bo")));
        string? subject = shelf.FirstName();
        console.Log(subject == null ? "none" : subject);
        IReadOnlyList<Named> noNames = Listed.CreateReadOnlyList<Named>();
        Shelf<Named> empty = new Shelf<Named>(noNames);
        string? subject2 = empty.FirstName();
        console.Log(subject2 == null ? "none" : subject2);
        int n = shelf.Tally<Named>(1) + shelf.Tally<Named>(2);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Tagged<IScene, int>(4)} {Tagged<IScene, string>("t")} {CountOf<object>(3)} {Relayed<IScene, int>(5)} {n}"));
        IList<string> builder = new List<string>();
        builder.Add("x");
        builder.Add("y");
        Listed.Add(builder, "w", 0);
        console.Log(builder.Count.ToString(CultureInfo.InvariantCulture) + " " + Listed.Get(builder, 0) + " " + Listed.RemoveLast(builder));
        IReadOnlyList<string> built = Listed.ToReadOnlyList(builder);
        console.Log(built.Count.ToString(CultureInfo.InvariantCulture) + " " + Listed.GetOr(built, 5, "?") + " " + Listed.Get(Listed.Slice(built, 1, 2), 0));
        IReadOnlyDictionary<string, int> m = Mapped.ConstructMap(Listed.CreateReadOnlyList<KeyValuePair<string, int>>(new KeyValuePair<string, int>("a", 1), new KeyValuePair<string, int>("b", 2)));
        int t9;
        try
        {
            t9 = Mapped.Get(m, "b");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t9 = -1;
        }
        int t10;
        try
        {
            t10 = Mapped.Get(m, "c");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t10 = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{t9} {t10} {(m.ContainsKey("a") ? "true" : "false")} {m.Count}"));
        IDictionary<string, int?> mb = new TemperLang.Core.OrderedDictionary<string, int?>();
        mb["q"] = 5;
        mb["n"] = null;
        int? subject3 = Mapped.GetOrDefault(mb, "q", 0);
        int? subject4 = Mapped.GetOrDefault(mb, "n", 0);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{(subject3 == null ? -2 : subject3.Value)} {(subject4 == null ? -2 : subject4.Value)} {(mb.ContainsKey("n") ? "true" : "false")}"));
        string? subject5;
        int? subject6;
        try
        {
            subject6 = Core.RemoveGet(mb, "q");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            subject6 = 0;
        }
        subject5 = subject6 == null ? null : subject6.Value.ToString(CultureInfo.InvariantCulture);
        console.Log(subject5 == null ? "null" : subject5);
        console.Log(Mapped.Keys(mb).Count.ToString(CultureInfo.InvariantCulture));
        Indexed();
        Both();
        tally = new TemperLang.Core.OrderedDictionary<string, int>();
        tally["k"] = 2;
        int t11;
        try
        {
            t11 = Bump("k");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t11 = -1;
        }
        int t12;
        try
        {
            t12 = Bump("z");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t12 = -1;
        }
        int t13;
        try
        {
            t13 = Mapped.Get(tally, "k");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t13 = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{t11} {t12} {t13}"));
        console.Log((FirstSame(Listed.CreateReadOnlyList<int>(1000), Listed.CreateReadOnlyList<int>(1000)) ? "true" : "false") + " " + (FirstSame(Listed.CreateReadOnlyList<int>(1000), Listed.CreateReadOnlyList<int>(7)) ? "true" : "false"));
    }
}
