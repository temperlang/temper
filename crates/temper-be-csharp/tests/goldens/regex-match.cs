using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Regex;

namespace Match;

public static class MatchGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly string text;
    internal static readonly string moreText;

    internal static void CheckGroup(Group group)
    {
        string name = group.Name;
        string value = group.Value;
        int begin = group.Begin;
        int end = group.End;
        string size = StringUtil.CountBetween(text, begin, end).ToString(CultureInfo.InvariantCulture);
        string begin2 = StringUtil.CountBetween(text, 0, begin).ToString(CultureInfo.InvariantCulture);
        console.Log("match group '" + name + "': '" + value + "' at " + begin2 + " size " + size);
    }

    static MatchGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Match");
        Regex regex = ((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(RegexGlobal.OneOrMore(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodePoints("ab")))), new CodePoints("c")))).Compiled();
        Regex full = RegexGlobal.Entire(regex.Data).Compiled();
        IReadOnlyList<string> strings = Listed.CreateReadOnlyList<string>("abc", "ac", "bbabac", "ab", "dc", "acc", "cbc");
        {
            int length = strings.Count;
            int i = 0;
            while (i < length)
            {
                string @string = Listed.Get(strings, i);
                i = i + 1;
                console.Log("found " + @string + ": " + (regex.Found(@string) ? "true" : "false"));
                console.Log("matches " + @string + ": " + (full.Found(@string) ? "true" : "false"));
            }
        }
        Regex catcher = ((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(new Repeat(new CodePoints("a"), 1, null, false), new Capture("intro", new Repeat(new CodePoints("b"), 1, null, false)), new CodePoints("c"), new Repeat(new Or(Listed.CreateReadOnlyList<IRegexNode>(new Capture("option1", new CodePoints("d")), new Capture("alphaOption2", new CodePoints("e")))), 1, null, false)))).Compiled();
        text = "🌍aaabbceef";
        global::TemperLang.Std.Regex.Match match = catcher.Find(text);
        CheckGroup(match.Full);
        CheckGroup(Mapped.Get(match.Groups, "intro"));
        CheckGroup(Mapped.Get(match.Groups, "alphaOption2"));
        Regex thing = ((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(97, 122)), false), 1, null, false)).Compiled();
        string multiText = "---abc---xyz---";
        global::TemperLang.Std.Regex.Match first = thing.Find(multiText);
        global::TemperLang.Std.Regex.Match second = thing.Find(multiText, first.Full.End);
        console.Log("Found both " + first.Full.Value + " and " + second.Full.Value + ".");
        moreText = "🌍aaabbcee🌎abcd🌏";
        string fn(global::TemperLang.Std.Regex.Match match)
        {
            IReadOnlyDictionary<string, Group> groups = match.Groups;
            string option1;
            try
            {
                option1 = Mapped.Get(groups, "option1").Value;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                option1 = "";
            }
            string alphaOption2;
            try
            {
                alphaOption2 = Mapped.Get(groups, "alphaOption2").Value;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                alphaOption2 = "";
            }
            int begin = StringUtil.CountBetween(moreText, 0, match.Full.Begin);
            return "-" + option1 + alphaOption2 + begin.ToString(CultureInfo.InvariantCulture) + "-";
        }
        string replaced = catcher.Replace(moreText, fn);
        console.Log("replaced: " + replaced);
        string notReplaced = catcher.Replace("nope", (global::TemperLang.Std.Regex.Match match) => "hi");
        console.Log("not replaced: " + notReplaced);
        string noMatch;
        try
        {
            noMatch = catcher.Find("not here").Full.Value;
        }
        catch (global::TemperLang.Core.Bubble)
        {
            noMatch = "sorry";
        }
        console.Log("No match says " + noMatch);
        IRegexNode or = ((IRegexNode)new Or(Listed.CreateReadOnlyList<IRegexNode>(new CodePoints("ab"), new CodePoints("bc")))).Compiled().Data;
        string option1 = "ab";
        string option2 = "bc";
        string optionNone = "cb";
        console.Log("or option 1: " + (or.Found(option1) ? "true" : "false"));
        console.Log("or option 2: " + (or.Found(option2) ? "true" : "false"));
        console.Log("or option none: " + (or.Found(optionNone) ? "true" : "false"));
        Regex begin = ((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(RegexGlobal.Begin, new CodePoints("a")))).Compiled();
        string beginsWithATrue = "ab";
        string beginsWithAFalse = "bab";
        console.Log("begin true: " + (begin.Found(beginsWithATrue) ? "true" : "false"));
        console.Log("begin false: " + (begin.Found(beginsWithAFalse) ? "true" : "false"));
        Regex negatedBasic = RegexGlobal.Entire(((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(new CodePoints("a"), new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(97, 99)), true)))).Compiled().Data).Compiled();
        string nbTrue = "a🌍";
        string nbFalse = "ac";
        console.Log("negated basic true: " + (negatedBasic.Found(nbTrue) ? "true" : "false"));
        console.Log("negated basic false: " + (negatedBasic.Found(nbFalse) ? "true" : "false"));
        string simplerLines = "It was the best of times.\nIt was the worst of times.";
        console.Log(((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(), true), 1, null, false)).Compiled().Find(simplerLines).Full.Value);
        string t1;
        try
        {
            t1 = ((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(), false), 1, null, false)).Compiled().Find(simplerLines).Full.Value;
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = "-";
        }
        console.Log(t1);
        Regex notLow = ((IRegexNode)RegexGlobal.OneOrMore(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(0, 47)), true))).Compiled();
        console.Log("not low: " + notLow.Find("-/a🌍-").Full.Value);
        console.Log("not space or >: " + ((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodePoints("\t"), new CodePoints("\n"), new CodePoints(" "), new CodePoints(">")), true), 1, null, false)).Compiled().Find("ab>c").Full.Value);
        IRegexNode idStart = ((IRegexNode)new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(97, 122), new CodeRange(65, 90), new CodePoints("_")), false)).Compiled().Data;
        Or idContinue = new Or(Listed.CreateReadOnlyList<IRegexNode>(idStart, ((IRegexNode)new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(48, 57)), false)).Compiled().Data));
        Regex word = ((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(RegexGlobal.WordBoundary, idStart, new Repeat(idContinue, 0, null), RegexGlobal.WordBoundary))).Compiled();
        string expected = "def_456";
        string found = word.Find("123_abc " + expected + " ghi_789").Full.Value;
        console.Log("Found id: " + found);
        string stringToEscape = "$(*^";
        Regex regexWithInterpolatedString = ((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(RegexGlobal.Dot, new Repeat(new CodePoints(stringToEscape), 1, null, false), RegexGlobal.Dot))).Compiled();
        string stringToEscapeAndMore = "a" + stringToEscape + stringToEscape + "b";
        global::TemperLang.Std.Regex.Match escapedMatch = regexWithInterpolatedString.Find(stringToEscapeAndMore);
        console.Log(escapedMatch.Full.Value);
        global::TemperLang.Std.Regex.Match ownFullMatch = ((IRegexNode)new Sequence(Listed.CreateReadOnlyList<IRegexNode>(RegexGlobal.Dot, new Capture("full", RegexGlobal.Dot)))).Compiled().Find("ab");
        string ownFull = Mapped.Get(ownFullMatch.Groups, "full").Value;
        console.Log("Own full: " + ownFull + " vs " + ownFullMatch.Full.Value);
        string comboLines = "It was the best\nof times.\r\n\rIt was the worst of times.";
        {
            IReadOnlyList<string> list = ((IRegexNode)new Or(Listed.CreateReadOnlyList<IRegexNode>(new CodePoints("\n"), new Sequence(Listed.CreateReadOnlyList<IRegexNode>(new CodePoints("\r"), new Repeat(new CodePoints("\n"), 0, 1, false)))))).Compiled().Split(comboLines);
            int length2 = list.Count;
            int i2 = 0;
            while (i2 < length2)
            {
                string line = Listed.Get(list, i2);
                i2 = i2 + 1;
                console.Log(line + ";");
            }
        }
    }
}
