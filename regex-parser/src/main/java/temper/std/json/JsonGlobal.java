package temper.std.json;
import temper.core.Core;
import java.util.List;
import static java.lang.Double.NaN;
import temper.core.Nullable;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;
public final class JsonGlobal {
    private JsonGlobal() {
    }
    static final List<String> hexDigits__386;
    static int parseJsonValue(String sourceText__695, int i__696, JsonProducer out__697) {
        int return__315;
        fn__698: {
            i__696 = JsonGlobal.skipJsonSpaces(sourceText__695, i__696);
            if (!Core.stringHasIndex(sourceText__695, i__696)) {
                JsonGlobal.expectedTokenError(sourceText__695, i__696, out__697, "JSON value");
                return__315 = -1;
                break fn__698;
            }
            int subject_956 = sourceText__695.codePointAt(i__696);
            switch (subject_956) {
                case 123:
                    {
                    return JsonGlobal.parseJsonObject(sourceText__695, i__696, out__697);
                }
                case 91:
                    {
                    return JsonGlobal.parseJsonArray(sourceText__695, i__696, out__697);
                }
                case 34:
                    {
                    return JsonGlobal.parseJsonString(sourceText__695, i__696, out__697);
                }
                default:
                    {
                    boolean t_1007;
                    if (subject_956 == 116) {
                        t_1007 = true;
                    } else {
                        t_1007 = subject_956 == 102;
                    }
                    if (t_1007) {
                        return JsonGlobal.parseJsonBoolean(sourceText__695, i__696, out__697);
                    } else if (subject_956 == 110) {
                        return JsonGlobal.parseJsonNull(sourceText__695, i__696, out__697);
                    } else {
                        return JsonGlobal.parseJsonNumber(sourceText__695, i__696, out__697);
                    }
                }
            }
        }
        return return__315;
    }
    static void encodeHex4(int cp__603, StringBuilder buffer__604) {
        int b0__606 = cp__603 / 4096 & 15;
        int b1__607 = cp__603 / 256 & 15;
        int b2__608 = cp__603 / 16 & 15;
        int b3__609 = cp__603 & 15;
        buffer__604.append(Core.listGet(hexDigits__386, b0__606));
        buffer__604.append(Core.listGet(hexDigits__386, b1__607));
        buffer__604.append(Core.listGet(hexDigits__386, b2__608));
        buffer__604.append(Core.listGet(hexDigits__386, b3__609));
    }
    static void encodeJsonString(String x__595, StringBuilder buffer__596) {
        buffer__596.append("\"");
        int i__598 = 0;
        int emitted__599 = i__598;
        while (Core.stringHasIndex(x__595, i__598)) {
            int cp__600 = x__595.codePointAt(i__598);
            String replacement__601;
            switch (cp__600) {
                case 8:
                    {
                    replacement__601 = "\\b";
                    break;
                }
                case 9:
                    {
                    replacement__601 = "\\t";
                    break;
                }
                case 10:
                    {
                    replacement__601 = "\\n";
                    break;
                }
                case 12:
                    {
                    replacement__601 = "\\f";
                    break;
                }
                case 13:
                    {
                    replacement__601 = "\\r";
                    break;
                }
                case 34:
                    {
                    replacement__601 = "\\\"";
                    break;
                }
                case 92:
                    {
                    replacement__601 = "\\\\";
                    break;
                }
                default:
                    {
                    boolean t_1138;
                    if (cp__600 < 32) {
                        t_1138 = true;
                    } else if (55296 <= cp__600) {
                        t_1138 = cp__600 <= 57343;
                    } else {
                        t_1138 = false;
                    }
                    if (t_1138) {
                        replacement__601 = "\\u";
                    } else {
                        replacement__601 = "";
                    }
                    break;
                }
            }
            int nextI__602 = Core.stringNext(x__595, i__598);
            if (!replacement__601.equals("")) {
                Core.stringBuilderAppendBetween(buffer__596, x__595, emitted__599, i__598);
                buffer__596.append(replacement__601);
                if (replacement__601.equals("\\u")) {
                    JsonGlobal.encodeHex4(cp__600, buffer__596);
                }
                emitted__599 = nextI__602;
            }
            i__598 = nextI__602;
        }
        Core.stringBuilderAppendBetween(buffer__596, x__595, emitted__599, i__598);
        buffer__596.append("\"");
    }
    static void storeJsonError(JsonProducer out__684, String explanation__685) {
        @Nullable JsonParseErrorReceiver subject_879 = out__684.getParseErrorReceiver();
        if (subject_879 != null) {
            subject_879.explainJsonError(explanation__685);
        }
    }
    static void expectedTokenError(String sourceText__678, int i__679, JsonProducer out__680, String shortExplanation__681) {
        String gotten__683;
        if (Core.stringHasIndex(sourceText__678, i__679)) {
            gotten__683 = "`" + Core.stringSlice(sourceText__678, i__679, sourceText__678.length()) + "`";
        } else {
            gotten__683 = "end-of-file";
        }
        JsonGlobal.storeJsonError(out__680, "Expected " + shortExplanation__681 + ", but got " + gotten__683);
    }
    static int skipJsonSpaces(String sourceText__692, int i__693) {
        while (Core.stringHasIndex(sourceText__692, i__693)) {
            int subject_940 = sourceText__692.codePointAt(i__693);
            boolean t_1112;
            switch (subject_940) {
                case 9:
                    {
                    t_1112 = true;
                    break;
                }
                case 10:
                    {
                    t_1112 = true;
                    break;
                }
                case 13:
                    {
                    t_1112 = true;
                    break;
                }
                default:
                    {
                    t_1112 = subject_940 == 32;
                    break;
                }
            }
            if (!t_1112) {
                break;
            }
            i__693 = Core.stringNext(sourceText__692, i__693);
        }
        return i__693;
    }
    static int decodeHexUnsigned(String sourceText__733, int start__734, int limit__735) {
        int return__320;
        fn__736: {
            int n__737 = 0;
            int i__738 = start__734;
            while (Integer.compare(i__738, limit__735) < 0) {
                int cp__739 = sourceText__733.codePointAt(i__738);
                int digit__740;
                boolean t_1104;
                if (48 <= cp__739) {
                    t_1104 = cp__739 <= 57;
                } else {
                    t_1104 = false;
                }
                if (t_1104) {
                    digit__740 = cp__739 - 48;
                } else {
                    boolean t_1107;
                    if (65 <= cp__739) {
                        t_1107 = cp__739 <= 70;
                    } else {
                        t_1107 = false;
                    }
                    if (t_1107) {
                        digit__740 = cp__739 - 65 + 10;
                    } else {
                        boolean t_1109;
                        if (97 <= cp__739) {
                            t_1109 = cp__739 <= 102;
                        } else {
                            t_1109 = false;
                        }
                        if (t_1109) {
                            digit__740 = cp__739 - 97 + 10;
                        } else {
                            return__320 = -1;
                            break fn__736;
                        }
                    }
                }
                n__737 = n__737 * 16 + digit__740;
                i__738 = Core.stringNext(sourceText__733, i__738);
            }
            return n__737;
        }
        return return__320;
    }
    static int parseJsonStringTo(String sourceText__717, int i__718, StringBuilder sb__719, JsonProducer errOut__720) {
        int return__319;
        fn__721: {
            boolean t_1081;
            if (!Core.stringHasIndex(sourceText__717, i__718)) {
                t_1081 = true;
            } else {
                t_1081 = sourceText__717.codePointAt(i__718) != 34;
            }
            if (t_1081) {
                JsonGlobal.expectedTokenError(sourceText__717, i__718, errOut__720, "\"");
                return__319 = -1;
                break fn__721;
            }
            i__718 = Core.stringNext(sourceText__717, i__718);
            int leadSurrogate__722 = -1;
            int consumed__723 = i__718;
            while (Core.stringHasIndex(sourceText__717, i__718)) {
                int t_1088;
                int cp__724 = sourceText__717.codePointAt(i__718);
                if (cp__724 == 34) {
                    break;
                }
                int iNext__725 = Core.stringNext(sourceText__717, i__718);
                int end__726 = sourceText__717.length();
                boolean needToFlush__727 = false;
                if (cp__724 != 92) {
                    t_1088 = cp__724;
                } else {
                    needToFlush__727 = true;
                    if (!Core.stringHasIndex(sourceText__717, iNext__725)) {
                        JsonGlobal.expectedTokenError(sourceText__717, iNext__725, errOut__720, "escape sequence");
                        return__319 = -1;
                        break fn__721;
                    }
                    int esc0__729 = sourceText__717.codePointAt(iNext__725);
                    iNext__725 = Core.stringNext(sourceText__717, iNext__725);
                    boolean t_1083;
                    switch (esc0__729) {
                        case 34:
                            {
                            t_1083 = true;
                            break;
                        }
                        case 92:
                            {
                            t_1083 = true;
                            break;
                        }
                        default:
                            {
                            t_1083 = esc0__729 == 47;
                            break;
                        }
                    }
                    if (t_1083) {
                        t_1088 = esc0__729;
                    } else {
                        switch (esc0__729) {
                            case 98:
                                {
                                t_1088 = 8;
                                break;
                            }
                            case 102:
                                {
                                t_1088 = 12;
                                break;
                            }
                            case 110:
                                {
                                t_1088 = 10;
                                break;
                            }
                            case 114:
                                {
                                t_1088 = 13;
                                break;
                            }
                            case 116:
                                {
                                t_1088 = 9;
                                break;
                            }
                            case 117:
                                {
                                int hex__730;
                                if (Core.stringHasAtLeast(sourceText__717, iNext__725, end__726, 4)) {
                                    int startHex__731 = iNext__725;
                                    iNext__725 = Core.stringNext(sourceText__717, iNext__725);
                                    iNext__725 = Core.stringNext(sourceText__717, iNext__725);
                                    iNext__725 = Core.stringNext(sourceText__717, iNext__725);
                                    iNext__725 = Core.stringNext(sourceText__717, iNext__725);
                                    hex__730 = JsonGlobal.decodeHexUnsigned(sourceText__717, startHex__731, iNext__725);
                                } else {
                                    hex__730 = -1;
                                }
                                if (hex__730 < 0) {
                                    JsonGlobal.expectedTokenError(sourceText__717, iNext__725, errOut__720, "four hex digits");
                                    return__319 = -1;
                                    break fn__721;
                                }
                                t_1088 = hex__730;
                                break;
                            }
                            default:
                                {
                                JsonGlobal.expectedTokenError(sourceText__717, iNext__725, errOut__720, "escape sequence");
                                return__319 = -1;
                                break fn__721;
                            }
                        }
                    }
                }
                int decodedCp__728 = t_1088;
                if (leadSurrogate__722 >= 0) {
                    needToFlush__727 = true;
                    int lead__732 = leadSurrogate__722;
                    boolean t_1096;
                    if (56320 <= decodedCp__728) {
                        t_1096 = decodedCp__728 <= 57343;
                    } else {
                        t_1096 = false;
                    }
                    if (t_1096) {
                        leadSurrogate__722 = -1;
                        decodedCp__728 = 65536 + ((lead__732 - 55296) * 1024 | decodedCp__728 - 56320);
                    }
                } else {
                    boolean t_1098;
                    if (55296 <= decodedCp__728) {
                        t_1098 = decodedCp__728 <= 56319;
                    } else {
                        t_1098 = false;
                    }
                    if (t_1098) {
                        needToFlush__727 = true;
                    }
                }
                if (needToFlush__727) {
                    Core.stringBuilderAppendBetween(sb__719, sourceText__717, consumed__723, i__718);
                    if (leadSurrogate__722 >= 0) {
                        Core.stringBuilderAppendCodePoint(sb__719, leadSurrogate__722);
                    }
                    boolean t_1100;
                    if (55296 <= decodedCp__728) {
                        t_1100 = decodedCp__728 <= 56319;
                    } else {
                        t_1100 = false;
                    }
                    if (t_1100) {
                        leadSurrogate__722 = decodedCp__728;
                    } else {
                        leadSurrogate__722 = -1;
                        Core.stringBuilderAppendCodePoint(sb__719, decodedCp__728);
                    }
                    consumed__723 = iNext__725;
                }
                i__718 = iNext__725;
            }
            boolean t_1102;
            if (!Core.stringHasIndex(sourceText__717, i__718)) {
                t_1102 = true;
            } else {
                t_1102 = sourceText__717.codePointAt(i__718) != 34;
            }
            if (t_1102) {
                JsonGlobal.expectedTokenError(sourceText__717, i__718, errOut__720, "\"");
                return -1;
            } else {
                if (leadSurrogate__722 >= 0) {
                    Core.stringBuilderAppendCodePoint(sb__719, leadSurrogate__722);
                } else {
                    Core.stringBuilderAppendBetween(sb__719, sourceText__717, consumed__723, i__718);
                }
                i__718 = Core.stringNext(sourceText__717, i__718);
                return i__718;
            }
        }
        return return__319;
    }
    static int parseJsonObject(String sourceText__699, int i__700, JsonProducer out__701) {
        int return__316;
        fn__702: {
            boolean t_1065;
            if (!Core.stringHasIndex(sourceText__699, i__700)) {
                t_1065 = true;
            } else {
                t_1065 = sourceText__699.codePointAt(i__700) != 123;
            }
            if (t_1065) {
                JsonGlobal.expectedTokenError(sourceText__699, i__700, out__701, "'{'");
                return__316 = -1;
                break fn__702;
            }
            out__701.startObject();
            i__700 = JsonGlobal.skipJsonSpaces(sourceText__699, Core.stringNext(sourceText__699, i__700));
            boolean t_1067;
            if (Core.stringHasIndex(sourceText__699, i__700)) {
                t_1067 = sourceText__699.codePointAt(i__700) != 125;
            } else {
                t_1067 = false;
            }
            if (t_1067) {
                while (true) {
                    StringBuilder keyBuffer__703 = new StringBuilder();
                    int afterKey__704 = JsonGlobal.parseJsonStringTo(sourceText__699, i__700, keyBuffer__703, out__701);
                    if (afterKey__704 < 0) {
                        return__316 = -1;
                        break fn__702;
                    }
                    out__701.objectKey(keyBuffer__703.toString());
                    int t_1071 = Core.requireStringIndex(afterKey__704);
                    i__700 = JsonGlobal.skipJsonSpaces(sourceText__699, t_1071);
                    boolean t_1072;
                    if (Core.stringHasIndex(sourceText__699, i__700)) {
                        t_1072 = sourceText__699.codePointAt(i__700) == 58;
                    } else {
                        t_1072 = false;
                    }
                    if (t_1072) {
                        i__700 = Core.stringNext(sourceText__699, i__700);
                        int afterPropertyValue__705 = JsonGlobal.parseJsonValue(sourceText__699, i__700, out__701);
                        if (afterPropertyValue__705 < 0) {
                            return__316 = -1;
                            break fn__702;
                        }
                        int t_1076 = Core.requireStringIndex(afterPropertyValue__705);
                        i__700 = t_1076;
                    } else {
                        JsonGlobal.expectedTokenError(sourceText__699, i__700, out__701, "':'");
                        return__316 = -1;
                        break fn__702;
                    }
                    i__700 = JsonGlobal.skipJsonSpaces(sourceText__699, i__700);
                    boolean t_1077;
                    if (Core.stringHasIndex(sourceText__699, i__700)) {
                        t_1077 = sourceText__699.codePointAt(i__700) == 44;
                    } else {
                        t_1077 = false;
                    }
                    if (t_1077) {
                        i__700 = JsonGlobal.skipJsonSpaces(sourceText__699, Core.stringNext(sourceText__699, i__700));
                    } else {
                        break;
                    }
                }
            }
            boolean t_1079;
            if (Core.stringHasIndex(sourceText__699, i__700)) {
                t_1079 = sourceText__699.codePointAt(i__700) == 125;
            } else {
                t_1079 = false;
            }
            if (t_1079) {
                out__701.endObject();
                return Core.stringNext(sourceText__699, i__700);
            } else {
                JsonGlobal.expectedTokenError(sourceText__699, i__700, out__701, "'}'");
                return -1;
            }
        }
        return return__316;
    }
    static int parseJsonArray(String sourceText__706, int i__707, JsonProducer out__708) {
        int return__317;
        fn__709: {
            boolean t_1054;
            if (!Core.stringHasIndex(sourceText__706, i__707)) {
                t_1054 = true;
            } else {
                t_1054 = sourceText__706.codePointAt(i__707) != 91;
            }
            if (t_1054) {
                JsonGlobal.expectedTokenError(sourceText__706, i__707, out__708, "'['");
                return__317 = -1;
                break fn__709;
            }
            out__708.startArray();
            i__707 = JsonGlobal.skipJsonSpaces(sourceText__706, Core.stringNext(sourceText__706, i__707));
            boolean t_1056;
            if (Core.stringHasIndex(sourceText__706, i__707)) {
                t_1056 = sourceText__706.codePointAt(i__707) != 93;
            } else {
                t_1056 = false;
            }
            if (t_1056) {
                while (true) {
                    int afterElementValue__710 = JsonGlobal.parseJsonValue(sourceText__706, i__707, out__708);
                    if (afterElementValue__710 < 0) {
                        return__317 = -1;
                        break fn__709;
                    }
                    int t_1060 = Core.requireStringIndex(afterElementValue__710);
                    i__707 = t_1060;
                    i__707 = JsonGlobal.skipJsonSpaces(sourceText__706, i__707);
                    boolean t_1061;
                    if (Core.stringHasIndex(sourceText__706, i__707)) {
                        t_1061 = sourceText__706.codePointAt(i__707) == 44;
                    } else {
                        t_1061 = false;
                    }
                    if (t_1061) {
                        i__707 = JsonGlobal.skipJsonSpaces(sourceText__706, Core.stringNext(sourceText__706, i__707));
                    } else {
                        break;
                    }
                }
            }
            boolean t_1063;
            if (Core.stringHasIndex(sourceText__706, i__707)) {
                t_1063 = sourceText__706.codePointAt(i__707) == 93;
            } else {
                t_1063 = false;
            }
            if (t_1063) {
                out__708.endArray();
                return Core.stringNext(sourceText__706, i__707);
            } else {
                JsonGlobal.expectedTokenError(sourceText__706, i__707, out__708, "']'");
                return -1;
            }
        }
        return return__317;
    }
    static int parseJsonString(String sourceText__711, int i__712, JsonProducer out__713) {
        StringBuilder sb__715 = new StringBuilder();
        int after__716 = JsonGlobal.parseJsonStringTo(sourceText__711, i__712, sb__715, out__713);
        if (after__716 >= 0) {
            out__713.stringValue(sb__715.toString());
        }
        return after__716;
    }
    static int afterSubstring(String string__755, int inString__756, String substring__757) {
        int return__323;
        fn__758: {
            int i__759 = inString__756;
            int j__760 = 0;
            while (Core.stringHasIndex(substring__757, j__760)) {
                if (!Core.stringHasIndex(string__755, i__759)) {
                    return__323 = -1;
                    break fn__758;
                }
                if (string__755.codePointAt(i__759) != substring__757.codePointAt(j__760)) {
                    return__323 = -1;
                    break fn__758;
                }
                i__759 = Core.stringNext(string__755, i__759);
                j__760 = Core.stringNext(substring__757, j__760);
            }
            return i__759;
        }
        return return__323;
    }
    static int parseJsonBoolean(String sourceText__741, int i__742, JsonProducer out__743) {
        int return__321;
        fn__744: {
            int ch0__745;
            if (Core.stringHasIndex(sourceText__741, i__742)) {
                ch0__745 = sourceText__741.codePointAt(i__742);
            } else {
                ch0__745 = 0;
            }
            int end__746 = sourceText__741.length();
            @Nullable String keyword__747;
            int n__748;
            switch (ch0__745) {
                case 102:
                    {
                    keyword__747 = "false";
                    n__748 = 5;
                    break;
                }
                case 116:
                    {
                    keyword__747 = "true";
                    n__748 = 4;
                    break;
                }
                default:
                    {
                    keyword__747 = null;
                    n__748 = 0;
                    break;
                }
            }
            if (keyword__747 != null) {
                String keyword_982 = keyword__747;
                if (Core.stringHasAtLeast(sourceText__741, i__742, end__746, n__748)) {
                    int after__749 = JsonGlobal.afterSubstring(sourceText__741, i__742, keyword_982);
                    if (after__749 >= 0) {
                        return__321 = Core.requireStringIndex(after__749);
                        out__743.booleanValue(n__748 == 4);
                        break fn__744;
                    }
                }
            }
            JsonGlobal.expectedTokenError(sourceText__741, i__742, out__743, "`false` or `true`");
            return -1;
        }
        return return__321;
    }
    static int parseJsonNull(String sourceText__750, int i__751, JsonProducer out__752) {
        int return__322;
        fn__753: {
            int after__754 = JsonGlobal.afterSubstring(sourceText__750, i__751, "null");
            if (after__754 >= 0) {
                return__322 = Core.requireStringIndex(after__754);
                out__752.nullValue();
                break fn__753;
            }
            JsonGlobal.expectedTokenError(sourceText__750, i__751, out__752, "`null`");
            return -1;
        }
        return return__322;
    }
    static int parseJsonNumber(String sourceText__761, int i__762, JsonProducer out__763) {
        int return__324;
        fn__764: {
            boolean isNegative__765 = false;
            int startOfNumber__766 = i__762;
            boolean t_1009;
            if (Core.stringHasIndex(sourceText__761, i__762)) {
                t_1009 = sourceText__761.codePointAt(i__762) == 45;
            } else {
                t_1009 = false;
            }
            if (t_1009) {
                isNegative__765 = true;
                i__762 = Core.stringNext(sourceText__761, i__762);
            }
            int digit0__767;
            if (Core.stringHasIndex(sourceText__761, i__762)) {
                digit0__767 = sourceText__761.codePointAt(i__762);
            } else {
                digit0__767 = -1;
            }
            boolean t_1013;
            if (digit0__767 < 48) {
                t_1013 = true;
            } else {
                t_1013 = 57 < digit0__767;
            }
            if (t_1013) {
                String error__768;
                boolean t_1015;
                if (!isNegative__765) {
                    t_1015 = digit0__767 != 46;
                } else {
                    t_1015 = false;
                }
                if (t_1015) {
                    error__768 = "JSON value";
                } else {
                    error__768 = "digit";
                }
                JsonGlobal.expectedTokenError(sourceText__761, i__762, out__763, error__768);
                return__324 = -1;
                break fn__764;
            }
            i__762 = Core.stringNext(sourceText__761, i__762);
            int nDigits__769 = 1;
            double tentativeFloat64__770 = (double)(digit0__767 - 48);
            long tentativeInt64__771 = (long)(digit0__767 - 48);
            boolean overflowInt64__772 = false;
            if (48 != digit0__767) {
                while (Core.stringHasIndex(sourceText__761, i__762)) {
                    int possibleDigit__773 = sourceText__761.codePointAt(i__762);
                    boolean t_1018;
                    if (48 <= possibleDigit__773) {
                        t_1018 = possibleDigit__773 <= 57;
                    } else {
                        t_1018 = false;
                    }
                    if (t_1018) {
                        i__762 = Core.stringNext(sourceText__761, i__762);
                        nDigits__769 = nDigits__769 + 1;
                        int nextDigit__774 = possibleDigit__773 - 48;
                        tentativeFloat64__770 = tentativeFloat64__770 * 10.0D + (double) nextDigit__774;
                        long oldInt64__775 = tentativeInt64__771;
                        tentativeInt64__771 = tentativeInt64__771 * 10 + (long) nextDigit__774;
                        if (tentativeInt64__771 < oldInt64__775) {
                            boolean t_1020;
                            if (-9223372036854775808L - oldInt64__775 == -((long) nextDigit__774)) {
                                if (isNegative__765) {
                                    t_1020 = oldInt64__775 > 0;
                                } else {
                                    t_1020 = false;
                                }
                            } else {
                                t_1020 = false;
                            }
                            if (!t_1020) {
                                overflowInt64__772 = true;
                            }
                        }
                    } else {
                        break;
                    }
                }
            }
            int nDigitsAfterPoint__776 = 0;
            boolean t_1023;
            if (Core.stringHasIndex(sourceText__761, i__762)) {
                t_1023 = 46 == sourceText__761.codePointAt(i__762);
            } else {
                t_1023 = false;
            }
            if (t_1023) {
                i__762 = Core.stringNext(sourceText__761, i__762);
                int afterPoint__777 = i__762;
                while (Core.stringHasIndex(sourceText__761, i__762)) {
                    int possibleDigit__778 = sourceText__761.codePointAt(i__762);
                    boolean t_1025;
                    if (48 <= possibleDigit__778) {
                        t_1025 = possibleDigit__778 <= 57;
                    } else {
                        t_1025 = false;
                    }
                    if (t_1025) {
                        i__762 = Core.stringNext(sourceText__761, i__762);
                        nDigits__769 = nDigits__769 + 1;
                        nDigitsAfterPoint__776 = nDigitsAfterPoint__776 + 1;
                        tentativeFloat64__770 = tentativeFloat64__770 * 10.0D + (double)(possibleDigit__778 - 48);
                    } else {
                        break;
                    }
                }
                if (i__762 == afterPoint__777) {
                    JsonGlobal.expectedTokenError(sourceText__761, i__762, out__763, "digit");
                    return__324 = -1;
                    break fn__764;
                }
            }
            int nExponentDigits__779 = 0;
            boolean t_1027;
            if (Core.stringHasIndex(sourceText__761, i__762)) {
                t_1027 = 101 == (sourceText__761.codePointAt(i__762) | 32);
            } else {
                t_1027 = false;
            }
            if (t_1027) {
                i__762 = Core.stringNext(sourceText__761, i__762);
                if (!Core.stringHasIndex(sourceText__761, i__762)) {
                    JsonGlobal.expectedTokenError(sourceText__761, i__762, out__763, "sign or digit");
                    return__324 = -1;
                    break fn__764;
                }
                int afterE__780 = sourceText__761.codePointAt(i__762);
                boolean t_1029;
                if (afterE__780 == 43) {
                    t_1029 = true;
                } else {
                    t_1029 = afterE__780 == 45;
                }
                if (t_1029) {
                    i__762 = Core.stringNext(sourceText__761, i__762);
                }
                while (Core.stringHasIndex(sourceText__761, i__762)) {
                    int possibleDigit__781 = sourceText__761.codePointAt(i__762);
                    boolean t_1031;
                    if (48 <= possibleDigit__781) {
                        t_1031 = possibleDigit__781 <= 57;
                    } else {
                        t_1031 = false;
                    }
                    if (t_1031) {
                        i__762 = Core.stringNext(sourceText__761, i__762);
                        nExponentDigits__779 = nExponentDigits__779 + 1;
                    } else {
                        break;
                    }
                }
                if (nExponentDigits__779 == 0) {
                    JsonGlobal.expectedTokenError(sourceText__761, i__762, out__763, "exponent digit");
                    return__324 = -1;
                    break fn__764;
                }
            }
            int afterExponent__782 = i__762;
            boolean t_1033;
            if (nExponentDigits__779 == 0) {
                if (nDigitsAfterPoint__776 == 0) {
                    t_1033 = !overflowInt64__772;
                } else {
                    t_1033 = false;
                }
            } else {
                t_1033 = false;
            }
            if (t_1033) {
                long value__783;
                if (isNegative__765) {
                    value__783 = -tentativeInt64__771;
                } else {
                    value__783 = tentativeInt64__771;
                }
                boolean t_1037;
                if (-2147483648 <= value__783) {
                    t_1037 = value__783 <= 2147483647;
                } else {
                    t_1037 = false;
                }
                if (t_1037) {
                    out__763.int32Value((int) value__783);
                } else {
                    out__763.int64Value(value__783);
                }
                return__324 = i__762;
                break fn__764;
            }
            String numericTokenString__784 = Core.stringSlice(sourceText__761, startOfNumber__766, i__762);
            double doubleValue__785 = NaN;
            boolean t_1039;
            if (nExponentDigits__779 != 0) {
                t_1039 = true;
            } else {
                t_1039 = nDigitsAfterPoint__776 != 0;
            }
            if (t_1039) {
                try {
                    doubleValue__785 = Core.stringToFloat64(numericTokenString__784);
                } catch (RuntimeException ignored$3) {
                }
            }
            boolean t_1041;
            if (Double.doubleToLongBits(doubleValue__785) != Double.doubleToLongBits(NEGATIVE_INFINITY)) {
                if (Double.doubleToLongBits(doubleValue__785) != Double.doubleToLongBits(POSITIVE_INFINITY)) {
                    t_1041 = Double.doubleToLongBits(doubleValue__785) != Double.doubleToLongBits(NaN);
                } else {
                    t_1041 = false;
                }
            } else {
                t_1041 = false;
            }
            if (t_1041) {
                out__763.float64Value(doubleValue__785);
            } else {
                out__763.numericTokenValue(numericTokenString__784);
            }
            return i__762;
        }
        return return__324;
    }
    public static void parseJsonToProducer(String sourceText__687, JsonProducer out__688) {
        int i__690 = 0;
        int afterValue__691 = JsonGlobal.parseJsonValue(sourceText__687, i__690, out__688);
        if (afterValue__691 >= 0) {
            int t_1004 = Core.requireStringIndex(afterValue__691);
            i__690 = JsonGlobal.skipJsonSpaces(sourceText__687, t_1004);
            boolean t_1005;
            if (Core.stringHasIndex(sourceText__687, i__690)) {
                t_1005 = out__688.getParseErrorReceiver() != null;
            } else {
                t_1005 = false;
            }
            if (t_1005) {
                JsonGlobal.storeJsonError(out__688, "Extraneous JSON `" + Core.stringSlice(sourceText__687, i__690, sourceText__687.length()) + "`");
            }
        }
    }
    public static JsonSyntaxTree parseJson(String sourceText__786) {
        JsonSyntaxTreeProducer p__788 = new JsonSyntaxTreeProducer();
        JsonGlobal.parseJsonToProducer(sourceText__786, p__788);
        return p__788.toJsonSyntaxTree();
    }
    public static JsonAdapter<Boolean> booleanJsonAdapter() {
        return new BooleanJsonAdapter();
    }
    public static JsonAdapter<Double> float64JsonAdapter() {
        return new Float64JsonAdapter();
    }
    public static JsonAdapter<Integer> int32JsonAdapter() {
        return new Int32JsonAdapter();
    }
    public static JsonAdapter<Long> int64JsonAdapter() {
        return new Int64JsonAdapter();
    }
    public static JsonAdapter<String> stringJsonAdapter() {
        return new StringJsonAdapter();
    }
    public static<T__189> JsonAdapter<List<T__189>> listJsonAdapter(JsonAdapter<T__189> adapterForT__864) {
        return new ListJsonAdapter<>(adapterForT__864);
    }
    static {
        hexDigits__386 = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f");
    }
}
