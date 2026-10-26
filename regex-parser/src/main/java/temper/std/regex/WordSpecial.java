package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public final class WordSpecial implements SpecialSet {
    public WordSpecial() {
    }
    public Regex compiled() {
        return SpecialSet.super.compiled();
    }
    public boolean found(String text_825) {
        return SpecialSet.super.found(text_825);
    }
    public Match find(String text_827) {
        return SpecialSet.super.find(text_827);
    }
    public String replace(String text_829, Function<Match, String> format_830) {
        return SpecialSet.super.replace(text_829, format_830);
    }
    public List<String> split(String text_832) {
        return SpecialSet.super.split(text_832);
    }
}
