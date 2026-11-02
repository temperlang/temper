package work;

/** Temper's connected Counter class, with the members Temper would generate. */
public final class Counter {
    private int total;

    public void add(int i) {
        total += i;
    }

    public int total() {
        return total;
    }
}
