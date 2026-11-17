package classes;

final class AnyFailing<T> implements Failing<T> {
    public AnyFailing() {}

    public T fail() {
        throw new AssertionError("panic");
    }
}
