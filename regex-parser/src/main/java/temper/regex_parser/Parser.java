package temper.regex_parser;
import temper.core.Core;
import temper.std.regex.RegexNode;
import temper.std.regex.CodePoints;
import java.util.List;
import temper.std.regex.CodePart;
import temper.std.regex.Repeat;
import java.util.Map;
import temper.std.regex.CodeSet;
import java.util.ArrayList;
import static temper.std.regex.RegexGlobal.Word;
import static temper.std.regex.RegexGlobal.Digit;
import static temper.std.regex.RegexGlobal.Space;
import temper.core.Nullable;
import temper.std.regex.Or;
import temper.std.regex.Capture;
import java.util.function.Function;
import temper.std.regex.Sequence;
import temper.std.regex.CodeRange;
import static temper.std.regex.RegexGlobal.End;
import static temper.std.regex.RegexGlobal.Dot;
import static temper.std.regex.RegexGlobal.Begin;
import static temper.std.regex.RegexGlobal.WordBoundary;
final class Parser {
    public final String chars;
    public final Map<String, RegexNode> slots;
    public int pos;
    public final List<String> errors;
    public void error(String e__185) {
        int position__187 = Core.stringCountBetween(this.chars, 0, this.pos);
        Core.listAdd(this.errors, "at position " + Integer.toString(position__187) + ": " + e__185);
    }
    public boolean isDone() {
        return this.chars.length() <= this.pos;
    }
    public int peek() {
        int return__115;
        fn__191: {
            if (this.isDone()) {
                this.error("done");
                return__115 = 0;
                break fn__191;
            }
            return this.chars.codePointAt(this.pos);
        }
        return return__115;
    }
    public void advance() {
        int t_286 = Core.stringNext(this.chars, this.pos);
        this.pos = t_286;
    }
    public int read() {
        int ret__196 = this.peek();
        this.advance();
        return ret__196;
    }
    public boolean matchChar(int s__198) {
        if (this.peek() == s__198) {
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
    boolean isHexDigit(int c__203) {
        boolean t_390;
        if (c__203 >= 48) {
            t_390 = c__203 <= 57;
        } else {
            t_390 = false;
        }
        if (t_390) {
            return true;
        } else {
            boolean t_392;
            if (c__203 >= 97) {
                t_392 = c__203 <= 102;
            } else {
                t_392 = false;
            }
            if (t_392) {
                return true;
            } else if (c__203 >= 65) {
                return c__203 <= 70;
            } else {
                return false;
            }
        }
    }
    int hexValue(int c__206) {
        boolean t_384;
        if (c__206 >= 48) {
            t_384 = c__206 <= 57;
        } else {
            t_384 = false;
        }
        if (t_384) {
            return c__206 - 48;
        } else {
            boolean t_386;
            if (c__206 >= 97) {
                t_386 = c__206 <= 102;
            } else {
                t_386 = false;
            }
            if (t_386) {
                return c__206 - 97 + 10;
            } else {
                boolean t_388;
                if (c__206 >= 65) {
                    t_388 = c__206 <= 70;
                } else {
                    t_388 = false;
                }
                if (t_388) {
                    return c__206 - 65 + 10;
                } else {
                    throw Core.bubble();
                }
            }
        }
    }
    RegexNode finishUnicodeEscape() {
        if (this.matchChar(123)) {
            int codePoint__210 = 0;
            int count__211 = 0;
            while (!this.matchChar(125)) {
                int t_380;
                boolean t_378;
                if (this.isDone()) {
                    t_378 = true;
                } else {
                    t_378 = count__211 >= 6;
                }
                if (t_378) {
                    this.error("invalid unicode escape");
                    throw Core.bubble();
                }
                int c__212 = this.read();
                int t_381 = codePoint__210 * 16;
                try {
                    t_380 = this.hexValue(c__212);
                } catch (RuntimeException ignored$1) {
                    this.error("invalid hex digit in unicode escape");
                    throw Core.bubble();
                }
                codePoint__210 = t_381 + t_380;
                count__211 = count__211 + 1;
            }
            if (count__211 == 0) {
                this.error("empty unicode escape");
                throw Core.bubble();
            }
            String t_406 = Core.stringFromCodePoint(codePoint__210);
            return new CodePoints(t_406);
        } else {
            int codePoint__213 = 0;
            int i__214 = 0;
            while (i__214 < 4) {
                int t_382;
                if (this.isDone()) {
                    this.error("incomplete unicode escape");
                    throw Core.bubble();
                }
                int c__215 = this.read();
                int t_383 = codePoint__213 * 16;
                try {
                    t_382 = this.hexValue(c__215);
                } catch (RuntimeException ignored$2) {
                    this.error("invalid hex digit in unicode escape");
                    throw Core.bubble();
                }
                codePoint__213 = t_383 + t_382;
                i__214 = i__214 + 1;
            }
            String t_407 = Core.stringFromCodePoint(codePoint__213);
            return new CodePoints(t_407);
        }
    }
    public RegexNode charClassBody() {
        boolean invert__218 = this.matchChar(94);
        List<CodePart> opts__219 = new ArrayList<>();
        while (!this.matchChar(93)) {
            if (this.isDone()) {
                throw Core.bubble();
            }
            CodePart first__220 = this.charClassUnit();
            if (this.matchChar(45)) {
                if (this.matchChar(93)) {
                    Core.listAdd(opts__219, new CodePoints("-"));
                    break;
                }
                CodePart second__221 = this.charClassUnit();
                int t_404 = this.extractCode(first__220);
                int t_405 = this.extractCode(second__221);
                Core.listAdd(opts__219, new CodeRange(t_404, t_405));
            } else {
                Core.listAdd(opts__219, first__220);
            }
        }
        return new CodeSet(List.copyOf(opts__219), invert__218);
    }
    CodePart charClassUnit() {
        if (this.matchChar(92)) {
            if (this.matchChar(98)) {
                return new CodePoints("\b");
            } else {
                try {
                    RegexNode t_376 = this.finishEscape();
                    return Core.cast(CodePart.class, t_376);
                } catch (RuntimeException ignored$3) {
                    this.error("invalid code part");
                    throw Core.bubble();
                }
            }
        } else {
            String t_403 = Core.stringFromCodePoint(this.read());
            return new CodePoints(t_403);
        }
    }
    int extractCode(CodePart codePart__225) {
        try {
            CodePoints t_375 = Core.cast(CodePoints.class, codePart__225);
            return t_375.getValue().codePointAt(0);
        } catch (RuntimeException ignored$4) {
            this.error("invalid range edge");
            throw Core.bubble();
        }
    }
    RegexNode finishHexEscape() {
        int codePoint__229 = 0;
        int i__230 = 0;
        while (i__230 < 2) {
            int t_373;
            if (this.isDone()) {
                this.error("incomplete hex escape");
                throw Core.bubble();
            }
            int c__231 = this.read();
            int t_374 = codePoint__229 * 16;
            try {
                t_373 = this.hexValue(c__231);
            } catch (RuntimeException ignored$5) {
                this.error("invalid hex digit in hex escape");
                throw Core.bubble();
            }
            codePoint__229 = t_374 + t_373;
            i__230 = i__230 + 1;
        }
        String t_402 = Core.stringFromCodePoint(codePoint__229);
        return new CodePoints(t_402);
    }
    RegexNode finishEscape() {
        if (this.isDone()) {
            this.error("trailing '\\' at end of pattern");
            throw Core.bubble();
        }
        int escapeCode__234 = this.peek();
        if (this.matchChar(48)) {
            return new CodePoints("\u0000");
        } else if (this.matchChar(97)) {
            return new CodePoints("\u0007");
        } else if (this.matchChar(98)) {
            return WordBoundary;
        } else if (this.matchChar(66)) {
            this.error("\\B (non-word boundary) is not supported");
            throw Core.bubble();
        } else if (this.matchChar(100)) {
            return Digit;
        } else if (this.matchChar(68)) {
            return new CodeSet(List.of(Digit), true);
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
        } else {
            boolean t_355;
            if (this.matchChar(40)) {
                t_355 = true;
            } else if (this.matchChar(41)) {
                t_355 = true;
            } else if (this.matchChar(91)) {
                t_355 = true;
            } else if (this.matchChar(93)) {
                t_355 = true;
            } else if (this.matchChar(123)) {
                t_355 = true;
            } else if (this.matchChar(125)) {
                t_355 = true;
            } else if (this.matchChar(46)) {
                t_355 = true;
            } else if (this.matchChar(92)) {
                t_355 = true;
            } else if (this.matchChar(45)) {
                t_355 = true;
            } else if (this.matchChar(47)) {
                t_355 = true;
            } else if (this.matchChar(94)) {
                t_355 = true;
            } else if (this.matchChar(36)) {
                t_355 = true;
            } else if (this.matchChar(124)) {
                t_355 = true;
            } else {
                t_355 = this.matchRepeat();
            }
            if (t_355) {
                String t_399 = Core.stringFromCodePoint(escapeCode__234);
                return new CodePoints(t_399);
            } else {
                boolean t_369;
                if (this.peek() >= 49) {
                    t_369 = this.peek() <= 57;
                } else {
                    t_369 = false;
                }
                if (t_369) {
                    String t_400 = Core.stringFromCodePoint(this.peek());
                    this.error("backreferences (" + "\\" + t_400 + ") are not supported");
                    throw Core.bubble();
                } else {
                    boolean t_371;
                    if (this.matchChar(112)) {
                        t_371 = true;
                    } else {
                        t_371 = this.matchChar(80);
                    }
                    if (t_371) {
                        this.error("Unicode property escapes (\\p{...}) are not supported");
                        throw Core.bubble();
                    } else {
                        String t_401 = Core.stringFromCodePoint(this.peek());
                        this.error("unknown escape sequence: " + "\\" + t_401);
                        throw Core.bubble();
                    }
                }
            }
        }
    }
    public int braceMin;
    public int braceMax;
    boolean tryParseBraceQuantifier() {
        boolean return__128;
        fn__238: {
            int savedPos__239 = this.pos;
            if (!this.matchChar(123)) {
                return__128 = false;
                break fn__238;
            }
            int min__240 = 0;
            int minDigits__241 = 0;
            while (true) {
                boolean t_349;
                if (!this.isDone()) {
                    if (this.peek() >= 48) {
                        t_349 = this.peek() <= 57;
                    } else {
                        t_349 = false;
                    }
                } else {
                    t_349 = false;
                }
                if (!t_349) {
                    break;
                }
                min__240 = min__240 * 10 + (this.read() - 48);
                minDigits__241 = minDigits__241 + 1;
            }
            if (minDigits__241 == 0) {
                this.pos = savedPos__239;
                return__128 = false;
                break fn__238;
            }
            if (this.matchChar(125)) {
                int t_284 = min__240;
                this.braceMin = t_284;
                this.braceMax = min__240;
                return__128 = true;
                break fn__238;
            }
            if (!this.matchChar(44)) {
                this.pos = savedPos__239;
                return__128 = false;
                break fn__238;
            }
            int max__242 = 0;
            int maxDigits__243 = 0;
            while (true) {
                boolean t_352;
                if (!this.isDone()) {
                    if (this.peek() >= 48) {
                        t_352 = this.peek() <= 57;
                    } else {
                        t_352 = false;
                    }
                } else {
                    t_352 = false;
                }
                if (!t_352) {
                    break;
                }
                max__242 = max__242 * 10 + (this.read() - 48);
                maxDigits__243 = maxDigits__243 + 1;
            }
            if (!this.matchChar(125)) {
                this.pos = savedPos__239;
                return__128 = false;
                break fn__238;
            }
            this.braceMin = min__240;
            if (maxDigits__243 == 0) {
                this.braceMax = -1;
            } else {
                this.braceMax = max__242;
            }
            return true;
        }
        return return__128;
    }
    public RegexNode readPostfix(RegexNode exist__245) {
        if (this.matchChar(43)) {
            return new Repeat(exist__245, 1, null, this.matchChar(63));
        } else if (this.matchChar(42)) {
            return new Repeat(exist__245, 0, null, this.matchChar(63));
        } else if (this.matchChar(63)) {
            return new Repeat(exist__245, 0, 1, this.matchChar(63));
        } else if (this.tryParseBraceQuantifier()) {
            boolean nonGreedy__247 = this.matchChar(63);
            boolean t_347;
            if (this.braceMax != -1) {
                t_347 = this.braceMin > this.braceMax;
            } else {
                t_347 = false;
            }
            if (t_347) {
                this.error("quantifier {" + Integer.toString(this.braceMin) + "," + Integer.toString(this.braceMax) + "} has min greater than max");
                throw Core.bubble();
            }
            if (this.braceMax == -1) {
                return new Repeat(exist__245, this.braceMin, null, nonGreedy__247);
            } else {
                return new Repeat(exist__245, this.braceMin, this.braceMax, nonGreedy__247);
            }
        } else {
            return exist__245;
        }
    }
    public RegexNode readSingle() {
        if (this.matchChar(91)) {
            return this.charClassBody();
        } else if (this.matchChar(40)) {
            if (this.matchChar(63)) {
                if (this.matchChar(58)) {
                    RegexNode regex__250 = this.readOr();
                    if (!this.matchChar(41)) {
                        this.error("closing paren");
                        throw Core.bubble();
                    }
                    return regex__250;
                } else if (this.matchChar(36)) {
                    StringBuilder parts__251 = new StringBuilder();
                    while (!this.matchChar(41)) {
                        if (this.isDone()) {
                            this.error("missing closing paren in (?$...)");
                            throw Core.bubble();
                        }
                        Core.stringBuilderAppendCodePoint(parts__251, this.read());
                    }
                    String str__252 = parts__251.toString();
                    try {
                        return Core.mappedGet(this.slots, str__252);
                    } catch (RuntimeException ignored$6) {
                        this.error("no such slot: " + str__252);
                        throw Core.bubble();
                    }
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
                } else {
                    if (!this.isDone()) {
                        int checkPos__253 = this.pos;
                        int fc__254 = this.read();
                        if (!this.isDone()) {
                            if (this.peek() == 58) {
                                String t_397 = Core.stringFromCodePoint(fc__254);
                                this.error("inline flag group (?" + t_397 + ":...) is not supported");
                                throw Core.bubble();
                            }
                            boolean t_345;
                            if (fc__254 == 80) {
                                t_345 = this.peek() == 60;
                            } else {
                                t_345 = false;
                            }
                            if (t_345) {
                                this.error("Python-style (?P<name>...) is not supported; use (?name=pattern) instead");
                                throw Core.bubble();
                            }
                        }
                        this.pos = checkPos__253;
                    }
                    StringBuilder str__255 = new StringBuilder();
                    while (this.matchChar(32)) {
                    }
                    while (!this.matchChar(61)) {
                        if (this.isDone()) {
                            this.error("missing = in (?" + str__255.toString() + "=...)");
                            throw Core.bubble();
                        }
                        Core.stringBuilderAppendCodePoint(str__255, this.read());
                        while (this.matchChar(32)) {
                        }
                    }
                    RegexNode regex__256 = this.readOr();
                    if (!this.matchChar(41)) {
                        this.error("closing paren");
                        throw Core.bubble();
                    }
                    String name__257 = str__255.toString();
                    return new Capture(name__257, regex__256);
                }
            } else {
                RegexNode regex__258 = this.readOr();
                if (!this.matchChar(41)) {
                    this.error("closing paren");
                    throw Core.bubble();
                }
                return regex__258;
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
            String t_398 = Core.stringFromCodePoint(this.read());
            return new CodePoints(t_398);
        }
    }
    public RegexNode readSeq() {
        List<RegexNode> seq__262 = new ArrayList<>();
        List<String> codeBuffer__263 = new ArrayList<>();
        Runnable popCodeBuffer__261 = () -> {
            if (!codeBuffer__263.isEmpty()) {
                Function<String, String> fn__408 = it__265 -> it__265;
                Core.listAdd(seq__262, new CodePoints(Core.listJoinObj(codeBuffer__263, "", fn__408)));
                codeBuffer__263.clear();
            }
        };
        while (true) {
            boolean t_339;
            if (!this.isDone()) {
                if (this.peek() != 124) {
                    t_339 = this.peek() != 41;
                } else {
                    t_339 = false;
                }
            } else {
                t_339 = false;
            }
            if (!t_339) {
                break;
            }
            RegexNode t_396 = this.readSingle();
            RegexNode regex__266 = this.readPostfix(t_396);
            if (regex__266 instanceof CodePoints) {
                Core.listAdd(codeBuffer__263, Core.cast(CodePoints.class, regex__266).getValue());
            } else {
                popCodeBuffer__261.run();
                Core.listAdd(seq__262, regex__266);
            }
        }
        popCodeBuffer__261.run();
        if (seq__262.size() == 1) {
            return Core.listGet(seq__262, 0);
        } else {
            return new Sequence(List.copyOf(seq__262));
        }
    }
    public RegexNode readOr() {
        List<RegexNode> ors__269 = new ArrayList<>();
        while (true) {
            boolean t_335;
            if (!this.isDone()) {
                t_335 = this.peek() != 41;
            } else {
                t_335 = false;
            }
            if (!t_335) {
                break;
            }
            boolean t_337;
            if (ors__269.size() != 0) {
                t_337 = !this.matchChar(124);
            } else {
                t_337 = false;
            }
            if (t_337) {
                this.error("or");
                throw Core.bubble();
            }
            RegexNode regex__270 = this.readSeq();
            Core.listAdd(ors__269, regex__270);
        }
        if (ors__269.size() == 1) {
            return Core.listGet(ors__269, 0);
        } else {
            return new Or(List.copyOf(ors__269));
        }
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
    public Parser(String chars__272, @Nullable Map<String, RegexNode> slots__322) {
        Map<String, RegexNode> slots__273;
        if (slots__322 == null) {
            slots__273 = Core.mapConstructor(List.of());
        } else {
            slots__273 = slots__322;
        }
        this.chars = chars__272;
        this.slots = slots__273;
        this.pos = 0;
        List<String> t_274 = new ArrayList<>();
        this.errors = t_274;
        this.braceMin = 0;
        this.braceMax = 0;
    }
    public Parser(String chars__272) {
        this(chars__272, null);
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
    public void setPos(int newPos__297) {
        this.pos = newPos__297;
    }
    public List<String> getErrors() {
        return this.errors;
    }
    public int getBraceMin() {
        return this.braceMin;
    }
    public void setBraceMin(int newBraceMin__307) {
        this.braceMin = newBraceMin__307;
    }
    public int getBraceMax() {
        return this.braceMax;
    }
    public void setBraceMax(int newBraceMax__314) {
        this.braceMax = newBraceMax__314;
    }
}
