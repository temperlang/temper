using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;

namespace Spreads;

public static class SpreadsGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly Box a;
    internal static readonly TaskCompletionSource<int> gate;

    internal static Box Grown(Box b)
    {
        {
            Box spread = b;
            return new Box(b.W + 1, spread.H, spread.Label);
        }
    }

    internal static int AreaOf(ISized s)
    {
        return s.Area();
    }

    internal static int Said(string s, int n)
    {
        console.Log(s);
        return n;
    }

    internal static string Both(int x, Box b)
    {
        return x.ToString(CultureInfo.InvariantCulture) + " " + b.ToString();
    }

    internal static int Widths(Box b, int n)
    {
        Box t = b;
        {
            int i = 0;
            while (i < n)
            {
                Box spread = t;
                t = new Box(t.H, t.W + 1, spread.Label);
                i = i + 1;
            }
        }
        while (true)
        {
            Box spread1 = t;
            if (new Box(spread1.W, 1, spread1.Label).Area() >= 9)
            {
                break;
            }
            Box spread2 = t;
            t = new Box(t.W + 1, spread2.H, spread2.Label);
        }
        {
            int i = 0;
            while (i < 2)
            {
                Box spread = t;
                int arg = Said("h", t.W);
                int arg2 = Said("w", t.H);
                string? arg3 = spread.Label;
                t = new Box(arg2, arg, arg3);
                i = i + 1;
            }
        }
        if (t.W > 0)
        {
            Box b2;
            Box spread = b;
            b2 = new Box(spread.W, spread.H, "inner");
            console.Log(b2.ToString());
        }
        return t.W;
    }

    internal static Two<T> Swapped<T>(Two<T> t)
    {
        {
            Two<T> spread = t;
            return new Two<T>(t.Second, t.First);
        }
    }

    internal static IShape Doubled(IShape x)
    {
        IShape made;
        IShape shape;
        if (x is Circle)
        {
            Circle spread = (Circle)x;
            shape = new Circle(((Circle)x).R * 2);
        }
        else
        {
            Square square;
            if (x is Square)
            {
                Square spread = (Square)x;
                square = new Square(((Square)x).Side * 2);
            }
            else
            {
                Environment.FailFast("panic");
                throw new InvalidOperationException();
            }
            shape = square;
        }
        made = shape;
        return made;
    }

    internal static int Size(IShape x)
    {
        int n;
        int int32;
        if (x is Circle)
        {
            int32 = ((Circle)x).R;
        }
        else
        {
            int value;
            if (x is Square)
            {
                value = ((Square)x).Side;
            }
            else
            {
                Environment.FailFast("panic");
                throw new InvalidOperationException();
            }
            int32 = value;
        }
        n = int32;
        return n;
    }

    internal static Box Picked(bool c)
    {
        if (c)
        {
            {
                Box spread = a;
                return new Box(9, spread.H, spread.Label);
            }
        }
        else
        {
            return a;
        }
    }

    internal static Box Applied(Func<int, Box> f)
    {
        return f(4);
    }

    internal static int Positive(int n)
    {
        if (n < 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    internal static Box Sized(Box b, int n)
    {
        {
            Box spread = b;
            return new Box(Positive(n), spread.H, spread.Label);
        }
    }

    static SpreadsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Spreads");
        a = new Box(2, 3);
        Box b = Grown(a);
        console.Log(a.ToString() + " " + b.ToString() + " " + AreaOf(b).ToString(CultureInfo.InvariantCulture));
        Box spread10 = b;
        console.Log(new Box(spread10.W, spread10.H, "named").ToString());
        bool wide;
        bool boolean = a.W > 1;
        if (boolean)
        {
            Box spread21 = a;
            boolean = new Box(10, spread21.H, spread21.Label).Area() > 20;
        }
        wide = boolean;
        Two<int> p = new Two<int>(1, 2);
        Two<int> q;
        Two<int> spread3 = p;
        q = new Two<int>(spread3.First, 5);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{(wide ? "true" : "false")} {q.First} {q.Second}"));
        Box nested;
        Box spread4;
        Box spread5 = a;
        spread4 = new Box(7, spread5.H, spread5.Label);
        nested = new Box(spread4.W, 8, spread4.Label);
        int said = Said("one", 1);
        Box spread6 = a;
        console.Log(Both(said, new Box(spread6.W, Said("two", 2), spread6.Label)));
        string @string = nested.ToString() + " ";
        bool value1 = a.W > 5;
        if (!value1)
        {
            Box spread7 = a;
            value1 = new Box(Said("three", 3), spread7.H, spread7.Label).W == 3;
        }
        console.Log(@string + (value1 ? "true" : "false"));
        console.Log(Widths(a, 3).ToString(CultureInfo.InvariantCulture));
        Step s = new Step(1, "s").Next().Next();
        Two<int> same;
        Two<int> spread8 = q;
        same = new Two<int>(spread8.First, spread8.Second);
        Two<string> words = new Two<string>("a", "b");
        Two<string> flipped = Swapped(words);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{s.N} {s.Tag} {same.First}{same.Second} {flipped.First}{flipped.Second}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Size(Doubled(new Circle(3)))} {Size(Doubled(new Square(5)))}"));
        Box fn(int n)
        {
            {
                Box spread = a;
                return new Box(spread.W, n, spread.Label);
            }
        }
        Box made = Applied(fn);
        console.Log(Picked(true).ToString() + " " + Picked(false).ToString() + " " + made.ToString());
        Box caught;
        Box spread9 = a;
        int t1;
        try
        {
            t1 = Positive(-1);
        }
        catch
        {
            t1 = 0;
        }
        int t2;
        try
        {
            t2 = Positive(5);
        }
        catch
        {
            t2 = -1;
        }
        caught = new Box(t1, t2, spread9.Label);
        string @string2 = caught.ToString() + " ";
        string t3;
        try
        {
            t3 = Sized(a, 4).ToString();
        }
        catch
        {
            t3 = "-";
        }
        console.Log(@string2 + t3);
        string t4;
        try
        {
            Sized(a, -1);
            t4 = "sized";
        }
        catch
        {
            t4 = "bubbled";
        }
        console.Log(t4);
        gate = new TaskCompletionSource<int>();
        IEnumerable<Tuple<object?>> fn2Steps1()
        {
            Box later;
            Box spread = a;
            int arg = spread.W;
            int arg2;
            {
                Task<int> promise1;
                try
                {
                    promise1 = gate.Task;
                }
                catch
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                try
                {
                    arg2 = promise1.Result;
                }
                catch
                {
                    goto CATCH1;
                }
                goto OK1;
                CATCH1: ;
                {
                    arg2 = -1;
                }
                OK1: ;
            }
            later = new Box(arg, arg2, "later");
            console.Log(later.ToString());
        }
        IGenerator<Tuple<object?>> fn2()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fn2Steps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn2);
        global::TemperLang.Core.Async.CompletePromise(gate, 6);
    }
}
