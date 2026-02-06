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
    public static class Core
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

        public static void Bubble() => throw new Exception();

        public static T Bubble<T>() => throw new Exception();

        public static T CastToNonNull<T>(object item)
        {
            if (item == null)
            {
                throw new ArgumentNullException();
            }
            return (T)item;
        }

        internal static int Clamp(this int i, int min, int max)
        {
            return Math.Max(min, Math.Min(i, max));
        }

        // TODO Make this generic instead of object?
        public static int Compare(object a, object b)
        {
            // Approximately based on the Python implementation.
            if (a is double && b is double)
            {
                return Float64.Compare((double)a, (double)b);
            }
            else
            {
                // Just let it crash on bad casts.
                return Math.Sign(((IComparable)a).CompareTo((IComparable)b));
            }
        }

        public static int Div(this int a, int b)
        {
            if (b == 0)
            {
                Bubble();
            }
            return DivSafe(a, b);
        }

        public static int DivSafe(this int a, int b)
        {
            return a == int.MinValue && b == -1 ? int.MinValue : a / b;
        }

        public static long Div(this long a, long b)
        {
            if (b == 0)
            {
                Bubble();
            }
            return DivSafe(a, b);
        }

        public static long DivSafe(this long a, long b)
        {
            return a == long.MinValue && b == -1 ? long.MinValue : a / b;
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

        public static int Mod(this int a, int b)
        {
            if (b == 0)
            {
                Bubble();
            }
            return ModSafe(a, b);
        }

        public static int ModSafe(this int a, int b)
        {
            return a == int.MinValue && b == -1 ? 0 : a % b;
        }

        public static long Mod(this long a, long b)
        {
            if (b == 0)
            {
                Bubble();
            }
            return ModSafe(a, b);
        }

        public static long ModSafe(this long a, long b)
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
                throw new KeyNotFoundException();
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
            return string.Concat(codePoints.Select(codePoint => char.ConvertFromUtf32(codePoint)));
        }

        public static int ToInt(this long i64)
        {
            if (i64 < int.MinValue || i64 > int.MaxValue)
            {
                throw new OverflowException();
            }
            return (int)i64;
        }

        public static int ToInt(this string text, int radix = 10)
        {
            // Just reuse int64 parsing for convenience.
            long i64 = text.ToInt64(radix);
            return i64.ToInt();
        }

        public static long ToInt64(this string text, int radix = 10)
        {
            // Use the same exception types as Convert.ToInt32, where convenient enough.
            if (radix < 2 || radix > 36)
            {
                throw new ArgumentException();
            }
            if (text == "")
            {
                throw new ArgumentOutOfRangeException();
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
                var c = char.ToLower(text[index]);
                if (char.IsWhiteSpace(c))
                {
                    break;
                }
                index += 1;
                // Add next digit.
                var digit = c >= 'a' ? c - 'a' + 10 : c - '0';
                if (digit < 0 || digit >= radix || (c < 'a' && digit > 9))
                {
                    throw new FormatException();
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
                        throw new OverflowException();
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
                        throw new OverflowException();
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
                    throw new FormatException();
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
