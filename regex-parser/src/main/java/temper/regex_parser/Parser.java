package temper.regex_parser;
import temper.core.Core;
import temper.std.regex.RegexNode;
import temper.std.regex.CodePoints;
import java.util.List;
import temper.std.regex.Repeat;
import temper.std.regex.CodePart;
import temper.std.regex.Or;
import temper.std.regex.CodeSet;
import temper.std.regex.Sequence;
import java.util.Map;
import temper.core.Nullable;
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
import static temper.regex_parser.RegexParserGlobal.noSlots__294;
final class Parser {
    public final String chars;
    public final Map<String, RegexNode> slots;
    public int pos;
    public @Nullable String errorMessage;
    final Map<String, Boolean> names;
    boolean extended;
    boolean caseless;
    public void error(String e__408) {
        int position__410 = Core.stringCountBetween(this.chars, 0, this.pos);
        @Nullable String t_575 = "at position " + Integer.toString(position__410) + ": " + e__408;
        this.errorMessage = t_575;
        throw Core.bubble();
    }
    public boolean isDone() {
        return this.chars.length() <= this.pos;
    }
    public int peek() {
        return this.chars.codePointAt(this.pos);
    }
    public void advance() {
        int t_574 = Core.stringNext(this.chars, this.pos);
        this.pos = t_574;
    }
    public int read() {
        int ret__419 = this.peek();
        this.advance();
        return ret__419;
    }
    public boolean matchChar(int s__421) {
        boolean t_940;
        if (!this.isDone()) {
            t_940 = this.peek() == s__421;
        } else {
            t_940 = false;
        }
        if (t_940) {
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
    boolean isHexDigit(int c__426) {
        boolean t_936;
        if (c__426 >= 48) {
            t_936 = c__426 <= 57;
        } else {
            t_936 = false;
        }
        if (t_936) {
            return true;
        } else {
            boolean t_938;
            if (c__426 >= 97) {
                t_938 = c__426 <= 102;
            } else {
                t_938 = false;
            }
            if (t_938) {
                return true;
            } else if (c__426 >= 65) {
                return c__426 <= 70;
            } else {
                return false;
            }
        }
    }
    int hexValue(int c__429) {
        boolean t_930;
        if (c__429 >= 48) {
            t_930 = c__429 <= 57;
        } else {
            t_930 = false;
        }
        if (t_930) {
            return c__429 - 48;
        } else {
            boolean t_932;
            if (c__429 >= 97) {
                t_932 = c__429 <= 102;
            } else {
                t_932 = false;
            }
            if (t_932) {
                return c__429 - 97 + 10;
            } else {
                boolean t_934;
                if (c__429 >= 65) {
                    t_934 = c__429 <= 70;
                } else {
                    t_934 = false;
                }
                if (t_934) {
                    return c__429 - 65 + 10;
                } else {
                    throw Core.bubble();
                }
            }
        }
    }
    int readHexDigits(int count__432, String escape__433) {
        int codePoint__435 = 0;
        int i__436 = 0;
        while (i__436 < count__432) {
            int t_928;
            if (this.isDone()) {
                this.error("incomplete " + escape__433 + " escape");
            }
            int c__437 = this.read();
            int t_929 = codePoint__435 * 16;
            try {
                t_928 = this.hexValue(c__437);
            } catch (RuntimeException ignored$1) {
                this.error("invalid hex digit in " + escape__433 + " escape");
                throw Core.bubble();
            }
            codePoint__435 = t_929 + t_928;
            i__436 = i__436 + 1;
        }
        return codePoint__435;
    }
    CodePoints codePointsOf(int codePoint__439) {
        String t_927;
        try {
            t_927 = Core.stringFromCodePoint(codePoint__439);
        } catch (RuntimeException ignored$2) {
            this.error("invalid code point " + Integer.toString(codePoint__439, 16));
            throw Core.bubble();
        }
        return new CodePoints(t_927);
    }
    RegexNode finishUnicodeEscape() {
        if (this.matchChar(123)) {
            int codePoint__443 = 0;
            int count__444 = 0;
            while (!this.matchChar(125)) {
                int t_919;
                boolean t_917;
                if (this.isDone()) {
                    t_917 = true;
                } else {
                    t_917 = count__444 >= 6;
                }
                if (t_917) {
                    this.error("invalid unicode escape");
                }
                int c__445 = this.read();
                int t_920 = codePoint__443 * 16;
                try {
                    t_919 = this.hexValue(c__445);
                } catch (RuntimeException ignored$3) {
                    this.error("invalid hex digit in unicode escape");
                    throw Core.bubble();
                }
                codePoint__443 = t_920 + t_919;
                count__444 = count__444 + 1;
            }
            if (count__444 == 0) {
                this.error("empty unicode escape");
            }
            return this.codePointsOf(codePoint__443);
        } else {
            int codePoint__446 = this.readHexDigits(4, "unicode");
            boolean t_921;
            if (codePoint__446 >= 55296) {
                t_921 = codePoint__446 <= 56319;
            } else {
                t_921 = false;
            }
            if (t_921) {
                boolean t_923;
                if (!this.matchChar(92)) {
                    t_923 = true;
                } else {
                    t_923 = !this.matchChar(117);
                }
                if (t_923) {
                    this.error("high surrogate must be followed by a low surrogate");
                }
                int low__447 = this.readHexDigits(4, "unicode");
                boolean t_925;
                if (low__447 < 56320) {
                    t_925 = true;
                } else {
                    t_925 = low__447 > 57343;
                }
                if (t_925) {
                    this.error("high surrogate must be followed by a low surrogate");
                }
                codePoint__446 = 65536 + (codePoint__446 - 55296) * 1024 + (low__447 - 56320);
            }
            return this.codePointsOf(codePoint__446);
        }
    }
    boolean matchText(String text__449) {
        boolean return__221;
        fn__450: {
            int index__451 = this.pos;
            String this__1035 = text__449;
            int index__1037 = 0;
            while (Core.stringHasIndex(this__1035, index__1037)) {
                int codePoint__1038 = this__1035.codePointAt(index__1037);
                int c__452 = codePoint__1038;
                boolean t_915;
                if (!Core.stringHasIndex(this.chars, index__451)) {
                    t_915 = true;
                } else {
                    t_915 = this.chars.codePointAt(index__451) != c__452;
                }
                if (t_915) {
                    return__221 = false;
                    break fn__450;
                }
                index__451 = Core.stringNext(this.chars, index__451);
                index__1037 = Core.stringNext(this__1035, index__1037);
            }
            this.pos = index__451;
            return true;
        }
        return return__221;
    }
    void skipIgnored() {
        while (!this.isDone()) {
            boolean t_909;
            if (this.extended) {
                t_909 = RegexParserGlobal.isPatternSpace(this.peek());
            } else {
                t_909 = false;
            }
            if (t_909) {
                this.advance();
            } else {
                boolean t_911;
                if (this.extended) {
                    t_911 = this.matchChar(35);
                } else {
                    t_911 = false;
                }
                if (t_911) {
                    while (true) {
                        boolean t_913;
                        if (!this.isDone()) {
                            t_913 = !this.matchChar(10);
                        } else {
                            t_913 = false;
                        }
                        if (!t_913) {
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
        int index__457 = this.pos;
        while (true) {
            boolean t_907;
            if (Core.stringHasIndex(this.chars, index__457)) {
                t_907 = RegexParserGlobal.isAsciiLetter(this.chars.codePointAt(index__457));
            } else {
                t_907 = false;
            }
            if (!t_907) {
                break;
            }
            index__457 = Core.stringNext(this.chars, index__457);
        }
        if (index__457 != this.pos) {
            if (Core.stringHasIndex(this.chars, index__457)) {
                return this.chars.codePointAt(index__457) == 58;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    RegexNode readFlagGroup() {
        boolean outerExtended__460 = this.extended;
        boolean outerCaseless__461 = this.caseless;
        while (!this.matchChar(58)) {
            int flag__462 = this.read();
            switch (flag__462) {
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
                    String t_1094 = Core.stringFromCodePoint(flag__462);
                    this.error("inline flag (?" + t_1094 + ":...) is not supported");
                    break;
                }
            }
        }
        RegexNode regex__463 = this.readOr();
        boolean rewrite__464;
        if (this.caseless) {
            rewrite__464 = !outerCaseless__461;
        } else {
            rewrite__464 = false;
        }
        this.extended = outerExtended__460;
        this.caseless = outerCaseless__461;
        if (!this.matchChar(41)) {
            this.error("closing paren");
        }
        if (rewrite__464) {
            return RegexParserGlobal.ignoreCase(regex__463);
        } else {
            return regex__463;
        }
    }
    boolean atShorthand() {
        boolean return__225;
        fn__466: {
            boolean t_897;
            if (this.isDone()) {
                t_897 = true;
            } else {
                t_897 = this.peek() != 92;
            }
            if (t_897) {
                return__225 = false;
                break fn__466;
            }
            int after__467 = Core.stringNext(this.chars, this.pos);
            if (!Core.stringHasIndex(this.chars, after__467)) {
                return__225 = false;
                break fn__466;
            }
            int subject_630 = this.chars.codePointAt(after__467);
            boolean t_899;
            switch (subject_630) {
                case 100:
                    {
                    t_899 = true;
                    break;
                }
                case 68:
                    {
                    t_899 = true;
                    break;
                }
                case 115:
                    {
                    t_899 = true;
                    break;
                }
                case 83:
                    {
                    t_899 = true;
                    break;
                }
                case 119:
                    {
                    t_899 = true;
                    break;
                }
                default:
                    {
                    t_899 = subject_630 == 87;
                    break;
                }
            }
            if (t_899) {
                return true;
            } else {
                return false;
            }
        }
        return return__225;
    }
    public RegexNode charClassBody() {
        boolean invert__470 = this.matchChar(94);
        List<CodePart> opts__471 = new ArrayList<>();
        List<RegexNode> negations__472 = new ArrayList<>();
        StringBuilder optMembers__473 = new StringBuilder();
        List<String> negatedMembers__474 = new ArrayList<>();
        while (!this.matchChar(93)) {
            if (this.isDone()) {
                this.error("missing closing ']'");
            }
            if (this.atShorthand()) {
                this.advance();
                int letter__475 = this.read();
                boolean t_886;
                switch (letter__475) {
                    case 68:
                        {
                        t_886 = true;
                        break;
                    }
                    case 83:
                        {
                        t_886 = true;
                        break;
                    }
                    default:
                        {
                        t_886 = letter__475 == 87;
                        break;
                    }
                }
                if (t_886) {
                    Core.listAdd(negations__472, new CodeSet(List.of(RegexParserGlobal.shorthandSet(letter__475)), true));
                    Core.listAdd(negatedMembers__474, RegexParserGlobal.shorthandMembers(letter__475));
                } else {
                    Core.listAdd(opts__471, RegexParserGlobal.shorthandSet(letter__475));
                    optMembers__473.append(RegexParserGlobal.shorthandMembers(letter__475));
                }
                if (this.matchChar(45)) {
                    if (this.matchChar(93)) {
                        Core.listAdd(opts__471, new CodePoints("-"));
                        break;
                    }
                    this.error("invalid range edge");
                }
                continue;
            }
            CodePart first__476 = this.charClassUnit();
            if (this.matchChar(45)) {
                if (this.matchChar(93)) {
                    Core.listAdd(opts__471, first__476);
                    Core.listAdd(opts__471, new CodePoints("-"));
                    break;
                }
                if (this.isDone()) {
                    this.error("missing closing ']'");
                }
                CodePart second__477 = this.charClassUnit();
                int min__478 = this.extractCode(first__476);
                int max__479 = this.extractCode(second__477);
                if (min__478 > max__479) {
                    this.error("character class range is out of order");
                }
                Core.listAdd(opts__471, new CodeRange(min__478, max__479));
            } else {
                Core.listAdd(opts__471, first__476);
            }
        }
        List<CodePart> items__480 = List.copyOf(opts__471);
        boolean t_891;
        if (negations__472.isEmpty()) {
            if (!invert__470) {
                if (items__480.size() == 1) {
                    t_891 = !(Core.listGet(items__480, 0) instanceof CodeRange);
                } else {
                    t_891 = false;
                }
            } else {
                t_891 = false;
            }
        } else {
            t_891 = false;
        }
        if (t_891) {
            return Core.listGet(items__480, 0);
        } else if (negations__472.isEmpty()) {
            return new CodeSet(items__480, invert__470);
        } else if (!invert__470) {
            if (!items__480.isEmpty()) {
                Core.listAdd(negations__472, new CodeSet(items__480), 0);
            }
            if (negations__472.size() == 1) {
                return Core.listGet(negations__472, 0);
            } else {
                return new Or(List.copyOf(negations__472));
            }
        } else {
            List<CodePart> excluded__481;
            if (this.caseless) {
                excluded__481 = RegexParserGlobal.ignoreCaseParts(items__480);
            } else {
                excluded__481 = items__480;
            }
            return RegexParserGlobal.negatedClass(excluded__481, optMembers__473.toString(), List.copyOf(negatedMembers__474));
        }
    }
    CodePart charClassUnit() {
        if (this.matchChar(92)) {
            if (this.matchChar(98)) {
                return new CodePoints("\b");
            } else {
                RegexNode escape__484 = this.finishEscape();
                try {
                    return Core.cast(CodePart.class, escape__484);
                } catch (RuntimeException ignored$4) {
                    this.error("escape not allowed in a character class");
                    throw Core.bubble();
                }
            }
        } else {
            String t_1093 = Core.stringFromCodePoint(this.read());
            return new CodePoints(t_1093);
        }
    }
    int extractCode(CodePart codePart__486) {
        try {
            CodePoints t_883 = Core.cast(CodePoints.class, codePart__486);
            return t_883.getValue().codePointAt(0);
        } catch (RuntimeException ignored$5) {
            this.error("invalid range edge");
            throw Core.bubble();
        }
    }
    RegexNode finishHexEscape() {
        int t_1091 = this.readHexDigits(2, "hex");
        String t_1092 = Core.stringFromCodePoint(t_1091);
        return new CodePoints(t_1092);
    }
    RegexNode finishEscape() {
        if (this.isDone()) {
            this.error("trailing '\\' at end of pattern");
        }
        int escapeCode__492 = this.peek();
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
            boolean t_875;
            if (this.isDone()) {
                t_875 = true;
            } else {
                t_875 = !RegexParserGlobal.isAsciiLetter(this.peek());
            }
            if (t_875) {
                this.error("\\c must be followed by a letter");
            }
            String t_1087 = Core.stringFromCodePoint(this.read() % 32);
            return new CodePoints(t_1087);
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
            boolean t_877;
            if (RegexParserGlobal.isAsciiPunctuation(escapeCode__492)) {
                t_877 = true;
            } else {
                t_877 = escapeCode__492 == 32;
            }
            if (t_877) {
                this.advance();
                String t_1088 = Core.stringFromCodePoint(escapeCode__492);
                return new CodePoints(t_1088);
            } else {
                boolean t_879;
                if (this.peek() >= 49) {
                    t_879 = this.peek() <= 57;
                } else {
                    t_879 = false;
                }
                if (t_879) {
                    String t_1089 = Core.stringFromCodePoint(this.peek());
                    this.error("backreferences (" + "\\" + t_1089 + ") are not supported");
                    throw Core.bubble();
                } else {
                    boolean t_881;
                    if (this.matchChar(112)) {
                        t_881 = true;
                    } else {
                        t_881 = this.matchChar(80);
                    }
                    if (t_881) {
                        this.error("Unicode property escapes (\\p{...}) are not supported");
                        throw Core.bubble();
                    } else {
                        String t_1090 = Core.stringFromCodePoint(this.peek());
                        this.error("unknown escape sequence: " + "\\" + t_1090);
                        throw Core.bubble();
                    }
                }
            }
        }
    }
    public int braceMin;
    public int braceMax;
    int appendDigit(int count__496) {
        int digit__498 = this.read() - 48;
        if (count__496 > (2147483647 - digit__498) / 10) {
            this.error("repeat count is too large");
        }
        return count__496 * 10 + digit__498;
    }
    boolean tryParseBraceQuantifier() {
        boolean return__232;
        fn__500: {
            int savedPos__501 = this.pos;
            if (!this.matchChar(123)) {
                return__232 = false;
                break fn__500;
            }
            int min__502 = 0;
            int minDigits__503 = 0;
            while (true) {
                boolean t_869;
                if (!this.isDone()) {
                    if (this.peek() >= 48) {
                        t_869 = this.peek() <= 57;
                    } else {
                        t_869 = false;
                    }
                } else {
                    t_869 = false;
                }
                if (!t_869) {
                    break;
                }
                min__502 = this.appendDigit(min__502);
                minDigits__503 = minDigits__503 + 1;
            }
            if (minDigits__503 == 0) {
                this.pos = savedPos__501;
                return__232 = false;
                break fn__500;
            }
            if (this.matchChar(125)) {
                int t_567 = min__502;
                this.braceMin = t_567;
                this.braceMax = min__502;
                return__232 = true;
                break fn__500;
            }
            if (!this.matchChar(44)) {
                this.pos = savedPos__501;
                return__232 = false;
                break fn__500;
            }
            int max__504 = 0;
            int maxDigits__505 = 0;
            while (true) {
                boolean t_872;
                if (!this.isDone()) {
                    if (this.peek() >= 48) {
                        t_872 = this.peek() <= 57;
                    } else {
                        t_872 = false;
                    }
                } else {
                    t_872 = false;
                }
                if (!t_872) {
                    break;
                }
                max__504 = this.appendDigit(max__504);
                maxDigits__505 = maxDigits__505 + 1;
            }
            if (!this.matchChar(125)) {
                this.pos = savedPos__501;
                return__232 = false;
                break fn__500;
            }
            this.braceMin = min__502;
            if (maxDigits__505 == 0) {
                this.braceMax = -1;
            } else {
                this.braceMax = max__504;
            }
            return true;
        }
        return return__232;
    }
    public RegexNode readPostfix(RegexNode exist__507) {
        RegexNode return__233;
        fn__508: {
            Repeat repeat__509;
            if (this.matchChar(43)) {
                repeat__509 = new Repeat(exist__507, 1, null, this.matchChar(63));
            } else if (this.matchChar(42)) {
                repeat__509 = new Repeat(exist__507, 0, null, this.matchChar(63));
            } else if (this.matchChar(63)) {
                repeat__509 = new Repeat(exist__507, 0, 1, this.matchChar(63));
            } else {
                boolean t_1086 = this.tryParseBraceQuantifier();
                if (t_1086) {
                    boolean nonGreedy__510 = this.matchChar(63);
                    boolean t_864;
                    if (this.braceMax != -1) {
                        t_864 = this.braceMin > this.braceMax;
                    } else {
                        t_864 = false;
                    }
                    if (t_864) {
                        this.error("quantifier {" + Integer.toString(this.braceMin) + "," + Integer.toString(this.braceMax) + "} has min greater than max");
                    }
                    if (this.braceMax == -1) {
                        repeat__509 = new Repeat(exist__507, this.braceMin, null, nonGreedy__510);
                    } else {
                        repeat__509 = new Repeat(exist__507, this.braceMin, this.braceMax, nonGreedy__510);
                    }
                } else {
                    return__233 = exist__507;
                    break fn__508;
                }
            }
            boolean t_867;
            if (this.matchRepeat()) {
                t_867 = true;
            } else {
                t_867 = this.tryParseBraceQuantifier();
            }
            if (t_867) {
                this.error("nothing to repeat");
            }
            return repeat__509;
        }
        return return__233;
    }
    void claimName(String name__512) {
        if (!RegexParserGlobal.isCaptureName(name__512)) {
            this.error("invalid capture name '" + name__512 + "'; use an ASCII letter followed by ASCII letters or digits");
        }
        if (this.names.containsKey(name__512)) {
            this.error("duplicate capture name '" + name__512 + "'");
        }
        this.names.put(name__512, true);
    }
    void claimNames(RegexNode node__515) {
        if (node__515 instanceof Capture) {
            Capture t_853 = Core.cast(Capture.class, node__515);
            this.claimName(t_853.getName());
            this.claimNames(t_853.getItem());
        } else if (node__515 instanceof Or) {
            List<RegexNode> this__1039 = Core.cast(Or.class, node__515).getItems();
            int n__1041 = this__1039.size();
            int i__1042 = 0;
            while (i__1042 < n__1041) {
                RegexNode el__1043 = Core.listGet(this__1039, i__1042);
                i__1042 = i__1042 + 1;
                RegexNode item__517 = el__1043;
                this.claimNames(item__517);
            }
        } else if (node__515 instanceof Sequence) {
            List<RegexNode> this__1044 = Core.cast(Sequence.class, node__515).getItems();
            int n__1046 = this__1044.size();
            int i__1047 = 0;
            while (i__1047 < n__1046) {
                RegexNode el__1048 = Core.listGet(this__1044, i__1047);
                i__1047 = i__1047 + 1;
                RegexNode item__518 = el__1048;
                this.claimNames(item__518);
            }
        } else if (node__515 instanceof Repeat) {
            Repeat t_862 = Core.cast(Repeat.class, node__515);
            this.claimNames(t_862.getItem());
        }
    }
    public RegexNode readSingle() {
        if (this.matchChar(91)) {
            return this.charClassBody();
        } else if (this.matchChar(40)) {
            if (this.matchChar(63)) {
                if (this.matchChar(58)) {
                    RegexNode regex__521 = this.readOr();
                    if (!this.matchChar(41)) {
                        this.error("closing paren");
                    }
                    return regex__521;
                } else if (this.matchChar(36)) {
                    StringBuilder parts__522 = new StringBuilder();
                    while (!this.matchChar(41)) {
                        if (this.isDone()) {
                            this.error("missing closing paren in (?$...)");
                        }
                        Core.stringBuilderAppendCodePoint(parts__522, this.read());
                    }
                    String str__523 = parts__522.toString();
                    RegexNode slot__524;
                    try {
                        slot__524 = Core.mappedGet(this.slots, str__523);
                    } catch (RuntimeException ignored$6) {
                        this.error("no such slot: " + str__523);
                        throw Core.bubble();
                    }
                    this.claimNames(slot__524);
                    return slot__524;
                } else if (this.matchChar(47)) {
                    this.error("flags after a regex literal are not supported; use a group like (?i:...)");
                    throw Core.bubble();
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
                        int checkPos__525 = this.pos;
                        int fc__526 = this.read();
                        if (!this.isDone()) {
                            boolean t_844;
                            if (fc__526 == 80) {
                                if (this.peek() == 60) {
                                    t_844 = true;
                                } else if (this.peek() == 61) {
                                    t_844 = true;
                                } else {
                                    t_844 = this.peek() == 62;
                                }
                            } else {
                                t_844 = false;
                            }
                            if (t_844) {
                                this.error("Python-style (?P...) groups are not supported; use (?name=pattern) instead");
                            }
                        }
                        this.pos = checkPos__525;
                    }
                    StringBuilder str__527 = new StringBuilder();
                    while (this.matchChar(32)) {
                    }
                    while (true) {
                        boolean t_848;
                        if (!this.isDone()) {
                            if (this.peek() != 32) {
                                t_848 = this.peek() != 61;
                            } else {
                                t_848 = false;
                            }
                        } else {
                            t_848 = false;
                        }
                        if (!t_848) {
                            break;
                        }
                        Core.stringBuilderAppendCodePoint(str__527, this.read());
                    }
                    while (this.matchChar(32)) {
                    }
                    if (!this.matchChar(61)) {
                        this.error("missing = in (?" + str__527.toString() + "=...)");
                    }
                    String name__528 = str__527.toString();
                    this.claimName(name__528);
                    RegexNode regex__529 = this.readOr();
                    if (!this.matchChar(41)) {
                        this.error("closing paren");
                    }
                    return new Capture(name__528, regex__529);
                }
            } else {
                RegexNode regex__530 = this.readOr();
                if (!this.matchChar(41)) {
                    this.error("closing paren");
                }
                return regex__530;
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
            String t_1085 = Core.stringFromCodePoint(this.read());
            return new CodePoints(t_1085);
        }
    }
    public RegexNode readSeq() {
        List<RegexNode> seq__535 = new ArrayList<>();
        List<String> codeBuffer__536 = new ArrayList<>();
        class Local_1 {
            void popCodeBuffer__533() {
                if (!codeBuffer__536.isEmpty()) {
                    Function<String, String> fn__1095 = it__538 -> it__538;
                    Core.listAdd(seq__535, new CodePoints(Core.listJoinObj(codeBuffer__536, "", fn__1095)));
                    codeBuffer__536.clear();
                }
            }
        }
        final Local_1 local$1 = new Local_1();
        Consumer<RegexNode> add__534 = regex__539 -> {
            if (regex__539 instanceof CodePoints) {
                CodePoints t_836 = Core.cast(CodePoints.class, regex__539);
                Core.listAdd(codeBuffer__536, t_836.getValue());
            } else {
                local$1.popCodeBuffer__533();
                Core.listAdd(seq__535, regex__539);
            }
        };
        this.skipIgnored();
        while (true) {
            boolean t_837;
            if (!this.isDone()) {
                if (this.peek() != 124) {
                    t_837 = this.peek() != 41;
                } else {
                    t_837 = false;
                }
            } else {
                t_837 = false;
            }
            if (!t_837) {
                break;
            }
            RegexNode single__541 = this.readSingle();
            this.skipIgnored();
            RegexNode regex__542 = this.readPostfix(single__541);
            if (regex__542 instanceof Sequence) {
                List<RegexNode> this__1049 = Core.cast(Sequence.class, regex__542).getItems();
                int n__1051 = this__1049.size();
                int i__1052 = 0;
                while (i__1052 < n__1051) {
                    RegexNode el__1053 = Core.listGet(this__1049, i__1052);
                    i__1052 = i__1052 + 1;
                    RegexNode item__543 = el__1053;
                    add__534.accept(item__543);
                }
            } else {
                add__534.accept(regex__542);
            }
            this.skipIgnored();
        }
        local$1.popCodeBuffer__533();
        if (seq__535.size() == 1) {
            return Core.listGet(seq__535, 0);
        } else {
            return new Sequence(List.copyOf(seq__535));
        }
    }
    public RegexNode readOr() {
        List<RegexNode> ors__546 = new ArrayList<>();
        while (true) {
            boolean t_827;
            if (!this.isDone()) {
                t_827 = this.peek() != 41;
            } else {
                t_827 = false;
            }
            if (!t_827) {
                break;
            }
            boolean t_829;
            if (ors__546.size() != 0) {
                t_829 = !this.matchChar(124);
            } else {
                t_829 = false;
            }
            if (t_829) {
                this.error("or");
            }
            RegexNode regex__547 = this.readSeq();
            if (regex__547 instanceof Or) {
                Core.listAddAll(ors__546, Core.cast(Or.class, regex__547).getItems());
            } else {
                Core.listAdd(ors__546, regex__547);
            }
        }
        int subject_648 = ors__546.size();
        switch (subject_648) {
            case 0:
                {
                return new Sequence(List.of());
            }
            case 1:
                {
                return Core.listGet(ors__546, 0);
            }
            default:
                {
                return new Or(List.copyOf(ors__546));
            }
        }
    }
    public RegexNode readAll() {
        RegexNode result__550 = this.readOr();
        if (!this.isDone()) {
            this.error("unexpected ')'; unmatched closing parenthesis");
        }
        return result__550;
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
    public Parser(String chars__552, @Nullable Map<String, RegexNode> slots__683) {
        Map<String, RegexNode> slots__553;
        if (slots__683 == null) {
            slots__553 = noSlots__294;
        } else {
            slots__553 = slots__683;
        }
        this.chars = chars__552;
        this.slots = slots__553;
        this.pos = 0;
        this.errorMessage = null;
        Map<String, Boolean> t_556 = new LinkedHashMap<>();
        this.names = t_556;
        this.extended = false;
        this.caseless = false;
        this.braceMin = 0;
        this.braceMax = 0;
    }
    public Parser(String chars__552) {
        this(chars__552, null);
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
    public void setPos(int newPos__606) {
        this.pos = newPos__606;
    }
    public @Nullable String getErrorMessage() {
        return this.errorMessage;
    }
    public void setErrorMessage(@Nullable String newErrorMessage__613) {
        this.errorMessage = newErrorMessage__613;
    }
    public int getBraceMin() {
        return this.braceMin;
    }
    public void setBraceMin(int newBraceMin__620) {
        this.braceMin = newBraceMin__620;
    }
    public int getBraceMax() {
        return this.braceMax;
    }
    public void setBraceMax(int newBraceMax__627) {
        this.braceMax = newBraceMax__627;
    }
}
