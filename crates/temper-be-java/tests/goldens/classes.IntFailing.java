package classes;

final class IntFailing implements Failing<Integer> {
    public IntFailing() {}

    public Integer fail() {
        throw new AssertionError("panic");
    }
}
