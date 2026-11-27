// Host code using the generated `AsyncTurns` library the standard way,
// for `functional.rs`'s `runs`: it prints a line per check, which the test
// compares with what it expects. csc compiles it with nullable reference
// types and warnings as errors.

using System;
using System.Collections.Generic;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;
using AsyncTurns;
using TemperLang.Core;
// The tests' assembly has a namespace `Async` too.
using TemperAsync = TemperLang.Core.Async;

static class Interop
{
    static async Task Main()
    {
        Core.InitSimpleLogging();
        TemperAsync.BeginTopLevel();
        try
        {
            // Its top-level code logs, and starts its async blocks.
            RuntimeHelpers.RunClassConstructor(typeof(AsyncTurnsGlobal).TypeHandle);
        }
        finally
        {
            TemperAsync.EndTopLevel();
        }
        TemperAsync.WaitUntilSafeToExit();

        // `await` takes a generated promise: it's a `Task`. Host code
        // resumes outside any turn.
        int value = await AsyncTurnsGlobal.Later(1);
        Console.WriteLine($"await {value}, in a turn {TemperAsync.InTurn}");

        // With a runner of the host's, steps run where it says.
        int steps = 0;
        TemperAsync.SetAsyncRunner(step => Task.Run(() =>
        {
            Interlocked.Increment(ref steps);
            step();
        }));
        try
        {
            value = await AsyncTurnsGlobal.Later(3);
        }
        finally
        {
            TemperAsync.SetAsyncRunner(null);
        }
        Console.WriteLine($"runner {value} {steps > 0}");

        // Blocking inside a turn, before the promise settles, throws.
        TemperAsync.BeginTopLevel();
        try
        {
            TemperAsync.GetPromiseResult(AsyncTurnsGlobal.Later(4));
            Console.WriteLine("in a turn: returned");
        }
        catch (InvalidOperationException e)
        {
            Console.WriteLine($"in a turn: {e.Message.Contains("would deadlock")}");
        }
        finally
        {
            TemperAsync.EndTopLevel();
        }

        // `foreach` steps a generator.
        var seen = new List<int>();
        foreach (var _ in AsyncTurnsGlobal.Ticks(3, seen.Add))
        {
            // Each step reports through the callback.
        }
        Console.WriteLine($"foreach {string.Join(",", seen)}");

        // A generator stepped again after a failed step is done.
        using IEnumerator<Tuple<object?>> g = AsyncTurnsGlobal.Ticks(3, i =>
        {
            if (i == 1)
            {
                throw new ArgumentException("boom");
            }
        }).GetEnumerator();
        g.MoveNext();
        try
        {
            g.MoveNext();
        }
        catch (ArgumentException)
        {
            Console.WriteLine("failed step");
        }
        Console.WriteLine($"then done {!g.MoveNext()}");

        // `foreach` takes a lazy generator's values, and stops early.
        var fib = new List<int>();
        foreach (int n in AsyncTurnsGlobal.Fibonacci())
        {
            if (fib.Count == 8)
            {
                break;
            }
            fib.Add(n);
        }
        Console.WriteLine($"fibonacci {string.Join(",", fib)}");
        var path = new List<int>();
        foreach (int n in AsyncTurnsGlobal.Collatz(7))
        {
            path.Add(n);
        }
        Console.WriteLine($"collatz {string.Join(",", path)}");

        // A declared failure is an exception of its own class.
        try
        {
            Console.WriteLine($"halve returned {AsyncTurnsGlobal.Halve(3)}");
        }
        catch (Odd)
        {
            Console.WriteLine($"halve {AsyncTurnsGlobal.Halve(8)} caught");
        }
        TemperAsync.WaitUntilSafeToExit();
    }
}
