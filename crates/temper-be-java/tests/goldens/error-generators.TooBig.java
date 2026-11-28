package error_generators;

@SuppressWarnings("serial")
final class TooBig extends ErrorGeneratorsException {
    public final int n;

    public TooBig(int n) {
        this.n = n;
    }

    public String getMessage() {
        return "too big (n: " + this.n + ")";
    }

    public int getN() {
        return this.n;
    }
}
