package temper.std.regex;
import temper.core.Nullable;
public final class Repeat implements RegexNode {
    public final RegexNode item;
    public final int min;
    public final @Nullable Integer max;
    public final boolean reluctant;
    public static final class Builder {
        RegexNode item;
        public Builder item(RegexNode item) {
            this.item = item;
            return this;
        }
        int min;
        boolean min__set;
        public Builder min(int min) {
            min__set = true;
            this.min = min;
            return this;
        }
        @Nullable Integer max;
        boolean max__set;
        public Builder max(@Nullable Integer max) {
            max__set = true;
            this.max = max;
            return this;
        }
        @Nullable Boolean reluctant;
        public Builder reluctant(@Nullable Boolean reluctant) {
            this.reluctant = reluctant;
            return this;
        }
        public Repeat build() {
            if (!min__set || !max__set || item == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!min__set) {
                    _message.append(" min");
                }
                if (!max__set) {
                    _message.append(" max");
                }
                if (item == null) {
                    _message.append(" item");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Repeat(item, min, max, reluctant);
        }
    }
    public Repeat(RegexNode item__217, int min__218, @Nullable Integer max__219, @Nullable Boolean reluctant__538) {
        boolean reluctant__220;
        if (reluctant__538 == null) {
            reluctant__220 = false;
        } else {
            reluctant__220 = reluctant__538.booleanValue();
        }
        this.item = item__217;
        this.min = min__218;
        this.max = max__219;
        this.reluctant = reluctant__220;
    }
    public Repeat(RegexNode item__217, int min__218, @Nullable Integer max__219) {
        this(item__217, min__218, max__219, null);
    }
    public RegexNode getItem() {
        return this.item;
    }
    public int getMin() {
        return this.min;
    }
    public @Nullable Integer getMax() {
        return this.max;
    }
    public boolean isReluctant() {
        return this.reluctant;
    }
}
