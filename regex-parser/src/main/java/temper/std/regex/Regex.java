package temper.std.regex;
import static temper.std.regex.RegexGlobal.regexRefs__157;
import java.util.List;
import temper.core.Nullable;
import java.util.function.Function;
public final class Regex {
    public final RegexNode data;
    public Regex(RegexNode data__257) {
        RegexNode t_414 = data__257;
        this.data = t_414;
        String formatted__259 = RegexFormatter.regexFormat(data__257);
        Object t_413 = Core.regexCompiledFormatted(data__257, formatted__259);
        this.compiled = t_413;
    }
    public boolean found(String text__261) {
        return Core.regexCompiledFound(this, this.compiled, text__261);
    }
    public Match find(String text__264, @Nullable Integer begin__543) {
        int begin__265;
        if (begin__543 == null) {
            begin__265 = 0;
        } else {
            begin__265 = begin__543.intValue();
        }
        return Core.regexCompiledFind(this, this.compiled, text__264, begin__265, regexRefs__157);
    }
    public Match find(String text__264) {
        return find(text__264, null);
    }
    public String replace(String text__268, Function<Match, String> format__269) {
        return Core.regexCompiledReplace(this, this.compiled, text__268, format__269, regexRefs__157);
    }
    public List<String> split(String text__272) {
        return Core.regexCompiledSplit(this, this.compiled, text__272, regexRefs__157);
    }
    final Object compiled;
    public RegexNode getData() {
        return this.data;
    }
}
