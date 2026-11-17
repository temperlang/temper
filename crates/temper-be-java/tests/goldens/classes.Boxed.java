package classes;

final class Boxed<T> {
    public final T value;
    public final int tag;

    public Boxed(T value, int tag) {
        this.value = value;
        this.tag = tag;
    }

    public boolean eq(Boxed<T> other) {
        return this.tag == other.tag;
    }

    public boolean sameAs(Boxed<T> other) {
        if (this.eq(other)) {
            return other.eq(this);
        } else {
            return false;
        }
    }

    public T getValue() {
        return this.value;
    }

    public int getTag() {
        return this.tag;
    }

    public static final class Builder<T> {
        T value;

        public Builder<T> value(T value) {
            this.value = value;
            return this;
        }

        int tag;
        boolean tag__set;

        public Builder<T> tag(int tag) {
            tag__set = true;
            this.tag = tag;
            return this;
        }

        public Boxed<T> build() {
            if (value == null || !tag__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (value == null) {
                    _message.append(" value");
                }
                if (!tag__set) {
                    _message.append(" tag");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Boxed<>(value, tag);
        }
    }
}
