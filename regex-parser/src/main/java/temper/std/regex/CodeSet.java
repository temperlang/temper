package temper.std.regex;
import java.util.List;
import temper.core.Nullable;
public final class CodeSet implements RegexNode {
    public final List<CodePart> items;
    public final boolean negated;
    public static final class Builder {
        List<CodePart> items;
        public Builder items(List<CodePart> items) {
            this.items = items;
            return this;
        }
        @Nullable Boolean negated;
        public Builder negated(@Nullable Boolean negated) {
            this.negated = negated;
            return this;
        }
        public CodeSet build() {
            return new CodeSet(items, negated);
        }
    }
    public CodeSet(List<CodePart> items__207, @Nullable Boolean negated__537) {
        boolean negated__208;
        if (negated__537 == null) {
            negated__208 = false;
        } else {
            negated__208 = negated__537.booleanValue();
        }
        this.items = items__207;
        this.negated = negated__208;
    }
    public CodeSet(List<CodePart> items__207) {
        this(items__207, null);
    }
    public List<CodePart> getItems() {
        return this.items;
    }
    public boolean isNegated() {
        return this.negated;
    }
}
