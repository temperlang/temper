using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace CallbackChanges;

public static class CallbackChangesGlobal
{
    internal static readonly IList<int> f;
    internal static int peeks;
    internal static readonly IList<int> t;
    internal static readonly IList<int> u;
    internal static readonly IList<int> s;

    internal static string Show(IReadOnlyList<int> ls)
    {
        return Listed.Join(ls, ",", (int n) => n.ToString(CultureInfo.InvariantCulture));
    }

    internal static IList<string> Words()
    {
        IList<string> w = new List<string>();
        {
            int i = 0;
            while (i < 3)
            {
                w.Add("w" + i.ToString(CultureInfo.InvariantCulture));
                i = i + 1;
            }
        }
        return w;
    }

    internal static int ReduceFromGrowing()
    {
        IList<int> g = new List<int>(Listed.CreateReadOnlyList<int>(1, 2));
        int fn(int sum, int n)
        {
            if (g.Count < 4)
            {
                g.Add(9);
            }
            return sum * 10 + n;
        }
        return Enumerable.Aggregate(Listed.AsReadOnly(g), 0, (Func<int, int, int>)fn);
    }

    internal static int MapShrinking()
    {
        IList<string> w = Words();
        string fn(string s)
        {
            if (w.Count > 1)
            {
                Listed.RemoveLast(w);
            }
            return s;
        }
        return Listed.ToReadOnlyList(Enumerable.Select(Listed.AsReadOnly(w), (Func<string, string>)fn)).Count;
    }

    internal static int ReduceShrinking()
    {
        IList<string> w = Words();
        string fn(string all, string s)
        {
            if (w.Count > 1)
            {
                Listed.RemoveLast(w);
            }
            return all;
        }
        string all2 = Listed.Reduce(w, (Func<string, string, string>)fn);
        if (all2.Length == 0)
        {
            return 0;
        }
        else
        {
            return 1;
        }
    }

    internal static int FilterGrowing()
    {
        IList<int> g = new List<int>(Listed.CreateReadOnlyList<int>(1, 2));
        bool fn(int n)
        {
            if (g.Count < 4)
            {
                g.Add(9);
            }
            return true;
        }
        return Listed.ToReadOnlyList(Enumerable.Where(g, (Func<int, bool>)fn)).Count;
    }

    internal static int JoinGrowing()
    {
        IList<int> g = new List<int>(Listed.CreateReadOnlyList<int>(1, 2));
        string fn(int n)
        {
            if (g.Count < 4)
            {
                g.Add(9);
            }
            return n.ToString(CultureInfo.InvariantCulture);
        }
        string joined = Listed.Join(g, ",", (Func<int, string>)fn);
        if (joined.Length == 0)
        {
            return 0;
        }
        else
        {
            return 1;
        }
    }

    internal static IDictionary<string, int> Letters()
    {
        IDictionary<string, int> m = new TemperLang.Core.OrderedDictionary<string, int>();
        m["a" + 1.ToString(CultureInfo.InvariantCulture)] = 1;
        m["b" + 2.ToString(CultureInfo.InvariantCulture)] = 2;
        m["c" + 3.ToString(CultureInfo.InvariantCulture)] = 3;
        return m;
    }

    internal static int ForEachRemoving()
    {
        IDictionary<string, int> m = Letters();
        int sum = 0;
        void fn(string k, int v)
        {
            if (m.ContainsKey("c3"))
            {
                try
                {
                    Core.RemoveGet(m, "c3");
                }
                catch
                {
                }
            }
            sum = sum + v;
        }
        Mapped.ForEach(m, (Action<string, int>)fn);
        return sum;
    }

    internal static int ForEachAdding()
    {
        IDictionary<string, int> m = Letters();
        int count = 0;
        void fn(string k, int v)
        {
            count = count + 1;
            if (m.Count < 30)
            {
                m["k" + m.Count.ToString(CultureInfo.InvariantCulture)] = 1;
            }
        }
        Mapped.ForEach(m, (Action<string, int>)fn);
        return count;
    }

    internal static int ToListWithRemoving()
    {
        IDictionary<string, int> m = Letters();
        int fn(string k, int v)
        {
            if (m.ContainsKey("c3"))
            {
                try
                {
                    Core.RemoveGet(m, "c3");
                }
                catch
                {
                }
            }
            return v;
        }
        return Mapped.ToListWith(Mapped.AsReadOnly(m), (Func<string, int, int>)fn).Count;
    }

    static CallbackChangesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CallbackChanges");
        IList<int> b = new List<int>(Listed.CreateReadOnlyList<int>(1, 2));
        Listed.AddAll(b, Listed.AsReadOnly(b));
        IList<int> c = new List<int>(Listed.CreateReadOnlyList<int>(1, 2));
        Listed.AddAll(c, Listed.AsReadOnly(c), 1);
        IList<int> d = new List<int>(Listed.CreateReadOnlyList<int>(1, 2, 3));
        IReadOnlyList<int> removed = Listed.Splice(d, 1, 1, Listed.AsReadOnly(d));
        console.Log("self " + Show(Listed.AsReadOnly(b)) + " " + Show(Listed.AsReadOnly(c)) + " " + Show(Listed.AsReadOnly(d)) + " " + Show(removed));
        IList<int> e = new List<int>(Listed.CreateReadOnlyList<int>(1, 2));
        IReadOnlyList<int> kept = Listed.ToReadOnlyList(Enumerable.Where(e, (int n) => n > 0));
        e.Add(3);
        console.Log("filter " + Show(kept) + " " + Show(Listed.AsReadOnly(e)));
        f = new List<int>(Listed.CreateReadOnlyList<int>(3, 1, 2));
        peeks = 0;
        int fn2(int a, int z)
        {
            peeks = peeks + f.Count;
            return a - z;
        }
        Listed.Sort(f, (Func<int, int, int>)fn2);
        console.Log("peeking " + Show(Listed.AsReadOnly(f)));
        int t1;
        try
        {
            t1 = ReduceFromGrowing();
        }
        catch
        {
            t1 = -1;
        }
        int t2;
        try
        {
            t2 = MapShrinking();
        }
        catch
        {
            t2 = -1;
        }
        int t3;
        try
        {
            t3 = ReduceShrinking();
        }
        catch
        {
            t3 = -1;
        }
        int t4;
        try
        {
            t4 = FilterGrowing();
        }
        catch
        {
            t4 = -1;
        }
        int t5;
        try
        {
            t5 = JoinGrowing();
        }
        catch
        {
            t5 = -1;
        }
        int t6;
        try
        {
            t6 = ForEachRemoving();
        }
        catch
        {
            t6 = -1;
        }
        int t7;
        try
        {
            t7 = ForEachAdding();
        }
        catch
        {
            t7 = -1;
        }
        int t8;
        try
        {
            t8 = ToListWithRemoving();
        }
        catch
        {
            t8 = -1;
        }
        IReadOnlyList<int> walked = Listed.CreateReadOnlyList<int>(t1, t2, t3, t4, t5, t6, t7, t8);
        console.Log("walks " + Show(walked));
        t = new List<int>(Listed.CreateReadOnlyList<int>(3, 1, 2));
        int fn3(int a, int z)
        {
            Listed.Set(t, 0, 99);
            return a - z;
        }
        Listed.Sort(t, (Func<int, int, int>)fn3);
        u = new List<int>(Listed.CreateReadOnlyList<int>(3, 1, 2));
        int fn4(int a, int z)
        {
            int last = Listed.RemoveLast(u);
            u.Add(last + 10);
            return a - z;
        }
        Listed.Sort(u, (Func<int, int, int>)fn4);
        console.Log("same length " + Show(Listed.AsReadOnly(t)) + " " + Show(Listed.AsReadOnly(u)));
        s = new List<int>(Listed.CreateReadOnlyList<int>(3, 1, 2));
        int fn5(int a, int z)
        {
            if (s.Count < 40)
            {
                s.Add(0);
            }
            return a - z;
        }
        Listed.Sort(s, (Func<int, int, int>)fn5);
        console.Log("sorted " + s.Count.ToString(CultureInfo.InvariantCulture));
    }
}
