package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public final class SpaceSpecial implements SpecialSet {
    public SpaceSpecial() {
    }
    public Regex compiled() {
        return SpecialSet.super.compiled();
    }
    public boolean found(String text_815) {
        return SpecialSet.super.found(text_815);
    }
    public Match find(String text_817) {
        return SpecialSet.super.find(text_817);
    }
    public String replace(String text_819, Function<Match, String> format_820) {
        return SpecialSet.super.replace(text_819, format_820);
    }
    public List<String> split(String text_822) {
        return SpecialSet.super.split(text_822);
    }
}
