using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace Locals;

public static class LocalsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static string Show(IReadOnlyList<int> x)
    {
        IList<string> s = new List<string>();
        s.Add("[");
        string c = "";
        {
            int i = 0;
            while (i < x.Count)
            {
                s.Add(c);
                s.Add(Listed.Get(x, i).ToString(CultureInfo.InvariantCulture));
                c = ", ";
                i = i + 1;
            }
        }
        s.Add("]");
        return Listed.Join(s, "", (string v) => v);
    }

    internal static T NestBlock<T>(Func<T> b)
    {
        return b();
    }

    internal static void NestBlockVoid(Action b)
    {
        b();
    }

    internal static void ConstantCapture(IReadOnlyList<int> x, int a)
    {
        int b = a * 1;
        IReadOnlyList<int> @out = Listed.ToReadOnlyList(Enumerable.Select(x, (int n) => n + b));
        console.Log("constantCapture = " + Show(@out));
    }

    internal static void ConstantCaptureNested(IReadOnlyList<int> x, int a)
    {
        int b = a * 1;
        IReadOnlyList<int> @out = NestBlock(() => Listed.ToReadOnlyList(Enumerable.Select(x, (int n) => n + b)));
        console.Log("constantCaptureNested = " + Show(@out));
    }

    internal static void MutableCapture(IReadOnlyList<int> x)
    {
        int sum = 0;
        int fn(int n)
        {
            sum = sum + n;
            return sum;
        }
        IReadOnlyList<int> @out = Listed.ToReadOnlyList(Enumerable.Select(x, (Func<int, int>)fn));
        console.Log("mutableCapture = " + Show(@out) + "; sum=" + sum.ToString(CultureInfo.InvariantCulture));
    }

    internal static void MutableCaptureNested(IReadOnlyList<int> x)
    {
        int sum = 0;
        IReadOnlyList<int> fn()
        {
            int fn2(int n)
            {
                sum = sum + n;
                return sum;
            }
            return Listed.ToReadOnlyList(Enumerable.Select(x, (Func<int, int>)fn2));
        }
        IReadOnlyList<int> @out = NestBlock((Func<IReadOnlyList<int>>)fn);
        console.Log("mutableCaptureNested = " + Show(@out) + "; sum=" + sum.ToString(CultureInfo.InvariantCulture));
    }

    public static void Thresh(IReadOnlyList<int> values, int value)
    {
        value = value + 1;
        IReadOnlyList<int> result = Listed.ToReadOnlyList(Enumerable.Where(values, (int it) => it > value));
        console.Log("thresh length = " + result.Count.ToString(CultureInfo.InvariantCulture));
    }

    public static void Thresh2(IReadOnlyList<int> values, int value)
    {
        int value2 = value;
        value2 = value2 + 1;
        IReadOnlyList<int> result = Listed.ToReadOnlyList(Enumerable.Where(values, (int it) => it > value2));
        console.Log("thresh2 length = " + result.Count.ToString(CultureInfo.InvariantCulture));
    }

    internal static void SelfRecursion(IReadOnlyList<int> x)
    {
        void mapper(int i, IList<int> @out)
        {
            if (i < x.Count)
            {
                @out.Add(Listed.Get(x, i) * 2);
                mapper(i + 1, @out);
            }
        }
        IList<int> @out2 = new List<int>();
        mapper(0, @out2);
        console.Log("selfRecursion = " + Show(Listed.AsReadOnly(@out2)));
    }

    internal static void SelfRecursionNested(IReadOnlyList<int> x)
    {
        void mapper(int i, IList<int> @out)
        {
            void fn2()
            {
                if (i < x.Count)
                {
                    @out.Add(Listed.Get(x, i) * 2);
                    mapper(i + 1, @out);
                }
            }
            NestBlockVoid(fn2);
        }
        IList<int> @out2 = new List<int>();
        mapper(0, @out2);
        console.Log("selfRecursionNested = " + Show(Listed.AsReadOnly(@out2)));
    }

    internal static void MutualRecursion(IReadOnlyList<int> x)
    {
        void walker(int i, IList<int> @out)
        {
            if (i < x.Count)
            {
                putter(i, @out);
            }
        }
        void putter(int i2, IList<int> @out2)
        {
            @out2.Add(Listed.Get(x, i2) * 2);
            walker(i2 + 1, @out2);
        }
        IList<int> @out3 = new List<int>();
        walker(0, @out3);
        console.Log("mutualRecursion = " + Show(Listed.AsReadOnly(@out3)));
    }

    internal static IList<int> GatherHandlers(Hub<int> hub)
    {
        IList<int> items = new List<int>();
        {
            int i = 0;
            while (i < 3)
            {
                int i2 = i;
                hub.OnAction((int item) => items.Add(item + i2));
                i = i + 1;
            }
        }
        return items;
    }

    static LocalsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Locals");
        ConstantCapture(Listed.CreateReadOnlyList<int>(1, 2, 3), 1);
        ConstantCaptureNested(Listed.CreateReadOnlyList<int>(1, 2, 3), 1);
        MutableCapture(Listed.CreateReadOnlyList<int>(1, 2, 3));
        MutableCaptureNested(Listed.CreateReadOnlyList<int>(1, 2, 3));
        Thresh(Listed.CreateReadOnlyList<int>(4, 5, 6), 4);
        Thresh2(Listed.CreateReadOnlyList<int>(4, 5, 6), 4);
        SelfRecursion(Listed.CreateReadOnlyList<int>(1, 2, 3));
        SelfRecursionNested(Listed.CreateReadOnlyList<int>(1, 2, 3));
        MutualRecursion(Listed.CreateReadOnlyList<int>(1, 2, 3));
        Hub<int> hub = new Hub<int>();
        IList<int> items = GatherHandlers(hub);
        hub.ActionPerformed(1);
    }
}
