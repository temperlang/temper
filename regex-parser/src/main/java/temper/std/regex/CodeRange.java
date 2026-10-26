package temper.std.regex;
public final class CodeRange implements CodePart {
    public final int min;
    public final int max;
    public static final class Builder {
        int min;
        boolean min__set;
        public Builder min(int min) {
            min__set = true;
            this.min = min;
            return this;
        }
        int max;
        boolean max__set;
        public Builder max(int max) {
            max__set = true;
            this.max = max;
            return this;
        }
        public CodeRange build() {
            if (!min__set || !max__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!min__set) {
                    _message.append(" min");
                }
                if (!max__set) {
                    _message.append(" max");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new CodeRange(min, max);
        }
    }
    public CodeRange(int min__202, int max__203) {
        this.min = min__202;
        this.max = max__203;
    }
    public int getMin() {
        return this.min;
    }
    public int getMax() {
        return this.max;
    }
}
