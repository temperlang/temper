using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;

namespace AsyncTurns;

public static class AsyncTurnsGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly Counter c;
    internal static readonly TaskCompletionSource<int> gateA;
    internal static readonly TaskCompletionSource<int> gateB;

    public static Task<int> Later(int n)
    {
        TaskCompletionSource<int> pb = new TaskCompletionSource<int>();
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            global::TemperLang.Core.Async.CompletePromise(pb, n * 2);
            yield break;
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        return pb.Task;
    }

    internal static IEnumerable<T> Generated<T>(Func<IEnumerable<T>> make)
    {
        return make();
    }

    public static IEnumerable<Tuple<object?>> Ticks(int n, Action<int> each)
    {
        IEnumerable<Tuple<object?>> fnSteps2()
        {
            {
                int i = 0;
                while (i < n)
                {
                    each(i);
                    yield return default!;
                    i = i + 1;
                }
            }
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps2);
        }
        return Generated((Func<IEnumerable<Tuple<object?>>>)fn);
    }

    internal static IEnumerable<T> StartedAt<T>(int start, Func<int, IEnumerable<T>> make)
    {
        return make(start);
    }

    public static IEnumerable<int> Fibonacci()
    {
        IEnumerable<int> fnSteps3()
        {
            int a = 0;
            int b = 1;
            while (true)
            {
                yield return a;
                int next = a + b;
                a = b;
                b = next;
            }
        }
        IGenerator<int> fn()
        {
            return Core.AdaptGenerator<int>(fnSteps3);
        }
        return Generated((Func<IEnumerable<int>>)fn);
    }

    public static IEnumerable<int> Collatz(int start)
    {
        IEnumerable<int> fnSteps4(int first)
        {
            int n = first;
            while (true)
            {
                yield return n;
                if (n == 1)
                {
                    break;
                }
                if (Core.ModSafe(n, 2) == 0)
                {
                    n = Core.DivSafe(n, 2);
                }
                else
                {
                    n = 3 * n + 1;
                }
            }
        }
        IGenerator<int> fn(int first)
        {
            return Core.AdaptGenerator<int>(() => fnSteps4(first));
        }
        return StartedAt(start, (Func<int, IEnumerable<int>>)fn);
    }

    internal static IReadOnlyList<int> Take(IEnumerable<int> g, int n)
    {
        IList<int> taken = new List<int>();
        while (taken.Count < n)
        {
            IGeneratorResult<int> r = Core.GeneratorNext(g);
            if (r is ValueResult<int>)
            {
                taken.Add(((ValueResult<int>)r).Value);
            }
            else
            {
                break;
            }
        }
        return Listed.ToReadOnlyList(taken);
    }

    internal static string Shown(IReadOnlyList<int> ints)
    {
        return Listed.Join(ints, " ", (int i) => i.ToString(CultureInfo.InvariantCulture));
    }

    static AsyncTurnsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("AsyncTurns");
        IEnumerable<Tuple<object?>> t = Ticks(2, (int i) => console.Log("tick " + i.ToString(CultureInfo.InvariantCulture)));
        Core.GeneratorNext(t);
        Core.GeneratorNext(t);
        Core.GeneratorNext(t);
        console.Log("fibonacci " + Shown(Take(Fibonacci(), 10)));
        console.Log("collatz " + Shown(Take(Collatz(6), 100)));
        IEnumerable<int> fib = Fibonacci();
        Take(fib, 5);
        console.Log("then " + Shown(Take(fib, 3)));
        c = new Counter();
        gateA = new TaskCompletionSource<int>();
        gateB = new TaskCompletionSource<int>();
        IEnumerable<Tuple<object?>> fn2Steps1()
        {
            c.Bump("A", 10000);
            int k;
            {
                Task<int> promise1;
                try
                {
                    promise1 = gateA.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                try
                {
                    k = global::TemperLang.Core.Async.GetPromiseResult(promise1);
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH1;
                }
                goto OK1;
                CATCH1: ;
                {
                    k = -1;
                }
                OK1: ;
            }
            c.Bump("A" + k.ToString(CultureInfo.InvariantCulture), 10000);
        }
        IGenerator<Tuple<object?>> fn2()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fn2Steps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn2);
        IEnumerable<Tuple<object?>> fn3Steps1()
        {
            c.Bump("B", 10000);
            int k;
            {
                Task<int> promise2;
                try
                {
                    promise2 = gateB.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH2;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                try
                {
                    k = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH2;
                }
                goto OK2;
                CATCH2: ;
                {
                    k = -1;
                }
                OK2: ;
            }
            c.Bump("B" + k.ToString(CultureInfo.InvariantCulture), 10000);
        }
        IGenerator<Tuple<object?>> fn3()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fn3Steps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn3);
        IEnumerable<Tuple<object?>> fn4Steps1()
        {
            console.Log("opener: calling complete");
            global::TemperLang.Core.Async.CompletePromise(gateA, 1);
            global::TemperLang.Core.Async.CompletePromise(gateB, 2);
            console.Log("opener: complete returned");
            yield break;
        }
        IGenerator<Tuple<object?>> fn4()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fn4Steps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn4);
    }
}
