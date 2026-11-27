package panics;

@SuppressWarnings("serial")
final class Positive extends Problem {
    public final int n;

    public Positive(int n) {
        this.n = n;
    }

    public String getMessage() {
        return "positive (n: " + this.n + ")";
    }

    public int getN() {
        return this.n;
    }
}
