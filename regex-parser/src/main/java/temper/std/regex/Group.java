package temper.std.regex;
public final class Group {
    public final String name;
    public final String value;
    public final int begin;
    public final int end;
    public static final class Builder {
        String name;
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        String value;
        public Builder value(String value) {
            this.value = value;
            return this;
        }
        int begin;
        boolean begin__set;
        public Builder begin(int begin) {
            begin__set = true;
            this.begin = begin;
            return this;
        }
        int end;
        boolean end__set;
        public Builder end(int end) {
            end__set = true;
            this.end = end;
            return this;
        }
        public Group build() {
            if (!begin__set || !end__set || name == null || value == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!begin__set) {
                    _message.append(" begin");
                }
                if (!end__set) {
                    _message.append(" end");
                }
                if (name == null) {
                    _message.append(" name");
                }
                if (value == null) {
                    _message.append(" value");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Group(name, value, begin, end);
        }
    }
    public Group(String name__242, String value__243, int begin__244, int end__245) {
        this.name = name__242;
        this.value = value__243;
        this.begin = begin__244;
        this.end = end__245;
    }
    public String getName() {
        return this.name;
    }
    public String getValue() {
        return this.value;
    }
    public int getBegin() {
        return this.begin;
    }
    public int getEnd() {
        return this.end;
    }
}
