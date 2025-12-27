using System;
using System.Collections;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

#if NET6_0_OR_GREATER
#nullable enable
#endif

namespace TemperLang.Core
{
    public class Async
    {
#if NET6_0_OR_GREATER
        public static void BreakPromise<T>(TaskCompletionSource<T> p, Exception? resolution = null)
#else
        public static void BreakPromise<T>(TaskCompletionSource<T> p, Exception resolution = null)
#endif
        {
            if (resolution == null)
            {
                resolution = new Exception();
            }
            try
            {
                p.SetException(resolution);
            }
            catch (InvalidOperationException)
            {
                // Temper semantics allow for multiple resolution where subsequent
                // resolutions are no-ops.
            }
        }

        public static void CompletePromise<T>(TaskCompletionSource<T> p, T resolution)
        {
            try
            {
                p.SetResult(resolution);
            }
            catch (InvalidOperationException)
            {
                // The note for breakPromise above applies here too.
            }
        }

#if NET6_0_OR_GREATER
        public static void LaunchGeneratorAsync(Func<IEnumerable<object?>> e)
#else
        public static void LaunchGeneratorAsync(Func<IEnumerable<object>> e)
#endif
        {
#if NET6_0_OR_GREATER
            IEnumerator<object?> enumerator = e().GetEnumerator();
#else
            IEnumerator<object> enumerator = e().GetEnumerator();
#endif
            IncrementUnresolved();
            Task.Run(() =>
            {
                OneStep(enumerator);
            });
        }

        private class AwaitResult
        {
            public readonly Task Task;
            public AwaitResult(Task task)
            {
                this.Task = task;
            }
        }

#if NET6_0_OR_GREATER
        public static System.Tuple<object?> AwakeUpon<T>(Task<T> task)
        {
            return new System.Tuple<object?>(new AwaitResult((Task)task));
        }
#else
        public static System.Tuple<object> AwakeUpon<T>(Task<T> task)
        {
            return new System.Tuple<object>(new AwaitResult((Task)task));
        }
#endif

#if NET6_0_OR_GREATER
        private static void OneStep(IEnumerator<object?> awaiter)
#else
        private static void OneStep(IEnumerator<object> awaiter)
#endif
        {
            bool continuing = false; // Is responsibility to decrement unresolved handed off?
            try
            {
                if (awaiter.MoveNext())
                {
#if NET6_0_OR_GREATER
                    object? yielded = awaiter.Current;
                    bool isOneTuple = yielded != null && yielded.GetType() == typeof(System.Tuple<object?>);
#else
                    object yielded = awaiter.Current;
                    bool isOneTuple = yielded != null && yielded.GetType() == typeof(System.Tuple<object>);
#endif
                    if (isOneTuple)
                    {
#if NET6_0_OR_GREATER
                        object? tupleContent = ((System.Tuple<object?>)yielded).Item1;
#else
                        object tupleContent = ((System.Tuple<object>)yielded).Item1;
#endif
                        if (tupleContent != null && tupleContent.GetType() == typeof(AwaitResult))
                        {
                            Task task = ((AwaitResult)tupleContent).Task;
                            task.ContinueWith((_) =>
                            {
                                OneStep(awaiter);
                            });
                            continuing = true; // The continuation is responsible for decrementing
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.ToString());
                Console.WriteLine(new System.Diagnostics.StackTrace().ToString());
            }
            finally
            {
                if (!continuing)
                {
                    DecrementUnresolved();
                }
            }
        }

        // A count of pending promises
#if NET6_0_OR_GREATER
        private static volatile uint unresolvedCount = 0;
#else
        // Apparently no direct uint support on older dotnet.
        private static volatile int unresolvedCount = 0;
#endif
        private static void IncrementUnresolved()
        {
            allPromisesResolved.Reset();
            Interlocked.Increment(ref unresolvedCount);
        }
        private static void DecrementUnresolved()
        {
            // TODO Protect against subzero?
            long after = Interlocked.Decrement(ref unresolvedCount);
            if (after == 0)
            {
                allPromisesResolved.Set();
            }
        }

        // Signalled when the unresolved count goes to zero.
        private static ManualResetEvent allPromisesResolved = new ManualResetEvent(true);
        public static void WaitUntilSafeToExit()
        {
            allPromisesResolved.WaitOne();
        }
    }
}
