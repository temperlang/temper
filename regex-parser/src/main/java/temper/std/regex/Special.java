package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public interface Special extends RegexNode {
    default Regex compiled() {
        return RegexNode.super.compiled();
    }
    default boolean found(String text_745) {
        return RegexNode.super.found(text_745);
    }
    default Match find(String text_747) {
        return RegexNode.super.find(text_747);
    }
    default String replace(String text_749, Function<Match, String> format_750) {
        return RegexNode.super.replace(text_749, format_750);
    }
    default List<String> split(String text_752) {
        return RegexNode.super.split(text_752);
    }
}
