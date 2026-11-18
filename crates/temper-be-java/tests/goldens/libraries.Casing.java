package libraries;

public final class Casing {
    public final String name;
    public static final Casing first = new Casing("first");
    public static final Casing last = named("last");
    private static final String mark = "#";

    public boolean eq(Casing other) {
        return this.name.equals(other.name);
    }

    Casing(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }

    static Casing named(String name) {
        return new Casing(name);
    }

    public String getMarked() {
        return Casing.mark + this.name;
    }

    public String getName() {
        return this.name;
    }
}
