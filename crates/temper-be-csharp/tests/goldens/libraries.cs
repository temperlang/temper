using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace Libraries;

public static class LibrariesGlobal
{
    internal static readonly global::Libraries.Casing casingFirst;
    internal static readonly global::Libraries.Casing casingLast;
    internal static readonly string casingMark;
    internal static volatile int ticks;
    internal static readonly IReadOnlyList<int> extras;
    private static readonly object moduleLock = new();

    public static Grid ParseGrid(string s)
    {
        if (s == "")
        {
            return Core.Bubble<Grid>();
        }
        return new Grid(1, Listed.CreateReadOnlyList<double>(ParseFloat64(s)));
    }

    internal static global::Libraries.Tagged Tagged(string? label = null)
    {
        return new global::Libraries.Tagged(label);
    }

    internal static int Weight(int c)
    {
        int @base = c == 42 ? 0 : 6;
        return (c > 50 ? 1 : 2) + @base + c;
    }

    internal static string ShapeName(IShape s)
    {
        string name;
        string @string;
        if (s is Dot)
        {
            @string = "dot";
        }
        else
        {
            string value;
            if (s is Line)
            {
                value = "line";
            }
            else
            {
                Environment.FailFast("panic");
                throw new InvalidOperationException();
            }
            @string = value;
        }
        name = @string;
        return name;
    }

    internal static IShape ShapeOf(int n)
    {
        IShape shape = n > 1 ? new Line(n) : new Dot(n);
        return shape;
    }

    internal static IReadOnlyList<int> Sizes(IShape s)
    {
        IReadOnlyList<int> found = s is Dot ? Listed.CreateReadOnlyList<int>(((Dot)s).Size, ((Dot)s).Size) : Listed.CreateReadOnlyList<int>();
        return found;
    }

    internal static int Rows(IReadOnlyList<double> values, int ncols)
    {
        try
        {
            return Core.Div(values.Count, ncols);
        }
        catch
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
    }

    internal static Grid Scaled(Grid grid)
    {
        if (grid.Ncols == 0)
        {
            return Core.Bubble<Grid>();
        }
        return grid;
    }

    internal static IReadOnlyList<double> Cubes(Grid grid)
    {
        try
        {
            IReadOnlyList<double> more = Listed.ToReadOnlyList(Enumerable.Select(Scaled(grid).Values, (double x) => x * x * x));
            return more;
        }
        catch
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
    }

    internal static void Each(IReadOnlyList<double> values, Action<double> f)
    {
        {
            int i = 0;
            while (i < values.Count)
            {
                f(values[i]);
                i = i + 1;
            }
        }
    }

    internal static double Total(IReadOnlyList<double> values)
    {
        double sum = 0.0;
        void fn(double x)
        {
            try
            {
                double part = x / 2.0;
                sum = sum + part;
            }
            catch
            {
                Environment.FailFast("panic");
                throw new InvalidOperationException();
            }
        }
        Each(values, fn);
        return sum;
    }

    internal static double Half(double x)
    {
        try
        {
            return x / 2.0;
        }
        catch
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
    }

    internal static double Cube(double x)
    {
        return x * 0.25;
    }

    internal static double Corner(Grid grid)
    {
        try
        {
            return grid.Get(1, 1);
        }
        catch
        {
            return -1.0;
        }
    }

    internal static Grid? Maybe(int n)
    {
        if (n > 0)
        {
            return new Grid(n, Listed.CreateReadOnlyList<double>(1.0));
        }
        else
        {
            return null;
        }
    }

    internal static IReadOnlyList<double> Mapped(Grid grid)
    {
        Grid t1;
        try
        {
            t1 = Scaled(grid);
        }
        catch
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
        double fn(double x)
        {
            if (Float64.Compare(x, 1.5) > 0)
            {
                return x * 2.0;
            }
            else
            {
                return x;
            }
        }
        return Listed.ToReadOnlyList(Enumerable.Select(t1.Values, (Func<double, double>)fn));
    }

    internal static int ByteAt(IReadOnlyList<int> digits, bool @short, int c)
    {
        int @byte = @short ? digits[c] * 17 : digits[2 * c] * 16 + digits[2 * c + 1];
        return @byte;
    }

    internal static bool Ascending(IReadOnlyList<int> digits)
    {
        if (digits.Count > 1)
        {
            return digits[0] + 1 == digits[1];
        }
        else
        {
            return false;
        }
    }

    internal static int FirstDrop(IReadOnlyList<int> digits)
    {
        int i = 1;
        while (i < digits.Count && digits[i - 1] <= digits[i])
        {
            i = i + 1;
        }
        return i;
    }

    internal static int Tick()
    {
        lock (moduleLock)
        {
            ticks = ticks + 1;
        }
        return ticks;
    }

    internal static int TickIf(bool @short)
    {
        int n = @short ? Tick() : Tick() * 10 + Tick();
        return n;
    }

    internal static bool Ticked()
    {
        if (ticks > 0)
        {
            return Tick() * 10 + Tick() > 0;
        }
        else
        {
            return false;
        }
    }

    internal static int TickWhile()
    {
        int i = 0;
        while (i < 3 && Tick() < Tick())
        {
            i = i + 1;
        }
        return i;
    }

    public static int SizeWith<T>(IReadOnlyList<T> items)
    {
        return items.Count + extras.Count;
    }

    internal static string? Text(int n)
    {
        if (n > 0)
        {
            return "x";
        }
        else
        {
            return null;
        }
    }

    internal static int? Count(int n)
    {
        if (n > 0)
        {
            return n;
        }
        else
        {
            return null;
        }
    }

    internal static global::Libraries.Casing? Casing(int n)
    {
        if (n > 0)
        {
            return casingFirst;
        }
        else
        {
            return null;
        }
    }

    internal static string Captured(string s)
    {
        Grid? found;
        try
        {
            found = ParseGrid(s);
        }
        catch
        {
            found = null;
        }
        return Listed.Join(Listed.ToReadOnlyList(Enumerable.Select(Listed.CreateReadOnlyList<int>(1, 2), (int x) => found == null)), ",", (bool b) => b ? "true" : "false");
    }

    internal static string Bidi()
    {
        return "look\u202Eout";
    }

    internal static string Word(int n)
    {
        if (n > 1)
        {
            return "b";
        }
        else
        {
            return "a";
        }
    }

    internal static int JoinEffects<T>(IList<IEffect<T>> into, IReadOnlyList<IEffect<T>> more)
    {
        {
            int length = more.Count;
            int i = 0;
            while (i < length)
            {
                IEffect<T> e = more[i];
                i = i + 1;
                into.Add(e);
            }
        }
        return into.Count;
    }

    public static string EffectsOf<T>(string text, T @event, T other)
    {
        IList<IEffect<T>> into = new List<IEffect<T>>();
        JoinEffects(into, Listed.CreateReadOnlyList<IEffect<T>>());
        JoinEffects(into, Listed.CreateReadOnlyList<IEffect<T>>(new Plain<T>(text)));
        JoinEffects(into, Listed.CreateReadOnlyList<IEffect<T>>(new Happened<T>(@event), new Plain<T>("!"), new Happened<T>(other)));
        string shown = "";
        {
            IReadOnlyList<IEffect<T>> list = Listed.ToReadOnlyList(into);
            int length = list.Count;
            int i = 0;
            while (i < length)
            {
                IEffect<T> e = list[i];
                i = i + 1;
                if (e is Plain<T>)
                {
                    shown = shown + ((Plain<T>)e).Text;
                }
                else
                {
                    shown = shown + "*";
                }
            }
        }
        return shown;
    }

    public static bool SameHeld<T>(Held<T>? a, Held<T>? b)
        where T : IShown
    {
        Held<T>? left = a;
        Held<T>? right = b;
        return left == null ? right == null : right == null ? false : object.ReferenceEquals(left, right);
    }

    internal static string? Problem(int n)
    {
        string? p;
        string? @string;
        if (n == 0)
        {
            @string = null;
        }
        else
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
        p = @string;
        return p;
    }

    internal static string? Message(int n)
    {
        string? p;
        string? @string;
        if (n == 0)
        {
            @string = null;
        }
        else
        {
            string? value;
            if (n == 1)
            {
                value = "one";
            }
            else
            {
                string? @string2;
                if (n == 2)
                {
                    @string2 = null;
                }
                else
                {
                    Environment.FailFast("panic");
                    throw new InvalidOperationException();
                }
                value = @string2;
            }
            @string = value;
        }
        p = @string;
        return p;
    }

    internal static int SumParsed(IReadOnlyList<string> xs)
    {
        int total = 0;
        {
            int i = 0;
            while (i < xs.Count)
            {
                string x = xs[i];
                int n;
                int? held = null;
                try
                {
                    held = Core.ToInt(x);
                }
                catch
                {
                }
                if (held == null)
                {
                    {
                        i = i + 1;
                        continue;
                    }
                }
                else
                {
                    n = held.Value;
                }
                total = total + n;
                i = i + 1;
            }
        }
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                string x = xs[i];
                i = i + 1;
                int m;
                int? held = null;
                try
                {
                    held = Core.ToInt(x);
                }
                catch
                {
                }
                if (held == null)
                {
                    continue;
                }
                else
                {
                    m = held.Value;
                }
                total = total + m * 10;
            }
        }
        return total;
    }

    internal static int Backwards(IReadOnlyList<int> xs)
    {
        int t = 0;
        {
            int i = xs.Count;
            while (true)
            {
                i = i - 1;
                if (i < 0)
                {
                    break;
                }
                t = t * 10 + xs[i];
            }
        }
        return t;
    }

    internal static string FormatFloat64(double d)
    {
        if (double.IsInfinity(d))
        {
            return d < 0 ? "-Infinity" : "Infinity";
        }
        if (double.IsNaN(d))
        {
            return "NaN";
        }
        string s = d.ToString(global::System.Globalization.CultureInfo.InvariantCulture);
        int e = s.IndexOf('E', global::System.StringComparison.Ordinal);
        string main = e == -1 ? s : s[..e];
        string frac = main.Contains('.', global::System.StringComparison.Ordinal) ? "" : ".0";
        string exp = e == -1 ? "" : string.Concat("e", global::System.MemoryExtensions.AsSpan(s, e + 1));
        return main + frac + exp;
    }

    internal static double ParseFloat64(string s)
    {
        string trimmed = s.Trim();
        if (trimmed.StartsWith('.') || trimmed.EndsWith('.'))
        {
            throw new global::System.FormatException();
        }
        return trimmed switch
        {
            "Infinity" => double.PositiveInfinity,
            "-Infinity" => double.NegativeInfinity,
            _ => double.Parse(
                trimmed,
                global::System.Globalization.NumberStyles.Float,
                global::System.Globalization.CultureInfo.InvariantCulture),
        };
    }

    static LibrariesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Libraries");
        casingFirst = new global::Libraries.Casing("first");
        casingLast = global::Libraries.Casing.Named("last");
        casingMark = "#";
        ticks = 0;
        extras = Listed.CreateReadOnlyList<int>(1);
        Grid grid = new Grid(2, Listed.CreateReadOnlyList<double>(1.0, 2.0, 3.0, 4.0));
        console.Log(Rows(grid.Values, 2).ToString(CultureInfo.InvariantCulture) + " " + FormatFloat64(Half(3.0)) + " " + FormatFloat64(Cube(8.0)));
        console.Log(FormatFloat64(Corner(grid)));
        console.Log(FormatFloat64(Total(grid.Values)));
        string? none = Text(0);
        int? some = Count(3);
        string? part1 = none;
        int? part2 = some;
        int? part3;
        Grid? subject = Maybe(0);
        part3 = subject == null ? null : (int?)subject.Ncols;
        console.Log((part1 == null ? "null" : part1) + " " + (part2 == null ? "null" : part2.Value.ToString(CultureInfo.InvariantCulture)) + " " + (part3 == null ? "null" : part3.Value.ToString(CultureInfo.InvariantCulture)));
        console.Log((Maybe(1) != null ? "true" : "false") + " " + (Maybe(0) != null ? "true" : "false") + " " + (Grid.Parse("") == null ? "true" : "false"));
        console.Log(casingFirst.ToString());
        console.Log(Listed.Join(Mapped(grid), ",", (double x) => FormatFloat64(x)));
        console.Log(Listed.Join(Cubes(grid), ",", (double x) => FormatFloat64(x)));
        global::Libraries.Casing? left1 = Casing(1);
        global::Libraries.Casing? left2 = Casing(0);
        global::Libraries.Casing? left3 = Casing(0);
        global::Libraries.Casing? right1 = Casing(0);
        console.Log(((left1 == null ? false : left1.Eq(casingFirst)) ? "true" : "false") + " " + ((left2 == null ? false : left2.Eq(casingFirst)) ? "true" : "false") + " " + (!(left3 == null ? right1 == null : right1 == null ? false : left3.Eq(right1)) ? "true" : "false"));
        console.Log(Captured("") + " " + Captured("2.5"));
        console.Log(Bidi() != "lookout" ? "true" : "false");
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{StringUtil.CompareStringsByCodePoint(Word(1), Word(2))} {StringUtil.CompareStringsByCodePoint(Word(2), Word(1))} {StringUtil.CompareStringsByCodePoint(Word(2), Word(3))}"));
        string named = Word(2);
        console.Log(named == Word(3) && named + "!" != named ? "true" : "false");
        IReadOnlyList<int> digits = Listed.CreateReadOnlyList<int>(1, 2, 4, 3);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{ByteAt(digits, true, 1)} {ByteAt(digits, false, 0)} {(Ascending(digits) ? "true" : "false")} {FirstDrop(digits)} {SizeWith(digits)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{TickIf(true)} {TickIf(false)} {(Ticked() ? "true" : "false")} {TickWhile()}"));
        string @string1 = string.Create(CultureInfo.InvariantCulture, $"{ShapeName(ShapeOf(1))} {ShapeName(ShapeOf(2))} {Sizes(ShapeOf(1)).Count} {Sizes(ShapeOf(2)).Count} ");
        int? part4 = new Dot(1).Grown;
        int? part5 = new Dot(3).Grown;
        console.Log(@string1 + (part4 == null ? "null" : part4.Value.ToString(CultureInfo.InvariantCulture)) + " " + (part5 == null ? "null" : part5.Value.ToString(CultureInfo.InvariantCulture)) + " " + casingLast.ToString() + " " + casingFirst.Marked);
        Carry carry = new Carry(3);
        string value1 = (carry.Carrying ? "true" : "false") + " " + carry.State.ToString(CultureInfo.InvariantCulture) + " " + (new Carry(0).Carrying ? "true" : "false") + " ";
        string? part6 = Tagged().Label;
        string? part7 = Tagged("x").Label;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{value1}{(part6 == null ? "null" : part6)} {(part7 == null ? "null" : part7)} {Weight(42)} {Weight(60)}"));
        IReadOnlyList<bool> flags;
        IList<bool> b = new List<bool>();
        b.Add(true);
        b.Add(false);
        flags = Listed.ToReadOnlyList(b);
        Joiner joiner = new Joiner();
        joiner.AddWord(new global::Libraries.Word("a"));
        joiner.AddFlags(Listed.CreateReadOnlyList<bool>(true));
        joiner.AddTexts(Listed.CreateReadOnlyList<string>("x", "y"));
        IReadOnlyList<string?> maybes = Listed.CreateReadOnlyList<string?>(null, "z");
        joiner.AddMaybes(maybes);
        console.Log(joiner.Out);
        Held<Tag> held = new Held<Tag>(new Tag("a"), null);
        Held<Tag> other = new Held<Tag>(new Tag("b"), held);
        Held<Tag>? nobody = null;
        string @string21 = held.ToString() + " ";
        Tag? subject2;
        Held<Tag>? subject3 = other.Next;
        subject2 = subject3 == null ? null : subject3.Item;
        console.Log(@string21 + (subject2 == null ? new Tag("none") : subject2).ToString() + " " + (SameHeld(held, held) ? "true" : "false") + " " + (SameHeld(held, other) ? "true" : "false") + " " + (SameHeld(nobody, nobody) ? "true" : "false") + " " + (SameHeld(nobody, held) ? "true" : "false"));
        console.Log(EffectsOf("ab", 1, 2) + " " + new Bundle<string>(Listed.CreateReadOnlyList<IEffect<string>>(), "2").First + " " + flags.Count.ToString(CultureInfo.InvariantCulture));
        Ticks counter = new Ticks();
        ICounting<string> stepper = counter;
        stepper.Step();
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{stepper.Step()[0]} {counter.Count} {new Names().Take(new Tag("abc"))} {((IEscapes)new Quoting()).EscapeFlag(true)}"));
        string? subject4 = Problem(0);
        string? subject5 = Message(1);
        string? subject6 = Message(2);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{(subject4 == null ? "none" : subject4)} {(subject5 == null ? "none" : subject5)} {(subject6 == null ? "none" : subject6)} {SumParsed(Listed.CreateReadOnlyList<string>("1", "x", "2"))} {Backwards(Listed.CreateReadOnlyList<int>(1, 2, 3))}"));
    }
}
