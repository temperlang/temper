package temper.regex_parser;
import temper.core.Core;
import temper.std.regex.RegexNode;
import temper.std.regex.CodePoints;
import java.util.List;
import temper.std.regex.Repeat;
import temper.std.regex.CodePart;
import temper.std.regex.Or;
import temper.std.regex.CodeSet;
import java.util.Map;
import temper.core.Nullable;
import temper.std.regex.Sequence;
import temper.std.regex.Capture;
import java.util.ArrayList;
import static temper.std.regex.RegexGlobal.End;
import static temper.std.regex.RegexGlobal.Word;
import static temper.std.regex.RegexGlobal.Begin;
import static temper.std.regex.RegexGlobal.Digit;
import static temper.std.regex.RegexGlobal.Space;
import temper.std.regex.CodeRange;
import java.util.function.Function;
import java.util.function.Consumer;
import static temper.std.regex.RegexGlobal.Dot;
import java.util.LinkedHashMap;
import static temper.std.regex.RegexGlobal.WordBoundary;
import static temper.regex_parser.RegexParserGlobal.noSlots__293;
final class Parser {
    public final String chars;
    public final Map<String, RegexNode> slots;
    public int pos;
    public @Nullable String errorMessage;
    final Map<String, Boolean> names;
    boolean extended;
    boolean caseless;
    public void error(String e__407) {
        int position__409 = Core.stringCountBetween(this.chars, 0, this.pos);
        @Nullable String t_574 = "at position " + Integer.toString(position__409) + ": " + e__407;
        this.errorMessage = t_574;
        throw Core.bubble();
    }
    public boolean isDone() {
        return this.chars.length() <= this.pos;
    }
    public int peek() {
        return this.chars.codePointAt(this.pos);
    }
    public void advance() {
        int t_573 = Core.stringNext(this.chars, this.pos);
        this.pos = t_573;
    }
    public int read() {
        int ret__418 = this.peek();
        this.advance();
        return ret__418;
    }
    public boolean matchChar(int s__420) {
        boolean t_938;
        if (!this.isDone()) {
            t_938 = this.peek() == s__420;
        } else {
            t_938 = false;
        }
        if (t_938) {
            this.advance();
            return true;
        } else {
            return false;
        }
    }
    boolean matchRepeat() {
        if (this.matchChar(43)) {
            return true;
        } else if (this.matchChar(42)) {
            return true;
        } else {
            return this.matchChar(63);
        }
    }
    boolean isHexDigit(int c__425) {
        boolean t_934;
        if (c__425 >= 48) {
            t_934 = c__425 <= 57;
        } else {
            t_934 = false;
        }
        if (t_934) {
            return true;
        } else {
            boolean t_936;
            if (c__425 >= 97) {
                t_936 = c__425 <= 102;
            } else {
                t_936 = false;
            }
            if (t_936) {
                return true;
            } else if (c__425 >= 65) {
                return c__425 <= 70;
            } else {
                return false;
            }
        }
    }
    int hexValue(int c__428) {
        boolean t_928;
        if (c__428 >= 48) {
            t_928 = c__428 <= 57;
        } else {
            t_928 = false;
        }
        if (t_928) {
            return c__428 - 48;
        } else {
            boolean t_930;
            if (c__428 >= 97) {
                t_930 = c__428 <= 102;
            } else {
                t_930 = false;
            }
            if (t_930) {
                return c__428 - 97 + 10;
            } else {
                boolean t_932;
                if (c__428 >= 65) {
                    t_932 = c__428 <= 70;
                } else {
                    t_932 = false;
                }
                if (t_932) {
                    return c__428 - 65 + 10;
                } else {
                    throw Core.bubble();
                }
            }
        }
    }
    int readHexDigits(int count__431, String escape__432) {
        int codePoint__434 = 0;
        int i__435 = 0;
        while (i__435 < count__431) {
            int t_926;
            if (this.isDone()) {
                this.error("incomplete " + escape__432 + " escape");
            }
            int c__436 = this.read();
            int t_927 = codePoint__434 * 16;
            try {
                t_926 = this.hexValue(c__436);
            } catch (RuntimeException ignored$1) {
                this.error("invalid hex digit in " + escape__432 + " escape");
                throw Core.bubble();
            }
            codePoint__434 = t_927 + t_926;
            i__435 = i__435 + 1;
        }
        return codePoint__434;
    }
    CodePoints codePointsOf(int codePoint__438) {
        String t_925;
        try {
            t_925 = Core.stringFromCodePoint(codePoint__438);
        } catch (RuntimeException ignored$2) {
            this.error("invalid code point " + Integer.toString(codePoint__438, 16));
            throw Core.bubble();
        }
        return new CodePoints(t_925);
    }
    RegexNode finishUnicodeEscape() {
        if (this.matchChar(123)) {
            int codePoint__442 = 0;
            int count__443 = 0;
            while (!this.matchChar(125)) {
                int t_917;
                boolean t_915;
                if (this.isDone()) {
                    t_915 = true;
                } else {
                    t_915 = count__443 >= 6;
                }
                if (t_915) {
                    this.error("invalid unicode escape");
                }
                int c__444 = this.read();
                int t_918 = codePoint__442 * 16;
                try {
                    t_917 = this.hexValue(c__444);
                } catch (RuntimeException ignored$3) {
                    this.error("invalid hex digit in unicode escape");
                    throw Core.bubble();
                }
                codePoint__442 = t_918 + t_917;
                count__443 = count__443 + 1;
            }
            if (count__443 == 0) {
                this.error("empty unicode escape");
            }
            return this.codePointsOf(codePoint__442);
        } else {
            int codePoint__445 = this.readHexDigits(4, "unicode");
            boolean t_919;
            if (codePoint__445 >= 55296) {
                t_919 = codePoint__445 <= 56319;
            } else {
                t_919 = false;
            }
            if (t_919) {
                boolean t_921;
                if (!this.matchChar(92)) {
                    t_921 = true;
                } else {
                    t_921 = !this.matchChar(117);
                }
                if (t_921) {
                    this.error("high surrogate must be followed by a low surrogate");
                }
                int low__446 = this.readHexDigits(4, "unicode");
                boolean t_923;
                if (low__446 < 56320) {
                    t_923 = true;
                } else {
                    t_923 = low__446 > 57343;
                }
                if (t_923) {
                    this.error("high surrogate must be followed by a low surrogate");
                }
                codePoint__445 = 65536 + (codePoint__445 - 55296) * 1024 + (low__446 - 56320);
            }
            return this.codePointsOf(codePoint__445);
        }
    }
    boolean matchText(String text__448) {
        boolean return__220;
        fn__449: {
            int index__450 = this.pos;
            String this__1033 = text__448;
            int index__1035 = 0;
            while (Core.stringHasIndex(this__1033, index__1035)) {
                int codePoint__1036 = this__1033.codePointAt(index__1035);
                int c__451 = codePoint__1036;
                boolean t_913;
                if (!Core.stringHasIndex(this.chars, index__450)) {
                    t_913 = true;
                } else {
                    t_913 = this.chars.codePointAt(index__450) != c__451;
                }
                if (t_913) {
                    return__220 = false;
                    break fn__449;
                }
                index__450 = Core.stringNext(this.chars, index__450);
                index__1035 = Core.stringNext(this__1033, index__1035);
            }
            this.pos = index__450;
            return true;
        }
        return return__220;
    }
    void skipIgnored() {
        while (!this.isDone()) {
            boolean t_907;
            if (this.extended) {
                t_907 = RegexParserGlobal.isPatternSpace(this.peek());
            } else {
                t_907 = false;
            }
            if (t_907) {
                this.advance();
            } else {
                boolean t_909;
                if (this.extended) {
                    t_909 = this.matchChar(35);
                } else {
                    t_909 = false;
                }
                if (t_909) {
                    while (true) {
                        boolean t_911;
                        if (!this.isDone()) {
                            t_911 = !this.matchChar(10);
                        } else {
                            t_911 = false;
                        }
                        if (!t_911) {
                            break;
                        }
                        this.advance();
                    }
                } else if (this.matchText("(?#")) {
                    while (!this.matchChar(41)) {
                        if (this.isDone()) {
                            this.error("missing closing paren in (?#...)");
                        }
                        this.advance();
                    }
                } else {
                    break;
                }
            }
        }
    }
    boolean atFlagGroup() {
        int index__456 = this.pos;
        while (true) {
            boolean t_905;
            if (Core.stringHasIndex(this.chars, index__456)) {
                t_905 = RegexParserGlobal.isAsciiLetter(this.chars.codePointAt(index__456));
            } else {
                t_905 = false;
            }
            if (!t_905) {
                break;
            }
            index__456 = Core.stringNext(this.chars, index__456);
        }
        if (index__456 != this.pos) {
            if (Core.stringHasIndex(this.chars, index__456)) {
                return this.chars.codePointAt(index__456) == 58;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    RegexNode readFlagGroup() {
        boolean outerExtended__459 = this.extended;
        boolean outerCaseless__460 = this.caseless;
        while (!this.matchChar(58)) {
            int flag__461 = this.read();
            switch (flag__461) {
                case 105:
                    {
                    this.caseless = true;
                    break;
                }
                case 120:
                    {
                    this.extended = true;
                    break;
                }
                default:
                    {
                    String t_1092 = Core.stringFromCodePoint(flag__461);
                    this.error("inline flag (?" + t_1092 + ":...) is not supported");
                    break;
                }
            }
        }
        RegexNode regex__462 = this.readOr();
        boolean rewrite__463;
        if (this.caseless) {
            rewrite__463 = !outerCaseless__460;
        } else {
            rewrite__463 = false;
        }
        this.extended = outerExtended__459;
        this.caseless = outerCaseless__460;
        if (!this.matchChar(41)) {
            this.error("closing paren");
        }
        if (rewrite__463) {
            return RegexParserGlobal.ignoreCase(regex__462);
        } else {
            return regex__462;
        }
    }
    boolean atShorthand() {
        boolean return__224;
        fn__465: {
            boolean t_895;
            if (this.isDone()) {
                t_895 = true;
            } else {
                t_895 = this.peek() != 92;
            }
            if (t_895) {
                return__224 = false;
                break fn__465;
            }
            int after__466 = Core.stringNext(this.chars, this.pos);
            if (!Core.stringHasIndex(this.chars, after__466)) {
                return__224 = false;
                break fn__465;
            }
            int subject_629 = this.chars.codePointAt(after__466);
            boolean t_897;
            switch (subject_629) {
                case 100:
                    {
                    t_897 = true;
                    break;
                }
                case 68:
                    {
                    t_897 = true;
                    break;
                }
                case 115:
                    {
                    t_897 = true;
                    break;
                }
                case 83:
                    {
                    t_897 = true;
                    break;
                }
                case 119:
                    {
                    t_897 = true;
                    break;
                }
                default:
                    {
                    t_897 = subject_629 == 87;
                    break;
                }
            }
            if (t_897) {
                return true;
            } else {
                return false;
            }
        }
        return return__224;
    }
    public RegexNode charClassBody() {
        boolean invert__469 = this.matchChar(94);
        List<CodePart> opts__470 = new ArrayList<>();
        List<RegexNode> negations__471 = new ArrayList<>();
        StringBuilder optMembers__472 = new StringBuilder();
        List<String> negatedMembers__473 = new ArrayList<>();
        while (!this.matchChar(93)) {
            if (this.isDone()) {
                this.error("missing closing ']'");
            }
            if (this.atShorthand()) {
                this.advance();
                int letter__474 = this.read();
                boolean t_884;
                switch (letter__474) {
                    case 68:
                        {
                        t_884 = true;
                        break;
                    }
                    case 83:
                        {
                        t_884 = true;
                        break;
                    }
                    default:
                        {
                        t_884 = letter__474 == 87;
                        break;
                    }
                }
                if (t_884) {
                    Core.listAdd(negations__471, new CodeSet(List.of(RegexParserGlobal.shorthandSet(letter__474)), true));
                    Core.listAdd(negatedMembers__473, RegexParserGlobal.shorthandMembers(letter__474));
                } else {
                    Core.listAdd(opts__470, RegexParserGlobal.shorthandSet(letter__474));
                    optMembers__472.append(RegexParserGlobal.shorthandMembers(letter__474));
                }
                if (this.matchChar(45)) {
                    if (this.matchChar(93)) {
                        Core.listAdd(opts__470, new CodePoints("-"));
                        break;
                    }
                    this.error("invalid range edge");
                }
                continue;
            }
            CodePart first__475 = this.charClassUnit();
            if (this.matchChar(45)) {
                if (this.matchChar(93)) {
                    Core.listAdd(opts__470, first__475);
                    Core.listAdd(opts__470, new CodePoints("-"));
                    break;
                }
                if (this.isDone()) {
                    this.error("missing closing ']'");
                }
                CodePart second__476 = this.charClassUnit();
                int min__477 = this.extractCode(first__475);
                int max__478 = this.extractCode(second__476);
                if (min__477 > max__478) {
                    this.error("character class range is out of order");
                }
                Core.listAdd(opts__470, new CodeRange(min__477, max__478));
            } else {
                Core.listAdd(opts__470, first__475);
            }
        }
        List<CodePart> items__479 = List.copyOf(opts__470);
        boolean t_889;
        if (negations__471.isEmpty()) {
            if (!invert__469) {
                if (items__479.size() == 1) {
                    t_889 = !(Core.listGet(items__479, 0) instanceof CodeRange);
                } else {
                    t_889 = false;
                }
            } else {
                t_889 = false;
            }
        } else {
            t_889 = false;
        }
        if (t_889) {
            return Core.listGet(items__479, 0);
        } else if (negations__471.isEmpty()) {
            return new CodeSet(items__479, invert__469);
        } else if (!invert__469) {
            if (!items__479.isEmpty()) {
                Core.listAdd(negations__471, new CodeSet(items__479), 0);
            }
            if (negations__471.size() == 1) {
                return Core.listGet(negations__471, 0);
            } else {
                return new Or(List.copyOf(negations__471));
            }
        } else {
            List<CodePart> excluded__480;
            if (this.caseless) {
                excluded__480 = RegexParserGlobal.ignoreCaseParts(items__479);
            } else {
                excluded__480 = items__479;
            }
            return RegexParserGlobal.negatedClass(excluded__480, optMembers__472.toString(), List.copyOf(negatedMembers__473));
        }
    }
    CodePart charClassUnit() {
        if (this.matchChar(92)) {
            if (this.matchChar(98)) {
                return new CodePoints("\b");
            } else {
                RegexNode escape__483 = this.finishEscape();
                try {
                    return Core.cast(CodePart.class, escape__483);
                } catch (RuntimeException ignored$4) {
                    this.error("escape not allowed in a character class");
                    throw Core.bubble();
                }
            }
        } else {
            String t_1091 = Core.stringFromCodePoint(this.read());
            return new CodePoints(t_1091);
        }
    }
    int extractCode(CodePart codePart__485) {
        try {
            CodePoints t_881 = Core.cast(CodePoints.class, codePart__485);
            return t_881.getValue().codePointAt(0);
        } catch (RuntimeException ignored$5) {
            this.error("invalid range edge");
            throw Core.bubble();
        }
    }
    RegexNode finishHexEscape() {
        int t_1089 = this.readHexDigits(2, "hex");
        String t_1090 = Core.stringFromCodePoint(t_1089);
        return new CodePoints(t_1090);
    }
    RegexNode finishEscape() {
        if (this.isDone()) {
            this.error("trailing '\\' at end of pattern");
        }
        int escapeCode__491 = this.peek();
        if (this.matchChar(48)) {
            return new CodePoints("\u0000");
        } else if (this.matchChar(97)) {
            return new CodePoints("\u0007");
        } else if (this.matchChar(65)) {
            return Begin;
        } else if (this.matchChar(98)) {
            return WordBoundary;
        } else if (this.matchChar(66)) {
            this.error("\\B (non-word boundary) is not supported");
            throw Core.bubble();
        } else if (this.matchChar(99)) {
            boolean t_873;
            if (this.isDone()) {
                t_873 = true;
            } else {
                t_873 = !RegexParserGlobal.isAsciiLetter(this.peek());
            }
            if (t_873) {
                this.error("\\c must be followed by a letter");
            }
            String t_1085 = Core.stringFromCodePoint(this.read() % 32);
            return new CodePoints(t_1085);
        } else if (this.matchChar(100)) {
            return Digit;
        } else if (this.matchChar(68)) {
            return new CodeSet(List.of(Digit), true);
        } else if (this.matchChar(101)) {
            return new CodePoints("\u001b");
        } else if (this.matchChar(102)) {
            return new CodePoints("\f");
        } else if (this.matchChar(110)) {
            return new CodePoints("\n");
        } else if (this.matchChar(114)) {
            return new CodePoints("\r");
        } else if (this.matchChar(115)) {
            return Space;
        } else if (this.matchChar(83)) {
            return new CodeSet(List.of(Space), true);
        } else if (this.matchChar(116)) {
            return new CodePoints("\t");
        } else if (this.matchChar(117)) {
            return this.finishUnicodeEscape();
        } else if (this.matchChar(118)) {
            return new CodePoints("\u000b");
        } else if (this.matchChar(119)) {
            return Word;
        } else if (this.matchChar(87)) {
            return new CodeSet(List.of(Word), true);
        } else if (this.matchChar(120)) {
            return this.finishHexEscape();
        } else if (this.matchChar(122)) {
            return End;
        } else {
            boolean t_875;
            if (RegexParserGlobal.isAsciiPunctuation(escapeCode__491)) {
                t_875 = true;
            } else {
                t_875 = escapeCode__491 == 32;
            }
            if (t_875) {
                this.advance();
                String t_1086 = Core.stringFromCodePoint(escapeCode__491);
                return new CodePoints(t_1086);
            } else {
                boolean t_877;
                if (this.peek() >= 49) {
                    t_877 = this.peek() <= 57;
                } else {
                    t_877 = false;
                }
                if (t_877) {
                    String t_1087 = Core.stringFromCodePoint(this.peek());
                    this.error("backreferences (" + "\\" + t_1087 + ") are not supported");
                    throw Core.bubble();
                } else {
                    boolean t_879;
                    if (this.matchChar(112)) {
                        t_879 = true;
                    } else {
                        t_879 = this.matchChar(80);
                    }
                    if (t_879) {
                        this.error("Unicode property escapes (\\p{...}) are not supported");
                        throw Core.bubble();
                    } else {
                        String t_1088 = Core.stringFromCodePoint(this.peek());
                        this.error("unknown escape sequence: " + "\\" + t_1088);
                        throw Core.bubble();
                    }
                }
            }
        }
    }
    public int braceMin;
    public int braceMax;
    int appendDigit(int count__495) {
        int digit__497 = this.read() - 48;
        if (count__495 > (2147483647 - digit__497) / 10) {
            this.error("repeat count is too large");
        }
        return count__495 * 10 + digit__497;
    }
    boolean tryParseBraceQuantifier() {
        boolean return__231;
        fn__499: {
            int savedPos__500 = this.pos;
            if (!this.matchChar(123)) {
                return__231 = false;
                break fn__499;
            }
            int min__501 = 0;
            int minDigits__502 = 0;
            while (true) {
                boolean t_867;
                if (!this.isDone()) {
                    if (this.peek() >= 48) {
                        t_867 = this.peek() <= 57;
                    } else {
                        t_867 = false;
                    }
                } else {
                    t_867 = false;
                }
                if (!t_867) {
                    break;
                }
                min__501 = this.appendDigit(min__501);
                minDigits__502 = minDigits__502 + 1;
            }
            if (minDigits__502 == 0) {
                this.pos = savedPos__500;
                return__231 = false;
                break fn__499;
            }
            if (this.matchChar(125)) {
                int t_566 = min__501;
                this.braceMin = t_566;
                this.braceMax = min__501;
                return__231 = true;
                break fn__499;
            }
            if (!this.matchChar(44)) {
                this.pos = savedPos__500;
                return__231 = false;
                break fn__499;
            }
            int max__503 = 0;
            int maxDigits__504 = 0;
            while (true) {
                boolean t_870;
                if (!this.isDone()) {
                    if (this.peek() >= 48) {
                        t_870 = this.peek() <= 57;
                    } else {
                        t_870 = false;
                    }
                } else {
                    t_870 = false;
                }
                if (!t_870) {
                    break;
                }
                max__503 = this.appendDigit(max__503);
                maxDigits__504 = maxDigits__504 + 1;
            }
            if (!this.matchChar(125)) {
                this.pos = savedPos__500;
                return__231 = false;
                break fn__499;
            }
            this.braceMin = min__501;
            if (maxDigits__504 == 0) {
                this.braceMax = -1;
            } else {
                this.braceMax = max__503;
            }
            return true;
        }
        return return__231;
    }
    public RegexNode readPostfix(RegexNode exist__506) {
        RegexNode return__232;
        fn__507: {
            Repeat repeat__508;
            if (this.matchChar(43)) {
                repeat__508 = new Repeat(exist__506, 1, null, this.matchChar(63));
            } else if (this.matchChar(42)) {
                repeat__508 = new Repeat(exist__506, 0, null, this.matchChar(63));
            } else if (this.matchChar(63)) {
                repeat__508 = new Repeat(exist__506, 0, 1, this.matchChar(63));
            } else {
                boolean t_1084 = this.tryParseBraceQuantifier();
                if (t_1084) {
                    boolean nonGreedy__509 = this.matchChar(63);
                    boolean t_862;
                    if (this.braceMax != -1) {
                        t_862 = this.braceMin > this.braceMax;
                    } else {
                        t_862 = false;
                    }
                    if (t_862) {
                        this.error("quantifier {" + Integer.toString(this.braceMin) + "," + Integer.toString(this.braceMax) + "} has min greater than max");
                    }
                    if (this.braceMax == -1) {
                        repeat__508 = new Repeat(exist__506, this.braceMin, null, nonGreedy__509);
                    } else {
                        repeat__508 = new Repeat(exist__506, this.braceMin, this.braceMax, nonGreedy__509);
                    }
                } else {
                    return__232 = exist__506;
                    break fn__507;
                }
            }
            boolean t_865;
            if (this.matchRepeat()) {
                t_865 = true;
            } else {
                t_865 = this.tryParseBraceQuantifier();
            }
            if (t_865) {
                this.error("nothing to repeat");
            }
            return repeat__508;
        }
        return return__232;
    }
    void claimName(String name__511) {
        if (!RegexParserGlobal.isCaptureName(name__511)) {
            this.error("invalid capture name '" + name__511 + "'; use an ASCII letter followed by ASCII letters or digits");
        }
        if (this.names.containsKey(name__511)) {
            this.error("duplicate capture name '" + name__511 + "'");
        }
        this.names.put(name__511, true);
    }
    void claimNames(RegexNode node__514) {
        if (node__514 instanceof Capture) {
            Capture t_851 = Core.cast(Capture.class, node__514);
            this.claimName(t_851.getName());
            this.claimNames(t_851.getItem());
        } else if (node__514 instanceof Or) {
            List<RegexNode> this__1037 = Core.cast(Or.class, node__514).getItems();
            int n__1039 = this__1037.size();
            int i__1040 = 0;
            while (i__1040 < n__1039) {
                RegexNode el__1041 = Core.listGet(this__1037, i__1040);
                i__1040 = i__1040 + 1;
                RegexNode item__516 = el__1041;
                this.claimNames(item__516);
            }
        } else if (node__514 instanceof Sequence) {
            List<RegexNode> this__1042 = Core.cast(Sequence.class, node__514).getItems();
            int n__1044 = this__1042.size();
            int i__1045 = 0;
            while (i__1045 < n__1044) {
                RegexNode el__1046 = Core.listGet(this__1042, i__1045);
                i__1045 = i__1045 + 1;
                RegexNode item__517 = el__1046;
                this.claimNames(item__517);
            }
        } else if (node__514 instanceof Repeat) {
            Repeat t_860 = Core.cast(Repeat.class, node__514);
            this.claimNames(t_860.getItem());
        }
    }
    public RegexNode readSingle() {
        if (this.matchChar(91)) {
            return this.charClassBody();
        } else if (this.matchChar(40)) {
            if (this.matchChar(63)) {
                if (this.matchChar(58)) {
                    RegexNode regex__520 = this.readOr();
                    if (!this.matchChar(41)) {
                        this.error("closing paren");
                    }
                    return regex__520;
                } else if (this.matchChar(36)) {
                    StringBuilder parts__521 = new StringBuilder();
                    while (!this.matchChar(41)) {
                        if (this.isDone()) {
                            this.error("missing closing paren in (?$...)");
                        }
                        Core.stringBuilderAppendCodePoint(parts__521, this.read());
                    }
                    String str__522 = parts__521.toString();
                    RegexNode slot__523;
                    try {
                        slot__523 = Core.mappedGet(this.slots, str__522);
                    } catch (RuntimeException ignored$6) {
                        this.error("no such slot: " + str__522);
                        throw Core.bubble();
                    }
                    this.claimNames(slot__523);
                    return slot__523;
                } else if (this.matchChar(61)) {
                    this.error("positive lookhead is not supported");
                    throw Core.bubble();
                } else if (this.matchChar(60)) {
                    if (this.matchChar(61)) {
                        this.error("positive lookbehind (?<=...) is not supported");
                        throw Core.bubble();
                    } else if (this.matchChar(33)) {
                        this.error("negative lookbehind (?<!...) is not supported");
                        throw Core.bubble();
                    } else {
                        this.error("(?<name>...) named capture syntax is not supported; use (?name=pattern) instead");
                        throw Core.bubble();
                    }
                } else if (this.matchChar(33)) {
                    this.error("negative lookahead is not supported");
                    throw Core.bubble();
                } else if (this.atFlagGroup()) {
                    return this.readFlagGroup();
                } else {
                    if (!this.isDone()) {
                        int checkPos__524 = this.pos;
                        int fc__525 = this.read();
                        if (!this.isDone()) {
                            boolean t_842;
                            if (fc__525 == 80) {
                                if (this.peek() == 60) {
                                    t_842 = true;
                                } else if (this.peek() == 61) {
                                    t_842 = true;
                                } else {
                                    t_842 = this.peek() == 62;
                                }
                            } else {
                                t_842 = false;
                            }
                            if (t_842) {
                                this.error("Python-style (?P...) groups are not supported; use (?name=pattern) instead");
                            }
                        }
                        this.pos = checkPos__524;
                    }
                    StringBuilder str__526 = new StringBuilder();
                    while (this.matchChar(32)) {
                    }
                    while (true) {
                        boolean t_846;
                        if (!this.isDone()) {
                            if (this.peek() != 32) {
                                t_846 = this.peek() != 61;
                            } else {
                                t_846 = false;
                            }
                        } else {
                            t_846 = false;
                        }
                        if (!t_846) {
                            break;
                        }
                        Core.stringBuilderAppendCodePoint(str__526, this.read());
                    }
                    while (this.matchChar(32)) {
                    }
                    if (!this.matchChar(61)) {
                        this.error("missing = in (?" + str__526.toString() + "=...)");
                    }
                    String name__527 = str__526.toString();
                    this.claimName(name__527);
                    RegexNode regex__528 = this.readOr();
                    if (!this.matchChar(41)) {
                        this.error("closing paren");
                    }
                    return new Capture(name__527, regex__528);
                }
            } else {
                RegexNode regex__529 = this.readOr();
                if (!this.matchChar(41)) {
                    this.error("closing paren");
                }
                return regex__529;
            }
        } else if (this.matchChar(94)) {
            return Begin;
        } else if (this.matchChar(36)) {
            return End;
        } else if (this.matchChar(46)) {
            return Dot;
        } else if (this.matchChar(92)) {
            return this.finishEscape();
        } else if (this.matchRepeat()) {
            this.error("invalid repeat");
            throw Core.bubble();
        } else {
            String t_1083 = Core.stringFromCodePoint(this.read());
            return new CodePoints(t_1083);
        }
    }
    public RegexNode readSeq() {
        List<RegexNode> seq__534 = new ArrayList<>();
        List<String> codeBuffer__535 = new ArrayList<>();
        class Local_1 {
            void popCodeBuffer__532() {
                if (!codeBuffer__535.isEmpty()) {
                    Function<String, String> fn__1093 = it__537 -> it__537;
                    Core.listAdd(seq__534, new CodePoints(Core.listJoinObj(codeBuffer__535, "", fn__1093)));
                    codeBuffer__535.clear();
                }
            }
        }
        final Local_1 local$1 = new Local_1();
        Consumer<RegexNode> add__533 = regex__538 -> {
            if (regex__538 instanceof CodePoints) {
                CodePoints t_834 = Core.cast(CodePoints.class, regex__538);
                Core.listAdd(codeBuffer__535, t_834.getValue());
            } else {
                local$1.popCodeBuffer__532();
                Core.listAdd(seq__534, regex__538);
            }
        };
        this.skipIgnored();
        while (true) {
            boolean t_835;
            if (!this.isDone()) {
                if (this.peek() != 124) {
                    t_835 = this.peek() != 41;
                } else {
                    t_835 = false;
                }
            } else {
                t_835 = false;
            }
            if (!t_835) {
                break;
            }
            RegexNode single__540 = this.readSingle();
            this.skipIgnored();
            RegexNode regex__541 = this.readPostfix(single__540);
            if (regex__541 instanceof Sequence) {
                List<RegexNode> this__1047 = Core.cast(Sequence.class, regex__541).getItems();
                int n__1049 = this__1047.size();
                int i__1050 = 0;
                while (i__1050 < n__1049) {
                    RegexNode el__1051 = Core.listGet(this__1047, i__1050);
                    i__1050 = i__1050 + 1;
                    RegexNode item__542 = el__1051;
                    add__533.accept(item__542);
                }
            } else {
                add__533.accept(regex__541);
            }
            this.skipIgnored();
        }
        local$1.popCodeBuffer__532();
        if (seq__534.size() == 1) {
            return Core.listGet(seq__534, 0);
        } else {
            return new Sequence(List.copyOf(seq__534));
        }
    }
    public RegexNode readOr() {
        List<RegexNode> ors__545 = new ArrayList<>();
        while (true) {
            boolean t_825;
            if (!this.isDone()) {
                t_825 = this.peek() != 41;
            } else {
                t_825 = false;
            }
            if (!t_825) {
                break;
            }
            boolean t_827;
            if (ors__545.size() != 0) {
                t_827 = !this.matchChar(124);
            } else {
                t_827 = false;
            }
            if (t_827) {
                this.error("or");
            }
            RegexNode regex__546 = this.readSeq();
            if (regex__546 instanceof Or) {
                Core.listAddAll(ors__545, Core.cast(Or.class, regex__546).getItems());
            } else {
                Core.listAdd(ors__545, regex__546);
            }
        }
        if (ors__545.size() == 1) {
            return Core.listGet(ors__545, 0);
        } else {
            return new Or(List.copyOf(ors__545));
        }
    }
    public RegexNode readAll() {
        RegexNode result__549 = this.readOr();
        if (!this.isDone()) {
            this.error("unexpected ')'; unmatched closing parenthesis");
        }
        return result__549;
    }
    public static final class Builder {
        String chars;
        public Builder chars(String chars) {
            this.chars = chars;
            return this;
        }
        @Nullable Map<String, RegexNode> slots;
        public Builder slots(@Nullable Map<String, RegexNode> slots) {
            this.slots = slots;
            return this;
        }
        public Parser build() {
            return new Parser(chars, slots);
        }
    }
    public Parser(String chars__551, @Nullable Map<String, RegexNode> slots__681) {
        Map<String, RegexNode> slots__552;
        if (slots__681 == null) {
            slots__552 = noSlots__293;
        } else {
            slots__552 = slots__681;
        }
        this.chars = chars__551;
        this.slots = slots__552;
        this.pos = 0;
        this.errorMessage = null;
        Map<String, Boolean> t_555 = new LinkedHashMap<>();
        this.names = t_555;
        this.extended = false;
        this.caseless = false;
        this.braceMin = 0;
        this.braceMax = 0;
    }
    public Parser(String chars__551) {
        this(chars__551, null);
    }
    public String getChars() {
        return this.chars;
    }
    public Map<String, RegexNode> getSlots() {
        return this.slots;
    }
    public int getPos() {
        return this.pos;
    }
    public void setPos(int newPos__605) {
        this.pos = newPos__605;
    }
    public @Nullable String getErrorMessage() {
        return this.errorMessage;
    }
    public void setErrorMessage(@Nullable String newErrorMessage__612) {
        this.errorMessage = newErrorMessage__612;
    }
    public int getBraceMin() {
        return this.braceMin;
    }
    public void setBraceMin(int newBraceMin__619) {
        this.braceMin = newBraceMin__619;
    }
    public int getBraceMax() {
        return this.braceMax;
    }
    public void setBraceMax(int newBraceMax__626) {
        this.braceMax = newBraceMax__626;
    }
}
