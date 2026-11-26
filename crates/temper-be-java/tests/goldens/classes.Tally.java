package classes;

final class Tally {
    public int count;
    public final String name;

    public Tally(int count, String name) {
        this.count = count;
        this.name = name;
    }

    public int getCount() {
        return this.count;
    }

    public void setCount(int newCount) {
        this.count = newCount;
    }

    public String getName() {
        return this.name;
    }

    public static final class Builder {
        int count;
        boolean count__set;

        public Builder count(int count) {
            count__set = true;
            this.count = count;
            return this;
        }

        String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Tally build() {
            if (!count__set || name == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!count__set) {
                    _message.append(" count");
                }
                if (name == null) {
                    _message.append(" name");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Tally(count, name);
        }
    }
}
