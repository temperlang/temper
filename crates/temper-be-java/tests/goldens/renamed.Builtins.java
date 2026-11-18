package renamed;

public final class Builtins {
    public final int hash;

    public Builtins(int hash) {
        this.hash = hash;
    }

    public int get(int i) {
        return this.hash + i;
    }

    public int set(int i) {
        return i * 2;
    }

    public int map(int i) {
        return i + 1;
    }

    public String format() {
        return "#" + this.hash;
    }

    public int getInt() {
        return this.hash * 10;
    }

    public int later() {
        return this.getInt() + 1;
    }

    public int init() {
        return this.hash + 3;
    }

    public String print() {
        return "print " + this.init();
    }

    public int getHash() {
        return this.hash;
    }
}
