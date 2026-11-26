using System;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace Classes;

public static class ClassesGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly Rect rectUnit;

    internal static Loose? MaybeLoose(int n)
    {
        if (n == 0)
        {
            return null;
        }
        else
        {
            return new Loose(n);
        }
    }

    internal static IShape Shape(IShape x)
    {
        return x;
    }

    internal static Theme Picked(Theme t, string why)
    {
        console.Log(why);
        return t;
    }

    internal static string Noted(string s)
    {
        console.Log(s);
        return s;
    }

    internal static string Compared(Func<Loose?, Loose?, bool> eq)
    {
        IReadOnlyList<int> ns = Listed.CreateReadOnlyList<int>(0, 1, -1, 2);
        IList<string> @out = new List<string>();
        {
            int length = ns.Count;
            int i = 0;
            while (i < length)
            {
                int a = ns[i];
                i = i + 1;
                {
                    int length2 = ns.Count;
                    int i2 = 0;
                    while (i2 < length2)
                    {
                        int b = ns[i2];
                        i2 = i2 + 1;
                        @out.Add(eq(MaybeLoose(a), MaybeLoose(b)) ? "T" : "f");
                    }
                }
            }
        }
        return Listed.Join(@out, "", (string s) => s);
    }

    internal static Type TypeNamed(int n)
    {
        {
            if (n == 0)
            {
                return typeof(void);
            }
            else if (n == 1)
            {
                return typeof(bool);
            }
            else if (n == 2)
            {
                return typeof(int);
            }
            else if (n == 3)
            {
                return typeof(long);
            }
            else if (n == 4)
            {
                return typeof(double);
            }
            else
            {
                return typeof(string);
            }
        }
    }

    internal static Type Same(Type t)
    {
        return t;
    }

    internal static T OrFail<T>(bool ok, T x, IFailing<T> f)
    {
        if (ok)
        {
            return x;
        }
        else
        {
            return f.Fail();
        }
    }

    static ClassesGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Classes");
        rectUnit = new Rect(1, 1);
        Rect r = new Rect(2, 3);
        console.Log(r.ToString());
        console.Log(r.Area.ToString(CultureInfo.InvariantCulture));
        IShape s = Shape(r);
        console.Log(s.Describe());
        IShape q = Shape(new Square(4));
        console.Log(q.Describe());
        console.Log(q.Area.ToString(CultureInfo.InvariantCulture));
        console.Log(r.Scaled(2).Scaled(3).Area.ToString(CultureInfo.InvariantCulture));
        console.Log(rectUnit.Scaled(5).ToString());
        Counter c = Counter.Zero();
        c.Bump().Bump();
        console.Log(c.Count.ToString(CultureInfo.InvariantCulture));
        c.Doubled = 10;
        console.Log(c.Doubled.ToString(CultureInfo.InvariantCulture));
        console.Log(c.Same(new Counter().Bump().Bump()) ? "true" : "false");
        c.Added = 5;
        int added;
        int t2;
        int t = 2;
        c.Added = t;
        t2 = t;
        c.Added = t2;
        added = t2;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{added} {c.Count}"));
        console.Log((new Point(1, 2).Eq(new Point(1, 2)) ? "true" : "false") + " " + (!new Point(1, 2).Eq(new Point(2, 1)) ? "true" : "false"));
        console.Log(new Holder(new Loose(3), "a").Eq(new Holder(new Loose(-3), "a")) ? "true" : "false");
        Loose loose = new Loose(2);
        string @string = (loose.Mirrors(new Loose(-2)) ? "true" : "false") + " ";
        Loose? left = MaybeLoose(-2);
        Loose left2 = loose;
        Loose? right = MaybeLoose(0);
        Loose? left3 = MaybeLoose(0);
        console.Log(@string + ((left == null ? false : left.Eq(loose)) ? "true" : "false") + " " + ((right == null ? false : left2.Eq(right)) ? "true" : "false") + " " + (!(left3 == null ? false : left3.Eq(loose)) ? "true" : "false"));
        string matched;
        Loose? subject = MaybeLoose(-2);
        matched = (subject == null ? false : subject.Eq(loose)) ? "loose" : "other";
        console.Log(matched);
        Boxed<string> b1 = new Boxed<string>("x", 1);
        Boxed<string> b3 = new Boxed<string>("z", 2);
        console.Log(b1.Value + " " + (b1.Eq(new Boxed<string>("y", 1)) ? "true" : "false") + " " + (!b1.Eq(b3) ? "true" : "false") + " " + (b1.SameAs(new Boxed<string>("y", 1)) ? "true" : "false") + " " + (b3.SameAs(b1) ? "true" : "false"));
        console.Log((new Version(1, 2).CompareTo(new Version(1, 3)) < 0 ? "true" : "false") + " " + (new Version(2, 0).CompareTo(new Version(1, 9)) >= 0 ? "true" : "false"));
        console.Log((q is Square ? "true" : "false") + " " + (s is Square ? "true" : "false"));
        Square sq = (Square)q;
        console.Log(sq.Area.ToString(CultureInfo.InvariantCulture));
        if (s is Rect)
        {
            console.Log(((Rect)s).W.ToString(CultureInfo.InvariantCulture));
        }
        Stock st = new Stock(Listed.CreateReadOnlyList<int>(4, 5));
        st.Items = Listed.CreateReadOnlyList<int>(1, 2, 3);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{st.Count} {st.Size} {st.SizeNow()} {st.Items.Count}"));
        Theme plain = new Theme("black", "white");
        Theme loud;
        Theme spread = plain;
        loud = new Theme(spread.Fg, spread.Bg, 20, true);
        Theme dark;
        Theme spread2 = loud;
        dark = new Theme(plain.Bg, plain.Fg, spread2.Size, spread2.Bold);
        console.Log(plain.ToString() + " | " + loud.ToString() + " | " + dark.ToString());
        Theme mixed;
        Theme spread3 = Picked(dark, "old");
        string arg = spread3.Bg;
        string arg2 = Noted("red");
        bool arg3 = spread3.Bold;
        mixed = new Theme(arg2, arg, 9, arg3);
        console.Log(mixed.ToString());
        Theme ordered;
        Theme spread4 = Picked(plain, "first");
        ordered = new Theme(Noted("second"), Noted("third"), spread4.Size, spread4.Bold);
        console.Log(ordered.ToString());
        Span wide;
        Span spread5 = new Span(2, 5);
        wide = new Span(spread5.Start, 9);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{wide.Start} {wide.End} {wide.Length}"));
        console.Log(Compared((Loose? a, Loose? b) => a == null ? b == null : b == null ? false : a.Eq(b)));
        console.Log(Compared((Loose? a, Loose? b) => !(a == null ? b == null : b == null ? false : a.Eq(b))));
        console.Log(Compared((Loose? a, Loose? b) => b == null ? a == null : a == null ? false : b.Eq(a)));
        bool fn4(Loose? a, Loose? b)
        {
            if (a == null)
            {
                return false;
            }
            if (b == null)
            {
                return false;
            }
            return a.Mirrors(b);
        }
        console.Log(Compared(fn4));
        Type favorite = Same(typeof(long));
        int named = 0;
        {
            int i = 0;
            while (i < 6)
            {
                TypeNamed(i);
                named = named + 1;
                i = i + 1;
            }
        }
        console.Log(named.ToString(CultureInfo.InvariantCulture) + " types");
        Sql sql = new Sql();
        sql.AppendString("it");
        sql.AppendString(null);
        sql.AppendInt(3);
        sql.AppendShape(new Square(2));
        sql.AppendRect(new Rect(1, 2));
        console.Log(sql.Accumulated);
        int seven = 7;
        Sql accumulator = new Sql();
        {
            accumulator.AppendSafe("SELECT ");
            accumulator.AppendInt(seven);
            accumulator.AppendSafe(", ");
            accumulator.AppendString("x");
            accumulator.AppendSafe(" FROM ");
            accumulator.AppendRect(new Rect(2, 2));
        }
        console.Log(accumulator.Accumulated);
        ILimited stepper = new Stepper(3);
        console.Log("next " + stepper.Next(1).ToString(CultureInfo.InvariantCulture));
        console.Log(OrFail(true, 4, new IntFailing()).ToString(CultureInfo.InvariantCulture) + " " + OrFail(true, "s", new AnyFailing<string>()));
    }
}
