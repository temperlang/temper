package spreads;

final class Two<T> {
    public final T first;
    public final T second;

    public Two(T first, T second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return this.first;
    }

    public T getSecond() {
        return this.second;
    }

    public static final class Builder<T> {
        T first;

        public Builder<T> first(T first) {
            this.first = first;
            return this;
        }

        T second;

        public Builder<T> second(T second) {
            this.second = second;
            return this;
        }

        public Two<T> build() {
            if (first == null || second == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (first == null) {
                    _message.append(" first");
                }
                if (second == null) {
                    _message.append(" second");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Two<>(first, second);
        }
    }
}
