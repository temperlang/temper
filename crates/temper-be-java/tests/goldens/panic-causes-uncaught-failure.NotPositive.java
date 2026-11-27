package panic_causes_uncaught_failure;

@SuppressWarnings("serial")
final class NotPositive extends Problem {
    public final int n;

    public NotPositive(int n) {
        this.n = n;
    }

    public String getMessage() {
        return "not positive (n: " + this.n + ")";
    }

    public int getN() {
        return this.n;
    }
}
