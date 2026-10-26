package temper.std.regex;
import temper.core.Core;
import temper.core.Nullable;
import static temper.std.regex.Codes.tab;
import static temper.std.regex.Codes.dash;
import static temper.std.regex.Codes.space;
import static temper.std.regex.Codes.digit9;
import static temper.std.regex.Codes.lowerZ;
import static temper.std.regex.Codes.newline;
import static temper.std.regex.Codes.uint16Max;
import static temper.std.regex.Codes.surrogateMin;
import static temper.std.regex.Codes.surrogateMax;
import static temper.std.regex.Codes.carriageReturn;
import static temper.std.regex.Codes.highControlMax;
import static temper.std.regex.RegexGlobal.regexRefs__157;
import static temper.std.regex.Codes.supplementalMin;
import static temper.std.regex.RegexGlobal.escapeNeeds__158;
final class RegexFormatter {
    final StringBuilder out;
    public static String regexFormat(RegexNode data__302) {
        return new RegexFormatter().format(data__302);
    }
    public String format(RegexNode regex__305) {
        this.pushRegex(regex__305);
        return this.out.toString();
    }
    void pushRegex(RegexNode regex__308) {
        if (regex__308 instanceof Capture) {
            Capture t_632 = Core.cast(Capture.class, regex__308);
            this.pushCapture(t_632);
        } else if (regex__308 instanceof CodePoints) {
            CodePoints t_635 = Core.cast(CodePoints.class, regex__308);
            this.pushCodePoints(t_635, false);
        } else if (regex__308 instanceof CodeRange) {
            CodeRange t_638 = Core.cast(CodeRange.class, regex__308);
            this.pushCodeRange(t_638);
        } else if (regex__308 instanceof CodeSet) {
            CodeSet t_641 = Core.cast(CodeSet.class, regex__308);
            this.pushCodeSet(t_641);
        } else if (regex__308 instanceof Or) {
            Or t_644 = Core.cast(Or.class, regex__308);
            this.pushOr(t_644);
        } else if (regex__308 instanceof Repeat) {
            Repeat t_647 = Core.cast(Repeat.class, regex__308);
            this.pushRepeat(t_647);
        } else if (regex__308 instanceof Sequence) {
            Sequence t_650 = Core.cast(Sequence.class, regex__308);
            this.pushSequence(t_650);
        } else if (regex__308 instanceof BeginSpecial) {
            this.out.append("^");
        } else if (regex__308 instanceof DotSpecial) {
            this.out.append(".");
        } else if (regex__308 instanceof EndSpecial) {
            this.out.append("$");
        } else if (regex__308 instanceof WordBoundarySpecial) {
            this.out.append("\\b");
        } else if (regex__308 instanceof DigitSpecial) {
            this.out.append("\\d");
        } else if (regex__308 instanceof SpaceSpecial) {
            this.out.append("\\s");
        } else if (regex__308 instanceof WordSpecial) {
            this.out.append("\\w");
        }
    }
    void pushCapture(Capture capture__311) {
        this.out.append("(");
        this.pushCaptureName(this.out, capture__311.getName());
        this.pushRegex(capture__311.getItem());
        this.out.append(")");
    }
    void pushCaptureName(StringBuilder out__314, String name__315) {
        out__314.append("?<" + name__315 + ">");
    }
    void pushCode(int code__318, boolean insideCodeSet__319) {
        fn__320: {
            String specialEscape__321;
            if (code__318 == carriageReturn) {
                specialEscape__321 = "r";
            } else if (code__318 == newline) {
                specialEscape__321 = "n";
            } else if (code__318 == tab) {
                specialEscape__321 = "t";
            } else {
                specialEscape__321 = "";
            }
            if (!specialEscape__321.equals("")) {
                this.out.append("\\");
                this.out.append(specialEscape__321);
                break fn__320;
            }
            if (code__318 <= 127) {
                int escapeNeed__322 = Core.listGet(escapeNeeds__158, code__318);
                boolean t_620;
                if (escapeNeed__322 == 2) {
                    t_620 = true;
                } else if (insideCodeSet__319) {
                    t_620 = code__318 == dash;
                } else {
                    t_620 = false;
                }
                if (t_620) {
                    this.out.append("\\");
                    StringBuilder t_704 = this.out;
                    String t_703 = Core.stringFromCodePoint(code__318);
                    t_704.append(t_703);
                    break fn__320;
                } else if (escapeNeed__322 == 0) {
                    StringBuilder t_706 = this.out;
                    String t_705 = Core.stringFromCodePoint(code__318);
                    t_706.append(t_705);
                    break fn__320;
                }
            }
            boolean t_627;
            if (code__318 >= supplementalMin) {
                t_627 = true;
            } else if (code__318 > highControlMax) {
                boolean t_625;
                boolean t_623;
                if (surrogateMin <= code__318) {
                    t_623 = code__318 <= surrogateMax;
                } else {
                    t_623 = false;
                }
                if (t_623) {
                    t_625 = true;
                } else {
                    t_625 = code__318 == uint16Max;
                }
                t_627 = !t_625;
            } else {
                t_627 = false;
            }
            if (t_627) {
                StringBuilder t_708 = this.out;
                String t_707 = Core.stringFromCodePoint(code__318);
                t_708.append(t_707);
            } else {
                temper.std.regex.Core.regexFormatterPushCodeTo(this, this.out, code__318, insideCodeSet__319);
            }
        }
    }
    void pushCodePoints(CodePoints codePoints__329, boolean insideCodeSet__330) {
        String value__332 = codePoints__329.getValue();
        int index__333 = 0;
        while (Core.stringHasIndex(value__332, index__333)) {
            this.pushCode(value__332.codePointAt(index__333), insideCodeSet__330);
            index__333 = Core.stringNext(value__332, index__333);
        }
    }
    void pushCodeRange(CodeRange codeRange__335) {
        this.out.append("[");
        this.pushCodeRangeUnwrapped(codeRange__335);
        this.out.append("]");
    }
    void pushCodeRangeUnwrapped(CodeRange codeRange__338) {
        this.pushCode(codeRange__338.getMin(), true);
        this.out.append("-");
        this.pushCode(codeRange__338.getMax(), true);
    }
    void pushCodeSet(CodeSet codeSet__341) {
        RegexNode adjusted__343 = this.adjustCodeSet(codeSet__341, regexRefs__157);
        if (adjusted__343 instanceof CodeSet) {
            CodeSet t_616 = Core.cast(CodeSet.class, adjusted__343);
            if (t_616.getItems().isEmpty()) {
                if (t_616.isNegated()) {
                    this.out.append("[\\s\\S]");
                } else {
                    this.out.append("(?:$.)");
                }
            } else {
                this.out.append("[");
                if (t_616.isNegated()) {
                    this.out.append("^");
                }
                int i__344 = 0;
                while (i__344 < t_616.getItems().size()) {
                    this.pushCodeSetItem(Core.listGet(t_616.getItems(), i__344));
                    i__344 = i__344 + 1;
                }
                this.out.append("]");
            }
        } else {
            this.pushRegex(adjusted__343);
        }
    }
    RegexNode adjustCodeSet(CodeSet codeSet__346, RegexRefs regexRefs__347) {
        return codeSet__346;
    }
    void pushCodeSetItem(CodePart codePart__350) {
        if (codePart__350 instanceof CodePoints) {
            CodePoints t_607 = Core.cast(CodePoints.class, codePart__350);
            this.pushCodePoints(t_607, true);
        } else if (codePart__350 instanceof CodeRange) {
            CodeRange t_610 = Core.cast(CodeRange.class, codePart__350);
            this.pushCodeRangeUnwrapped(t_610);
        } else if (codePart__350 instanceof SpecialSet) {
            SpecialSet t_613 = Core.cast(SpecialSet.class, codePart__350);
            this.pushRegex(t_613);
        }
    }
    void pushOr(Or or__353) {
        if (!or__353.getItems().isEmpty()) {
            this.out.append("(?:");
            this.pushRegex(Core.listGet(or__353.getItems(), 0));
            int i__355 = 1;
            while (i__355 < or__353.getItems().size()) {
                this.out.append("|");
                this.pushRegex(Core.listGet(or__353.getItems(), i__355));
                i__355 = i__355 + 1;
            }
            this.out.append(")");
        }
    }
    void pushRepeat(Repeat repeat__357) {
        this.out.append("(?:");
        this.pushRegex(repeat__357.getItem());
        this.out.append(")");
        int min__359 = repeat__357.getMin();
        @Nullable Integer max__360 = repeat__357.getMax();
        boolean t_599;
        if (min__359 == 0) {
            boolean t_709;
            if (max__360 == null) {
                t_709 = false;
            } else {
                t_709 = max__360.intValue() == 1;
            }
            t_599 = t_709;
        } else {
            t_599 = false;
        }
        if (t_599) {
            this.out.append("?");
        } else {
            boolean t_601;
            if (min__359 == 0) {
                t_601 = max__360 == null;
            } else {
                t_601 = false;
            }
            if (t_601) {
                this.out.append("*");
            } else {
                boolean t_603;
                if (min__359 == 1) {
                    t_603 = max__360 == null;
                } else {
                    t_603 = false;
                }
                if (t_603) {
                    this.out.append("+");
                } else {
                    boolean t_711;
                    this.out.append("{" + Integer.toString(min__359));
                    if (max__360 == null) {
                        t_711 = false;
                    } else {
                        t_711 = min__359 == max__360.intValue();
                    }
                    if (!t_711) {
                        this.out.append(",");
                        if (max__360 != null) {
                            int max_571 = max__360.intValue();
                            this.out.append(Integer.toString(max_571));
                        }
                    }
                    this.out.append("}");
                }
            }
        }
        if (repeat__357.isReluctant()) {
            this.out.append("?");
        }
    }
    void pushSequence(Sequence sequence__362) {
        int i__364 = 0;
        while (i__364 < sequence__362.getItems().size()) {
            this.pushRegex(Core.listGet(sequence__362.getItems(), i__364));
            i__364 = i__364 + 1;
        }
    }
    public @Nullable Integer maxCode(CodePart codePart__366) {
        if (codePart__366 instanceof CodePoints) {
            CodePoints t_589 = Core.cast(CodePoints.class, codePart__366);
            String value__368 = t_589.getValue();
            if (value__368.isEmpty()) {
                return null;
            } else {
                int max__369 = 0;
                int index__370 = 0;
                while (Core.stringHasIndex(value__368, index__370)) {
                    int next__371 = value__368.codePointAt(index__370);
                    if (next__371 > max__369) {
                        max__369 = next__371;
                    }
                    index__370 = Core.stringNext(value__368, index__370);
                }
                return max__369;
            }
        } else if (codePart__366 instanceof CodeRange) {
            return Core.cast(CodeRange.class, codePart__366).getMax();
        } else if (codePart__366 instanceof DigitSpecial) {
            return digit9;
        } else if (codePart__366 instanceof SpaceSpecial) {
            return space;
        } else if (codePart__366 instanceof WordSpecial) {
            return lowerZ;
        } else {
            return null;
        }
    }
    public RegexFormatter() {
        StringBuilder t_412 = new StringBuilder();
        this.out = t_412;
    }
}
