package classes;

public final class Rect implements Shape {
    public final int w;
    public final int h;
    private static final Rect unit = new Rect(1, 1);

    public Rect(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public int getArea() {
        return this.w * this.h;
    }

    public String toString() {
        return "Rect(" + this.w + ", " + this.h + ")";
    }

    public Rect scaled(int k) {
        return new Rect(this.w * k, this.h * k);
    }

    public int getW() {
        return this.w;
    }

    public int getH() {
        return this.h;
    }

    public static Rect getUnit() {
        return Rect.unit;
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

        public Rect build() {
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
            return new Rect(w, h);
        }
    }
}
