package modules.core;

public final class Ratio {
    public final int n;
    public final int d;

    public Ratio(int n, int d) {
        this.n = n;
        this.d = d;
    }

    public boolean eq(Ratio other) {
        return this.n * other.d == other.n * this.d;
    }

    public int getN() {
        return this.n;
    }

    public int getD() {
        return this.d;
    }

    public static final class Builder {
        int n;
        boolean n__set;

        public Builder n(int n) {
            n__set = true;
            this.n = n;
            return this;
        }

        int d;
        boolean d__set;

        public Builder d(int d) {
            d__set = true;
            this.d = d;
            return this;
        }

        public Ratio build() {
            if (!n__set || !d__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!n__set) {
                    _message.append(" n");
                }
                if (!d__set) {
                    _message.append(" d");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Ratio(n, d);
        }
    }
}
