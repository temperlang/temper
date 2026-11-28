package classes;

final class Span {
    public final int start;
    public final int end;
    public final int length;

    public Span(int start, int end) {
        this.start = start;
        this.end = end;
        this.length = this.end - this.start;
    }

    public int getStart() {
        return this.start;
    }

    public int getEnd() {
        return this.end;
    }

    public int getLength() {
        return this.length;
    }

    public static final class Builder {
        int start;
        boolean start__set;

        public Builder start(int start) {
            start__set = true;
            this.start = start;
            return this;
        }

        int end;
        boolean end__set;

        public Builder end(int end) {
            end__set = true;
            this.end = end;
            return this;
        }

        public Span build() {
            if (!start__set || !end__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!start__set) {
                    _message.append(" start");
                }
                if (!end__set) {
                    _message.append(" end");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Span(start, end);
        }
    }
}
