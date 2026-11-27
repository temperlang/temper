package async_turns;

@SuppressWarnings("serial")
public final class Odd extends HalveException {
    public final int n;

    public Odd(int n) {
        this.n = n;
    }

    public String getMessage() {
        return "odd (n: " + this.n + ")";
    }

    public int getN() {
        return this.n;
    }
}
