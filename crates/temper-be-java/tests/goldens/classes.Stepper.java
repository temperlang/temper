package classes;

public final class Stepper implements Limited {
    public final int limit;

    public Stepper(int limit) {
        this.limit = limit;
    }

    public int next(int n) {
        if (n < this.limit) {
            return n + 1;
        } else {
            return this.tooFar();
        }
    }

    public int tooFar() {
        throw new AssertionError("panic");
    }

    public int getLimit() {
        return this.limit;
    }
}
