package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public final class DigitSpecial implements SpecialSet {
    public DigitSpecial() {
    }
    public Regex compiled() {
        return SpecialSet.super.compiled();
    }
    public boolean found(String text_805) {
        return SpecialSet.super.found(text_805);
    }
    public Match find(String text_807) {
        return SpecialSet.super.find(text_807);
    }
    public String replace(String text_809, Function<Match, String> format_810) {
        return SpecialSet.super.replace(text_809, format_810);
    }
    public List<String> split(String text_812) {
        return SpecialSet.super.split(text_812);
    }
}
