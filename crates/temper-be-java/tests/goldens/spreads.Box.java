package spreads;

import temper.core.Nullable;

final class Box implements Sized {
    public final int w;
    public final int h;
    public final @Nullable String label;

    public Box(int w, int h, @Nullable String label) {
        this.w = w;
        this.h = h;
        this.label = label;
    }

    public Box(int w, int h) {
        this(w, h, null);
    }

    public int area() {
        return this.w * this.h;
    }

    public String toString() {
        @Nullable String subject = this.label;
        return (subject == null ? "box" : subject) + " " + this.w + "x" + this.h;
    }

    public int getW() {
        return this.w;
    }

    public int getH() {
        return this.h;
    }

    public @Nullable String getLabel() {
        return this.label;
    }

    public static final class Builder {
        int w;
        boolean w__set;

        public Builder w(int w) {
            w__set = true;
            this.w = w;
            return this;
        }

        int h;
        boolean h__set;

        public Builder h(int h) {
            h__set = true;
            this.h = h;
            return this;
        }

        @Nullable String label;

        public Builder label(@Nullable String label) {
            this.label = label;
            return this;
        }

        public Box build() {
            if (!w__set || !h__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!w__set) {
                    _message.append(" w");
                }
                if (!h__set) {
                    _message.append(" h");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Box(w, h, label);
        }
    }
}
