package errors_undeclared_name;

final class Box {
    public final int value;

    public Box(int value) {
        this.value = value;
    }

    public int get() {
        return this.value;
    }

    public String bad() {
        throw new AssertionError("panic");
    }

    public int getValue() {
        return this.value;
    }
}
