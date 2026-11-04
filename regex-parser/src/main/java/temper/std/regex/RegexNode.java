package temper.std.regex;
import java.util.List;
import java.util.function.Function;
public interface RegexNode {
    default Regex compiled() {
        return new Regex(this);
    }
    default boolean found(String text__165) {
        return this.compiled().found(text__165);
    }
    default Match find(String text__168) {
        return this.compiled().find(text__168);
    }
    /**
     * Replace and split functions are also available. Both apply to all matches in
     * the string, replacing all or splitting at all.
     */
    default String replace(String text__171, Function<Match, String> format__172) {
        return this.compiled().replace(text__171, format__172);
    }
    default List<String> split(String text__175) {
        return this.compiled().split(text__175);
    }
}
