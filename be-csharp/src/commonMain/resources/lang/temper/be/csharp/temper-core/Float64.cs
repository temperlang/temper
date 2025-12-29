using System;

namespace TemperLang.Core
{
    public static class Float64
    {
        public static int Compare(this double x, double y)
        {
            // Not named CompareTo for avoiding name conflict.
            // TODO Can we do clever DoubleToLongBits here like java?
            return double.IsNaN(x)
                ? (double.IsNaN(y) ? 0 : 1)
                : double.IsNaN(y)
                    ? -1
                    // In practice, double CompareTo returns -1, 0, 1, but it
                    // doesn't promise that.
                    : SignInt(
                        x == 0 && y == 0
                            ? BitConverter
                                .DoubleToInt64Bits(x)
                                .CompareTo(BitConverter.DoubleToInt64Bits(y))
                            : x.CompareTo(y)
                    );
        }

        public static double ExpM1(this double x)
        {
#if NET7_0_OR_GREATER
            return double.ExpM1(x);
#else
            // TODO Stability below tolerance.
            return Math.Exp(x) - 1;
#endif
        }

        public static string Format(this double d)
        {
            // Not named ToString for avoiding name conflict.
            if (double.IsInfinity(d))
            {
                return d < 0 ? "-Infinity" : "Infinity";
            }
            else if (double.IsNaN(d))
            {
                return "NaN";
            }
            // Numeric.
            var s = d.ToString();
            var eIndex = s.IndexOf('E');
            var main = eIndex == -1 ? s : s.Substring(0, eIndex);
            var frac = main.Contains(".") ? "" : ".0";
            var exp = eIndex == -1 ? "" : "e" + s.Substring(eIndex + 1, s.Length - (eIndex + 1));
            return main + frac + exp;
        }

        public static double LogP1(this double x)
        {
#if NET7_0_OR_GREATER
            return double.LogP1(x);
#else
            // TODO Stability below tolerance.
            return Math.Log(x + 1);
#endif
        }

        public static bool Near(
            this double x,
            double y,
            double? relTol = null,
            double? absTol = null
        )
        {
            var rel = relTol ?? 1e-9;
            var abs = absTol ?? 0.0;
            var margin = Math.Max(Math.Max(Math.Abs(x), Math.Abs(y)) * rel, abs);
            return Math.Abs(x - y) < margin;
        }

        public static double Sign(this double x)
        {
            return x == 0.0 ? x : Math.Sign(x);
        }

        public static int SignInt(this double x)
        {
            return (int)x.Sign();
        }

        private const long mantissaLimit = (1L << 53) - 1;

        public static double ToFloat64(this long i64)
        {
            if (i64 < -mantissaLimit || i64 > mantissaLimit)
            {
                throw new OverflowException();
            }
            return (double)i64;
        }

        public static double ToFloat64(this string s)
        {
            // TODO Probably need more checks.
            var trimmed = s.Trim();
            if (trimmed.StartsWith(".") || trimmed.EndsWith("."))
            {
                throw new Exception();
            }
            return trimmed == "Infinity"
                ? double.PositiveInfinity
                : trimmed == "-Infinity"
                    ? double.NegativeInfinity
                    : double.Parse(s);
        }

        public static int ToInt(double value)
        {
            // Check this way because nans, too.
            if (!(value > int.MinValue - 1.0 && value < int.MaxValue + 1.0))
            {
                throw new OverflowException();
            }
            return (int)value;
        }

        public static long ToInt64(double value)
        {
            // Check this way because nans, too.
            if (!(value >= -mantissaLimit && value <= mantissaLimit))
            {
                throw new OverflowException();
            }
            return (long)value;
        }
    }
}
