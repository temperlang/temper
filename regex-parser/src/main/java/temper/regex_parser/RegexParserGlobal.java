package temper.regex_parser;
import temper.core.Core;
import temper.std.regex.RegexNode;
import temper.std.regex.CodePoints;
import temper.std.regex.CodeRange;
import temper.std.regex.CodeSet;
import java.util.List;
import temper.std.regex.CodePart;
import temper.std.regex.SpecialSet;
import temper.std.regex.Sequence;
import temper.std.regex.Or;
import temper.std.regex.Repeat;
import temper.std.regex.Capture;
import temper.std.regex.Regex;
import temper.std.regex.Special;
import java.util.Map;
import temper.core.Nullable;
import java.util.function.Function;
import java.util.ArrayList;
import static temper.std.regex.RegexGlobal.Word;
import static temper.std.regex.RegexGlobal.Digit;
import static temper.std.regex.RegexGlobal.Space;
public final class RegexParserGlobal {
    private RegexParserGlobal() {
    }
    static final Map<String, RegexNode> noSlots__294;
    static void formatTo(StringBuilder out__339, RegexNode node__340) {
        if (node__340 instanceof CodePoints) {
            String this__1063 = Core.cast(CodePoints.class, node__340).getValue();
            int index__1065 = 0;
            while (Core.stringHasIndex(this__1063, index__1065)) {
                int codePoint__1066 = this__1063.codePointAt(index__1065);
                int c__342 = codePoint__1066;
                RegexParserGlobal.appendCode(out__339, c__342, false);
                index__1065 = Core.stringNext(this__1063, index__1065);
            }
        } else if (node__340 instanceof CodeRange) {
            CodeRange t_764 = Core.cast(CodeRange.class, node__340);
            out__339.append("[");
            RegexParserGlobal.appendCode(out__339, t_764.getMin(), true);
            out__339.append("-");
            RegexParserGlobal.appendCode(out__339, t_764.getMax(), true);
            out__339.append("]");
        } else if (node__340 instanceof CodeSet) {
            CodeSet t_767 = Core.cast(CodeSet.class, node__340);
            RegexParserGlobal.formatSet(out__339, t_767);
        } else if (node__340 instanceof Or) {
            Or t_770 = Core.cast(Or.class, node__340);
            String separator__343 = "";
            List<RegexNode> this__1067 = t_770.getItems();
            int n__1069 = this__1067.size();
            int i__1070 = 0;
            while (i__1070 < n__1069) {
                RegexNode el__1071 = Core.listGet(this__1067, i__1070);
                i__1070 = i__1070 + 1;
                RegexNode item__344 = el__1071;
                out__339.append(separator__343);
                separator__343 = "|";
                RegexParserGlobal.formatTo(out__339, item__344);
            }
        } else if (node__340 instanceof Sequence) {
            List<RegexNode> this__1072 = Core.cast(Sequence.class, node__340).getItems();
            int n__1074 = this__1072.size();
            int i__1075 = 0;
            while (i__1075 < n__1074) {
                RegexNode el__1076 = Core.listGet(this__1072, i__1075);
                i__1075 = i__1075 + 1;
                RegexNode item__345 = el__1076;
                boolean t_757 = item__345 instanceof Or;
                RegexParserGlobal.formatGrouped(out__339, item__345, t_757);
            }
        } else if (node__340 instanceof Repeat) {
            Repeat t_776 = Core.cast(Repeat.class, node__340);
            RegexParserGlobal.formatGrouped(out__339, t_776.getItem(), RegexParserGlobal.needsGroupToRepeat(t_776.getItem()));
            int min__346 = t_776.getMin();
            @Nullable Integer max__347 = t_776.getMax();
            if (max__347 == null) {
                String t_777;
                switch (min__346) {
                    case 0:
                        {
                        t_777 = "*";
                        break;
                    }
                    case 1:
                        {
                        t_777 = "+";
                        break;
                    }
                    default:
                        {
                        t_777 = "{" + Integer.toString(min__346) + ",}";
                        break;
                    }
                }
                out__339.append(t_777);
            } else {
                boolean t_780;
                int max_733 = max__347.intValue();
                if (min__346 == 0) {
                    t_780 = max_733 == 1;
                } else {
                    t_780 = false;
                }
                if (t_780) {
                    out__339.append("?");
                } else if (min__346 == max_733) {
                    out__339.append("{" + Integer.toString(min__346) + "}");
                } else {
                    out__339.append("{" + Integer.toString(min__346) + "," + Integer.toString(max_733) + "}");
                }
            }
            if (t_776.isReluctant()) {
                out__339.append("?");
            }
        } else if (node__340 instanceof Capture) {
            Capture t_784 = Core.cast(Capture.class, node__340);
            out__339.append("(?" + t_784.getName() + "=");
            RegexParserGlobal.formatTo(out__339, t_784.getItem());
            out__339.append(")");
        } else if (node__340 instanceof Special) {
            Special t_787 = Core.cast(Special.class, node__340);
            String t_1081 = RegexParserGlobal.specialText(t_787);
            out__339.append(t_1081);
        } else {
            throw Core.bubble();
        }
    }
    static boolean hasCode(String text__361, int code__362) {
        boolean return__199;
        fn__363: {
            String this__1000 = text__361;
            int index__1002 = 0;
            while (Core.stringHasIndex(this__1000, index__1002)) {
                int codePoint__1003 = this__1000.codePointAt(index__1002);
                int c__364 = codePoint__1003;
                if (c__364 == code__362) {
                    return__199 = true;
                    break fn__363;
                }
                index__1002 = Core.stringNext(this__1000, index__1002);
            }
            return false;
        }
        return return__199;
    }
    static boolean isPatternSpace(int c__375) {
        return RegexParserGlobal.hasCode(" \t\n\u000b\f\r", c__375);
    }
    static boolean isAsciiLetter(int c__348) {
        boolean t_998;
        if (c__348 >= 97) {
            t_998 = c__348 <= 122;
        } else {
            t_998 = false;
        }
        if (t_998) {
            return true;
        } else if (c__348 >= 65) {
            return c__348 <= 90;
        } else {
            return false;
        }
    }
    static int otherCase(int letter__377) {
        boolean t_996;
        if (letter__377 >= 97) {
            t_996 = letter__377 <= 122;
        } else {
            t_996 = false;
        }
        if (t_996) {
            return letter__377 - 97 + 65;
        } else {
            return letter__377 - 65 + 97;
        }
    }
    static RegexNode ignoreCaseText(String text__385) {
        List<RegexNode> pieces__387 = new ArrayList<>();
        StringBuilder literal__388 = new StringBuilder();
        String this__1004 = text__385;
        int index__1006 = 0;
        while (Core.stringHasIndex(this__1004, index__1006)) {
            int codePoint__1007 = this__1004.codePointAt(index__1006);
            int c__389 = codePoint__1007;
            if (RegexParserGlobal.isAsciiLetter(c__389)) {
                if (!literal__388.toString().isEmpty()) {
                    Core.listAdd(pieces__387, new CodePoints(literal__388.toString()));
                    literal__388.setLength(0);
                }
                StringBuilder both__390 = new StringBuilder();
                Core.stringBuilderAppendCodePoint(both__390, c__389);
                Core.stringBuilderAppendCodePoint(both__390, RegexParserGlobal.otherCase(c__389));
                Core.listAdd(pieces__387, new CodeSet(List.of(new CodePoints(both__390.toString()))));
            } else {
                Core.stringBuilderAppendCodePoint(literal__388, c__389);
            }
            index__1006 = Core.stringNext(this__1004, index__1006);
        }
        if (!literal__388.toString().isEmpty()) {
            Core.listAdd(pieces__387, new CodePoints(literal__388.toString()));
        }
        int subject_576 = pieces__387.size();
        switch (subject_576) {
            case 0:
                {
                return new CodePoints(text__385);
            }
            case 1:
                {
                return Core.listGet(pieces__387, 0);
            }
            default:
                {
                return new Sequence(List.copyOf(pieces__387));
            }
        }
    }
    static List<CodePart> ignoreCaseParts(List<CodePart> items__391) {
        List<CodePart> parts__393 = new ArrayList<>(items__391);
        List<CodePart> this__1008 = items__391;
        int n__1010 = this__1008.size();
        int i__1011 = 0;
        while (i__1011 < n__1010) {
            CodePart el__1012 = Core.listGet(this__1008, i__1011);
            i__1011 = i__1011 + 1;
            CodePart item__394 = el__1012;
            if (item__394 instanceof CodePoints) {
                String this__1013 = Core.cast(CodePoints.class, item__394).getValue();
                int index__1015 = 0;
                while (Core.stringHasIndex(this__1013, index__1015)) {
                    int codePoint__1016 = this__1013.codePointAt(index__1015);
                    int c__395 = codePoint__1016;
                    if (RegexParserGlobal.isAsciiLetter(c__395)) {
                        String t_989 = Core.stringFromCodePoint(RegexParserGlobal.otherCase(c__395));
                        Core.listAdd(parts__393, new CodePoints(t_989));
                    }
                    index__1015 = Core.stringNext(this__1013, index__1015);
                }
            } else if (item__394 instanceof CodeRange) {
                CodeRange t_995 = Core.cast(CodeRange.class, item__394);
                int lowMin__396 = Math.max(t_995.getMin(), 97);
                int lowMax__397 = Math.min(t_995.getMax(), 122);
                if (lowMin__396 <= lowMax__397) {
                    Core.listAdd(parts__393, new CodeRange(RegexParserGlobal.otherCase(lowMin__396), RegexParserGlobal.otherCase(lowMax__397)));
                }
                int upMin__398 = Math.max(t_995.getMin(), 65);
                int upMax__399 = Math.min(t_995.getMax(), 90);
                if (upMin__398 <= upMax__399) {
                    Core.listAdd(parts__393, new CodeRange(RegexParserGlobal.otherCase(upMin__398), RegexParserGlobal.otherCase(upMax__399)));
                }
            }
        }
        return List.copyOf(parts__393);
    }
    static RegexNode ignoreCase(RegexNode node__379) {
        if (node__379 instanceof CodePoints) {
            return RegexParserGlobal.ignoreCaseText(Core.cast(CodePoints.class, node__379).getValue());
        } else if (node__379 instanceof CodeRange) {
            CodeRange t_973 = Core.cast(CodeRange.class, node__379);
            return new CodeSet(RegexParserGlobal.ignoreCaseParts(List.of(t_973)));
        } else if (node__379 instanceof CodeSet) {
            CodeSet t_976 = Core.cast(CodeSet.class, node__379);
            return new CodeSet(RegexParserGlobal.ignoreCaseParts(t_976.getItems()), t_976.isNegated());
        } else if (node__379 instanceof Or) {
            Function<RegexNode, RegexNode> fn__1097 = item__381 -> RegexParserGlobal.ignoreCase(item__381);
            return new Or(Core.listMapObjToObj(Core.cast(Or.class, node__379).getItems(), fn__1097));
        } else if (node__379 instanceof Sequence) {
            Function<RegexNode, RegexNode> fn__1096 = item__383 -> RegexParserGlobal.ignoreCase(item__383);
            return new Sequence(Core.listMapObjToObj(Core.cast(Sequence.class, node__379).getItems(), fn__1096));
        } else if (node__379 instanceof Repeat) {
            Repeat t_985 = Core.cast(Repeat.class, node__379);
            return new Repeat(RegexParserGlobal.ignoreCase(t_985.getItem()), t_985.getMin(), t_985.getMax(), t_985.isReluctant());
        } else if (node__379 instanceof Capture) {
            Capture t_988 = Core.cast(Capture.class, node__379);
            return new Capture(t_988.getName(), RegexParserGlobal.ignoreCase(t_988.getItem()));
        } else {
            return node__379;
        }
    }
    static SpecialSet shorthandSet(int letter__357) {
        boolean t_964;
        if (letter__357 == 100) {
            t_964 = true;
        } else {
            t_964 = letter__357 == 68;
        }
        if (t_964) {
            return Digit;
        } else {
            boolean t_966;
            if (letter__357 == 115) {
                t_966 = true;
            } else {
                t_966 = letter__357 == 83;
            }
            if (t_966) {
                return Space;
            } else {
                return Word;
            }
        }
    }
    static String shorthandMembers(int letter__359) {
        boolean t_960;
        if (letter__359 == 100) {
            t_960 = true;
        } else {
            t_960 = letter__359 == 68;
        }
        if (t_960) {
            return "0123456789";
        } else {
            boolean t_962;
            if (letter__359 == 115) {
                t_962 = true;
            } else {
                t_962 = letter__359 == 83;
            }
            if (t_962) {
                return "\t\n\u000b\f\r ";
            } else {
                return "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
            }
        }
    }
    static CodeSet negatedClass(List<CodePart> items__365, String itemMembers__366, List<String> required__367) {
        StringBuilder kept__369 = new StringBuilder();
        String this__1017 = Core.listGet(required__367, 0);
        int index__1019 = 0;
        while (Core.stringHasIndex(this__1017, index__1019)) {
            int codePoint__1020 = this__1017.codePointAt(index__1019);
            int code__370 = codePoint__1020;
            boolean keep__371 = !RegexParserGlobal.hasCode(itemMembers__366, code__370);
            List<String> this__1021 = required__367;
            int n__1023 = this__1021.size();
            int i__1024 = 0;
            while (i__1024 < n__1023) {
                String el__1025 = Core.listGet(this__1021, i__1024);
                i__1024 = i__1024 + 1;
                String members__372 = el__1025;
                if (!RegexParserGlobal.hasCode(members__372, code__370)) {
                    keep__371 = false;
                }
            }
            List<CodePart> this__1026 = items__365;
            int n__1028 = this__1026.size();
            int i__1029 = 0;
            while (i__1029 < n__1028) {
                CodePart el__1030 = Core.listGet(this__1026, i__1029);
                i__1029 = i__1029 + 1;
                CodePart item__373 = el__1030;
                if (item__373 instanceof CodePoints) {
                    if (RegexParserGlobal.hasCode(Core.cast(CodePoints.class, item__373).getValue(), code__370)) {
                        keep__371 = false;
                    }
                } else if (item__373 instanceof CodeRange) {
                    CodeRange t_957 = Core.cast(CodeRange.class, item__373);
                    boolean t_958;
                    if (t_957.getMin() <= code__370) {
                        t_958 = code__370 <= t_957.getMax();
                    } else {
                        t_958 = false;
                    }
                    if (t_958) {
                        keep__371 = false;
                    }
                }
            }
            if (keep__371) {
                Core.stringBuilderAppendCodePoint(kept__369, code__370);
            }
            index__1019 = Core.stringNext(this__1017, index__1019);
        }
        String codes__374 = kept__369.toString();
        if (codes__374.isEmpty()) {
            return new CodeSet(List.of());
        } else {
            return new CodeSet(List.of(new CodePoints(codes__374)));
        }
    }
    static boolean isAsciiPunctuation(int c__352) {
        boolean t_946;
        if (c__352 >= 33) {
            t_946 = c__352 <= 47;
        } else {
            t_946 = false;
        }
        if (t_946) {
            return true;
        } else {
            boolean t_948;
            if (c__352 >= 58) {
                t_948 = c__352 <= 64;
            } else {
                t_948 = false;
            }
            if (t_948) {
                return true;
            } else {
                boolean t_950;
                if (c__352 >= 91) {
                    t_950 = c__352 <= 96;
                } else {
                    t_950 = false;
                }
                if (t_950) {
                    return true;
                } else if (c__352 >= 123) {
                    return c__352 <= 126;
                } else {
                    return false;
                }
            }
        }
    }
    static boolean isAsciiDigit(int c__350) {
        if (c__350 >= 48) {
            return c__350 <= 57;
        } else {
            return false;
        }
    }
    static boolean isCaptureName(String name__354) {
        boolean return__196;
        fn__355: {
            boolean t_944;
            if (name__354.isEmpty()) {
                t_944 = true;
            } else {
                t_944 = !RegexParserGlobal.isAsciiLetter(name__354.codePointAt(0));
            }
            if (t_944) {
                return__196 = false;
                break fn__355;
            }
            String this__1031 = name__354;
            int index__1033 = 0;
            while (Core.stringHasIndex(this__1031, index__1033)) {
                int codePoint__1034 = this__1031.codePointAt(index__1033);
                int c__356 = codePoint__1034;
                boolean t_942;
                if (!RegexParserGlobal.isAsciiLetter(c__356)) {
                    t_942 = !RegexParserGlobal.isAsciiDigit(c__356);
                } else {
                    t_942 = false;
                }
                if (t_942) {
                    return__196 = false;
                    break fn__355;
                }
                index__1033 = Core.stringNext(this__1031, index__1033);
            }
            return true;
        }
        return return__196;
    }
    public static RegexNode parseWith(String src__300, Map<String, RegexNode> slots__301) {
        return new Parser(src__300, slots__301).readAll();
    }
    public static Regex compileWith(String src__295, Map<String, RegexNode> slots__296) {
        RegexNode t_1084 = RegexParserGlobal.parseWith(src__295, slots__296);
        return t_1084.compiled();
    }
    public static RegexNode parse(String src__303) {
        return new Parser(src__303).readAll();
    }
    public static Regex compile(String src__298) {
        RegexNode t_1083 = RegexParserGlobal.parse(src__298);
        return t_1083.compiled();
    }
    static boolean isMeta(int c__317) {
        return RegexParserGlobal.hasCode("\\^$.|?*+()[]{}", c__317);
    }
    static void appendCode(StringBuilder out__319, int code__320, boolean inClass__321) {
        switch (code__320) {
            case 9:
                {
                out__319.append("\\t");
                break;
            }
            case 10:
                {
                out__319.append("\\n");
                break;
            }
            case 11:
                {
                out__319.append("\\v");
                break;
            }
            case 12:
                {
                out__319.append("\\f");
                break;
            }
            case 13:
                {
                out__319.append("\\r");
                break;
            }
            default:
                {
                boolean t_820;
                if (code__320 < 32) {
                    t_820 = true;
                } else {
                    t_820 = code__320 == 127;
                }
                if (t_820) {
                    out__319.append("\\" + "u{" + Integer.toString(code__320, 16) + "}");
                } else {
                    boolean t_822;
                    if (RegexParserGlobal.isMeta(code__320)) {
                        t_822 = true;
                    } else if (inClass__321) {
                        t_822 = code__320 == 45;
                    } else {
                        t_822 = false;
                    }
                    if (t_822) {
                        out__319.append("\\");
                    }
                    Core.stringBuilderAppendCodePoint(out__319, code__320);
                }
                break;
            }
        }
    }
    static String specialText(Special special__323) {
        if (special__323 instanceof SpecialSet) {
            SpecialSet t_819 = Core.cast(SpecialSet.class, special__323);
            Regex regex__325 = t_819.compiled();
            String subject_656 = Boolean.toString(regex__325.found("5")) + " " + Boolean.toString(regex__325.found("a")) + " " + Boolean.toString(regex__325.found(" "));
            if (subject_656.equals("true false false")) {
                return "\\d";
            } else if (subject_656.equals("false false true")) {
                return "\\s";
            } else if (subject_656.equals("true true false")) {
                return "\\w";
            } else {
                throw Core.bubble();
            }
        } else {
            Regex aS__326 = new Sequence(List.of(new CodePoints("a"), special__323)).compiled();
            Regex sA__327 = new Sequence(List.of(special__323, new CodePoints("a"))).compiled();
            String subject_660 = Boolean.toString(aS__326.found("a")) + " " + Boolean.toString(aS__326.found("ab")) + " " + Boolean.toString(sA__327.found("a")) + " " + Boolean.toString(sA__327.found("ba"));
            if (subject_660.equals("false true false true")) {
                return ".";
            } else if (subject_660.equals("false false true false")) {
                return "^";
            } else if (subject_660.equals("true false false false")) {
                return "$";
            } else if (subject_660.equals("true false true false")) {
                return "\\b";
            } else {
                throw Core.bubble();
            }
        }
    }
    static void formatSet(StringBuilder out__328, CodeSet set__329) {
        fn__330: {
            String t_816;
            boolean t_810;
            if (set__329.isNegated()) {
                if (set__329.getItems().size() == 1) {
                    t_810 = Core.listGet(set__329.getItems(), 0) instanceof SpecialSet;
                } else {
                    t_810 = false;
                }
            } else {
                t_810 = false;
            }
            if (t_810) {
                String t_815;
                SpecialSet t_814 = Core.cast(SpecialSet.class, Core.listGet(set__329.getItems(), 0));
                String subject_665 = RegexParserGlobal.specialText(t_814);
                if (subject_665.equals("\\d")) {
                    t_815 = "\\D";
                } else if (subject_665.equals("\\s")) {
                    t_815 = "\\S";
                } else {
                    t_815 = "\\W";
                }
                out__328.append(t_815);
                break fn__330;
            }
            if (set__329.isNegated()) {
                t_816 = "[^";
            } else {
                t_816 = "[";
            }
            out__328.append(t_816);
            List<CodePart> this__1054 = set__329.getItems();
            int n__1056 = this__1054.size();
            int i__1057 = 0;
            while (i__1057 < n__1056) {
                CodePart el__1058 = Core.listGet(this__1054, i__1057);
                i__1057 = i__1057 + 1;
                CodePart item__331 = el__1058;
                if (item__331 instanceof CodePoints) {
                    String this__1059 = Core.cast(CodePoints.class, item__331).getValue();
                    int index__1061 = 0;
                    while (Core.stringHasIndex(this__1059, index__1061)) {
                        int codePoint__1062 = this__1059.codePointAt(index__1061);
                        int c__332 = codePoint__1062;
                        RegexParserGlobal.appendCode(out__328, c__332, true);
                        index__1061 = Core.stringNext(this__1059, index__1061);
                    }
                } else if (item__331 instanceof CodeRange) {
                    CodeRange t_804 = Core.cast(CodeRange.class, item__331);
                    RegexParserGlobal.appendCode(out__328, t_804.getMin(), true);
                    out__328.append("-");
                    RegexParserGlobal.appendCode(out__328, t_804.getMax(), true);
                } else if (item__331 instanceof SpecialSet) {
                    SpecialSet t_807 = Core.cast(SpecialSet.class, item__331);
                    String t_1082 = RegexParserGlobal.specialText(t_807);
                    out__328.append(t_1082);
                } else {
                    throw Core.bubble();
                }
            }
            out__328.append("]");
        }
    }
    static void formatGrouped(StringBuilder out__335, RegexNode node__336, boolean grouped__337) {
        if (grouped__337) {
            out__335.append("(?:");
            RegexParserGlobal.formatTo(out__335, node__336);
            out__335.append(")");
        } else {
            RegexParserGlobal.formatTo(out__335, node__336);
        }
    }
    static boolean needsGroupToRepeat(RegexNode node__333) {
        if (node__333 instanceof CodePoints) {
            CodePoints t_790 = Core.cast(CodePoints.class, node__333);
            if (!Core.stringHasAtLeast(t_790.getValue(), 0, t_790.getValue().length(), 1)) {
                return true;
            } else {
                return Core.stringHasAtLeast(t_790.getValue(), 0, t_790.getValue().length(), 2);
            }
        } else {
            boolean t_795;
            if (node__333 instanceof Sequence) {
                t_795 = true;
            } else if (node__333 instanceof Or) {
                t_795 = true;
            } else {
                t_795 = node__333 instanceof Repeat;
            }
            if (t_795) {
                return true;
            } else {
                return false;
            }
        }
    }
    /**
     * Writes a node as pattern text that parses back to an equivalent node.
     * Bubbles for node types this library doesn't know or code points that
     * aren't valid.
     */
    public static String format(RegexNode node__305) {
        StringBuilder out__307 = new StringBuilder();
        RegexParserGlobal.formatTo(out__307, node__305);
        return out__307.toString();
    }
    /**
     * Escapes text so it matches literally, outside of a (?x:...) group.
     */
    public static String escape(String text__308) {
        StringBuilder out__310 = new StringBuilder();
        String this__1077 = text__308;
        int index__1079 = 0;
        while (Core.stringHasIndex(this__1077, index__1079)) {
            int codePoint__1080 = this__1077.codePointAt(index__1079);
            int c__311 = codePoint__1080;
            RegexParserGlobal.appendCode(out__310, c__311, false);
            index__1079 = Core.stringNext(this__1077, index__1079);
        }
        return out__310.toString();
    }
    public static @Nullable String parseError(String src__312, @Nullable Map<String, RegexNode> slots__684) {
        Map<String, RegexNode> slots__313;
        if (slots__684 == null) {
            slots__313 = noSlots__294;
        } else {
            slots__313 = slots__684;
        }
        Parser parser__315 = new Parser(src__312, slots__313);
        boolean parsed__316;
        try {
            parser__315.readAll();
            parsed__316 = true;
        } catch (RuntimeException ignored$7) {
            parsed__316 = false;
        }
        if (parsed__316) {
            return null;
        } else {
            return parser__315.getErrorMessage();
        }
    }
    public static @Nullable String parseError(String src__312) {
        return parseError(src__312, null);
    }
    static {
        noSlots__294 = Core.mapConstructor(List.of());
    }
}
