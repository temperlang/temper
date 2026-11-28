package spreads;

final class Step {
    public final int n;
    public final String tag;

    public Step(int n, String tag) {
        this.n = n;
        this.tag = tag;
    }

    public Step next() {
        {
            Step spread = this;
            return new Step(this.n + 1, spread.tag);
        }
    }

    public int getN() {
        return this.n;
    }

    public String getTag() {
        return this.tag;
    }

    public static final class Builder {
        int n;
        boolean n__set;

        public Builder n(int n) {
            n__set = true;
            this.n = n;
            return this;
        }

        String tag;

        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public Step build() {
            if (!n__set || tag == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!n__set) {
                    _message.append(" n");
                }
                if (tag == null) {
                    _message.append(" tag");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Step(n, tag);
        }
    }
}
