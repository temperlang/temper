using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using C = System.Collections;

namespace TemperLang.Core
{
    // Only put things here if they have no or few things to group with.
    public static partial class Core
    {
        public static IGenerator<T> AdaptGenerator<T>(Func<IEnumerable<T>> makeEnumerable)
        {
            return new EnumerableWrapper<T>(makeEnumerable());
        }

        public static IGeneratorResult<T> GeneratorNext<T>(IEnumerable<T> generator)
        {
            // The `IEnumerable`s returned by `yield return`ing methods
            // are their own IEnumerators so this should be the same object.
            // Cast instead of calling GetEnumerator() to avoid any reset.
            IEnumerator<T> enumerator = generator.GetEnumerator();
            bool hasNext = enumerator.MoveNext();
            if (hasNext)
            {
                return new ValueResult<T>(enumerator.Current);
            }
            else
            {
                return DoneResult<T>.Singleton;
            }
        }

        /// <summary>
        /// Runs <paramref name="block"/>, giving whether it panicked, for
        /// std's <c>Test.assertPanics</c>: whether it threw anything but a
        /// declared failure (an <see cref="IFailure"/>) or a test
        /// framework's failure (MSTest's, NUnit's, or xUnit's), as on every
        /// backend, so a <see cref="Panic"/> or a host's exception. What
        /// isn't passes through.
        /// </summary>
        public static bool BlockPanics(Action block)
        {
            try
            {
                block();
            }
            catch (Exception e) when (!(e is IFailure) && !IsTestFailure(e))
            {
                return true;
            }
            return false;
        }

        static readonly string[] testFrameworks =
        {
            "Microsoft.VisualStudio.TestTools.UnitTesting", "NUnit.Framework", "Xunit",
        };

        /// <summary>Whether <paramref name="e"/> is a test framework's failure, by its type's namespace.</summary>
        static bool IsTestFailure(Exception e)
        {
            for (var type = e.GetType(); type != null; type = type.BaseType)
            {
                var ns = type.Namespace ?? "";
                foreach (var framework in testFrameworks)
                {
                    if (ns == framework || ns.StartsWith(framework + "."))
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        public static bool BitGet(this BitArray bits, int index) =>
            index >= bits.Length ? false : bits[index];

        public static void BitSet(this BitArray bits, int index, bool value)
        {
            if (index >= bits.Length)
            {
                if (!value)
                {
                    // Outside storage is already considered false.
                    return;
                }
                // Not sure if it matters here, but use 1.5x per FB Folly.
                // https://github.com/facebook/folly/blob/88e79a202aef64ea747bb836089a5a924c4e8d71/folly/docs/FBVector.md#memory-handling
                // Meanwhile, exponential growth here means that it's hard for
                // people who get the result to predict what the size will be,
                // but this seems better than inventing a custom type, and we
                // can at least promise that any unspecified bits are false.
                bits.Length = Math.Max(3 * bits.Length / 2, index + 1);
            }
            bits[index] = value;
        }

        /// <summary>
        /// A Temper panic, as core's docs say an operation panics, and as
        /// generated code's <c>panic()</c> throws: callers <c>throw</c>
        /// it, telling C# nothing after runs.
        /// </summary>
        public static Exception Panic(string message) => new Panic(message);

        /// <summary>
        /// A panic with <paramref name="cause"/> as its inner exception,
        /// as <c>orelse panic()</c> throws in place of the failure.
        /// </summary>
        public static Exception Panic(string message, Exception cause) =>
            new Panic(message, cause);

        internal static Exception OutOfBounds(int index, int length) =>
            Panic(FormattableString.Invariant(
                $"index {index} out of bounds for length {length}"
            ));

        /// <summary>
        /// A panic where C# needs a value, as code with an error throws.
        /// </summary>
        public static T ThrowPanic<T>(string message) => throw new Panic(message);

        /// <summary>Temper's <c>as</c>: panics if item isn't a T.</summary>
        public static T Cast<T>(object item) => item is T t ? t : throw new Panic("failed cast");

        /// <summary>
        /// Temper's <c>as</c> from a nullable type: panics if item is null
        /// or isn't a T.
        /// </summary>
        public static T CastToNonNull<T>(object item) => Cast<T>(item);

        internal static int Clamp(this int i, int min, int max)
        {
            return Math.Max(min, Math.Min(i, max));
        }

        /// <summary>
        /// Temper's <c>/</c>: C#'s, but for <c>MinValue / -1</c>, which wraps
        /// where C# would throw. A zero divisor throws C#'s
        /// <c>DivideByZeroException</c>, a panic.
        /// </summary>
        public static int Div(this int a, int b)
        {
            return a == int.MinValue && b == -1 ? int.MinValue : a / b;
        }

        /// <summary>
        /// Temper's <c>/</c>: C#'s, but for <c>MinValue / -1</c>, which wraps
        /// where C# would throw. A zero divisor throws C#'s
        /// <c>DivideByZeroException</c>, a panic.
        /// </summary>
        public static long Div(this long a, long b)
        {
            return a == long.MinValue && b == -1 ? long.MinValue : a / b;
        }

        public static int UShr(this int a, int b)
        {
            return (int)((uint)a >> (b & 0x1f));
        }

        public static long UShr(this long a, int b)
        {
            return (long)((ulong)a >> (b & 0x3f));
        }

        private const string DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";
        /** Like System.Convert.ToString but outputs the signed form. */
        public static string ConvertToString(int n, int radix)
        {
            StringBuilder sb = new StringBuilder();
            uint un;
            bool signed = false;
            if (n < 0)
            {
                signed = true;
                un = ((uint)~n) + 1;
            }
            else
            {
                un = (uint)n;
            }
            uint uRadix = (uint)radix;
            // do...while means for 0 we get "0"
            do
            {
                int digitValue = (int)(un % uRadix);
                un = un / uRadix;
                sb.Append(DIGITS[digitValue]);
            }
            while (un != 0);

            if (signed)
            {
                sb.Append("-");
            }
            ReverseInPlace(sb);
            return sb.ToString();
        }
        public static string ConvertToString(int n)
        {
            return n.ToString();
        }
        /** Like System.Convert.ToString but outputs the signed form. */
        public static string ConvertToString(long n, int radix)
        {
            StringBuilder sb = new StringBuilder();
            ulong un;
            bool signed = false;
            if (n < 0)
            {
                signed = true;
                un = ((ulong)~n) + 1;
            }
            else
            {
                un = (ulong)n;
            }
            uint uRadix = (uint)radix;
            // do...while means for 0 we get "0"
            do
            {
                int digitValue = (int)(un % uRadix);
                un = un / uRadix;
                sb.Append(DIGITS[digitValue]);
            }
            while (un != 0);

            if (signed)
            {
                sb.Append("-");
            }
            ReverseInPlace(sb);
            return sb.ToString();
        }
        public static string ConvertToString(long n)
        {
            return n.ToString();
        }

        private static void ReverseInPlace(StringBuilder sb)
        {
            int n = sb.Length;
            int i = 0;
            int j = n - 1;
            while (i < j)
            {
                char ci = sb[i];
                sb[i] = sb[j];
                sb[j] = ci;
                ++i;
                --j;
            }
        }

        private static System.Tuple<object?> emptySingleton = new System.Tuple<object?>(null);

        public static System.Tuple<object?> Empty()
        {
            return emptySingleton;
        }

        public static void Ignore<T>(this T item) { }

        public static void InitSimpleLogging()
        {
            Console.OutputEncoding = Encoding.UTF8;
            Trace.Listeners.Add(new TextWriterTraceListener(Console.Out));
            Trace.AutoFlush = true;
        }

        /// <summary>
        /// Easily labels an expression as garbage. Garbage comes from bad
        /// Temper code that doesn't easily translate. Typically won't compile
        /// in C#, which is ok. If it does compile, it throws, which is good.
        /// </summary>
        public static void Garbage(string message) => throw new Exception(message);

        public static void Garbage() => throw new Exception();

        /// <summary>Temper's <c>%</c>, as <see cref="Div(int, int)"/>.</summary>
        public static int Mod(this int a, int b)
        {
            return a == int.MinValue && b == -1 ? 0 : a % b;
        }

        /// <summary>Temper's <c>%</c>, as <see cref="Div(int, int)"/>.</summary>
        public static long Mod(this long a, long b)
        {
            return a == long.MinValue && b == -1 ? 0L : a % b;
        }

        public static void PureVirtual() => throw new NotSupportedException();

        public static T PureVirtual<T>() => throw new NotSupportedException();

        public static TValue RemoveGet<TKey, TValue>(
            this IDictionary<TKey, TValue> dictionary,
            TKey key
        )
        {
            TValue value;
#if NETCOREAPP2_0_OR_GREATER
            var found = dictionary.Remove(key, out value);
#else
            var found = dictionary.TryGetValue(key, out value);
#endif
            if (!found)
            {
                throw new MissingKey(key!);
            }
#if !NETCOREAPP2_0_OR_GREATER
            dictionary.Remove(key);
#endif
            return value;
        }

        public static IReadOnlyList<string> Split(string text, string separator)
        {
            if (separator.Length == 0)
            {
                // Convert each code point into a separate string.
                var result = new List<string>();
                int index = 0;
                while (index < text.Length)
                {
                    int code = char.ConvertToUtf32(text, index);
                    var length = code >= 0x1_0000 ? 2 : 1;
                    result.Add(text.Substring(index, length));
                    index += length;
                }
                return result.AsReadOnly();
            }
            else
            {
                // Just split by separator.
                return text.Split(new[] { separator }, StringSplitOptions.None);
            }
        }

        public static string StringFromCodePoint(int codePoint)
        {
            StringUtil.RequireScalarValue(codePoint);
            return char.ConvertFromUtf32(codePoint);
        }

        public static string StringFromCodePoints(List<int> codePoints)
        {
            return StringFromCodePoints((IReadOnlyList<int>)codePoints);
        }

        public static string StringFromCodePoints(IList<int> codePoints)
        {
            return StringFromCodePoints((IReadOnlyList<int>)codePoints);
        }

        public static string StringFromCodePoints(IReadOnlyList<int> codePoints)
        {
            return string.Concat(codePoints.Select(StringFromCodePoint));
        }

        /// <summary>The value as an int, or a ParseError out of range.</summary>
        public static int ToInt(this long i64)
        {
            if (i64 < int.MinValue || i64 > int.MaxValue)
            {
                throw new ParseError(i64.ToString(System.Globalization.CultureInfo.InvariantCulture), "out of Int32 range");
            }
            return (int)i64;
        }

        /// <summary>The int the text spells in the radix, or a ParseError.</summary>
        public static int ToInt(this string text, int radix = 10)
        {
            // Just reuse int64 parsing for convenience.
            long? i64 = ParseInt64(text, radix);
            if (i64 is not long n || n < int.MinValue || n > int.MaxValue)
            {
                throw new ParseError(text, "not an Int32");
            }
            return (int)n;
        }

        /// <summary>The long the text spells in the radix, or a ParseError.</summary>
        public static long ToInt64(this string text, int radix = 10) =>
            ParseInt64(text, radix) ?? throw new ParseError(text, "not an Int64");

        /// <summary>The long the text spells in the radix, or null.</summary>
        private static long? ParseInt64(string text, int radix)
        {
            // Null for whatever isn't an Int64 in the radix.
            if (radix < 2 || radix > 36)
            {
                return null;
            }
            if (text == "")
            {
                return null;
            }
            // Parse manually because Convert.ToInt[N] only supports bases 2, 8, 10, & 16.
            long sum = 0;
            long sign = 1;
            // Skip leading whitespace.
            var index = 0;
            while (index < text.Length)
            {
                var c = text[index];
                if (!char.IsWhiteSpace(c))
                {
                    break;
                }
                index += 1;
            }
            // Parse negative.
            if (index < text.Length && text[index] == '-')
            {
                sign = -1;
                index += 1;
            }
            // Parse digits.
            while (index < text.Length)
            {
                // Invariant, since culture can map I to dotless ı.
                var c = char.ToLowerInvariant(text[index]);
                if (char.IsWhiteSpace(c))
                {
                    break;
                }
                index += 1;
                // Add next digit.
                var digit = c >= 'a' ? c - 'a' + 10 : c - '0';
                if (digit < 0 || digit >= radix || (c < 'a' && digit > 9))
                {
                    return null;
                }
                // Check for overflow before adding.
                if (sign > 0)
                {
                    // Pretend max 15, text "16", radix 10.
                    // 1 > (15 - 6) / 10 -> 1 > 9 / 10 -> 1 > 0 -> true
                    // Now text "15".
                    // 1 > (15 - 5) / 10 -> 1 > 10 / 10 -> 1 > 1 -> false
                    if (sum > (long.MaxValue - digit) / radix)
                    {
                        return null;
                    }
                }
                else
                {
                    // Pretend min -16, text "-17", radix 10.
                    // -1 < (-16 + 7) / 10 -> -1 < -9 / 10 -> -1 < 0 -> true
                    // Now text "-16".
                    // -1 < (-16 + 6) / 10 -> -1 < -10 / 10 -> -1 < -1 -> false
                    // Then 1 * 10 + 6 -> 16 -> -16, later -16 * -1 -> -16
                    if (-sum < (long.MinValue + digit) / radix)
                    {
                        return null;
                    }
                }
                sum = sum * radix + digit;
            }
            // Skip trailing whitespace.
            while (index < text.Length)
            {
                var c = text[index];
                if (!char.IsWhiteSpace(c))
                {
                    return null;
                }
                index += 1;
            }
            // Sign our work.
            sum *= sign;
            return sum;
        }
    }

    internal class EnumerableWrapper<T> : IGenerator<T>, IDisposable
    {
        private IEnumerable<T> wrapped;
        private IEnumerator<T> enumerator;

        public EnumerableWrapper(IEnumerable<T> wrapped)
        {
            this.wrapped = wrapped;
            this.enumerator = wrapped.GetEnumerator();
        }

        public IEnumerator<T> GetEnumerator()
        {
            return this;
        }

        C.IEnumerator C.IEnumerable.GetEnumerator()
        {
            return this;
        }

        public T Current
        {
            get { return enumerator.Current; }
        }
        object C.IEnumerator.Current
        {
            get { return enumerator.Current; }
        }

        public bool MoveNext()
        {
            return enumerator.MoveNext();
        }

        public void Reset()
        {
            enumerator.Reset();
        }

        public void Dispose()
        {
            enumerator.Dispose();
        }
    }
}
