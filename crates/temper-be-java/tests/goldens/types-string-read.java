package read;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.InvalidCodePoint;
import temper.core.Nullable;
import temper.core.ParseError;

public final class ReadGlobal {
    private ReadGlobal() {}

    static final Console console;
    static final String hay;

    static void reportSliceInfo(String s) {
        int start = 0;
        int end = s.length();
        int count = Core.stringCountBetween(s, start, end);
        console.log("Count of code-points for `" + s + "`: " + count);
        console.log("Has at least 0: " + Core.stringHasAtLeast(s, start, end, 0));
        console.log("Has at least 1: " + Core.stringHasAtLeast(s, start, end, 1));
        console.log("Has at least 2: " + Core.stringHasAtLeast(s, start, end, 2));
        console.log("Has at least 3: " + Core.stringHasAtLeast(s, start, end, 3));
        console.log("Has at least 4: " + Core.stringHasAtLeast(s, start, end, 4));
        console.log("Has at least 5: " + Core.stringHasAtLeast(s, start, end, 5));
        console.log("Has at least 6: " + Core.stringHasAtLeast(s, start, end, 6));
        console.log("Has at least 7: " + Core.stringHasAtLeast(s, start, end, 7));
        console.log("Has at least 8: " + Core.stringHasAtLeast(s, start, end, 8));
        console.log("code-points");
        {
            int i = 0;
            while (Core.stringHasIndex(s, i)) {
                int cp = Core.stringGet(s, i);
                i = Core.stringNext(s, i);
                console.log("  " + Integer.toString(cp, 16));
            }
        }
    }

    static void compareEnds(String message) {
        int start = 0;
        int end = message.length();
        console.log("start < end? " + (Integer.compare(start, end) < 0));
        console.log("start > end? " + (Integer.compare(start, end) > 0));
        console.log("start == start? " + (start == start));
        console.log("end < end? " + (Integer.compare(end, end) < 0));
        console.log("end <= end? " + (Integer.compare(end, end) <= 0));
    }

    static String fromCodeNoInline(int code) throws InvalidCodePoint {
        console.log("From 0x" + Integer.toString(code, 16));
        return Core.stringFromCodePoint(code);
    }

    static String fromCodesNoInline(List<Integer> codes) throws InvalidCodePoint {
        console.log("From codes");
        return Core.stringFromCodePoints(codes);
    }

    static void checkParse(String string, @Nullable Integer radix) {
        String converted;
        try {
            converted = Integer.toString(radix != null ? Core.stringToInt(string, radix) : Core.stringToInt(string));
        } catch (ParseError ignored) {
            converted = "failed";
        }
        console.log("Parse " + string + " -> " + converted);
    }

    static void checkParse(String string) {
        checkParse(string, null);
    }

    static int reportFound(String label, String haystack, String needle, @Nullable Integer start) {
        int start2 = start != null ? start : 0;
        int index = haystack.indexOf(needle, start2);
        if (index >= 0) {
            String before = Core.stringSlice(haystack, Core.stringPrev(hay, start2), index);
            console.log(label + ": " + before + "...");
            return Core.stringNext(haystack, index);
        } else {
            console.log(label + ": (nope)");
            return 0;
        }
    }

    static int reportFound(String label, String haystack, String needle) {
        return reportFound(label, haystack, needle, null);
    }

    static int foundAt(String haystack, String needle, @Nullable Integer start) {
        int start2 = start != null ? start : 0;
        int index = haystack.indexOf(needle, start2);
        if (index >= 0) {
            return Core.stringCountBetween(haystack, 0, index);
        } else {
            return -1;
        }
    }

    static int foundAt(String haystack, String needle) {
        return foundAt(haystack, needle, null);
    }

    static {
        console = Core.getConsole(Logger.getLogger("read"));
        reportSliceInfo("κόσμε𐆊");
        reportSliceInfo("Hi");
        compareEnds("Hi");
        console.log(Core.stringFromCodePoint(127757));
        console.log(fromCodeNoInline(127757));
        String t;
        try {
            t = fromCodeNoInline(1114112);
        } catch (InvalidCodePoint ignored) {
            t = "nope";
        }
        console.log(t);
        String t2;
        try {
            t2 = fromCodeNoInline(55296);
        } catch (InvalidCodePoint ignored2) {
            t2 = "no to surrogate";
        }
        console.log(t2);
        console.log(fromCodesNoInline(List.of(128059, 8205, 10052, 65039)));
        String t3;
        try {
            t3 = fromCodesNoInline(List.of(55296, 56320));
        } catch (InvalidCodePoint ignored3) {
            t3 = "no to surrogates";
        }
        console.log(t3);
        String t4;
        try {
            t4 = fromCodesNoInline(List.of(65, -1, 56320));
        } catch (InvalidCodePoint e) {
            t4 = Integer.toString(e.codePoint, 16);
        }
        console.log(t4);
        String t5;
        try {
            t5 = Core.stringFromCodePoint(57343);
        } catch (InvalidCodePoint e2) {
            t5 = e2.getMessage();
        }
        console.log(t5);
        String t6;
        try {
            t6 = Integer.toString(Core.stringToInt("x"));
        } catch (ParseError e3) {
            t6 = e3.getMessage();
        }
        console.log(t6);
        String t7;
        try {
            t7 = Integer.toString(Core.float64ToInt(1e300));
        } catch (ParseError e4) {
            t7 = e4.reason;
        }
        console.log(t7);
        long t8;
        try {
            t8 = Core.stringToInt64("12");
        } catch (ParseError ignored4) {
            t8 = -1L;
        }
        console.log(Long.toString(t8));
        checkParse("-1", null);
        checkParse("2", null);
        checkParse(" 2 ", null);
        checkParse("2.0", null);
        checkParse("2a", null);
        checkParse("0x10", null);
        checkParse("totally", null);
        checkParse("2a", 16);
        checkParse("20", 7);
        checkParse("20", 17);
        checkParse("20", 37);
        checkParse("10", 0);
        checkParse("0", 1);
        String t9;
        try {
            t9 = Long.toString(Core.stringToInt64("10", 0));
        } catch (ParseError e5) {
            t9 = e5.reason;
        }
        console.log(t9);
        long t10;
        try {
            t10 = Core.stringToInt64("10", -2);
        } catch (ParseError ignored5) {
            t10 = -1L;
        }
        console.log(Long.toString(t10));
        {
            String s = "foo-bar";
            int after1_2 = Core.stringNext(s, 0);
            int after2_2 = Core.stringNext(s, after1_2);
            int after3 = Core.stringNext(s, after2_2);
            int after4 = Core.stringNext(s, after3);
            int after4b = Core.stringStep(s, 0, 4);
            int before1 = Core.stringPrev(s, after4b);
            int before3 = Core.stringStep(s, before1, -2);
            int before3b = Core.stringStep(s, before3, 0);
            int wayBefore = Core.stringStep(s, before3, -100);
            int wayAfter = Core.stringStep(s, before3, 100);
            console.log("s[3:]  -> " + Core.stringSlice(s, after3, s.length()));
            console.log("s[:3]  -> " + Core.stringSlice(s, 0, after3));
            console.log("s[1:4] -> " + Core.stringSlice(s, after1_2, after4));
            console.log("s[:4]  -> " + Core.stringSlice(s, 0, after4));
            console.log("s[1:4] -> " + Core.stringSlice(s, before3b, after4b));
            console.log("s[-many:many] -> " + Core.stringSlice(s, wayBefore, wayAfter));
        }
        hay = "It was the best of times, it was the worst of times, ...";
        String needle = "time";
        int after1 = reportFound("found 1", hay, needle);
        int after2 = reportFound("found 2", hay, needle, after1);
        reportFound("found 3", hay, needle, after2);
        reportFound("hay 2", "time comes for us all", needle);
        reportFound("hay 3", "in the nick of time", needle);
        reportFound("hay 4", "my name is tim", needle);
        reportFound("hay 5", "tim", needle);
        String nulHay = "ab\u0000#\u0000";
        console.log("nul: " + foundAt(nulHay, "\u0000"));
        int afterNul = Core.stringNext(nulHay, Core.stringNext(nulHay, Core.stringNext(nulHay, 0)));
        console.log("nul again: " + foundAt(nulHay, "\u0000", afterNul));
        console.log("soft hyphen: " + foundAt("ab\u00ad#", "\u00ad"));
        console.log("past soft hyphen: " + foundAt("ab\u00ad#", "b#"));
        console.log("zwj: " + foundAt("a\u200db", "ab"));
        console.log("composed: " + foundAt("é", "é"));
    }
}
