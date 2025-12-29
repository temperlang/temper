using System;
using System.Collections.Generic;

namespace TemperLang.Std.Regex
{
    /// <summary>
    /// Somewhat like System.Range but simpler and supports older dotnet.
    /// </summary>
    struct IntRange : IComparable<IntRange>
    {
        public readonly int Start;

        /// <summary>
        /// Exclusive end index.
        /// </summary>
        public readonly int End;

        public IntRange(int single)
            : this(single, single + 1) { }

        public IntRange(int start, int end)
        {
            if (start > end)
            {
                throw new ArgumentException();
            }
            Start = start;
            End = end;
        }

        public int CompareTo(IntRange other) =>
            Start == other.Start ? End - other.End : Start - other.Start;

        public int Count => End - Start;

        public bool IsEmpty => Start == End;

        public override string ToString()
        {
            return $"IntRange({Start}, {End})";
        }
    }

    /// <summary>
    /// Helpers for List of IntRange as a set of IntRange.
    /// </summary>
    static class IntRangeSet
    {
        /// <summary>
        /// Convert the list of ranges into its complement relative to the given
        /// universe.
        /// </summary>
        /// <param name="ranges">
        /// Must be sorted and nonoverlapping, with no empty ranges.
        /// </param>
        /// <param name="universe">Must include all the ranges given.</param>
        public static void Negate(List<IntRange> ranges, IntRange universe)
        {
            var result = new List<IntRange>();
            if (ranges.Count == 0)
            {
                result.Add(universe);
                return;
            }
            if (universe.Start > ranges[0].Start || universe.End < ranges[ranges.Count - 1].End)
            {
                throw new ArgumentException();
            }
            // Remember the last before we overwrite things.
            var last = ranges[ranges.Count - 1];
            // Check each range.
            var start = universe.Start;
            var index = 0;
            for (var r = 0; r < ranges.Count; r += 1)
            {
                var range = ranges[r];
                if (range.Start > start)
                {
                    // Common case, but maybe false for first range.
                    ranges[index] = new IntRange(start, range.Start);
                    start = range.End;
                    index += 1;
                }
            }
            // See if we have any past the end of ranges.
            if (last.End < universe.End)
            {
                var newLast = new IntRange(last.End, universe.End);
                if (index < ranges.Count - 1)
                {
                    ranges[index] = newLast;
                }
                else
                {
                    ranges.Add(newLast);
                }
                index += 1;
            }
            // Trim excess.
            if (index < ranges.Count)
            {
                ranges.RemoveRange(index, ranges.Count - index);
            }
        }

        /// <summary>
        /// Merge all the ranges into a minimal ordered list.
        /// </summary>
        public static void Merge(List<IntRange> ranges)
        {
            // Index is the most recently included index from ranges.
            var index = -1;
            var current = new IntRange(0, 0);
            ranges.Sort();
            for (var r = 0; r < ranges.Count; r += 1)
            {
                var range = ranges[r];
                if (range.IsEmpty)
                {
                    // No need for empty ranges here.
                }
                else if (index < 0)
                {
                    // First one. Always keep it.
                    current = range;
                    index = 0;
                }
                else if (range.Start <= current.End)
                {
                    // Merge into current.
                    current = new IntRange(current.Start, Math.Max(range.End, current.End));
                    ranges[index] = current;
                }
                else
                {
                    // Keep as separate new current.
                    current = range;
                    index += 1;
                }
            }
            // Trim excess.
            if (index < ranges.Count - 1)
            {
                ranges.RemoveRange(index + 1, ranges.Count - index - 1);
            }
        }
    }
}
