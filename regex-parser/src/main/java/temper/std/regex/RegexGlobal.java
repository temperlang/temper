package temper.std.regex;
import java.util.List;
import temper.core.Nullable;
import temper.core.Core;
import java.util.ArrayList;
import static temper.std.regex.Codes.dot;
import static temper.std.regex.Codes.dash;
import static temper.std.regex.Codes.peso;
import static temper.std.regex.Codes.pipe;
import static temper.std.regex.Codes.plus;
import static temper.std.regex.Codes.star;
import static temper.std.regex.Codes.space;
import static temper.std.regex.Codes.caret;
import static temper.std.regex.Codes.slash;
import static temper.std.regex.Codes.tilde;
import static temper.std.regex.Codes.digit0;
import static temper.std.regex.Codes.digit9;
import static temper.std.regex.Codes.upperA;
import static temper.std.regex.Codes.upperZ;
import static temper.std.regex.Codes.lowerA;
import static temper.std.regex.Codes.lowerZ;
import static temper.std.regex.Codes.question;
import static temper.std.regex.Codes.ampersand;
import static temper.std.regex.Codes.backslash;
import static temper.std.regex.Codes.curlyLeft;
import static temper.std.regex.Codes.roundLeft;
import static temper.std.regex.Codes.underscore;
import static temper.std.regex.Codes.curlyRight;
import static temper.std.regex.Codes.roundRight;
import static temper.std.regex.Codes.squareLeft;
import static temper.std.regex.Codes.squareRight;
public final class RegexGlobal {
    private RegexGlobal() {
    }
    static final List<Integer> escapeNeeds__158;
    static final RegexRefs regexRefs__157;
    public static final Special Begin;
    public static final Special Dot;
    public static final Special End;
    public static final Special WordBoundary;
    public static final SpecialSet Digit;
    public static final SpecialSet Space;
    public static final SpecialSet Word;
    static List<Integer> buildEscapeNeeds() {
        List<Integer> escapeNeeds__374 = new ArrayList<>();
        int code__375 = 0;
        while (code__375 <= 127) {
            int t_697;
            boolean t_673;
            if (code__375 == dash) {
                t_673 = true;
            } else if (code__375 == space) {
                t_673 = true;
            } else if (code__375 == underscore) {
                t_673 = true;
            } else {
                boolean t_669;
                if (digit0 <= code__375) {
                    t_669 = code__375 <= digit9;
                } else {
                    t_669 = false;
                }
                if (t_669) {
                    t_673 = true;
                } else {
                    boolean t_671;
                    if (upperA <= code__375) {
                        t_671 = code__375 <= upperZ;
                    } else {
                        t_671 = false;
                    }
                    if (t_671) {
                        t_673 = true;
                    } else if (lowerA <= code__375) {
                        t_673 = code__375 <= lowerZ;
                    } else {
                        t_673 = false;
                    }
                }
            }
            if (t_673) {
                t_697 = 0;
            } else {
                boolean t_680;
                if (code__375 == ampersand) {
                    t_680 = true;
                } else if (code__375 == backslash) {
                    t_680 = true;
                } else if (code__375 == caret) {
                    t_680 = true;
                } else if (code__375 == curlyLeft) {
                    t_680 = true;
                } else if (code__375 == curlyRight) {
                    t_680 = true;
                } else if (code__375 == dot) {
                    t_680 = true;
                } else if (code__375 == peso) {
                    t_680 = true;
                } else if (code__375 == pipe) {
                    t_680 = true;
                } else if (code__375 == plus) {
                    t_680 = true;
                } else if (code__375 == question) {
                    t_680 = true;
                } else if (code__375 == roundLeft) {
                    t_680 = true;
                } else if (code__375 == roundRight) {
                    t_680 = true;
                } else if (code__375 == slash) {
                    t_680 = true;
                } else if (code__375 == squareLeft) {
                    t_680 = true;
                } else if (code__375 == squareRight) {
                    t_680 = true;
                } else if (code__375 == star) {
                    t_680 = true;
                } else {
                    t_680 = code__375 == tilde;
                }
                if (t_680) {
                    t_697 = 2;
                } else {
                    t_697 = 1;
                }
            }
            Core.listAdd(escapeNeeds__374, t_697);
            code__375 = code__375 + 1;
        }
        return List.copyOf(escapeNeeds__374);
    }
    public static RegexNode entire(RegexNode item__221) {
        return new Sequence(List.of(Begin, item__221, End));
    }
    public static Repeat oneOrMore(RegexNode item__223, @Nullable Boolean reluctant__544) {
        boolean reluctant__224;
        if (reluctant__544 == null) {
            reluctant__224 = false;
        } else {
            reluctant__224 = reluctant__544.booleanValue();
        }
        return new Repeat(item__223, 1, null, reluctant__224);
    }
    public static Repeat oneOrMore(RegexNode item__223) {
        return oneOrMore(item__223, null);
    }
    public static Repeat optional(RegexNode item__226, @Nullable Boolean reluctant__545) {
        boolean reluctant__227;
        if (reluctant__545 == null) {
            reluctant__227 = false;
        } else {
            reluctant__227 = reluctant__545.booleanValue();
        }
        return new Repeat(item__226, 0, 1, reluctant__227);
    }
    public static Repeat optional(RegexNode item__226) {
        return optional(item__226, null);
    }
    static {
        escapeNeeds__158 = RegexGlobal.buildEscapeNeeds();
        regexRefs__157 = new RegexRefs();
        Begin = new BeginSpecial();
        Dot = new DotSpecial();
        End = new EndSpecial();
        WordBoundary = new WordBoundarySpecial();
        Digit = new DigitSpecial();
        Space = new SpaceSpecial();
        Word = new WordSpecial();
    }
}
