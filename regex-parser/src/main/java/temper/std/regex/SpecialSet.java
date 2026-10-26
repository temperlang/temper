package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public interface SpecialSet extends CodePart, Special {
    default Regex compiled() {
        return CodePart.super.compiled();
    }
    default boolean found(String text_795) {
        return CodePart.super.found(text_795);
    }
    default Match find(String text_797) {
        return CodePart.super.find(text_797);
    }
    default String replace(String text_799, Function<Match, String> format_800) {
        return CodePart.super.replace(text_799, format_800);
    }
    default List<String> split(String text_802) {
        return CodePart.super.split(text_802);
    }
}
