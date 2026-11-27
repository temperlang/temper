package broken;

public final class BrokenGlobal {
    private BrokenGlobal() {}

    static {
        if (true) {
            throw new AssertionError("panic");
        }
    }
}
