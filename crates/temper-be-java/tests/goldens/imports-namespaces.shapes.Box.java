package namespaces.shapes;

public final class Box<T> {
    public final T value;

    public Box(T value) {
        this.value = value;
    }

    public T getValue() {
        return this.value;
    }
}
