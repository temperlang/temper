// Host code using the generated `ErrorsInterop` library's declared
// errors, and `ErrorCauses`' made among a thrown one's arguments,
// the standard way, for `functional.rs`'s `runs`: it catches
// them by their own types, the library's base, and a sealed interface's
// abstract class, reads their properties, messages, and inner
// exceptions, and lets a panic pass a handler of the library's errors.
// It prints a line per check, which the test compares with what it
// expects. csc compiles it with nullable reference types and warnings as
// errors.

using System;
using System.Runtime.CompilerServices;
using ErrorsInterop;
using TemperLang.Core;
// The tests' assembly has a namespace `Async` too.
using TemperAsync = TemperLang.Core.Async;
// Its other names are `ErrorsInterop`'s too.
using ErrorCausesGlobal = ErrorCauses.ErrorCausesGlobal;
using Retry = ErrorCauses.Retry;
using Wrapped = ErrorCauses.Wrapped;

static class ErrorsHost
{
    static void Main()
    {
        Core.InitSimpleLogging();
        TemperAsync.BeginTopLevel();
        try
        {
            // Their top-level code logs.
            RuntimeHelpers.RunClassConstructor(typeof(ErrorsInteropGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(ErrorCausesGlobal).TypeHandle);
        }
        finally
        {
            TemperAsync.EndTopLevel();
        }

        try
        {
            ErrorsInteropGlobal.Lookup("z");
        }
        catch (NotFound e)
        {
            Console.WriteLine($"NotFound {e.Key}: {e.Message}");
        }

        try
        {
            ErrorsInteropGlobal.Lookup("y");
        }
        catch (ErrorsInteropException e)
        {
            Exception failure = e;
            Console.WriteLine($"base {failure is NotFound}, an Exception {failure.GetType().BaseType?.BaseType == typeof(Exception)}");
        }

        foreach (string text in new[] { "x", "" })
        {
            try
            {
                ErrorsInteropGlobal.Sign(text);
            }
            catch (SignException e)
            {
                Console.WriteLine($"sign {e.GetType().Name}: {e.Message}");
                if (e is BadSign bad && bad.Text != "x")
                {
                    throw new InvalidOperationException("text");
                }
            }
        }

        try
        {
            ErrorsInteropGlobal.Setting("port");
        }
        catch (LookupFailed e)
        {
            Exception? cause = e.InnerException;
            Console.WriteLine($"LookupFailed {e.Message}, inner {cause?.GetType().Name} {cause?.Message}");
        }

        // Only the error a `throw` in a handler makes takes the cause,
        // not one of its type made among its arguments; a constructor's
        // own parameter named `cause` doesn't get in the way.
        try
        {
            ErrorCausesGlobal.WrapLookup("w");
        }
        catch (Wrapped e)
        {
            Console.WriteLine($"wrapped {e.Label}, inner {e.InnerException?.GetType().Name}; {e.First?.Label}, inner {e.First?.InnerException?.GetType().Name ?? "none"}");
        }

        try
        {
            ErrorCausesGlobal.Retried("r", 2);
        }
        catch (Retry e)
        {
            Console.WriteLine($"{e.Reason} {e.Tries}, inner {e.InnerException?.Message}; made by host {new Retry("h").Tries} {new Retry("h", 4).InnerException?.Message ?? "none"}");
        }

        // Made before it was thrown in a handler, it has no inner
        // exception: C# sets one only where an exception is made.
        try
        {
            ErrorsInteropGlobal.MadeBefore("early");
        }
        catch (LookupFailed e)
        {
            Console.WriteLine($"made before {e.Message}, inner {e.InnerException?.Message ?? "none"}");
        }

        try
        {
            ErrorsInteropGlobal.ThrownAgain("first");
        }
        catch (LookupFailed e)
        {
            Console.WriteLine($"thrown again {e.Message}, inner {e.InnerException?.Message}");
        }

        // An error has its stack trace, from where it was thrown.
        try
        {
            ErrorsInteropGlobal.Lookup("w");
        }
        catch (NotFound e)
        {
            string top = (e.StackTrace ?? "").Split('\n')[0].Trim();
            Console.WriteLine($"trace {top.Split('(')[0]}");
        }

        // A panic is no error: it passes a handler of the library's
        // errors, which is all host code needs to catch.
        try
        {
            try
            {
                ErrorsInteropGlobal.Risky(2);
            }
            catch (ErrorsInteropException)
            {
                Console.WriteLine("panic caught");
            }
        }
        catch (Exception e)
        {
            Console.WriteLine($"panic passes, an error {e is ErrorsInteropException}, {e.GetType().Name}");
        }
    }
}
