package match;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.std.regex.Capture;
import temper.std.regex.CodePart;
import temper.std.regex.CodePoints;
import temper.std.regex.CodeRange;
import temper.std.regex.CodeSet;
import temper.std.regex.Group;
import temper.std.regex.Match;
import temper.std.regex.Or;
import temper.std.regex.Regex;
import temper.std.regex.RegexGlobal;
import temper.std.regex.RegexNode;
import temper.std.regex.Repeat;
import temper.std.regex.Sequence;

public final class MatchGlobal {
    private MatchGlobal() {}

    static final Console console;
    static final String text;
    static final String moreText;

    static void checkGroup(Group group) {
        String name = group.name;
        String value = group.value;
        int begin = group.begin;
        int end = group.end;
        String size = Integer.toString(Core.stringCountBetween(text, begin, end));
        String begin2 = Integer.toString(Core.stringCountBetween(text, 0, begin));
        console.log("match group '" + name + "': '" + value + "' at " + begin2 + " size " + size);
    }

    static {
        console = Core.getConsole(Logger.getLogger("match"));
        Regex regex = new Sequence(List.<RegexNode>of(RegexGlobal.oneOrMore(new CodeSet(List.<CodePart>of(new CodePoints("ab")))), new CodePoints("c"))).compiled();
        Regex full = RegexGlobal.entire(regex.data).compiled();
        List<String> strings = List.<String>of("abc", "ac", "bbabac", "ab", "dc", "acc", "cbc");
        {
            int length = strings.size();
            int i = 0;
            while (i < length) {
                String string = Core.listGet(strings, i);
                i = i + 1;
                console.log("found " + string + ": " + regex.found(string));
                console.log("matches " + string + ": " + full.found(string));
            }
        }
        Regex catcher = new Sequence(List.<RegexNode>of(new Repeat(new CodePoints("a"), 1, null, false), new Capture("intro", new Repeat(new CodePoints("b"), 1, null, false)), new CodePoints("c"), new Repeat(new Or(List.<RegexNode>of(new Capture("option1", new CodePoints("d")), new Capture("alphaOption2", new CodePoints("e")))), 1, null, false))).compiled();
        text = "🌍aaabbceef";
        Match match = catcher.find(text);
        checkGroup(match.full);
        checkGroup(Core.mappedGet(match.groups, "intro"));
        checkGroup(Core.mappedGet(match.groups, "alphaOption2"));
        Regex thing = new Repeat(new CodeSet(List.<CodePart>of(new CodeRange(97, 122)), false), 1, null, false).compiled();
        String multiText = "---abc---xyz---";
        Match first = thing.find(multiText);
        Match second = thing.find(multiText, first.full.end);
        console.log("Found both " + first.full.value + " and " + second.full.value + ".");
        moreText = "🌍aaabbcee🌎abcd🌏";
        Function<Match, String> fn = match2 -> {
            Map<String, Group> groups = match2.groups;
            String option1_2;
            try {
                option1_2 = Core.mappedGet(groups, "option1").value;
            } catch (RuntimeException ignored) {
                option1_2 = "";
            }
            String alphaOption2;
            try {
                alphaOption2 = Core.mappedGet(groups, "alphaOption2").value;
            } catch (RuntimeException ignored2) {
                alphaOption2 = "";
            }
            int begin2 = Core.stringCountBetween(moreText, 0, match2.full.begin);
            return "-" + option1_2 + alphaOption2 + begin2 + "-";
        };
        String replaced = catcher.replace(moreText, fn);
        console.log("replaced: " + replaced);
        String notReplaced = catcher.replace("nope", match2 -> "hi");
        console.log("not replaced: " + notReplaced);
        String noMatch;
        try {
            noMatch = catcher.find("not here").full.value;
        } catch (RuntimeException ignored3) {
            noMatch = "sorry";
        }
        console.log("No match says " + noMatch);
        RegexNode or = new Or(List.<RegexNode>of(new CodePoints("ab"), new CodePoints("bc"))).compiled().data;
        String option1 = "ab";
        String option2 = "bc";
        String optionNone = "cb";
        console.log("or option 1: " + or.found(option1));
        console.log("or option 2: " + or.found(option2));
        console.log("or option none: " + or.found(optionNone));
        Regex begin = new Sequence(List.<RegexNode>of(RegexGlobal.Begin, new CodePoints("a"))).compiled();
        String beginsWithATrue = "ab";
        String beginsWithAFalse = "bab";
        console.log("begin true: " + begin.found(beginsWithATrue));
        console.log("begin false: " + begin.found(beginsWithAFalse));
        Regex negatedBasic = RegexGlobal.entire(new Sequence(List.<RegexNode>of(new CodePoints("a"), new CodeSet(List.<CodePart>of(new CodeRange(97, 99)), true))).compiled().data).compiled();
        String nbTrue = "a🌍";
        String nbFalse = "ac";
        console.log("negated basic true: " + negatedBasic.found(nbTrue));
        console.log("negated basic false: " + negatedBasic.found(nbFalse));
        String simplerLines = "It was the best of times.\nIt was the worst of times.";
        console.log(new Repeat(new CodeSet(List.<CodePart>of(), true), 1, null, false).compiled().find(simplerLines).full.value);
        String t;
        try {
            t = new Repeat(new CodeSet(List.<CodePart>of(), false), 1, null, false).compiled().find(simplerLines).full.value;
        } catch (RuntimeException ignored4) {
            t = "-";
        }
        console.log(t);
        Regex notLow = RegexGlobal.oneOrMore(new CodeSet(List.<CodePart>of(new CodeRange(0, 47)), true)).compiled();
        console.log("not low: " + notLow.find("-/a🌍-").full.value);
        console.log("not space or >: " + new Repeat(new CodeSet(List.<CodePart>of(new CodePoints("\t"), new CodePoints("\n"), new CodePoints(" "), new CodePoints(">")), true), 1, null, false).compiled().find("ab>c").full.value);
        RegexNode idStart = new CodeSet(List.<CodePart>of(new CodeRange(97, 122), new CodeRange(65, 90), new CodePoints("_")), false).compiled().data;
        Or idContinue = new Or(List.<RegexNode>of(idStart, new CodeSet(List.<CodePart>of(new CodeRange(48, 57)), false).compiled().data));
        Regex word = new Sequence(List.<RegexNode>of(RegexGlobal.WordBoundary, idStart, new Repeat(idContinue, 0, null), RegexGlobal.WordBoundary)).compiled();
        String expected = "def_456";
        String found = word.find("123_abc " + expected + " ghi_789").full.value;
        console.log("Found id: " + found);
        String stringToEscape = "$(*^";
        Regex regexWithInterpolatedString = new Sequence(List.<RegexNode>of(RegexGlobal.Dot, new Repeat(new CodePoints(stringToEscape), 1, null, false), RegexGlobal.Dot)).compiled();
        String stringToEscapeAndMore = "a" + stringToEscape + stringToEscape + "b";
        Match escapedMatch = regexWithInterpolatedString.find(stringToEscapeAndMore);
        console.log(escapedMatch.full.value);
        Match ownFullMatch = new Sequence(List.<RegexNode>of(RegexGlobal.Dot, new Capture("full", RegexGlobal.Dot))).compiled().find("ab");
        String ownFull = Core.mappedGet(ownFullMatch.groups, "full").value;
        console.log("Own full: " + ownFull + " vs " + ownFullMatch.full.value);
        String comboLines = "It was the best\nof times.\r\n\rIt was the worst of times.";
        {
            List<String> list = new Or(List.<RegexNode>of(new CodePoints("\n"), new Sequence(List.<RegexNode>of(new CodePoints("\r"), new Repeat(new CodePoints("\n"), 0, 1, false))))).compiled().split(comboLines);
            int length2 = list.size();
            int i2 = 0;
            while (i2 < length2) {
                String line = Core.listGet(list, i2);
                i2 = i2 + 1;
                console.log(line + ";");
            }
        }
    }
}
