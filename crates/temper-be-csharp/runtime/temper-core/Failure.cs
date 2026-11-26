#nullable enable

using System;
using System.Runtime.CompilerServices;

namespace TemperLang.Core
{
    /// <summary>
    /// Marks a Temper library's declared failures (RFC 164): each of a
    /// library's root failure classes, as <c>WherewithException</c>,
    /// extends <see cref="Exception"/> and implements this, and its failure
    /// types extend those. Host code catches those, never this, which
    /// isn't an exception itself; generated code uses it only where it
    /// can't name a failure type. A failure's message is
    /// <see cref="Message"/>, Temper's <c>message</c>, and its cause is
    /// <see cref="InnerException"/>, the failure that was being handled
    /// where it was made and thrown, if any.
    /// </summary>
    public interface IFailure
    {
        /// <summary>What went wrong, for people: Temper's <c>message</c>.</summary>
        string Message { get; }

        /// <summary>
        /// What was being handled where this was made and thrown, as
        /// <see cref="Exception"/> has it.
        /// </summary>
        Exception? InnerException { get; }
    }

    /// <summary>
    /// The root of core's own declared failures, <see cref="MissingKey"/>,
    /// <see cref="ParseError"/>, and <see cref="InvalidCodePoint"/>, as each
    /// library's roots are its failures' (Temper's <c>CoreError</c>).
    /// </summary>
    public abstract class CoreException : Exception, IFailure
    {
        private protected CoreException(Exception? cause)
            : base(null, cause)
        {
        }
    }

    /// <summary>
    /// What a map's <c>get</c> throws for a key it doesn't have, with that
    /// key. (Later perhaps a <see cref="System.Collections.Generic.KeyNotFoundException"/>
    /// too, as C#'s own lookups throw.)
    /// </summary>
    public sealed class MissingKey : CoreException
    {
        private readonly object key;

        public MissingKey(object key)
            : this(key, null)
        {
        }

        public MissingKey(object key, Exception? cause)
            : base(cause)
        {
            this.key = key;
        }

        /// <summary>The key that was missing.</summary>
        public object Key => this.key;

        public override string Message => "missing key (key: " + this.key + ")";
    }

    /// <summary>
    /// What a conversion throws for a value it can't convert: its input, as
    /// text, and why, as <c>not an Int32</c>. (Later perhaps a
    /// <see cref="FormatException"/> too, as C#'s own parsing throws.)
    /// </summary>
    public sealed class ParseError : CoreException
    {
        public ParseError(string input, string reason)
            : this(input, reason, null)
        {
        }

        public ParseError(string input, string reason, Exception? cause)
            : base(cause)
        {
            this.Input = input;
            this.Reason = reason;
        }

        /// <summary>The text, or the number as text.</summary>
        public string Input { get; }

        /// <summary>Why it couldn't be converted.</summary>
        public string Reason { get; }

        public override string Message =>
            "parse error (input: " + this.Input + ", reason: " + this.Reason + ")";
    }

    /// <summary>
    /// What making a string from a code point throws for one that isn't a
    /// Unicode scalar value, with that number. (Later perhaps an
    /// <see cref="ArgumentOutOfRangeException"/> too, as
    /// <c>char.ConvertFromUtf32</c> throws.)
    /// </summary>
    public sealed class InvalidCodePoint : CoreException
    {
        public InvalidCodePoint(int codePoint)
            : this(codePoint, null)
        {
        }

        public InvalidCodePoint(int codePoint, Exception? cause)
            : base(cause)
        {
            this.CodePoint = codePoint;
        }

        /// <summary>The number that isn't a Unicode scalar value.</summary>
        public int CodePoint { get; }

        public override string Message =>
            "invalid code point (codePoint: " + this.CodePoint + ")";
    }

    // Declared failures' causes. C# sets an exception's `InnerException`
    // only through its constructor, so a Temper `throw new X(…)` in a
    // handler calls an internal constructor of X's that takes the cause
    // too, which passes it down to `Exception`'s. A failure made before
    // and thrown in a handler, or another library's, can't take one
    // then: `WithCause` keeps its cause beside it, for Temper's `cause`
    // alone.
    public static partial class Core
    {
        // Causes of failures thrown in a handler after they were made.
        static readonly ConditionalWeakTable<Exception, Exception> lateCauses =
            new ConditionalWeakTable<Exception, Exception>();

        /// <summary>
        /// <paramref name="failure"/>, with <paramref name="cause"/> as its
        /// cause, for a Temper <c>throw</c> in a handler of a failure made
        /// before, or made by another library, whose constructor that
        /// takes a cause is internal, unless it has a cause already: a failure thrown again
        /// keeps the cause it was first thrown with, and never becomes its
        /// own, nor its cause's. Its <see cref="Exception.InnerException"/>
        /// stays as it was; Temper's <c>cause</c> sees this one
        /// (<see cref="Cause"/>).
        /// </summary>
        public static T WithCause<T>(T failure, Exception cause)
            where T : Exception
        {
            if (failure.InnerException != null)
            {
                return failure;
            }
            lock (lateCauses)
            {
                if (lateCauses.TryGetValue(failure, out _))
                {
                    return failure;
                }
                for (Exception? c = cause; c != null; c = Cause(c))
                {
                    if (ReferenceEquals(c, failure))
                    {
                        return failure;
                    }
                }
                lateCauses.Add(failure, cause);
            }
            return failure;
        }

        /// <summary>
        /// What was being handled where <paramref name="failure"/> was
        /// thrown: its inner exception, else a cause it took after it was
        /// made (<see cref="WithCause"/>).
        /// </summary>
        public static Exception? Cause(Exception failure)
        {
            if (failure.InnerException != null)
            {
                return failure.InnerException;
            }
            lock (lateCauses)
            {
                return lateCauses.TryGetValue(failure, out var cause) ? cause : null;
            }
        }

        /// <summary>
        /// A declared failure's cause, as Temper's <c>Error.cause</c> gives
        /// it: the failure that was being handled where it was thrown, or
        /// null, also where that was something else, as a bubble.
        /// </summary>
        public static IFailure? FailureCause(IFailure failure) =>
            failure is Exception e ? Cause(e) as IFailure : null;

        /// <summary>
        /// Throws a declared failure, as an expression: Temper's
        /// <c>throw</c> where C# needs a value.
        /// </summary>
        public static T ThrowFailure<T>(Exception failure) => throw failure;
    }
}
