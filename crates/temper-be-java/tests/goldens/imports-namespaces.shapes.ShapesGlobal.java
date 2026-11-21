package namespaces.shapes;

public final class ShapesGlobal {
    private ShapesGlobal() {}

    public static final int unit;

    public static int twice(int x) {
        return x * 2;
    }

    static {
        unit = 1;
    }
}
