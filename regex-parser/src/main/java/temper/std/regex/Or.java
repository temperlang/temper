package temper.std.regex;
import java.util.List;
/**
 * `Or` matches any one of multiple options, such as `/ab|cd|e&#42;/`.
 */
public final class Or implements RegexNode {
    public final List<RegexNode> items;
    public Or(List<RegexNode> items__211) {
        this.items = items__211;
    }
    public List<RegexNode> getItems() {
        return this.items;
    }
}
