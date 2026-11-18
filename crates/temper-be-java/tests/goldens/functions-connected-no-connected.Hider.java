package work;

public final class Hider {
    private final Hidden hidden;

    public Hider(int i) {
        this.hidden = new Hidden(i);
    }

    public int plus(int j) {
        return WorkGlobal.sum(this.hidden.i, j);
    }

    public int times(int j) {
        return WorkGlobal.prod(this.hidden, j);
    }
}
