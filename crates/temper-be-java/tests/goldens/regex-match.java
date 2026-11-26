package match;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.MissingKey;
import temper.core.Nullable;
import temper.std.regex.Capture;
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
        Regex regex = new Sequence(List.of(RegexGlobal.oneOrMore(new CodeSet(List.of(new CodePoints("ab")))), new CodePoints("c"))).compiled();
        Regex full = RegexGlobal.entire(regex.data).compiled();
        List<String> strings = List.of("abc", "ac", "bbabac", "ab", "dc", "acc", "cbc");
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
        Regex catcher = new Sequence(List.of(new Repeat(new CodePoints("a"), 1, null, false), new Capture("intro", new Repeat(new CodePoints("b"), 1, null, false)), new CodePoints("c"), new Repeat(new Or(List.of(new Capture("option1", new CodePoints("d")), new Capture("alphaOption2", new CodePoints("e")))), 1, null, false))).compiled();
        text = "🌍aaabbceef";
        @Nullable Match subject = catcher.find(text);
        Match match = subject == null ? Core.throwAssertionError("panic") : subject;
        checkGroup(match.full);
        checkGroup(Core.mappedGet(match.groups, "intro"));
        checkGroup(Core.mappedGet(match.groups, "alphaOption2"));
        Regex thing = new Repeat(new CodeSet(List.of(new CodeRange(97, 122)), false), 1, null, false).compiled();
        String multiText = "---abc---xyz---";
        @Nullable Match subject2 = thing.find(multiText);
        Match first = subject2 == null ? Core.throwAssertionError("panic") : subject2;
        @Nullable Match subject3 = thing.find(multiText, first.full.end);
        Match second = subject3 == null ? Core.throwAssertionError("panic") : subject3;
        console.log("Found both " + first.full.value + " and " + second.full.value + ".");
        moreText = "🌍aaabbcee🌎abcd🌏";
        Function<Match, String> fn = match2 -> {
            Map<String, Group> groups = match2.groups;
            String option1_2;
            try {
                option1_2 = Core.mappedGet(groups, "option1").value;
            } catch (MissingKey ignored) {
                option1_2 = "";
            }
            String alphaOption2;
            try {
                alphaOption2 = Core.mappedGet(groups, "alphaOption2").value;
            } catch (MissingKey ignored2) {
                alphaOption2 = "";
            }
            int begin2 = Core.stringCountBetween(moreText, 0, match2.full.begin);
            return "-" + option1_2 + alphaOption2 + begin2 + "-";
        };
        String replaced = catcher.replace(moreText, fn);
        console.log("replaced: " + replaced);
        String notReplaced = catcher.replace("nope", match2 -> "hi");
        console.log("not replaced: " + notReplaced);
        @Nullable Match subject6 = catcher.find("not here");
        @Nullable Group subject5 = subject6 == null ? null : subject6.full;
        @Nullable String subject4 = subject5 == null ? null : subject5.value;
        String noMatch = subject4 == null ? "sorry" : subject4;
        console.log("No match says " + noMatch);
        RegexNode or = new Or(List.of(new CodePoints("ab"), new CodePoints("bc"))).compiled().data;
        String option1 = "ab";
        String option2 = "bc";
        String optionNone = "cb";
        console.log("or option 1: " + or.found(option1));
        console.log("or option 2: " + or.found(option2));
        console.log("or option none: " + or.found(optionNone));
        Regex begin = new Sequence(List.of(RegexGlobal.Begin, new CodePoints("a"))).compiled();
        String beginsWithATrue = "ab";
        String beginsWithAFalse = "bab";
        console.log("begin true: " + begin.found(beginsWithATrue));
        console.log("begin false: " + begin.found(beginsWithAFalse));
        Regex negatedBasic = RegexGlobal.entire(new Sequence(List.of(new CodePoints("a"), new CodeSet(List.of(new CodeRange(97, 99)), true))).compiled().data).compiled();
        String nbTrue = "a🌍";
        String nbFalse = "ac";
        console.log("negated basic true: " + negatedBasic.found(nbTrue));
        console.log("negated basic false: " + negatedBasic.found(nbFalse));
        String simplerLines = "It was the best of times.\nIt was the worst of times.";
        @Nullable Match subject9 = new Repeat(new CodeSet(List.of(), true), 1, null, false).compiled().find(simplerLines);
        @Nullable Group subject8 = subject9 == null ? null : subject9.full;
        @Nullable String subject7 = subject8 == null ? null : subject8.value;
        console.log(subject7 == null ? "-" : subject7);
        @Nullable Match subject12 = new Repeat(new CodeSet(List.of(), false), 1, null, false).compiled().find(simplerLines);
        @Nullable Group subject11 = subject12 == null ? null : subject12.full;
        @Nullable String subject10 = subject11 == null ? null : subject11.value;
        console.log(subject10 == null ? "-" : subject10);
        Regex notLow = RegexGlobal.oneOrMore(new CodeSet(List.of(new CodeRange(0, 47)), true)).compiled();
        @Nullable Match subject15 = notLow.find("-/a🌍-");
        @Nullable Group subject14 = subject15 == null ? null : subject15.full;
        @Nullable String subject13 = subject14 == null ? null : subject14.value;
        console.log("not low: " + (subject13 == null ? "-" : subject13));
        @Nullable Match subject18 = new Repeat(new CodeSet(List.of(new CodePoints("\t"), new CodePoints("\n"), new CodePoints(" "), new CodePoints(">")), true), 1, null, false).compiled().find("ab>c");
        @Nullable Group subject17 = subject18 == null ? null : subject18.full;
        @Nullable String subject16 = subject17 == null ? null : subject17.value;
        console.log("not space or >: " + (subject16 == null ? "-" : subject16));
        RegexNode idStart = new CodeSet(List.of(new CodeRange(97, 122), new CodeRange(65, 90), new CodePoints("_")), false).compiled().data;
        Or idContinue = new Or(List.of(idStart, new CodeSet(List.of(new CodeRange(48, 57)), false).compiled().data));
        Regex word = new Sequence(List.of(RegexGlobal.WordBoundary, idStart, new Repeat(idContinue, 0, null), RegexGlobal.WordBoundary)).compiled();
        String expected = "def_456";
        @Nullable Match subject21 = word.find("123_abc " + expected + " ghi_789");
        @Nullable Group subject20 = subject21 == null ? null : subject21.full;
        @Nullable String subject19 = subject20 == null ? null : subject20.value;
        String found = subject19 == null ? "-" : subject19;
        console.log("Found id: " + found);
        String stringToEscape = "$(*^";
        Regex regexWithInterpolatedString = new Sequence(List.of(RegexGlobal.Dot, new Repeat(new CodePoints(stringToEscape), 1, null, false), RegexGlobal.Dot)).compiled();
        String stringToEscapeAndMore = "a" + stringToEscape + stringToEscape + "b";
        @Nullable Match subject22 = regexWithInterpolatedString.find(stringToEscapeAndMore);
        Match escapedMatch = subject22 == null ? Core.throwAssertionError("panic") : subject22;
        console.log(escapedMatch.full.value);
        @Nullable Match subject23 = new Sequence(List.of(RegexGlobal.Dot, new Capture("full", RegexGlobal.Dot))).compiled().find("ab");
        Match ownFullMatch = subject23 == null ? Core.throwAssertionError("panic") : subject23;
        String ownFull = Core.mappedGet(ownFullMatch.groups, "full").value;
        console.log("Own full: " + ownFull + " vs " + ownFullMatch.full.value);
        String comboLines = "It was the best\nof times.\r\n\rIt was the worst of times.";
        {
            List<String> list = new Or(List.of(new CodePoints("\n"), new Sequence(List.of(new CodePoints("\r"), new Repeat(new CodePoints("\n"), 0, 1, false))))).compiled().split(comboLines);
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
