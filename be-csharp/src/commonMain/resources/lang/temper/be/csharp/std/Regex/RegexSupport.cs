using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using TemperLang.Core;
using R = System.Text.RegularExpressions;

namespace TemperLang.Std.Regex
{
    static class RegexSupport
    {
        private const int MaxBasicCodePoint = 0xFFFF;
        private const int MinSupplementalCodePoint = 0x10000;
        private const int MaxSupplementalCodePoint = 0x10FFFF;

        private const int MinSurrogateUnit = 0xD800;
        private const int MaxSurrogateUnit = 0xDFFF;
        private static readonly CodeRange SurrogateCodeRange = new CodeRange(
            MinSurrogateUnit,
            MaxSurrogateUnit
        );

        // Broader than needed but gets the job done.
        private static readonly IRegexNode SurrogateCodeRangePair = new Sequence(
            new List<IRegexNode> { SurrogateCodeRange, SurrogateCodeRange }
        );

        private static readonly IntRange CodeSetUniverse = new IntRange(
            0,
            MaxSupplementalCodePoint
        );
        private static readonly IntRange SupplementalIntRange = new IntRange(
            MinSupplementalCodePoint,
            MaxSupplementalCodePoint
        );

        internal static IRegexNode AdjustCodeSet(
            RegexFormatter formatter,
            CodeSet codeSet,
            RegexRefs regexRefs
        )
        {
            if (!codeSet.Negated)
            {
                if (
                    codeSet
                        .Items
                        .All(
                            (part) =>
                            {
                                return (formatter.MaxCode(part) ?? 0) < MinSupplementalCodePoint
                                    && !(part is ISpecialSet);
                            }
                        )
                )
                {
                    // Simple case that doesn't need extra processing.
                    return codeSet;
                }
            }
            // We're conservative above, so maybe not all cases need more
            // processing, but this should at least be sufficient.
            var ranges = CodeSetToIntRangeSet(codeSet);
            var converted = IntRangeSetToUtf16CodePattern(ranges);
            return converted;
        }

        static List<IntRange> CodeSetToIntRangeSet(CodeSet codeSet)
        {
            var ranges = new List<IntRange>();
            foreach (var part in codeSet.Items)
            {
                switch (part)
                {
                    case CodePoints codes:
                        int index = 0;
                        while (index < codes.Value.Length)
                        {
                            int code = char.ConvertToUtf32(codes.Value, index);
                            ranges.Add(new IntRange(code));
                            index += code >= 0x1_0000 ? 2 : 1;
                        }
                        break;
                    case CodeRange range:
                        ranges.Add(new IntRange(range.Min, range.Max + 1));
                        break;
                    case Digit:
                        ranges.Add(new IntRange('0', '9'));
                        break;
                    case Space:
                        foreach (var code in " \t\n\r\x0C\x0B")
                        {
                            ranges.Add(new IntRange(code));
                        }
                        break;
                    case Word:
                        // Go ascii for now.
                        ranges.Add(new IntRange('_'));
                        ranges.Add(new IntRange('0', '9'));
                        ranges.Add(new IntRange('A', 'Z'));
                        ranges.Add(new IntRange('a', 'z'));
                        break;
                }
            }
            IntRangeSet.Merge(ranges);
            if (codeSet.Negated)
            {
                IntRangeSet.Negate(ranges, CodeSetUniverse);
            }
            return ranges;
        }

        internal static object CompileFormatted(IRegexNode data, string text) => new R::Regex(text);

        internal static Match CompiledFind(
            Regex regex,
            object compiled,
            string text,
            int begin,
            RegexRefs regexRefs
        )
        {
            var match = CompiledFindEx((R::Regex)compiled, text, begin);
            if (match == null)
            {
                throw new Exception();
            }
            return match;
        }

        static Match? CompiledFindEx(R::Regex regex, string text, int offset = 0)
        {
            var match = regex.Match(text, offset);
            if (!match.Success)
            {
                return null;
            }
            var resultGroups = new List<KeyValuePair<string, Group>>();
            foreach (R::Group group in match.Groups)
            {
                if (!group.Success)
                {
                    continue;
                }
                var capture = group.Captures[group.Captures.Count - 1];
                resultGroups.Add(
                    new KeyValuePair<string, Group>(
                        group.Name,
                        new Group(group.Name, capture.Value, capture.Index, capture.Index + capture.Length)
                    )
                );
            }
            var groupsDict = new ReadOnlyDictionaryWrapper<string, Group>(
                new OrderedDictionary<string, Group>(resultGroups)
            );
            var full = match.Groups[0].Captures[0];
            var fullGroup = new Group("full", full.Value, full.Index, full.Index + full.Length);
            return new Match(fullGroup, groupsDict);
        }

        internal static bool CompiledFound(Regex regex, object compiled, string text) =>
            ((R::Regex)compiled).IsMatch(text);

        internal static string CompiledReplace(
            Regex regex,
            object compiled,
            string text,
            Func<Match, string> format,
            RegexRefs regexRefs
        )
        {
            var builder = new StringBuilder();
            var begin = 0;
            var keepBegin = begin;
            do
            {
                var match = CompiledFindEx((R::Regex)compiled, text, begin);
                if (match == null)
                {
                    if (builder.Length == 0)
                    {
                        // Just shortcircuit any string building.
                        return text;
                    }
                    else
                    {
                        builder.Append(text.Substring(keepBegin));
                        break;
                    }
                }
                builder.Append(text.Substring(keepBegin, match.Full.Begin - keepBegin));
                builder.Append(format(match));
                // Keep all skipped text, but always advance to avoid infinite loop.
                keepBegin = match.Full.End;
                begin = Math.Max(keepBegin, begin + 1);
            } while (begin <= text.Length); // `<=` to see string end
            return builder.ToString();
        }

        internal static IReadOnlyList<string> CompiledSplit(
            Regex regex,
            object compiled,
            string text,
            RegexRefs regexRefs
        )
        {
            return ((R::Regex)compiled).Split(text).AsReadOnly();
        }

        static IRegexNode IntRangeSetToUtf16CodePattern(List<IntRange> ranges)
        {
            var basicParts = new List<ICodePart>();
            var orItems = new List<IRegexNode>();
            for (var r = 0; r < ranges.Count; r += 1)
            {
                var range = ranges[r];
                if (range.End <= MinSupplementalCodePoint)
                {
                    // TODO Buffer up all individuals into a single CodePoints?
                    // TODO Perhaps fewer heap allocations that way.
                    basicParts.Add(
                        range.Count == 1
                            ? new CodePoints(((char)range.Start).ToString())
                            : new CodeRange(range.Start, range.End - 1)
                    );
                }
                else
                {
                    if (range.Start < MinSupplementalCodePoint)
                    {
                        // Add the part below in the supplemental range.
                        basicParts.Add(new CodeRange(range.Start, MaxBasicCodePoint));
                        range = new IntRange(MinSupplementalCodePoint, range.End);
                    }
                    if (orItems.Count == 0 && basicParts.Count > 0)
                    {
                        // For prettiness, put the small ones on the front.
                        orItems.Add(new CodeSet(basicParts));
                    }
                    // Supplemental space.
                    if (range.Count == 1)
                    {
                        orItems.Add(new CodePoints(char.ConvertFromUtf32(range.Start)));
                    }
                    else if (range.CompareTo(SupplementalIntRange) == 0)
                    {
                        // Expected common case.
                        orItems.Add(SurrogateCodeRangePair);
                    }
                    else
                    {
                        // Custom sequencing to support supplemental space.
                        var min = char.ConvertFromUtf32(range.Start);
                        var max = char.ConvertFromUtf32(range.End - 1);
                        if (min[0] == max[0])
                        {
                            // Only 1 range needed for a single lead surrogate.
                            orItems.Add(
                                new Sequence(
                                    new List<IRegexNode>
                                    {
                                        new CodePoints(min.Substring(0, 1)),
                                        new CodeRange(min[1], max[1]),
                                    }
                                )
                            );
                        }
                        else
                        {
                            // 2 or 3 ranges needed for more than one lead.
                            orItems.Add(
                                new Sequence(
                                    new List<IRegexNode>
                                    {
                                        new CodePoints(min.Substring(0, 1)),
                                        new CodeRange(min[1], MaxBasicCodePoint)
                                    }
                                )
                            );
                            if (max[0] - min[0] > 1)
                            {
                                // Gather up all the middle space into a sequence of 2 ranges.
                                orItems.Add(
                                    new Sequence(
                                        new List<IRegexNode>
                                        {
                                            new CodeRange(min[0] + 1, max[0] - 1),
                                            new CodeRange(0, MaxBasicCodePoint),
                                        }
                                    )
                                );
                            }
                            orItems.Add(
                                new Sequence(
                                    new List<IRegexNode>
                                    {
                                        new CodePoints(max.Substring(0, 1)),
                                        new CodeRange(0, max[1]),
                                    }
                                )
                            );
                        }
                    }
                }
            }
            return new Or(orItems);
        }

        internal static void PushCodeTo(
            RegexFormatter formatter,
            StringBuilder @out,
            int code,
            bool insideCodeSet
        )
        {
            // TODO Make more efficient encodings.
            // Ignore insideCodeSet for now.
            if (code < MinSupplementalCodePoint)
            {
                PushCode16To(@out, code);
            }
            else
            {
                // TODO Something manual to avoid allocation?
                var pair = char.ConvertFromUtf32(code);
                PushCode16To(@out, pair[0]);
                PushCode16To(@out, pair[1]);
            }
        }

        static void PushCode16To(StringBuilder @out, int code)
        {
            @out.Append(@"\u");
            @out.Append(code.ToString("X4"));
        }
    }
}
