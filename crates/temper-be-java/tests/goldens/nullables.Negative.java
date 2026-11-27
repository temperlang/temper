package nullables;

@SuppressWarnings("serial")
final class Negative extends Problem {
    public final int n;

    public Negative(int n) {
        this.n = n;
    }

    public String getMessage() {
        return "negative (n: " + this.n + ")";
    }

    public int getN() {
        return this.n;
    }
}
