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
    static final Map<String, RegexNode> noSlots__293;
    static void formatTo(StringBuilder out__338, RegexNode node__339) {
        if (node__339 instanceof CodePoints) {
            String this__1061 = Core.cast(CodePoints.class, node__339).getValue();
            int index__1063 = 0;
            while (Core.stringHasIndex(this__1061, index__1063)) {
                int codePoint__1064 = this__1061.codePointAt(index__1063);
                int c__341 = codePoint__1064;
                RegexParserGlobal.appendCode(out__338, c__341, false);
                index__1063 = Core.stringNext(this__1061, index__1063);
            }
        } else if (node__339 instanceof CodeRange) {
            CodeRange t_762 = Core.cast(CodeRange.class, node__339);
            out__338.append("[");
            RegexParserGlobal.appendCode(out__338, t_762.getMin(), true);
            out__338.append("-");
            RegexParserGlobal.appendCode(out__338, t_762.getMax(), true);
            out__338.append("]");
        } else if (node__339 instanceof CodeSet) {
            CodeSet t_765 = Core.cast(CodeSet.class, node__339);
            RegexParserGlobal.formatSet(out__338, t_765);
        } else if (node__339 instanceof Or) {
            Or t_768 = Core.cast(Or.class, node__339);
            String separator__342 = "";
            List<RegexNode> this__1065 = t_768.getItems();
            int n__1067 = this__1065.size();
            int i__1068 = 0;
            while (i__1068 < n__1067) {
                RegexNode el__1069 = Core.listGet(this__1065, i__1068);
                i__1068 = i__1068 + 1;
                RegexNode item__343 = el__1069;
                out__338.append(separator__342);
                separator__342 = "|";
                RegexParserGlobal.formatTo(out__338, item__343);
            }
        } else if (node__339 instanceof Sequence) {
            List<RegexNode> this__1070 = Core.cast(Sequence.class, node__339).getItems();
            int n__1072 = this__1070.size();
            int i__1073 = 0;
            while (i__1073 < n__1072) {
                RegexNode el__1074 = Core.listGet(this__1070, i__1073);
                i__1073 = i__1073 + 1;
                RegexNode item__344 = el__1074;
                boolean t_755 = item__344 instanceof Or;
                RegexParserGlobal.formatGrouped(out__338, item__344, t_755);
            }
        } else if (node__339 instanceof Repeat) {
            Repeat t_774 = Core.cast(Repeat.class, node__339);
            RegexParserGlobal.formatGrouped(out__338, t_774.getItem(), RegexParserGlobal.needsGroupToRepeat(t_774.getItem()));
            int min__345 = t_774.getMin();
            @Nullable Integer max__346 = t_774.getMax();
            if (max__346 == null) {
                String t_775;
                switch (min__345) {
                    case 0:
                        {
                        t_775 = "*";
                        break;
                    }
                    case 1:
                        {
                        t_775 = "+";
                        break;
                    }
                    default:
                        {
                        t_775 = "{" + Integer.toString(min__345) + ",}";
                        break;
                    }
                }
                out__338.append(t_775);
            } else {
                boolean t_778;
                int max_731 = max__346.intValue();
                if (min__345 == 0) {
                    t_778 = max_731 == 1;
                } else {
                    t_778 = false;
                }
                if (t_778) {
                    out__338.append("?");
                } else if (min__345 == max_731) {
                    out__338.append("{" + Integer.toString(min__345) + "}");
                } else {
                    out__338.append("{" + Integer.toString(min__345) + "," + Integer.toString(max_731) + "}");
                }
            }
            if (t_774.isReluctant()) {
                out__338.append("?");
            }
        } else if (node__339 instanceof Capture) {
            Capture t_782 = Core.cast(Capture.class, node__339);
            out__338.append("(?" + t_782.getName() + "=");
            RegexParserGlobal.formatTo(out__338, t_782.getItem());
            out__338.append(")");
        } else if (node__339 instanceof Special) {
            Special t_785 = Core.cast(Special.class, node__339);
            String t_1079 = RegexParserGlobal.specialText(t_785);
            out__338.append(t_1079);
        } else {
            throw Core.bubble();
        }
    }
    static boolean hasCode(String text__360, int code__361) {
        boolean return__198;
        fn__362: {
            String this__998 = text__360;
            int index__1000 = 0;
            while (Core.stringHasIndex(this__998, index__1000)) {
                int codePoint__1001 = this__998.codePointAt(index__1000);
                int c__363 = codePoint__1001;
                if (c__363 == code__361) {
                    return__198 = true;
                    break fn__362;
                }
                index__1000 = Core.stringNext(this__998, index__1000);
            }
            return false;
        }
        return return__198;
    }
    static boolean isPatternSpace(int c__374) {
        return RegexParserGlobal.hasCode(" \t\n\u000b\f\r", c__374);
    }
    static boolean isAsciiLetter(int c__347) {
        boolean t_996;
        if (c__347 >= 97) {
            t_996 = c__347 <= 122;
        } else {
            t_996 = false;
        }
        if (t_996) {
            return true;
        } else if (c__347 >= 65) {
            return c__347 <= 90;
        } else {
            return false;
        }
    }
    static int otherCase(int letter__376) {
        boolean t_994;
        if (letter__376 >= 97) {
            t_994 = letter__376 <= 122;
        } else {
            t_994 = false;
        }
        if (t_994) {
            return letter__376 - 97 + 65;
        } else {
            return letter__376 - 65 + 97;
        }
    }
    static RegexNode ignoreCaseText(String text__384) {
        List<RegexNode> pieces__386 = new ArrayList<>();
        StringBuilder literal__387 = new StringBuilder();
        String this__1002 = text__384;
        int index__1004 = 0;
        while (Core.stringHasIndex(this__1002, index__1004)) {
            int codePoint__1005 = this__1002.codePointAt(index__1004);
            int c__388 = codePoint__1005;
            if (RegexParserGlobal.isAsciiLetter(c__388)) {
                if (!literal__387.toString().isEmpty()) {
                    Core.listAdd(pieces__386, new CodePoints(literal__387.toString()));
                    literal__387.setLength(0);
                }
                StringBuilder both__389 = new StringBuilder();
                Core.stringBuilderAppendCodePoint(both__389, c__388);
                Core.stringBuilderAppendCodePoint(both__389, RegexParserGlobal.otherCase(c__388));
                Core.listAdd(pieces__386, new CodeSet(List.of(new CodePoints(both__389.toString()))));
            } else {
                Core.stringBuilderAppendCodePoint(literal__387, c__388);
            }
            index__1004 = Core.stringNext(this__1002, index__1004);
        }
        if (!literal__387.toString().isEmpty()) {
            Core.listAdd(pieces__386, new CodePoints(literal__387.toString()));
        }
        int subject_575 = pieces__386.size();
        switch (subject_575) {
            case 0:
                {
                return new CodePoints(text__384);
            }
            case 1:
                {
                return Core.listGet(pieces__386, 0);
            }
            default:
                {
                return new Sequence(List.copyOf(pieces__386));
            }
        }
    }
    static List<CodePart> ignoreCaseParts(List<CodePart> items__390) {
        List<CodePart> parts__392 = new ArrayList<>(items__390);
        List<CodePart> this__1006 = items__390;
        int n__1008 = this__1006.size();
        int i__1009 = 0;
        while (i__1009 < n__1008) {
            CodePart el__1010 = Core.listGet(this__1006, i__1009);
            i__1009 = i__1009 + 1;
            CodePart item__393 = el__1010;
            if (item__393 instanceof CodePoints) {
                String this__1011 = Core.cast(CodePoints.class, item__393).getValue();
                int index__1013 = 0;
                while (Core.stringHasIndex(this__1011, index__1013)) {
                    int codePoint__1014 = this__1011.codePointAt(index__1013);
                    int c__394 = codePoint__1014;
                    if (RegexParserGlobal.isAsciiLetter(c__394)) {
                        String t_987 = Core.stringFromCodePoint(RegexParserGlobal.otherCase(c__394));
                        Core.listAdd(parts__392, new CodePoints(t_987));
                    }
                    index__1013 = Core.stringNext(this__1011, index__1013);
                }
            } else if (item__393 instanceof CodeRange) {
                CodeRange t_993 = Core.cast(CodeRange.class, item__393);
                int lowMin__395 = Math.max(t_993.getMin(), 97);
                int lowMax__396 = Math.min(t_993.getMax(), 122);
                if (lowMin__395 <= lowMax__396) {
                    Core.listAdd(parts__392, new CodeRange(RegexParserGlobal.otherCase(lowMin__395), RegexParserGlobal.otherCase(lowMax__396)));
                }
                int upMin__397 = Math.max(t_993.getMin(), 65);
                int upMax__398 = Math.min(t_993.getMax(), 90);
                if (upMin__397 <= upMax__398) {
                    Core.listAdd(parts__392, new CodeRange(RegexParserGlobal.otherCase(upMin__397), RegexParserGlobal.otherCase(upMax__398)));
                }
            }
        }
        return List.copyOf(parts__392);
    }
    static RegexNode ignoreCase(RegexNode node__378) {
        if (node__378 instanceof CodePoints) {
            return RegexParserGlobal.ignoreCaseText(Core.cast(CodePoints.class, node__378).getValue());
        } else if (node__378 instanceof CodeRange) {
            CodeRange t_971 = Core.cast(CodeRange.class, node__378);
            return new CodeSet(RegexParserGlobal.ignoreCaseParts(List.of(t_971)));
        } else if (node__378 instanceof CodeSet) {
            CodeSet t_974 = Core.cast(CodeSet.class, node__378);
            return new CodeSet(RegexParserGlobal.ignoreCaseParts(t_974.getItems()), t_974.isNegated());
        } else if (node__378 instanceof Or) {
            Function<RegexNode, RegexNode> fn__1095 = item__380 -> RegexParserGlobal.ignoreCase(item__380);
            return new Or(Core.listMapObjToObj(Core.cast(Or.class, node__378).getItems(), fn__1095));
        } else if (node__378 instanceof Sequence) {
            Function<RegexNode, RegexNode> fn__1094 = item__382 -> RegexParserGlobal.ignoreCase(item__382);
            return new Sequence(Core.listMapObjToObj(Core.cast(Sequence.class, node__378).getItems(), fn__1094));
        } else if (node__378 instanceof Repeat) {
            Repeat t_983 = Core.cast(Repeat.class, node__378);
            return new Repeat(RegexParserGlobal.ignoreCase(t_983.getItem()), t_983.getMin(), t_983.getMax(), t_983.isReluctant());
        } else if (node__378 instanceof Capture) {
            Capture t_986 = Core.cast(Capture.class, node__378);
            return new Capture(t_986.getName(), RegexParserGlobal.ignoreCase(t_986.getItem()));
        } else {
            return node__378;
        }
    }
    static SpecialSet shorthandSet(int letter__356) {
        boolean t_962;
        if (letter__356 == 100) {
            t_962 = true;
        } else {
            t_962 = letter__356 == 68;
        }
        if (t_962) {
            return Digit;
        } else {
            boolean t_964;
            if (letter__356 == 115) {
                t_964 = true;
            } else {
                t_964 = letter__356 == 83;
            }
            if (t_964) {
                return Space;
            } else {
                return Word;
            }
        }
    }
    static String shorthandMembers(int letter__358) {
        boolean t_958;
        if (letter__358 == 100) {
            t_958 = true;
        } else {
            t_958 = letter__358 == 68;
        }
        if (t_958) {
            return "0123456789";
        } else {
            boolean t_960;
            if (letter__358 == 115) {
                t_960 = true;
            } else {
                t_960 = letter__358 == 83;
            }
            if (t_960) {
                return "\t\n\u000b\f\r ";
            } else {
                return "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
            }
        }
    }
    static CodeSet negatedClass(List<CodePart> items__364, String itemMembers__365, List<String> required__366) {
        StringBuilder kept__368 = new StringBuilder();
        String this__1015 = Core.listGet(required__366, 0);
        int index__1017 = 0;
        while (Core.stringHasIndex(this__1015, index__1017)) {
            int codePoint__1018 = this__1015.codePointAt(index__1017);
            int code__369 = codePoint__1018;
            boolean keep__370 = !RegexParserGlobal.hasCode(itemMembers__365, code__369);
            List<String> this__1019 = required__366;
            int n__1021 = this__1019.size();
            int i__1022 = 0;
            while (i__1022 < n__1021) {
                String el__1023 = Core.listGet(this__1019, i__1022);
                i__1022 = i__1022 + 1;
                String members__371 = el__1023;
                if (!RegexParserGlobal.hasCode(members__371, code__369)) {
                    keep__370 = false;
                }
            }
            List<CodePart> this__1024 = items__364;
            int n__1026 = this__1024.size();
            int i__1027 = 0;
            while (i__1027 < n__1026) {
                CodePart el__1028 = Core.listGet(this__1024, i__1027);
                i__1027 = i__1027 + 1;
                CodePart item__372 = el__1028;
                if (item__372 instanceof CodePoints) {
                    if (RegexParserGlobal.hasCode(Core.cast(CodePoints.class, item__372).getValue(), code__369)) {
                        keep__370 = false;
                    }
                } else if (item__372 instanceof CodeRange) {
                    CodeRange t_955 = Core.cast(CodeRange.class, item__372);
                    boolean t_956;
                    if (t_955.getMin() <= code__369) {
                        t_956 = code__369 <= t_955.getMax();
                    } else {
                        t_956 = false;
                    }
                    if (t_956) {
                        keep__370 = false;
                    }
                }
            }
            if (keep__370) {
                Core.stringBuilderAppendCodePoint(kept__368, code__369);
            }
            index__1017 = Core.stringNext(this__1015, index__1017);
        }
        String codes__373 = kept__368.toString();
        if (codes__373.isEmpty()) {
            return new CodeSet(List.of());
        } else {
            return new CodeSet(List.of(new CodePoints(codes__373)));
        }
    }
    static boolean isAsciiPunctuation(int c__351) {
        boolean t_944;
        if (c__351 >= 33) {
            t_944 = c__351 <= 47;
        } else {
            t_944 = false;
        }
        if (t_944) {
            return true;
        } else {
            boolean t_946;
            if (c__351 >= 58) {
                t_946 = c__351 <= 64;
            } else {
                t_946 = false;
            }
            if (t_946) {
                return true;
            } else {
                boolean t_948;
                if (c__351 >= 91) {
                    t_948 = c__351 <= 96;
                } else {
                    t_948 = false;
                }
                if (t_948) {
                    return true;
                } else if (c__351 >= 123) {
                    return c__351 <= 126;
                } else {
                    return false;
                }
            }
        }
    }
    static boolean isAsciiDigit(int c__349) {
        if (c__349 >= 48) {
            return c__349 <= 57;
        } else {
            return false;
        }
    }
    static boolean isCaptureName(String name__353) {
        boolean return__195;
        fn__354: {
            boolean t_942;
            if (name__353.isEmpty()) {
                t_942 = true;
            } else {
                t_942 = !RegexParserGlobal.isAsciiLetter(name__353.codePointAt(0));
            }
            if (t_942) {
                return__195 = false;
                break fn__354;
            }
            String this__1029 = name__353;
            int index__1031 = 0;
            while (Core.stringHasIndex(this__1029, index__1031)) {
                int codePoint__1032 = this__1029.codePointAt(index__1031);
                int c__355 = codePoint__1032;
                boolean t_940;
                if (!RegexParserGlobal.isAsciiLetter(c__355)) {
                    t_940 = !RegexParserGlobal.isAsciiDigit(c__355);
                } else {
                    t_940 = false;
                }
                if (t_940) {
                    return__195 = false;
                    break fn__354;
                }
                index__1031 = Core.stringNext(this__1029, index__1031);
            }
            return true;
        }
        return return__195;
    }
    public static RegexNode parseWith(String src__299, Map<String, RegexNode> slots__300) {
        return new Parser(src__299, slots__300).readAll();
    }
    public static Regex compileWith(String src__294, Map<String, RegexNode> slots__295) {
        RegexNode t_1082 = RegexParserGlobal.parseWith(src__294, slots__295);
        return t_1082.compiled();
    }
    public static RegexNode parse(String src__302) {
        return new Parser(src__302).readAll();
    }
    public static Regex compile(String src__297) {
        RegexNode t_1081 = RegexParserGlobal.parse(src__297);
        return t_1081.compiled();
    }
    static boolean isMeta(int c__316) {
        return RegexParserGlobal.hasCode("\\^$.|?*+()[]{}", c__316);
    }
    static void appendCode(StringBuilder out__318, int code__319, boolean inClass__320) {
        switch (code__319) {
            case 9:
                {
                out__318.append("\\t");
                break;
            }
            case 10:
                {
                out__318.append("\\n");
                break;
            }
            case 11:
                {
                out__318.append("\\v");
                break;
            }
            case 12:
                {
                out__318.append("\\f");
                break;
            }
            case 13:
                {
                out__318.append("\\r");
                break;
            }
            default:
                {
                boolean t_818;
                if (code__319 < 32) {
                    t_818 = true;
                } else {
                    t_818 = code__319 == 127;
                }
                if (t_818) {
                    out__318.append("\\" + "u{" + Integer.toString(code__319, 16) + "}");
                } else {
                    boolean t_820;
                    if (RegexParserGlobal.isMeta(code__319)) {
                        t_820 = true;
                    } else if (inClass__320) {
                        t_820 = code__319 == 45;
                    } else {
                        t_820 = false;
                    }
                    if (t_820) {
                        out__318.append("\\");
                    }
                    Core.stringBuilderAppendCodePoint(out__318, code__319);
                }
                break;
            }
        }
    }
    static String specialText(Special special__322) {
        if (special__322 instanceof SpecialSet) {
            SpecialSet t_817 = Core.cast(SpecialSet.class, special__322);
            Regex regex__324 = t_817.compiled();
            String subject_654 = Boolean.toString(regex__324.found("5")) + " " + Boolean.toString(regex__324.found("a")) + " " + Boolean.toString(regex__324.found(" "));
            if (subject_654.equals("true false false")) {
                return "\\d";
            } else if (subject_654.equals("false false true")) {
                return "\\s";
            } else if (subject_654.equals("true true false")) {
                return "\\w";
            } else {
                throw Core.bubble();
            }
        } else {
            Regex aS__325 = new Sequence(List.of(new CodePoints("a"), special__322)).compiled();
            Regex sA__326 = new Sequence(List.of(special__322, new CodePoints("a"))).compiled();
            String subject_658 = Boolean.toString(aS__325.found("a")) + " " + Boolean.toString(aS__325.found("ab")) + " " + Boolean.toString(sA__326.found("a")) + " " + Boolean.toString(sA__326.found("ba"));
            if (subject_658.equals("false true false true")) {
                return ".";
            } else if (subject_658.equals("false false true false")) {
                return "^";
            } else if (subject_658.equals("true false false false")) {
                return "$";
            } else if (subject_658.equals("true false true false")) {
                return "\\b";
            } else {
                throw Core.bubble();
            }
        }
    }
    static void formatSet(StringBuilder out__327, CodeSet set__328) {
        fn__329: {
            String t_814;
            boolean t_808;
            if (set__328.isNegated()) {
                if (set__328.getItems().size() == 1) {
                    t_808 = Core.listGet(set__328.getItems(), 0) instanceof SpecialSet;
                } else {
                    t_808 = false;
                }
            } else {
                t_808 = false;
            }
            if (t_808) {
                String t_813;
                SpecialSet t_812 = Core.cast(SpecialSet.class, Core.listGet(set__328.getItems(), 0));
                String subject_663 = RegexParserGlobal.specialText(t_812);
                if (subject_663.equals("\\d")) {
                    t_813 = "\\D";
                } else if (subject_663.equals("\\s")) {
                    t_813 = "\\S";
                } else {
                    t_813 = "\\W";
                }
                out__327.append(t_813);
                break fn__329;
            }
            if (set__328.isNegated()) {
                t_814 = "[^";
            } else {
                t_814 = "[";
            }
            out__327.append(t_814);
            List<CodePart> this__1052 = set__328.getItems();
            int n__1054 = this__1052.size();
            int i__1055 = 0;
            while (i__1055 < n__1054) {
                CodePart el__1056 = Core.listGet(this__1052, i__1055);
                i__1055 = i__1055 + 1;
                CodePart item__330 = el__1056;
                if (item__330 instanceof CodePoints) {
                    String this__1057 = Core.cast(CodePoints.class, item__330).getValue();
                    int index__1059 = 0;
                    while (Core.stringHasIndex(this__1057, index__1059)) {
                        int codePoint__1060 = this__1057.codePointAt(index__1059);
                        int c__331 = codePoint__1060;
                        RegexParserGlobal.appendCode(out__327, c__331, true);
                        index__1059 = Core.stringNext(this__1057, index__1059);
                    }
                } else if (item__330 instanceof CodeRange) {
                    CodeRange t_802 = Core.cast(CodeRange.class, item__330);
                    RegexParserGlobal.appendCode(out__327, t_802.getMin(), true);
                    out__327.append("-");
                    RegexParserGlobal.appendCode(out__327, t_802.getMax(), true);
                } else if (item__330 instanceof SpecialSet) {
                    SpecialSet t_805 = Core.cast(SpecialSet.class, item__330);
                    String t_1080 = RegexParserGlobal.specialText(t_805);
                    out__327.append(t_1080);
                } else {
                    throw Core.bubble();
                }
            }
            out__327.append("]");
        }
    }
    static void formatGrouped(StringBuilder out__334, RegexNode node__335, boolean grouped__336) {
        if (grouped__336) {
            out__334.append("(?:");
            RegexParserGlobal.formatTo(out__334, node__335);
            out__334.append(")");
        } else {
            RegexParserGlobal.formatTo(out__334, node__335);
        }
    }
    static boolean needsGroupToRepeat(RegexNode node__332) {
        if (node__332 instanceof CodePoints) {
            CodePoints t_788 = Core.cast(CodePoints.class, node__332);
            if (!Core.stringHasAtLeast(t_788.getValue(), 0, t_788.getValue().length(), 1)) {
                return true;
            } else {
                return Core.stringHasAtLeast(t_788.getValue(), 0, t_788.getValue().length(), 2);
            }
        } else {
            boolean t_793;
            if (node__332 instanceof Sequence) {
                t_793 = true;
            } else if (node__332 instanceof Or) {
                t_793 = true;
            } else {
                t_793 = node__332 instanceof Repeat;
            }
            if (t_793) {
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
    public static String format(RegexNode node__304) {
        StringBuilder out__306 = new StringBuilder();
        RegexParserGlobal.formatTo(out__306, node__304);
        return out__306.toString();
    }
    /**
     * Escapes text so it matches literally, outside of a (?x:...) group.
     */
    public static String escape(String text__307) {
        StringBuilder out__309 = new StringBuilder();
        String this__1075 = text__307;
        int index__1077 = 0;
        while (Core.stringHasIndex(this__1075, index__1077)) {
            int codePoint__1078 = this__1075.codePointAt(index__1077);
            int c__310 = codePoint__1078;
            RegexParserGlobal.appendCode(out__309, c__310, false);
            index__1077 = Core.stringNext(this__1075, index__1077);
        }
        return out__309.toString();
    }
    public static @Nullable String parseError(String src__311, @Nullable Map<String, RegexNode> slots__682) {
        Map<String, RegexNode> slots__312;
        if (slots__682 == null) {
            slots__312 = noSlots__293;
        } else {
            slots__312 = slots__682;
        }
        Parser parser__314 = new Parser(src__311, slots__312);
        boolean parsed__315;
        try {
            parser__314.readAll();
            parsed__315 = true;
        } catch (RuntimeException ignored$7) {
            parsed__315 = false;
        }
        if (parsed__315) {
            return null;
        } else {
            return parser__314.getErrorMessage();
        }
    }
    public static @Nullable String parseError(String src__311) {
        return parseError(src__311, null);
    }
    static {
        noSlots__293 = Core.mapConstructor(List.of());
    }
}
