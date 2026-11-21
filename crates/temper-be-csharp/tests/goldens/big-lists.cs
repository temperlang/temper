using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace BigLists;

public static class BigListsGlobal
{
    static BigListsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("BigLists");
        int n = 200000;
        IList<int> source = new List<int>();
        {
            int i = 0;
            while (i < n)
            {
                source.Add(i);
                i = i + 1;
            }
        }
        IReadOnlyList<int> values = Listed.ToReadOnlyList(source);
        IList<int> b = new List<int>();
        Listed.AddAll(b, values);
        Listed.AddAll(b, values, 0);
        b.Insert(n, -1);
        Listed.AddAll(b, values, n);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{b.Count} {b[0]} {b[n - 1]} {b[n]} {b[2 * n]} {b[2 * n + 1]} {b[3 * n]}"));
        IList<int> twice = new List<int>();
        Listed.AddAll(twice, values);
        Listed.AddAll(twice, Listed.AsReadOnly(twice), 1);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{twice.Count} {twice[0]} {twice[1]} {twice[n]} {twice[n + 1]}"));
        IList<int> s = new List<int>();
        s.Add(7);
        s.Add(8);
        IReadOnlyList<int> removed = Listed.Splice(s, 1, 0, values);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{s.Count} {removed.Count} {s[0]} {s[1]} {s[n]} {s[n + 1]}"));
        IList<int> c = new List<int>();
        c.Add(7);
        c.Add(8);
        Listed.Splice(c, -1, 0, values);
        Listed.Splice(c, 10 * n, 0, values);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{c.Count} {c[0]} {c[n - 1]} {c[n]} {c[n + 1]} {c[n + 2]} {c[2 * n + 1]}"));
        IList<int> own = new List<int>();
        Listed.AddAll(own, values);
        IReadOnlyList<int> gone = Listed.Splice(own, 0, 1, Listed.AsReadOnly(own));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{own.Count} {gone.Count} {own[0]} {own[n - 1]} {own[n]} {own[2 * n - 2]}"));
        IReadOnlyList<int> kept = Listed.ToReadOnlyList(Enumerable.Where(values, (int x) => x != 5));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{kept.Count} {kept[4]} {kept[5]} {kept[n - 2]}"));
        IList<int> codes = new List<int>();
        {
            int i2 = 0;
            while (i2 < n)
            {
                codes.Add(97 + Core.ModSafe(i2, 3));
                i2 = i2 + 1;
            }
        }
        string text;
        try
        {
            text = Core.StringFromCodePoints(Listed.ToReadOnlyList(codes));
        }
        catch
        {
            text = "";
        }
        int four = StringUtil.Next(text, StringUtil.Next(text, StringUtil.Next(text, StringUtil.Next(text, 0))));
        console.Log(StringUtil.CountBetween(text, 0, text.Length).ToString(CultureInfo.InvariantCulture) + " " + StringUtil.Slice(text, 0, four));
    }
}
