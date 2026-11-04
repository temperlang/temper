package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public interface CodePart extends RegexNode {
    default Regex compiled() {
        return RegexNode.super.compiled();
    }
    default boolean found(String text_725) {
        return RegexNode.super.found(text_725);
    }
    default Match find(String text_727) {
        return RegexNode.super.find(text_727);
    }
    default String replace(String text_729, Function<Match, String> format_730) {
        return RegexNode.super.replace(text_729, format_730);
    }
    default List<String> split(String text_732) {
        return RegexNode.super.split(text_732);
    }
}
