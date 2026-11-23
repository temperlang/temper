using System;
using System.Collections;
using System.Collections.Generic;
using System.Runtime.ExceptionServices;
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
                resolution = new Bubble();
            }
            Exception reason = resolution;
            // Temper semantics allow for multiple resolution where subsequent
            // resolutions are no-ops.
            Settle(() => p.TrySetException(reason));
        }

        public static void CompletePromise<T>(TaskCompletionSource<T> p, T resolution)
        {
            // The note for BreakPromise above applies here too.
            Settle(() => p.TrySetResult(resolution));
        }

        // A task runs its continuations, a host's `await` among them, inline
        // in the call that settles it.  So a settling inside a turn waits
        // until the turn ends, on the same thread and in order, so that host
        // code never runs inside a turn.  Temper code can't tell, since
        // settling only queues waiters.
        [ThreadStatic]
#if NET6_0_OR_GREATER
        private static List<Action>? settling;
#else
        private static List<Action> settling;
#endif

        private static void Settle(Action settle)
        {
            if (!InTurn)
            {
                settle();
                return;
            }
            if (settling == null)
            {
                settling = new List<Action>();
            }
            settling.Add(settle);
        }

        // Runs the settlings deferred by the turn that just ended on this
        // thread, unless it's still in one.
        private static void SettleDeferred()
        {
            while (!InTurn && settling != null)
            {
                var pending = settling;
                settling = null;
                foreach (var settle in pending)
                {
                    settle();
                }
            }
        }

        // Temper runs one async block at a time and switches only at `await`,
        // as the interpreter and JavaScript do (following Temper PR #532, which
        // does this for Python and Java).  Every step of every coroutine, its
        // launch and each resume after an awaited task settles, goes on one
        // FIFO queue that a single `temper-async` thread drains.  A task may
        // settle on any thread; that only queues the waiter, so
        // `CompletePromise` returns before the waiter runs.
        //
        // `Turn` is held while a step runs.  The generated `Main` holds it
        // while module initialization runs top-level code (`BeginTopLevel`),
        // so that no async block starts until that code is done.
        //
        // Generated code calls only the entry points (LaunchGeneratorAsync,
        // AwakeUpon, and BeginTopLevel and EndTopLevel), never the queue, so
        // the queue stays swappable: one queue per isolate or actor, many on
        // a thread pool, would make today's single queue the one-isolate case.
        private static readonly object Turn = new object();
        private static readonly Queue<Action> Ready = new Queue<Action>();
#if NET6_0_OR_GREATER
        private static Thread? runnerThread;
#else
        private static Thread runnerThread;
#endif

#if NET6_0_OR_GREATER
        private static Action<Action>? runner;
#else
        private static Action<Action> runner;
#endif

        /// <summary>
        /// Runs later async steps with <paramref name="stepRunner"/>, which
        /// gets each step and runs it, on any thread, in the order given, or
        /// with the default <c>temper-async</c> thread when null.  Each step
        /// still takes the turn, so steps never overlap.
        /// </summary>
#if NET6_0_OR_GREATER
        public static void SetAsyncRunner(Action<Action>? stepRunner)
#else
        public static void SetAsyncRunner(Action<Action> stepRunner)
#endif
        {
            runner = stepRunner;
        }

        private static void Schedule(Action step)
        {
            AddBusy(1);
            var custom = runner;
            if (custom != null)
            {
                custom(() =>
                {
                    try
                    {
                        lock (Turn)
                        {
                            step();
                        }
                        SettleDeferred();
                    }
                    finally
                    {
                        AddBusy(-1);
                    }
                });
                return;
            }
            lock (Ready)
            {
                Ready.Enqueue(step);
                if (runnerThread == null)
                {
                    runnerThread = new Thread(RunSteps);
                    runnerThread.Name = "temper-async";
                    runnerThread.IsBackground = true;
                    runnerThread.Start();
                }
                Monitor.Pulse(Ready);
            }
        }

        private static void RunSteps()
        {
            while (true)
            {
                Action step;
                lock (Ready)
                {
                    while (Ready.Count == 0)
                    {
                        Monitor.Wait(Ready);
                    }
                    step = Ready.Dequeue();
                }
                try
                {
                    lock (Turn)
                    {
                        step();
                    }
                    SettleDeferred();
                }
                finally
                {
                    // After settling, which queued what it woke.
                    AddBusy(-1);
                }
            }
        }

        // Steps queued or running and I/O in flight, which FinishAsyncWork
        // waits for, and the first failure that escaped a step since.
        private static readonly object Busy = new object();
        private static int busyCount = 0;
#if NET6_0_OR_GREATER
        private static Exception? escaped;
#else
        private static Exception escaped;
#endif

        private static void AddBusy(int n)
        {
            lock (Busy)
            {
                busyCount += n;
                if (busyCount == 0)
                {
                    Monitor.PulseAll(Busy);
                }
            }
        }

        /// <summary>
        /// Counts <paramref name="task"/> as I/O in flight, as std/net's
        /// requests, until it settles, so <see cref="FinishAsyncWork"/>
        /// waits for it.  The task returned settles with it, its waiters
        /// queued before the count drops.
        /// </summary>
        public static Task<T> TrackIo<T>(Task<T> task)
        {
            AddBusy(1);
            var tracked = new TaskCompletionSource<T>();
            task.ContinueWith(t =>
            {
                try
                {
                    if (t.IsCanceled)
                    {
                        tracked.TrySetCanceled();
                    }
                    else if (t.Exception != null)
                    {
                        tracked.TrySetException(t.Exception.InnerExceptions);
                    }
                    else
                    {
                        tracked.TrySetResult(t.Result);
                    }
                }
                finally
                {
                    AddBusy(-1);
                }
            }, TaskContinuationOptions.ExecuteSynchronously);
            return tracked.Task;
        }

        /// <summary>
        /// Called from generated tests after each test's body: waits until
        /// no async step is queued or running and no I/O is in flight, then
        /// throws the first failure that escaped a step meanwhile, so the
        /// test fails with it.  Blocks still awaiting a task that nothing
        /// settles stay waiting.
        /// </summary>
        public static void FinishAsyncWork()
        {
            if (MustNotBlock)
            {
                throw new InvalidOperationException(
                    "FinishAsyncWork inside an async turn would deadlock");
            }
#if NET6_0_OR_GREATER
            Exception? failure;
#else
            Exception failure;
#endif
            lock (Busy)
            {
                while (busyCount != 0)
                {
                    Monitor.Wait(Busy);
                }
                failure = escaped;
                escaped = null;
            }
            if (failure != null)
            {
                ExceptionDispatchInfo.Capture(failure).Throw();
            }
        }

        /// <summary>
        /// Called from a main method before it runs module top-level code.
        /// Async blocks launched by that code wait for <see cref="EndTopLevel"/>.
        /// </summary>
        public static void BeginTopLevel()
        {
            Monitor.Enter(Turn);
        }

        /// <summary>Lets async blocks run after <see cref="BeginTopLevel"/>.</summary>
        public static void EndTopLevel()
        {
            Monitor.Exit(Turn);
            SettleDeferred();
        }

        /// <summary>Whether this thread is in a turn: a step, or top-level code.</summary>
        public static bool InTurn => Monitor.IsEntered(Turn);

        // Whether blocking here could keep the runner from settling what's
        // awaited: in a turn, or on the runner's own thread, where host code
        // resumes after awaiting a promise settled there.
        private static bool MustNotBlock =>
            InTurn || Thread.CurrentThread == runnerThread;

        /// <summary>
        /// The task's result, waiting for it if need be.  Waiting inside a
        /// turn for a task not yet settled throws rather than deadlocking,
        /// since what would settle it may need the turn. A broken promise
        /// bubbles.
        /// </summary>
        public static T GetPromiseResult<T>(Task<T> task)
        {
            if (!task.IsCompleted && MustNotBlock)
            {
                throw new InvalidOperationException(
                    "blocking on an unsettled Temper promise inside an async turn"
                        + " would deadlock; await it instead");
            }
            try
            {
                return task.GetAwaiter().GetResult();
            }
            catch (Exception e) when (e is Bubble || e is Panic)
            {
                throw;
            }
            catch (Exception e)
            {
                // A broken promise bubbles, whatever broke it but a panic.
                throw new Bubble(e.Message, e);
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
            Schedule(() => OneStep(enumerator));
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
                            // Runs on whichever thread settles the task, or
                            // at once if it has; either way, the resume
                            // waits its turn on the runner.
                            task.ContinueWith((_) =>
                            {
                                Schedule(() => OneStep(awaiter));
                            }, TaskContinuationOptions.ExecuteSynchronously);
                            continuing = true; // The continuation is responsible for decrementing
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                // One failing block doesn't stop the others.  The failure
                // is kept for FinishAsyncWork, or for WaitUntilSafeToExit,
                // which ends a program with it, and reported on stderr,
                // never in what the program logs.
                lock (Busy)
                {
                    if (escaped == null)
                    {
                        escaped = ex;
                    }
                }
                Console.Error.WriteLine(ex.ToString());
                Console.Error.WriteLine(new System.Diagnostics.StackTrace().ToString());
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
            if (MustNotBlock)
            {
                throw new InvalidOperationException(
                    "WaitUntilSafeToExit inside an async turn would deadlock");
            }
            allPromisesResolved.WaitOne();
            // A failure that escaped a block, a panic or a host's error,
            // ends the program with it.
#if NET6_0_OR_GREATER
            Exception? failure;
#else
            Exception failure;
#endif
            lock (Busy)
            {
                failure = escaped;
                escaped = null;
            }
            if (failure != null)
            {
                ExceptionDispatchInfo.Capture(failure).Throw();
            }
        }
    }
}
