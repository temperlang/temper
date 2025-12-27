using System;
using T = System.Text;
using G = System.Collections.Generic;

namespace TemperLang.Core
{
    // Adapts C#'s UTF-16 strings to a code-point centric view to
    // support Temper String and StringIndex types.
    public static class StringUtil
    {
        public static void AppendBetween(T.StringBuilder sb, string s, int left, int right)
        {
            int length = s.Length;
            left = Math.Min(Math.Max(left, 0), length);
            right = Math.Min(Math.Max(right, left), length);
            sb.Append(s, left, right - left);
        }

        public static void AppendCodePoint(T.StringBuilder sb, int codePoint)
        {
            if (codePoint < 0 || (codePoint >= 0xD800 && codePoint <= 0xDFFF) || codePoint > 0x10FFFF)
            {
                throw new ArgumentOutOfRangeException(
                    nameof(codePoint),
                    $"Invalid Unicode scalar value: {codePoint:X}"
                );
            }
            // Manually append needed chars to avoid intermediate string.
            if (codePoint <= 0xFFFF)
            {
                sb.Append((char)codePoint);
            }
            else
            {
                codePoint -= 0x10000;
                sb.Append((char)((codePoint >> 10) + 0xD800));
                sb.Append((char)((codePoint & 0x3FF) + 0xDC00));
            }
        }

        public static int Get(string s, int index)
        {
            // Char.ConvertToUtf32 throws on orphaned surrogates, and we want to
            // control the output for that case.
            // And do throw if we start out of bounds.
            char c = s[index];
            if (0xD800 <= c && c <= 0xDBFF && index + 1 < s.Length)
            {
                char d = s[index + 1];
                if (0xDC00 <= d && d <= 0xDFFF)
                {
                    return 0x10000 + (((c - 0xD800) << 10) | (d - 0xDC00));
                }
            }
            return c;
        }

        public static int CountBetween(string s, int left, int right)
        {
            int limit = Math.Min(s.Length, right);
            int count = 0;
            for (int i = Math.Max(0, left); i < limit; ++i)
            {
                count += 1;
                if (i + 1 < right)
                {
                    char c = s[i];
                    if ('\uD800' <= c && c <= '\uDBFF')
                    {
                        char next = s[i + 1];
                        if ('\uDC00' <= next && next <= '\uDFFF')
                        {
                            ++i; // Skip both surrogates
                        }
                    }
                }
            }
            return count;
        }

        public static void ForEach(string s, Action<int> f)
        {
            int i = 0;
            int length = s.Length;
            while (i < length)
            {
                int cp = Get(s, i);
                f(cp);
                i += cp >= 0x1_0000 ? 2 : 1;
            }
        }

        public static bool HasAtLeast(string s, int left, int right, int minCount)
        {
            int length = s.Length;
            left = Math.Min(Math.Max(left, 0), length);
            right = Math.Min(Math.Max(right, left), length);
            int nUtf16 = right - left;
            if (nUtf16 < minCount) { return false; }
            if (nUtf16 >= minCount * 2) { return true; }
            // Fall back to an early-outing version of CountBetween.
            int count = 0;
            for (int i = left; i < right; ++i)
            {
                int cp = Get(s, i);
                count += 1;
                if (cp >= 0x1_0000)
                {
                    ++i; // skip both surrogates with ++i above
                    if (count >= minCount) { return true; }
                }
            }
            return count >= minCount;
        }

        public static bool HasIndex(string s, int index)
        {
            return 0 <= index && index < s.Length;
        }

        public static int Next(string s, int index)
        {
            int length = s.Length;
            int nextIndex = Math.Min(Math.Max(0, index), length);
            if (nextIndex < length)
            {
                int cp = Get(s, nextIndex);
                nextIndex += cp >= 0x1_0000 ? 2 : 1;
            }
            return nextIndex;
        }

        public static int Prev(string s, int index)
        {
            int prevIndex = Math.Min(Math.Max(0, index), s.Length);
            if (prevIndex != 0)
            {
                prevIndex -= 1;
                if (prevIndex != 0 && Get(s, prevIndex - 1) >= 0x1_0000)
                {
                    prevIndex -= 1; // Back up to beginning of supplementary code-point
                }
            }
            return prevIndex;
        }

        public static int Step(string s, int index, int step)
        {
            if (step >= 0)
            {
                for (int i = 0; i < step; i += 1)
                {
                    var oldIndex = index;
                    index = Next(s, index);
                    if (index == oldIndex)
                    {
                        break;
                    }
                }
            }
            else
            {
                for (int i = 0; i > step; i -= 1)
                {
                    var oldIndex = index;
                    index = Prev(s, index);
                    if (index == oldIndex)
                    {
                        break;
                    }
                }
            }
            return index;
        }

        public static string Slice(string s, int begin, int end)
        {
            int length = s.Length;
            begin = Math.Min(Math.Max(begin, 0), length);
            end = Math.Min(Math.Max(end, begin), length);
            // C#'s substring takes a substring length as the second argument.
            return s.Substring(begin, end - begin);
        }

        public static int RequireStringIndex(int i)
        {
            if (i >= 0)
            {
                return i;
            }
            throw new ArgumentOutOfRangeException("i");
        }

        public static int RequireNoStringIndex(int i)
        {
            if (i < 0)
            {
                return -1;
            }
            throw new ArgumentOutOfRangeException("i");
        }

        /**
         * Compares strings lexicographically by code-point.
         * Unlike C#'s default string comparison which is lexicographic by UTF-16 code unit,
         * this comparison is lexicographic by code-point.
         *
         * UTF-16 order:    "\U00010000" < "\U0000FFFF"
         * Codepoint order: "\U00010000" > "\U0000FFFF"
         */
        public static int CompareStringsByCodePoint(string a, string b)
        {
            int aLen = a.Length;
            int bLen = b.Length;
            int minLen = Math.Min(aLen, bLen);
            int i = 0;
            while (i < minLen)
            {
                int cpA = Get(a, i);
                int cpB = Get(b, i);
                int delta = cpA - cpB;
                if (delta != 0)
                {
                    return Math.Sign(delta);
                }
                i += cpA >= 0x1_0000 ? 2 : 1;
            }
            return Math.Sign(aLen - bLen);
        }

        /** Compares strings lexicographically by code-point */
        public static readonly G::Comparer<string> stringComparer =
            G::Comparer<string>.Create(CompareStringsByCodePoint);
    }
}
